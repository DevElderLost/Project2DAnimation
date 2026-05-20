package com.project2d.animation.timeline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;

import java.util.ArrayList;
import java.util.List;

public class AnimationProject {

    // ── Frame ─────────────────────────────────────────────────────────────────
    public static class Frame {
        public Bitmap bitmap;
        public boolean isEmpty = true;

        public Frame(int w, int h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        /** Thumbnail dengan background putih + isi bitmap jika tidak kosong */
        public Bitmap getThumbnail(int tw, int th) {
            if (tw <= 0 || th <= 0) return null;
            Bitmap thumb = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(thumb);
            c.drawColor(Color.WHITE);
            if (!isEmpty && bitmap != null && !bitmap.isRecycled()) {
                Matrix m = new Matrix();
                float sx = (float) tw / bitmap.getWidth();
                float sy = (float) th / bitmap.getHeight();
                float s  = Math.min(sx, sy);
                float dx = (tw - bitmap.getWidth() * s) / 2f;
                float dy = (th - bitmap.getHeight() * s) / 2f;
                m.postScale(s, s);
                m.postTranslate(dx, dy);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                c.drawBitmap(bitmap, m, p);
            }
            return thumb;
        }

        public void clear() {
            if (bitmap != null && !bitmap.isRecycled()) {
                new Canvas(bitmap).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            isEmpty = true;
        }
    }

    // ── Layer ─────────────────────────────────────────────────────────────────
    public static class Layer {
        public String  name;
        public boolean visible  = true;
        public float   opacity  = 1f;
        public final List<Frame> frames = new ArrayList<>();

        public Layer(String name, int frameCount, int docW, int docH) {
            this.name = name;
            for (int i = 0; i < frameCount; i++) frames.add(new Frame(docW, docH));
        }

        public Frame getFrame(int idx) {
            if (idx < 0 || idx >= frames.size()) return null;
            return frames.get(idx);
        }

        public void addFrame(int docW, int docH) {
            frames.add(new Frame(docW, docH));
        }

        public void insertFrame(int idx, int docW, int docH) {
            idx = Math.max(0, Math.min(idx, frames.size()));
            frames.add(idx, new Frame(docW, docH));
        }

        public void removeFrame(int idx) {
            if (frames.size() > 1 && idx >= 0 && idx < frames.size())
                frames.remove(idx);
        }

        public int getFrameCount() { return frames.size(); }

        /** Thumbnail layer = thumbnail frame aktif */
        public Bitmap getLayerThumbnail(int frameIdx, int tw, int th) {
            Frame f = getFrame(frameIdx);
            if (f == null) return null;
            return f.getThumbnail(tw, th);
        }
    }

    // ── Project ───────────────────────────────────────────────────────────────
    private int docW, docH;
    private int fps         = 24;
    private int frameCount  = 8;
    private int currentFrame = 0;
    private int currentLayer = 0;

    private final List<Layer> layers = new ArrayList<>();

    public AnimationProject(int docW, int docH) {
        this.docW = docW;
        this.docH = docH;
        layers.add(new Layer("Layer 1", frameCount, docW, docH));
    }

    public int getDocW()            { return docW; }
    public int getDocH()            { return docH; }
    public int getFps()             { return fps; }
    public int getFrameCount()      { return frameCount; }
    public int getCurrentFrameIdx() { return currentFrame; }
    public int getCurrentLayerIdx() { return currentLayer; }
    public int getLayerCount()      { return layers.size(); }

    public void setFps(int f)  { fps = Math.max(1, Math.min(60, f)); }

    public Layer getLayer(int idx) {
        if (idx < 0 || idx >= layers.size()) return null;
        return layers.get(idx);
    }
    public Layer getCurrentLayer() { return getLayer(currentLayer); }

    public Frame getCurrentFrame() {
        Layer l = getCurrentLayer();
        return l == null ? null : l.getFrame(currentFrame);
    }

    public Frame getFrameAt(int layerIdx, int frameIdx) {
        Layer l = getLayer(layerIdx);
        return l == null ? null : l.getFrame(frameIdx);
    }

    public void setCurrentFrame(int idx) {
        currentFrame = Math.max(0, Math.min(frameCount - 1, idx));
    }
    public void setCurrentLayer(int idx) {
        currentLayer = Math.max(0, Math.min(layers.size() - 1, idx));
    }

    public void nextFrame() { setCurrentFrame(currentFrame + 1); }
    public void prevFrame() { setCurrentFrame(currentFrame - 1); }

    public void addFrame() {
        frameCount++;
        for (Layer l : layers) l.addFrame(docW, docH);
    }

    public void insertFrameAfterCurrent() {
        int at = currentFrame + 1;
        frameCount++;
        for (Layer l : layers) l.insertFrame(at, docW, docH);
        setCurrentFrame(at);
    }

    public void removeCurrentFrame() {
        if (frameCount <= 1) return;
        for (Layer l : layers) l.removeFrame(currentFrame);
        frameCount--;
        if (currentFrame >= frameCount) currentFrame = frameCount - 1;
    }

    public void addLayer() {
        addLayer(false);
    }

    public void addLayer(boolean isBackground) {
        String name = isBackground
            ? "BG " + (layers.size() + 1)
            : "Layer " + (layers.size() + 1);
        Layer l = new Layer(name, frameCount, docW, docH);
        if (isBackground) {
            // Background layer ditambah di paling bawah (index 0)
            layers.add(0, l);
            if (currentLayer >= 0) currentLayer++; // shift index agar tetap pointing layer yang sama
        } else {
            layers.add(l);
        }
    }

    public void removeLayer(int idx) {
        if (layers.size() > 1 && idx >= 0 && idx < layers.size()) {
            layers.remove(idx);
            if (currentLayer >= layers.size()) currentLayer = layers.size() - 1;
        }
    }

    /** Composite semua layer ke satu bitmap */
    public Bitmap compositeFrame(int frameIdx) {
        Bitmap out = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(out);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (Layer l : layers) {
            if (!l.visible) continue;
            Frame f = l.getFrame(frameIdx);
            if (f == null || f.isEmpty || f.bitmap == null || f.bitmap.isRecycled()) continue;
            p.setAlpha((int)(l.opacity * 255));
            c.drawBitmap(f.bitmap, 0, 0, p);
        }
        return out;
    }

    public void resize(int newW, int newH) {
        docW = newW; docH = newH;
        for (Layer l : layers)
            for (Frame f : l.frames) {
                Bitmap nb = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
                if (!f.isEmpty && f.bitmap != null && !f.bitmap.isRecycled())
                    new Canvas(nb).drawBitmap(f.bitmap, 0, 0, null);
                if (f.bitmap != null && !f.bitmap.isRecycled()) f.bitmap.recycle();
                f.bitmap = nb;
            }
    }
}

// PATCH: tambah di bawah method addLayer() yang sudah ada
// Ini tidak bisa langsung append, jadi kita overwrite addLayer()
