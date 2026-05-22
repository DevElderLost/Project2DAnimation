package com.project2d.animation.timeline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;

import java.util.ArrayList;
import java.util.List;

/**
 * Model data animasi.
 *
 * Exposure:
 *  Setiap Frame punya "exposure" (jumlah playback-tick yang ditampilkan).
 *  exposure=1 → 1 tick (normal), exposure=2 → 2 tick (held 2 frame), dst.
 *
 *  Timeline "tick" = slot yang terlihat di timeline.
 *  Satu Frame dengan exposure=2 menempati 2 slot visual.
 *
 *  getFrameCount() → jumlah Frame unik (gambar)
 *  getTotalTicks() → total slot visual di timeline
 *  getFrameAtTick(tick) → Frame yang tampil di tick tertentu
 *  getTickStart(frameIdx) → tick awal frame tersebut
 */
public class AnimationProject {

    // ── Frame ─────────────────────────────────────────────────────────────────
    public static class Frame {
        public Bitmap  bitmap;
        public boolean isEmpty   = true;
        public int     exposure  = 1;   // berapa tick frame ini ditampilkan

        public Frame(int w, int h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        public Bitmap getThumbnail(int tw, int th) {
            if (tw<=0||th<=0) return null;
            Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas c   = new Canvas(out);
            c.drawColor(Color.WHITE);
            if (!isEmpty && bitmap!=null && !bitmap.isRecycled()) {
                Matrix m = new Matrix();
                float sx=tw/(float)bitmap.getWidth(), sy=th/(float)bitmap.getHeight();
                float s=Math.min(sx,sy);
                m.postScale(s,s);
                m.postTranslate((tw-bitmap.getWidth()*s)/2f,(th-bitmap.getHeight()*s)/2f);
                c.drawBitmap(bitmap,m,new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG));
            }
            return out;
        }

        public void clear() {
            if (bitmap!=null&&!bitmap.isRecycled())
                new Canvas(bitmap).drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            isEmpty=true;
        }
    }

    // ── Layer ─────────────────────────────────────────────────────────────────
    /** Blend mode untuk compositing layer */
    public enum BlendMode {
        NORMAL("Normal"),
        MULTIPLY("Multiply"),
        ADD("Add"),
        SCREEN("Screen"),
        OVERLAY("Overlay"),
        DARKEN("Darken"),
        LIGHTEN("Lighten"),
        COLOR_DODGE("Color Dodge"),
        COLOR_BURN("Color Burn"),
        HARD_LIGHT("Hard Light"),
        SOFT_LIGHT("Soft Light"),
        DIFFERENCE("Difference"),
        EXCLUSION("Exclusion");

        public final String label;
        BlendMode(String l){ label=l; }

        /** Konversi ke PorterDuff.Mode yang tersedia di Android */
        public android.graphics.PorterDuff.Mode toPorterDuff(){
            switch(this){
                case MULTIPLY:    return android.graphics.PorterDuff.Mode.MULTIPLY;
                case ADD:         return android.graphics.PorterDuff.Mode.ADD;
                case SCREEN:      return android.graphics.PorterDuff.Mode.SCREEN;
                case OVERLAY:     return android.graphics.PorterDuff.Mode.OVERLAY;
                case DARKEN:      return android.graphics.PorterDuff.Mode.DARKEN;
                case LIGHTEN:     return android.graphics.PorterDuff.Mode.LIGHTEN;
                case DIFFERENCE:  return android.graphics.PorterDuff.Mode.XOR; // closest
                default:          return android.graphics.PorterDuff.Mode.SRC_OVER;
            }
        }
    }

    public static class Layer {
        public String    name;
        public boolean   visible   = true;
        public float     opacity   = 1f;
        public BlendMode blendMode = BlendMode.NORMAL;
        public final List<Frame> frames = new ArrayList<>();

        public Layer(String name, int frameCount, int docW, int docH) {
            this.name = name;
            for (int i=0; i<frameCount; i++) frames.add(new Frame(docW,docH));
        }

        public Frame getFrame(int idx) {
            if (idx<0||idx>=frames.size()) return null;
            return frames.get(idx);
        }

        /** Frame yang tampil pada tick tertentu */
        public Frame getFrameAtTick(int tick) {
            int t=0;
            for (Frame f : frames) {
                if (tick>=t && tick<t+f.exposure) return f;
                t+=f.exposure;
            }
            return frames.isEmpty() ? null : frames.get(frames.size()-1);
        }

        /** Total tick (slot visual) semua frame di layer ini */
        public int getTotalTicks() {
            int t=0; for (Frame f:frames) t+=f.exposure; return t;
        }

        /** Tick pertama dari frame[idx] */
        public int getTickStart(int frameIdx) {
            int t=0;
            for (int i=0;i<Math.min(frameIdx,frames.size());i++) t+=frames.get(i).exposure;
            return t;
        }

        public void addFrame(int docW,int docH)            { frames.add(new Frame(docW,docH)); }
        public void insertFrame(int idx,int docW,int docH) { idx=Math.max(0,Math.min(idx,frames.size())); frames.add(idx,new Frame(docW,docH)); }
        public void removeFrame(int idx)                   { if(frames.size()>1&&idx>=0&&idx<frames.size()) frames.remove(idx); }
        public int  getFrameCount()                        { return frames.size(); }

        public Bitmap getLayerThumbnail(int frameIdx,int tw,int th){
            Frame f=getFrameForDisplay(frameIdx); return f==null?null:f.getThumbnail(tw,th);
        }

        /**
         * Dapatkan frame yang harus ditampilkan pada frameIdx tertentu,
         * dengan mempertimbangkan exposure (hold frames).
         *
         * Contoh: Layer punya 2 frame, frame[0].exposure=3, frame[1].exposure=2
         *   tick 0,1,2 → frame[0]
         *   tick 3,4   → frame[1]
         *   tick 5+    → frame[1] (frame terakhir tetap ditampilkan = hold to end)
         *
         * @param globalFrameIdx index frame global dari project (bukan tick)
         */
        public Frame getFrameForDisplay(int globalFrameIdx) {
            if (frames.isEmpty()) return null;

            // Konversi globalFrameIdx ke "tick" — anggap 1 frame global = 1 tick
            int tick = globalFrameIdx;

            int t = 0;
            Frame lastFrame = frames.get(0);
            for (Frame f : frames) {
                lastFrame = f; // selalu update lastFrame
                if (tick >= t && tick < t + f.exposure) {
                    return f; // tick ini ada di exposure range frame ini
                }
                t += f.exposure;
            }

            // tick melebihi semua exposure — kembalikan frame TERAKHIR
            // Ini yang membuat layer pendek "hold" sampai akhir timeline
            return lastFrame;
        }
    }

    // ── Project ───────────────────────────────────────────────────────────────
    private int docW,docH;
    private int fps=24;
    private int frameCount=8;           // jumlah Frame unik
    private int currentFrame=0;         // index Frame unik
    private int currentLayer=0;
    private final List<Layer> layers=new ArrayList<>();

    public AnimationProject(int docW,int docH){
        this.docW=docW; this.docH=docH;
        layers.add(new Layer("Layer 1",frameCount,docW,docH));
    }

    public int getDocW()            { return docW; }
    public int getDocH()            { return docH; }
    public int getFps()             { return fps; }
    public int getFrameCount()      { return frameCount; }
    public int getCurrentFrameIdx() { return currentFrame; }
    public int getCurrentLayerIdx() { return currentLayer; }
    public int getLayerCount()      { return layers.size(); }
    public void setFps(int f)       { fps=Math.max(1,Math.min(60,f)); }

    /** Total tick dari layer terpanjang (untuk lebar timeline) */
    public int getTotalTicks() {
        int max=0;
        for (Layer l:layers) max=Math.max(max,l.getTotalTicks());
        return Math.max(max,frameCount);
    }

    public Layer getLayer(int idx)    { return(idx<0||idx>=layers.size())?null:layers.get(idx); }
    public Layer getCurrentLayer()    { return getLayer(currentLayer); }
    public Frame getCurrentFrame(){
        Layer l=getCurrentLayer();
        if(l==null) return null;
        // Jika layer lebih pendek dari currentFrame, kembalikan frame terakhir
        int idx=Math.min(currentFrame, l.getFrameCount()-1);
        return l.getFrame(idx);
    }
    public Frame getFrameAt(int li,int fi){ Layer l=getLayer(li); return l==null?null:l.getFrame(fi); }

    public void setCurrentFrame(int idx){
        // Clamp ke ukuran layer aktif agar tidak out of bounds
        Layer l=getCurrentLayer();
        int maxFrame=l!=null?l.getFrameCount()-1:frameCount-1;
        currentFrame=Math.max(0,Math.min(maxFrame,idx));
    }
    public void setCurrentLayer(int idx){ currentLayer=Math.max(0,Math.min(layers.size()-1,idx)); }
    public void nextFrame()             { setCurrentFrame(currentFrame+1); }
    public void prevFrame()             { setCurrentFrame(currentFrame-1); }

    // ── Exposure ──────────────────────────────────────────────────────────────

    /** Naikkan exposure frame aktif di layer aktif sebesar +1 */
    public void increaseCurrentExposure(){
        Layer l=getCurrentLayer(); if(l==null) return;
        Frame f=l.getFrame(currentFrame); if(f==null) return;
        f.exposure=Math.min(f.exposure+1, 99);
    }

    /** Turunkan exposure frame aktif (min 1) */
    public void decreaseCurrentExposure(){
        Layer l=getCurrentLayer(); if(l==null) return;
        Frame f=l.getFrame(currentFrame); if(f==null) return;
        f.exposure=Math.max(1,f.exposure-1);
    }

    public int getCurrentExposure(){
        Layer l=getCurrentLayer(); if(l==null) return 1;
        Frame f=l.getFrame(currentFrame); return f==null?1:f.exposure;
    }

    // ── Frame edit ────────────────────────────────────────────────────────────
    /**
     * Tambah frame HANYA ke layer aktif.
     * Layer lain tidak berubah — frameCount global diupdate ke max semua layer.
     */
    public void addFrame(){
        Layer l=getCurrentLayer(); if(l==null) return;
        l.addFrame(docW,docH);
        recalcFrameCount();
    }

    /**
     * Insert frame setelah frame aktif HANYA di layer aktif.
     */
    public void insertFrameAfterCurrent(){
        Layer l=getCurrentLayer(); if(l==null) return;
        int at=currentFrame+1;
        l.insertFrame(at,docW,docH);
        recalcFrameCount();
        setCurrentFrame(at);
    }

    /**
     * Hapus frame aktif HANYA dari layer aktif.
     */
    public void removeCurrentFrame(){
        Layer l=getCurrentLayer(); if(l==null) return;
        if(l.getFrameCount()<=1) return;
        l.removeFrame(currentFrame);
        recalcFrameCount();
        if(currentFrame>=l.getFrameCount()) currentFrame=l.getFrameCount()-1;
    }

    /** Public wrapper untuk recalcFrameCount — dipakai dari luar */
    public void recalcFrameCountPublic(){ recalcFrameCount(); }

    /**
     * Hitung ulang frameCount = jumlah frame terbanyak di antara semua layer.
     * Ini menentukan lebar timeline.
     */
    private void recalcFrameCount(){
        int max=0;
        for(Layer l:layers) max=Math.max(max,l.getFrameCount());
        frameCount=Math.max(1,max);
    }

    // ── Layer edit ────────────────────────────────────────────────────────────
    public void addLayer(){ addLayer(false); }
    public void addLayer(boolean isBackground){
        String name=isBackground?"BG "+(layers.size()+1):"Layer "+(layers.size()+1);
        Layer l=new Layer(name,frameCount,docW,docH);
        if(isBackground){ layers.add(0,l); if(currentLayer>=0) currentLayer++; }
        else             layers.add(l);
    }

    public void removeLayer(int idx){
        if(layers.size()>1&&idx>=0&&idx<layers.size()){
            layers.remove(idx);
            if(currentLayer>=layers.size()) currentLayer=layers.size()-1;
        }
    }

    /** Composite semua layer untuk playback dengan blend mode dan exposure hold */
    public Bitmap compositeFrame(int frameIdx){
        Bitmap out=Bitmap.createBitmap(docW,docH,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out); c.drawColor(Color.WHITE);
        for(Layer l:layers){
            if(!l.visible) continue;
            // getFrameForDisplay mengembalikan frame yang benar dengan exposure hold
            Frame f=l.getFrameForDisplay(frameIdx);
            if(f==null||f.isEmpty||f.bitmap==null||f.bitmap.isRecycled()) continue;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            p.setAlpha((int)(l.opacity*255));
            if(l.blendMode!=BlendMode.NORMAL)
                p.setXfermode(new android.graphics.PorterDuffXfermode(l.blendMode.toPorterDuff()));
            c.drawBitmap(f.bitmap,0,0,p);
            p.setXfermode(null);
        }
        return out;
    }

    /**
     * Composite menggunakan tick (playback exposure-aware).
     * Layer yang lebih pendek "hold" frame terakhirnya.
     */
    public Bitmap compositeFrameAtTick(int tick){
        Bitmap out=Bitmap.createBitmap(docW,docH,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out); c.drawColor(Color.WHITE);
        for(Layer l:layers){
            if(!l.visible) continue;
            // getFrameAtTick sudah handle exposure, tapi tidak handle "hold to end"
            // jadi kita pakai getFrameForDisplay dengan tick sebagai index
            Frame f=l.getFrameForDisplay(tick);
            if(f==null||f.isEmpty||f.bitmap==null||f.bitmap.isRecycled()) continue;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            p.setAlpha((int)(l.opacity*255));
            if(l.blendMode!=BlendMode.NORMAL)
                p.setXfermode(new android.graphics.PorterDuffXfermode(l.blendMode.toPorterDuff()));
            c.drawBitmap(f.bitmap,0,0,p);
            p.setXfermode(null);
        }
        return out;
    }

    public void resize(int nW,int nH){
        docW=nW; docH=nH;
        for(Layer l:layers)
            for(Frame f:l.frames){
                Bitmap nb=Bitmap.createBitmap(nW,nH,Bitmap.Config.ARGB_8888);
                if(!f.isEmpty&&f.bitmap!=null&&!f.bitmap.isRecycled())
                    new Canvas(nb).drawBitmap(f.bitmap,0,0,null);
                if(f.bitmap!=null&&!f.bitmap.isRecycled()) f.bitmap.recycle();
                f.bitmap=nb;
            }
    }
}
