package com.project2d.animation.timeline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import java.util.ArrayList;
import java.util.List;

/**
 * Model data animasi: Layer → Frame → Bitmap.
 * Satu Layer berisi banyak Frame, tiap Frame punya satu Bitmap.
 */
public class AnimationProject {

    // ── Frame ─────────────────────────────────────────────────────────────────
    public static class Frame {
        public Bitmap bitmap;
        public boolean isEmpty = true;

        public Frame(int w, int h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        public Bitmap getThumbnail(int tw, int th) {
            Bitmap thumb = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(thumb);
            c.drawColor(Color.WHITE);
            if (!isEmpty && bitmap != null) {
                float sx = (float)tw/bitmap.getWidth();
                float sy = (float)th/bitmap.getHeight();
                float s  = Math.min(sx, sy);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postScale(s, s);
                c.drawBitmap(bitmap, m, null);
            }
            return thumb;
        }

        public void clear() {
            if (bitmap != null) {
                Canvas c = new Canvas(bitmap);
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            isEmpty = true;
        }
    }

    // ── Layer ─────────────────────────────────────────────────────────────────
    public static class Layer {
        public String name;
        public boolean visible = true;
        public float opacity   = 1f;
        public List<Frame> frames = new ArrayList<>();

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
            if (idx < 0) idx = 0;
            if (idx > frames.size()) idx = frames.size();
            frames.add(idx, new Frame(docW, docH));
        }

        public void removeFrame(int idx) {
            if (frames.size() > 1 && idx >= 0 && idx < frames.size())
                frames.remove(idx);
        }

        public int getFrameCount() { return frames.size(); }
    }

    // ── Project ───────────────────────────────────────────────────────────────
    private int docW, docH;
    private int fps = 24;
    private int frameCount = 12;
    private int currentFrame = 0;
    private int currentLayer = 0;

    private final List<Layer> layers = new ArrayList<>();

    public AnimationProject(int docW, int docH) {
        this.docW = docW;
        this.docH = docH;
        Layer layer = new Layer("Layer 1", frameCount, docW, docH);
        layers.add(layer);
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public int getDocW()       { return docW; }
    public int getDocH()       { return docH; }
    public int getFps()        { return fps; }
    public int getFrameCount() { return frameCount; }
    public int getCurrentFrameIdx() { return currentFrame; }
    public int getCurrentLayerIdx() { return currentLayer; }
    public int getLayerCount() { return layers.size(); }

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

    // ── Navigation ────────────────────────────────────────────────────────────
    public void setCurrentFrame(int idx) {
        currentFrame = Math.max(0, Math.min(frameCount - 1, idx));
    }

    public void setCurrentLayer(int idx) {
        currentLayer = Math.max(0, Math.min(layers.size()-1, idx));
    }

    public void nextFrame() { setCurrentFrame(currentFrame + 1); }
    public void prevFrame() { setCurrentFrame(currentFrame - 1); }

    // ── Edit ──────────────────────────────────────────────────────────────────
    public void addFrame() {
        frameCount++;
        for (Layer l : layers) l.addFrame(docW, docH);
    }

    public void insertFrameAfterCurrent() {
        frameCount++;
        int insertAt = currentFrame + 1;
        for (Layer l : layers) l.insertFrame(insertAt, docW, docH);
        setCurrentFrame(insertAt);
    }

    public void removeCurrentFrame() {
        if (frameCount <= 1) return;
        for (Layer l : layers) l.removeFrame(currentFrame);
        frameCount--;
        if (currentFrame >= frameCount) currentFrame = frameCount - 1;
    }

    public void addLayer() {
        Layer l = new Layer("Layer " + (layers.size()+1), frameCount, docW, docH);
        layers.add(l);
    }

    public void removeLayer(int idx) {
        if (layers.size() > 1 && idx >= 0 && idx < layers.size()) {
            layers.remove(idx);
            if (currentLayer >= layers.size()) currentLayer = layers.size()-1;
        }
    }

    /** Composite semua layer ke satu bitmap untuk frame tertentu */
    public Bitmap compositeFrame(int frameIdx) {
        Bitmap out = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        for (Layer l : layers) {
            if (!l.visible) continue;
            Frame f = l.getFrame(frameIdx);
            if (f == null || f.isEmpty || f.bitmap == null) continue;
            p.setAlpha((int)(l.opacity * 255));
            c.drawBitmap(f.bitmap, 0, 0, p);
        }
        return out;
    }

    public void resize(int newW, int newH) {
        this.docW = newW; this.docH = newH;
        for (Layer l : layers)
            for (Frame f : l.frames) {
                Bitmap nb = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
                if (!f.isEmpty && f.bitmap != null) new Canvas(nb).drawBitmap(f.bitmap, 0, 0, null);
                if (f.bitmap != null && !f.bitmap.isRecycled()) f.bitmap.recycle();
                f.bitmap = nb;
            }
    }
}
