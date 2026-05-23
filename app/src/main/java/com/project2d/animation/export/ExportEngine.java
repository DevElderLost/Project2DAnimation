package com.project2d.animation.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import com.project2d.animation.timeline.AnimationProject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportEngine {
    public interface ProgressCallback {
        void onProgress(String status, int current, int total);
    }

    public enum VideoCodec { H264, H265 }

    private final Context context;

    public ExportEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public void exportCurrentFrame(AnimationProject project, Uri outputUri, ProgressCallback callback) throws Exception {
        Bitmap bitmap = project.compositeFrame(project.getCurrentFrameIdx());
        if (bitmap == null) throw new IOException("Tidak ada frame untuk diexport");
        try (OutputStream out = context.getContentResolver().openOutputStream(outputUri)) {
            if (out == null) throw new IOException("Tidak bisa membuka target output");
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Gagal menulis PNG");
            }
        } finally {
            bitmap.recycle();
        }
        callback.onProgress("Selesai", 1, 1);
    }

    public void exportSequenceZip(AnimationProject project, Uri outputUri, ProgressCallback callback) throws Exception {
        List<Bitmap> frames = buildPlaybackFrames(project);
        if (frames.isEmpty()) throw new IOException("Tidak ada frame untuk diexport");

        try (OutputStream out = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zip = out == null ? null : new ZipOutputStream(out)) {
            if (zip == null) throw new IOException("Tidak bisa membuka target output");
            int total = frames.size();
            for (int i = 0; i < total; i++) {
                Bitmap bitmap = frames.get(i);
                String name = String.format(Locale.US, "%02d.png", i);
                zip.putNextEntry(new ZipEntry(name));
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                    throw new IOException("Gagal menulis PNG ke zip");
                }
                zip.closeEntry();
                callback.onProgress("Menulis frame " + (i + 1), i + 1, total);
                bitmap.recycle();
            }
        }
    }

    public void exportVideo(AnimationProject project, Uri outputUri, String container, VideoCodec codec, ProgressCallback callback) throws Exception {
        List<Bitmap> frames = buildPlaybackFrames(project);
        if (frames.isEmpty()) throw new IOException("Tidak ada frame untuk diexport");

        int width = frames.get(0).getWidth();
        int height = frames.get(0).getHeight();
        String mime = codec == VideoCodec.H265 ? "video/hevc" : "video/avc";
        MediaCodecInfo codecInfo = selectCodec(mime);
        if (codecInfo == null) {
            throw new IOException("Codec tidak tersedia: " + mime);
        }

        int colorFormat = selectColorFormat(codecInfo, mime);
        int fps = Math.max(1, project.getFps());
        int bitrate = Math.max(1_000_000, width * height * fps * 2);

        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        if (codec == VideoCodec.H265) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4_1);
        }

        android.os.ParcelFileDescriptor pfd = null;
        MediaMuxer muxer = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(outputUri, "rw");
            if (pfd == null) throw new IOException("Tidak bisa membuka target video");

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            boolean isMkv = "mkv".equalsIgnoreCase(container);
            int outputFormat = isMkv ? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            muxer = new MediaMuxer(pfd.getFileDescriptor(), outputFormat);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int trackIndex = -1;
            long presentationUs = 0;
            long frameDurationUs = 1_000_000L / fps;
            int total = frames.size();

            for (int i = 0; i < total; i++) {
                Bitmap frame = frames.get(i);
                byte[] yuv = bitmapToYuv(frame, colorFormat);
                int inputIndex = encoder.dequeueInputBuffer(10000);
                if (inputIndex < 0) {
                    drainEncoder(encoder, muxer, info, () -> trackIndex, newTrack -> trackIndex = newTrack);
                    inputIndex = encoder.dequeueInputBuffer(10000);
                }
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputIndex);
                if (inputBuffer == null) throw new IOException("Input buffer codec null");
                inputBuffer.clear();
                inputBuffer.put(yuv);
                encoder.queueInputBuffer(inputIndex, 0, yuv.length, presentationUs, 0);
                presentationUs += frameDurationUs;
                drainEncoder(encoder, muxer, info, () -> trackIndex, newTrack -> trackIndex = newTrack);
                callback.onProgress("Mengekspor frame " + (i + 1), i + 1, total);
                frame.recycle();
            }

            int inputIndex = encoder.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            drainEncoder(encoder, muxer, info, () -> trackIndex, newTrack -> trackIndex = newTrack);

            if (muxer != null) {
                muxer.stop();
            }
            callback.onProgress("Selesai", total, total);
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception ignored) {
                }
                encoder.release();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private List<Bitmap> buildPlaybackFrames(AnimationProject project) {
        int totalTicks = Math.max(1, project.getTotalTicks());
        List<Bitmap> frames = new ArrayList<>(totalTicks);
        for (int tick = 0; tick < totalTicks; tick++) {
            frames.add(project.compositeFrameAtTick(tick));
        }
        return frames;
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (mimeType.equalsIgnoreCase(type)) {
                    return info;
                }
            }
        }
        return null;
    }

    public boolean supportsH265() {
        return selectCodec("video/hevc") != null;
    }

    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mimeType);
        int[] formats = caps.colorFormats;
        for (int format : formats) {
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    || format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    || format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                return format;
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private void drainEncoder(MediaCodec encoder, MediaMuxer muxer, MediaCodec.BufferInfo info, java.util.function.IntSupplier trackSupplier, java.util.function.IntConsumer trackConsumer) {
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(info, 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = encoder.getOutputFormat();
                if (trackSupplier.getAsInt() == -1) {
                    int trackIndex = muxer.addTrack(format);
                    trackConsumer.accept(trackIndex);
                    muxer.start();
                }
                continue;
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            }
            if (outputIndex < 0) {
                continue;
            }

            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);
            if (outputBuffer == null) continue;
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (trackSupplier.getAsInt() != -1) {
                    muxer.writeSampleData(trackSupplier.getAsInt(), outputBuffer, info);
                }
                encoder.releaseOutputBuffer(outputIndex, false);
                return;
            }
            if (info.size > 0 && trackSupplier.getAsInt() != -1) {
                muxer.writeSampleData(trackSupplier.getAsInt(), outputBuffer, info);
            }
            encoder.releaseOutputBuffer(outputIndex, false);
        }
    }

    private byte[] bitmapToYuv(Bitmap bitmap, int colorFormat) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[(width * height * 3) / 2];
        int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = pixels[j * width + i];
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));
            }
        }

        for (int j = 0; j < height / 2; j++) {
            for (int i = 0; i < width / 2; i++) {
                int idx = 2 * j * width + 2 * i;
                int pixel1 = pixels[idx];
                int pixel2 = pixels[idx + 1];
                int pixel3 = pixels[idx + width];
                int pixel4 = pixels[idx + width + 1];
                int r = (Color.red(pixel1) + Color.red(pixel2) + Color.red(pixel3) + Color.red(pixel4)) / 4;
                int g = (Color.green(pixel1) + Color.green(pixel2) + Color.green(pixel3) + Color.green(pixel4)) / 4;
                int b = (Color.blue(pixel1) + Color.blue(pixel2) + Color.blue(pixel3) + Color.blue(pixel4)) / 4;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, v));
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
                } else {
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, v));
                }
            }
        }

        return yuv;
    }
}
