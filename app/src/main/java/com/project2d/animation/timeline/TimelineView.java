package com.project2d.animation.timeline;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.ui.ImGuiTheme;

/**
 * Timeline View — fixes:
 *  - Thumbnail diambil langsung dari frame.bitmap (tidak cache stale)
 *  - Tombol [-] di kiri bawah thumbnail frame aktif untuk hapus frame
 *  - Tombol [+] di kanan bawah thumbnail frame aktif untuk tambah frame
 *  - Layer panel: thumbnail layer + tombol [+] tambah layer + tombol [x] hapus layer
 *  - Canvas rotate fix: trySnapAngle TIDAK ada di sini (hanya di AnimationCanvasView)
 */
public class TimelineView extends View {

    public interface OnTimelineEvent {
        void onFrameChanged(int frameIdx);
        void onLayerChanged(int layerIdx);
        void onFrameAdded();
        void onFrameRemoved();
        void onLayerAdded();
        void onLayerRemoved(int layerIdx);
        void onPlayStateChanged(boolean playing);
    }

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static final int HEADER_H_DP  = 36;
    private static final int LAYER_W_DP   = 100;
    private static final int FRAME_W_DP   = 56;
    private static final int FRAME_H_DP   = 48;
    private static final int ROW_H_DP     = 58;
    private static final int BTN_SZ_DP    = 14;

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint pBg       = new Paint();
    private final Paint pHeader   = new Paint();
    private final Paint pBorder   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSubText  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameBg  = new Paint();
    private final Paint pFrameAct = new Paint();
    private final Paint pFrameHov = new Paint();
    private final Paint pThumb    = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint pBtn      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnDel   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerBg  = new Paint();
    private final Paint pLayerSel = new Paint();
    private final Paint pSep      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAddLayer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumbBg  = new Paint();

    private float density;
    private AnimationProject project;
    private OnTimelineEvent listener;

    // ── Playback ──────────────────────────────────────────────────────────────
    private boolean isPlaying = false;
    private boolean isLooping = true;
    private final Handler playHandler   = new Handler(Looper.getMainLooper());
    private final Runnable playRunnable = this::tickPlayback;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private float scrollX=0f, scrollY=0f;
    private float lastTouchX=0f, lastTouchY=0f;
    private boolean scrolling=false;

    // ── Hit rects ─────────────────────────────────────────────────────────────
    private final RectF btnPlay     = new RectF();
    private final RectF btnStop     = new RectF();
    private final RectF btnPrev     = new RectF();
    private final RectF btnNext     = new RectF();
    private final RectF btnLoop     = new RectF();
    private final RectF btnAddLayer = new RectF();

    // Per-frame mini buttons (only for active frame in active layer)
    private final RectF frameAddBtn = new RectF(); // [+] kanan bawah
    private final RectF frameDelBtn = new RectF(); // [-] kiri bawah

    // Per-layer buttons
    private static final int MAX_LAYERS = 20;
    private final RectF[] layerDelBtns = new RectF[MAX_LAYERS];

    private int hoveredFrame=-1;

    public TimelineView(Context c)               { super(c); init(); }
    public TimelineView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        for (int i=0; i<MAX_LAYERS; i++) layerDelBtns[i] = new RectF();

        pBg.setColor(0xFF15151F);
        pHeader.setColor(0xFF0E0E1A);
        pBorder.setColor(0xFF3A3A5A);    pBorder.setStyle(Paint.Style.STROKE); pBorder.setStrokeWidth(1f);
        pText.setColor(0xFFCCCCDD);      pText.setTextSize(12*density); pText.setTypeface(Typeface.MONOSPACE);
        pSubText.setColor(0xFF777788);   pSubText.setTextSize(9*density); pSubText.setTypeface(Typeface.MONOSPACE);
        pFrameBg.setColor(0xFF1E1E2E);
        pFrameAct.setColor(0xFF2A2A50);
        pFrameHov.setColor(0xFF22223A);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);      pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED); pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(0xFFFFFFFF);      pBtnT.setTextSize(10*density); pBtnT.setTypeface(Typeface.MONOSPACE); pBtnT.setAntiAlias(true);
        pBtnDel.setColor(0xFF8B2020);    pBtnDel.setStyle(Paint.Style.FILL);
        pLayerBg.setColor(0xFF181828);
        pLayerSel.setColor(0xFF252545);
        pSep.setColor(0xFF252535);       pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pCursor.setColor(0xFFFF3333);    pCursor.setStyle(Paint.Style.STROKE); pCursor.setStrokeWidth(2f);
        pAddLayer.setColor(0xFF1E4020);  pAddLayer.setStyle(Paint.Style.FILL);
        pThumbBg.setColor(Color.WHITE);

        setClickable(true);
        setWillNotDraw(false);
    }

    public void setProject(AnimationProject p)       { project=p; invalidate(); }
    public void setOnTimelineEvent(OnTimelineEvent l) { listener=l; }

    // ── Playback ──────────────────────────────────────────────────────────────

    private void tickPlayback() {
        if (!isPlaying || project==null) return;
        int next = project.getCurrentFrameIdx()+1;
        if (next >= project.getFrameCount()) {
            if (isLooping) next=0; else { stopPlayback(); return; }
        }
        project.setCurrentFrame(next);
        if (listener!=null) listener.onFrameChanged(next);
        invalidate();
        playHandler.postDelayed(playRunnable, 1000L/Math.max(1,project.getFps()));
    }

    public void startPlayback() {
        if (isPlaying) return;
        isPlaying=true;
        if (listener!=null) listener.onPlayStateChanged(true);
        playHandler.post(playRunnable); invalidate();
    }

    public void stopPlayback() {
        isPlaying=false;
        playHandler.removeCallbacks(playRunnable);
        if (listener!=null) listener.onPlayStateChanged(false);
        invalidate();
    }

    public boolean isPlaying() { return isPlaying; }

    // ── onDraw ────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (project==null) return;
        int w=getWidth(), h=getHeight();
        float hH   = HEADER_H_DP*density;
        float lW   = LAYER_W_DP*density;
        float fW   = FRAME_W_DP*density;
        float fH   = FRAME_H_DP*density;
        float rH   = ROW_H_DP*density;
        float pad  = 4*density;
        float bSz  = BTN_SZ_DP*density;

        // Background
        canvas.drawRect(0,0,w,h,pBg);

        // ── Header ────────────────────────────────────────────────────────────
        canvas.drawRect(0,0,w,hH,pHeader);
        drawHeaderControls(canvas,w,hH);
        canvas.drawLine(0,hH,w,hH,pBorder);

        // ── Layer panel background ─────────────────────────────────────────
        canvas.drawRect(0,hH,lW,h,pLayerBg);
        canvas.drawLine(lW,hH,lW,h,pBorder);

        // Tombol [+] tambah layer — pojok kiri bawah header
        btnAddLayer.set(lW-bSz*2-4*density, hH+4*density, lW-4*density, hH+bSz+4*density);
        canvas.drawRoundRect(btnAddLayer,3,3,pAddLayer);
        drawCentered(canvas,"+",btnAddLayer,pBtnT);

        // Clip content area
        canvas.save();
        canvas.clipRect(0,hH,w,h);

        int layerCount = project.getLayerCount();
        for (int li=0; li<layerCount; li++) {
            float ly = hH + li*rH - scrollY;
            if (ly+rH<hH || ly>h) continue;

            AnimationProject.Layer layer = project.getLayer(li);
            boolean selLayer = (li==project.getCurrentLayerIdx());

            // Layer row background
            canvas.drawRect(0, ly, lW, ly+rH, selLayer?pLayerSel:pLayerBg);
            canvas.drawLine(0, ly+rH, lW, ly+rH, pSep);

            // Layer thumbnail (kiri, ukuran kecil)
            float thumbSz = rH-pad*2;
            float thumbX  = pad;
            float thumbY  = ly+pad;
            Bitmap layerThumb = layer.getLayerThumbnail(project.getCurrentFrameIdx(),(int)thumbSz,(int)thumbSz);
            if (layerThumb!=null) {
                canvas.drawRect(thumbX,thumbY,thumbX+thumbSz,thumbY+thumbSz,pThumbBg);
                canvas.drawBitmap(layerThumb,thumbX,thumbY,pThumb);
                canvas.drawRect(thumbX,thumbY,thumbX+thumbSz,thumbY+thumbSz,pBorder);
            }

            // Visibility dot
            float dotX = thumbX+thumbSz+6*density;
            float dotY = ly+rH/2f;
            Paint dotP = new Paint(Paint.ANTI_ALIAS_FLAG); dotP.setStyle(Paint.Style.FILL);
            dotP.setColor(layer.visible?0xFF44BB44:0xFF555566);
            canvas.drawCircle(dotX,dotY,4*density,dotP);

            // Layer name
            Paint.FontMetrics fm = pText.getFontMetrics();
            float ty = ly+rH/2f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(truncate(layer.name,8), dotX+8*density, ty, pText);

            // [x] delete layer button — pojok kanan
            if (li < MAX_LAYERS) {
                layerDelBtns[li].set(lW-bSz-pad, ly+pad, lW-pad, ly+pad+bSz);
                canvas.drawRoundRect(layerDelBtns[li],2,2,pBtnDel);
                drawCentered(canvas,"x",layerDelBtns[li],pBtnT);
            }

            // ── Frame strip ───────────────────────────────────────────────────
            canvas.save();
            canvas.clipRect(lW,hH,w,h);

            int frameCount = project.getFrameCount();
            for (int fi=0; fi<frameCount; fi++) {
                float fx = lW + fi*fW - scrollX;
                if (fx+fW<lW || fx>w) continue;

                boolean actFrame = (fi==project.getCurrentFrameIdx());
                boolean hovFrame = (fi==hoveredFrame && selLayer);

                RectF fr = new RectF(fx, ly, fx+fW, ly+rH);
                canvas.drawRect(fr, actFrame?pFrameAct:(hovFrame?pFrameHov:pFrameBg));
                canvas.drawLine(fx+fW,ly,fx+fW,ly+rH,pSep);

                // Frame thumbnail — langsung dari bitmap, JANGAN cache
                AnimationProject.Frame frame = layer.getFrame(fi);
                if (frame!=null) {
                    float tw=fW-pad*2, th=fH-pad*2;
                    if (tw>0&&th>0) {
                        // Background putih thumbnail
                        canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pThumbBg);
                        if (!frame.isEmpty && frame.bitmap!=null && !frame.bitmap.isRecycled()) {
                            // Scale bitmap ke ukuran thumbnail
                            Matrix m = new Matrix();
                            float sx = tw/frame.bitmap.getWidth();
                            float sy = th/frame.bitmap.getHeight();
                            float s  = Math.min(sx,sy);
                            m.postScale(s,s);
                            m.postTranslate(fx+pad,ly+pad);
                            canvas.drawBitmap(frame.bitmap,m,pThumb);
                        }
                        canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pBorder);
                    }
                }

                // Frame number
                canvas.drawText(String.valueOf(fi+1), fx+2*density, ly+rH-3*density, pSubText);

                // [+] dan [-] hanya di frame aktif, layer aktif
                if (actFrame && selLayer) {
                    // [+] kanan bawah
                    frameAddBtn.set(fx+fW-bSz-1,ly+rH-bSz-1,fx+fW-1,ly+rH-1);
                    canvas.drawRoundRect(frameAddBtn,2,2,pBtn);
                    drawCentered(canvas,"+",frameAddBtn,pBtnT);

                    // [-] kiri bawah
                    frameDelBtn.set(fx+1,ly+rH-bSz-1,fx+bSz+1,ly+rH-1);
                    canvas.drawRoundRect(frameDelBtn,2,2,pBtnDel);
                    drawCentered(canvas,"-",frameDelBtn,pBtnT);
                }
            }
            canvas.restore();
        }

        // Playback cursor
        float curX = lW + project.getCurrentFrameIdx()*fW + fW/2f - scrollX;
        if (curX>=lW && curX<=w)
            canvas.drawLine(curX, hH, curX, h, pCursor);

        canvas.restore();

        // Top border
        canvas.drawLine(0,0,w,0,pBorder);
    }

    // ── Header controls ───────────────────────────────────────────────────────

    private void drawHeaderControls(Canvas canvas, int w, float hH) {
        float pad=5*density, bH=hH-pad*2, bW=bH*1.5f;
        float x=pad;

        btnPrev.set(x,pad,x+bH,pad+bH); x+=bH+pad;
        drawBtn(canvas,btnPrev,"|<");

        btnPlay.set(x,pad,x+bW,pad+bH); x+=bW+pad;
        drawBtn(canvas,btnPlay,isPlaying?"||":"▶");

        btnStop.set(x,pad,x+bH,pad+bH); x+=bH+pad;
        drawBtn(canvas,btnStop,"■");

        btnNext.set(x,pad,x+bH,pad+bH); x+=bH+pad;
        drawBtn(canvas,btnNext,">|");

        btnLoop.set(x,pad,x+bH*1.2f,pad+bH); x+=bH*1.2f+pad;
        Paint lp=new Paint(isLooping?pBtnH:pBtn); lp.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnLoop,3,3,lp);
        drawCentered(canvas,"↺",btnLoop,pBtnT);

        if (project!=null) {
            String fps=project.getFps()+" fps";
            canvas.drawText(fps, x+pad, hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f, pSubText);
        }

        // Frame counter kanan
        if (project!=null) {
            String fc=(project.getCurrentFrameIdx()+1)+"/"+project.getFrameCount();
            float fcW=pText.measureText(fc);
            canvas.drawText(fc, w-fcW-pad*2, hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f, pText);
        }
    }

    private void drawBtn(Canvas canvas, RectF r, String icon) {
        canvas.drawRoundRect(r,3,3,pBtn);
        Paint bb=new Paint(pBorder); bb.setStyle(Paint.Style.STROKE); bb.setColor(0xFF5555AA);
        canvas.drawRoundRect(r,3,3,bb);
        drawCentered(canvas,icon,r,pBtnT);
    }

    private void drawCentered(Canvas canvas, String text, RectF r, Paint p) {
        Paint.FontMetrics fm=p.getFontMetrics();
        canvas.drawText(text, r.centerX()-p.measureText(text)/2f,
            r.centerY()-(fm.ascent+fm.descent)/2f, p);
    }

    private String truncate(String s, int max) {
        return s.length()>max ? s.substring(0,max-1)+"…" : s;
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float tx=e.getX(), ty=e.getY();
        float hH=HEADER_H_DP*density;
        float lW=LAYER_W_DP*density;
        float fW=FRAME_W_DP*density;
        float rH=ROW_H_DP*density;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX=tx; lastTouchY=ty; scrolling=false;
                hoveredFrame=hitFrame(tx,ty); invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx=tx-lastTouchX, dy=ty-lastTouchY;
                if (!scrolling && (Math.abs(dx)>6*density||Math.abs(dy)>6*density)) scrolling=true;
                if (scrolling && ty>hH) {
                    if (tx>lW) scrollX=Math.max(0,scrollX-dx);
                    scrollY=Math.max(0,scrollY-dy);
                    lastTouchX=tx; lastTouchY=ty;
                }
                hoveredFrame=hitFrame(tx,ty); invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (!scrolling) handleTap(tx,ty);
                scrolling=false; hoveredFrame=-1; invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                scrolling=false; hoveredFrame=-1; invalidate();
                return true;
        }
        return true;
    }

    private void handleTap(float tx, float ty) {
        if (project==null || listener==null) return;
        float hH=HEADER_H_DP*density;
        float lW=LAYER_W_DP*density;
        float fW=FRAME_W_DP*density;
        float rH=ROW_H_DP*density;

        // Header buttons
        if (ty<=hH) {
            if (btnPlay.contains(tx,ty))  { if(isPlaying) stopPlayback(); else startPlayback(); return; }
            if (btnStop.contains(tx,ty))  { stopPlayback(); project.setCurrentFrame(0); listener.onFrameChanged(0); return; }
            if (btnPrev.contains(tx,ty))  { stopPlayback(); project.prevFrame(); listener.onFrameChanged(project.getCurrentFrameIdx()); return; }
            if (btnNext.contains(tx,ty))  { stopPlayback(); project.nextFrame(); listener.onFrameChanged(project.getCurrentFrameIdx()); return; }
            if (btnLoop.contains(tx,ty))  { isLooping=!isLooping; invalidate(); return; }
            if (btnAddLayer.contains(tx,ty)) { project.addLayer(); listener.onLayerAdded(); return; }
            return;
        }

        // Layer delete buttons
        int layerCount=project.getLayerCount();
        for (int li=0; li<Math.min(layerCount,MAX_LAYERS); li++) {
            if (layerDelBtns[li].contains(tx,ty) && tx<=lW) {
                project.removeLayer(li); listener.onLayerRemoved(li); return;
            }
        }

        // Frame [+] dan [-] buttons
        if (frameAddBtn.contains(tx,ty)) { project.insertFrameAfterCurrent(); listener.onFrameAdded(); return; }
        if (frameDelBtn.contains(tx,ty)) { project.removeCurrentFrame(); listener.onFrameRemoved(); return; }

        // Frame tap (kanan layer panel)
        if (tx>lW) {
            int fi=hitFrame(tx,ty);
            if (fi>=0) { project.setCurrentFrame(fi); listener.onFrameChanged(fi); }
        }

        // Layer tap (kiri layer panel)
        if (tx<=lW) {
            int li=hitLayer(ty);
            if (li>=0) { project.setCurrentLayer(li); listener.onLayerChanged(li); }
        }
    }

    private int hitFrame(float x, float y) {
        if (project==null) return -1;
        float hH=HEADER_H_DP*density, lW=LAYER_W_DP*density, fW=FRAME_W_DP*density;
        if (x<=lW||y<=hH) return -1;
        int fi=(int)((x-lW+scrollX)/fW);
        return (fi>=0&&fi<project.getFrameCount()) ? fi : -1;
    }

    private int hitLayer(float y) {
        if (project==null) return -1;
        float hH=HEADER_H_DP*density, rH=ROW_H_DP*density;
        if (y<=hH) return -1;
        int li=(int)((y-hH+scrollY)/rH);
        return (li>=0&&li<project.getLayerCount()) ? li : -1;
    }
}
