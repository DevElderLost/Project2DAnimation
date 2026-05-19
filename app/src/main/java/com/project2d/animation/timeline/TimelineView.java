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
 * Timeline View — panel di bawah layar (slide up/down).
 *
 * Layout (dari atas ke bawah):
 *   - Header bar: tombol play/pause/stop, prev/next frame, FPS label
 *   - Layer list (kiri): nama layer + visibility toggle
 *   - Frame strip (kanan): thumbnail tiap frame, frame aktif disorot merah
 *     • Tombol [+] di kanan thumbnail → tambah frame setelah frame itu
 *     • Drag horizontal frame → reorder (hold 400ms)
 *   - Scroll horizontal untuk frame strip
 */
public class TimelineView extends View {

    public interface OnTimelineEvent {
        void onFrameChanged(int frameIdx);
        void onLayerChanged(int layerIdx);
        void onFrameAdded();
        void onPlayStateChanged(boolean playing);
        void onRequestRedraw(); // canvas perlu invalidate
    }

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int HEADER_H_DP   = 36;
    private static final int LAYER_W_DP    = 90;
    private static final int FRAME_W_DP    = 52;
    private static final int FRAME_H_DP    = 52;
    private static final int THUMB_PAD_DP  = 4;
    private static final int ROW_H_DP      = 56;

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint pBg         = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHeader     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSubText    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameAct   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameHov   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumb      = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pBtn        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerSel   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPlayLine   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAddBtn     = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private AnimationProject project;
    private OnTimelineEvent listener;

    // ── Playback ──────────────────────────────────────────────────────────────
    private boolean isPlaying   = false;
    private boolean isLooping   = true;
    private final Handler playHandler = new Handler(Looper.getMainLooper());
    private final Runnable playRunnable = this::tickPlayback;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private float scrollX   = 0f; // horizontal scroll for frame strip
    private float scrollY   = 0f; // vertical scroll for layers
    private float lastTouchX, lastTouchY;
    private boolean scrolling = false;

    // ── Touch state ───────────────────────────────────────────────────────────
    private int  touchedFrame  = -1;
    private int  touchedLayer  = -1;
    private int  hoveredFrame  = -1;
    private int  hoveredAddBtn = -1; // frame index of hovered [+] button

    // Header button rects
    private final RectF btnPlay   = new RectF();
    private final RectF btnStop   = new RectF();
    private final RectF btnPrev   = new RectF();
    private final RectF btnNext   = new RectF();
    private final RectF btnLoop   = new RectF();
    private final RectF btnAddLayer = new RectF();

    public TimelineView(Context c)               { super(c); init(); }
    public TimelineView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init(){
        density = getResources().getDisplayMetrics().density;

        pBg.setColor(0xFF1A1A22);         pBg.setStyle(Paint.Style.FILL);
        pHeader.setColor(0xFF131320);     pHeader.setStyle(Paint.Style.FILL);
        pBorder.setColor(0xFF3A3A5A);     pBorder.setStyle(Paint.Style.STROKE); pBorder.setStrokeWidth(1f);
        pText.setColor(0xFFDDDDDD);       pText.setTextSize(12*density); pText.setTypeface(Typeface.MONOSPACE); pText.setAntiAlias(true);
        pSubText.setColor(0xFF888899);    pSubText.setTextSize(10*density); pSubText.setTypeface(Typeface.MONOSPACE); pSubText.setAntiAlias(true);
        pFrameBg.setColor(0xFF252535);    pFrameBg.setStyle(Paint.Style.FILL);
        pFrameAct.setColor(0xFF3A3A6A);   pFrameAct.setStyle(Paint.Style.FILL);
        pFrameHov.setColor(0xFF2A2A4A);   pFrameHov.setStyle(Paint.Style.FILL);
        pThumb.setAntiAlias(true); pThumb.setFilterBitmap(true);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);      pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED); pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(0xFFFFFFFF); pBtnT.setTextSize(11*density); pBtnT.setTypeface(Typeface.MONOSPACE); pBtnT.setAntiAlias(true);
        pLayerBg.setColor(0xFF1E1E2E);   pLayerBg.setStyle(Paint.Style.FILL);
        pLayerSel.setColor(0xFF2A2A4A);  pLayerSel.setStyle(Paint.Style.FILL);
        pSep.setColor(0xFF2A2A3A);       pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pPlayLine.setColor(0xFFFF4444);  pPlayLine.setStyle(Paint.Style.STROKE); pPlayLine.setStrokeWidth(2f);
        pAddBtn.setColor(0xFF3A5A3A);    pAddBtn.setStyle(Paint.Style.FILL);

        setClickable(true);
        setWillNotDraw(false);
    }

    public void setProject(AnimationProject p) { this.project=p; invalidate(); }
    public void setOnTimelineEvent(OnTimelineEvent l){ this.listener=l; }

    // ── Playback ──────────────────────────────────────────────────────────────
    private void tickPlayback(){
        if(!isPlaying||project==null) return;
        int next=project.getCurrentFrameIdx()+1;
        if(next>=project.getFrameCount()){
            if(isLooping) next=0;
            else{ stopPlayback(); return; }
        }
        project.setCurrentFrame(next);
        if(listener!=null) listener.onFrameChanged(next);
        invalidate();
        long interval=1000L/Math.max(1,project.getFps());
        playHandler.postDelayed(playRunnable,interval);
    }

    public void startPlayback(){
        if(isPlaying) return;
        isPlaying=true;
        if(listener!=null) listener.onPlayStateChanged(true);
        playHandler.post(playRunnable);
        invalidate();
    }

    public void stopPlayback(){
        isPlaying=false;
        playHandler.removeCallbacks(playRunnable);
        if(listener!=null) listener.onPlayStateChanged(false);
        invalidate();
    }

    public boolean isPlaying(){ return isPlaying; }

    // ── onDraw ────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas){
        if(project==null) return;
        int w=getWidth(), h=getHeight();
        float headerH = HEADER_H_DP*density;
        float layerW  = LAYER_W_DP*density;
        float frameW  = FRAME_W_DP*density;
        float frameH  = FRAME_H_DP*density;
        float rowH    = ROW_H_DP*density;
        float pad     = THUMB_PAD_DP*density;

        // Background
        canvas.drawRect(0,0,w,h,pBg);

        // ── Header ────────────────────────────────────────────────────────────
        canvas.drawRect(0,0,w,headerH,pHeader);
        drawHeaderControls(canvas,w,headerH);

        // Top border
        canvas.drawLine(0,headerH,w,headerH,pBorder);

        // ── Layer column header ────────────────────────────────────────────
        canvas.drawRect(0,headerH,layerW,h,pLayerBg);
        canvas.drawLine(layerW,headerH,layerW,h,pBorder);

        // "+" add layer button
        float addBtnSz=20*density;
        btnAddLayer.set(layerW-addBtnSz-4*density, headerH+4*density, layerW-4*density, headerH+addBtnSz+4*density);
        canvas.drawRoundRect(btnAddLayer,3,3,pAddBtn);
        drawCenteredText(canvas,"+",btnAddLayer,pBtnT);

        // Clip layer list to below header
        canvas.save();
        canvas.clipRect(0,headerH,w,h);

        int layerCount=project.getLayerCount();
        for(int li=0;li<layerCount;li++){
            float ly=headerH+li*rowH-scrollY;
            if(ly+rowH<headerH||ly>h) continue;
            AnimationProject.Layer layer=project.getLayer(li);

            // Layer row bg
            boolean isSelLayer=(li==project.getCurrentLayerIdx());
            canvas.drawRect(0,ly,layerW,ly+rowH,isSelLayer?pLayerSel:pLayerBg);
            canvas.drawLine(0,ly+rowH,layerW,ly+rowH,pSep);

            // Visibility dot
            float dotR=5*density, dotX=10*density, dotY=ly+rowH/2f;
            Paint dotP=new Paint(Paint.ANTI_ALIAS_FLAG); dotP.setStyle(Paint.Style.FILL);
            dotP.setColor(layer.visible?0xFF44BB44:0xFF666666);
            canvas.drawCircle(dotX,dotY,dotR,dotP);

            // Layer name
            Paint.FontMetrics fm=pText.getFontMetrics();
            float ty=ly+rowH/2f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(layer.name,dotX+dotR*2+4*density,ty,pText);

            // ── Frame strip for this layer ────────────────────────────────
            canvas.save();
            canvas.clipRect(layerW,headerH,w,h);

            int frameCount=project.getFrameCount();
            for(int fi=0;fi<frameCount;fi++){
                float fx=layerW+fi*frameW-scrollX;
                float fy=ly;
                if(fx+frameW<layerW||fx>w) continue;

                RectF fr=new RectF(fx,fy,fx+frameW,fy+rowH);
                boolean isActFrame=(fi==project.getCurrentFrameIdx());
                boolean isHov=(fi==hoveredFrame&&li==project.getCurrentLayerIdx());

                canvas.drawRect(fr,isActFrame?pFrameAct:(isHov?pFrameHov:pFrameBg));

                // Thumbnail
                AnimationProject.Frame frame=layer.getFrame(fi);
                if(frame!=null){
                    int tw=(int)(frameW-pad*2), th=(int)(frameH-pad*2);
                    if(tw>0&&th>0){
                        Bitmap thumb=frame.getThumbnail(tw,th);
                        canvas.drawBitmap(thumb,fx+pad,fy+pad,pThumb);
                    }
                }

                // Frame number
                canvas.drawText(String.valueOf(fi+1),fx+3*density,fy+rowH-4*density,pSubText);

                // Frame separator
                canvas.drawLine(fx+frameW,fy,fx+frameW,fy+rowH,pSep);

                // [+] add frame button — kanan atas tiap frame, layer aktif saja
                if(isSelLayer&&fi==project.getCurrentFrameIdx()){
                    float plusSz=14*density;
                    float plusX=fx+frameW-plusSz-1*density;
                    float plusY=fy+1*density;
                    RectF plusRect=new RectF(plusX,plusY,plusX+plusSz,plusY+plusSz);
                    if(fi==hoveredAddBtn) canvas.drawRoundRect(plusRect,2,2,pBtnH);
                    else canvas.drawRoundRect(plusRect,2,2,pAddBtn);
                    drawCenteredText(canvas,"+",plusRect,pBtnT);
                }
            }
            canvas.restore();
        }

        // ── Playback cursor (red vertical line) ───────────────────────────
        float cursorX=layerW+project.getCurrentFrameIdx()*frameW+frameW/2f-scrollX;
        if(cursorX>=layerW&&cursorX<=w)
            canvas.drawLine(cursorX,headerH,cursorX,h,pPlayLine);

        canvas.restore();

        // Bottom border
        canvas.drawLine(0,0,w,0,pBorder);
    }

    private void drawHeaderControls(Canvas canvas,int w,float headerH){
        float pad=6*density, btnH=headerH-pad*2, btnW=btnH*1.6f;
        float x=pad;

        // Prev frame
        btnPrev.set(x,pad,x+btnH,pad+btnH); x+=btnH+pad;
        drawIconBtn(canvas,btnPrev,"|<");

        // Play / Pause
        btnPlay.set(x,pad,x+btnW,pad+btnH); x+=btnW+pad;
        drawIconBtn(canvas,btnPlay,isPlaying?"||":">");

        // Stop
        btnStop.set(x,pad,x+btnH,pad+btnH); x+=btnH+pad;
        drawIconBtn(canvas,btnStop,"[]");

        // Next frame
        btnNext.set(x,pad,x+btnH,pad+btnH); x+=btnH+pad;
        drawIconBtn(canvas,btnNext,">|");

        // Loop toggle
        btnLoop.set(x,pad,x+btnH*1.2f,pad+btnH); x+=btnH*1.2f+pad;
        Paint lp=new Paint(isLooping?pBtnH:pBtn); lp.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnLoop,3,3,lp);
        drawCenteredText(canvas,"\u21BA",btnLoop,pBtnT);

        // FPS label
        if(project!=null){
            String fps=project.getFps()+" fps";
            canvas.drawText(fps,x+pad,headerH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pText);
        }

        // Frame counter — right side
        if(project!=null){
            String fc=(project.getCurrentFrameIdx()+1)+"/"+project.getFrameCount();
            float fcW=pText.measureText(fc);
            canvas.drawText(fc,w-fcW-pad*2,headerH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pText);
        }
    }

    private void drawIconBtn(Canvas canvas,RectF rect,String icon){
        canvas.drawRoundRect(rect,3,3,pBtn);
        Paint bb=new Paint(pBorder); bb.setStyle(Paint.Style.STROKE); bb.setColor(0xFF5555AA);
        canvas.drawRoundRect(rect,3,3,bb);
        drawCenteredText(canvas,icon,rect,pBtnT);
    }

    private void drawCenteredText(Canvas canvas,String text,RectF rect,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics();
        float x=rect.centerX()-p.measureText(text)/2f;
        float y=rect.centerY()-(fm.ascent+fm.descent)/2f;
        canvas.drawText(text,x,y,p);
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(), ty=e.getY();
        float headerH=HEADER_H_DP*density;
        float layerW=LAYER_W_DP*density;
        float frameW=FRAME_W_DP*density;
        float rowH=ROW_H_DP*density;

        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastTouchX=tx; lastTouchY=ty; scrolling=false;
                hoveredFrame=hitFrame(tx,ty);
                hoveredAddBtn=hitAddBtn(tx,ty);
                touchedFrame=hoveredFrame; touchedLayer=hitLayer(ty);
                invalidate(); return true;

            case MotionEvent.ACTION_MOVE:
                float dx=tx-lastTouchX, dy=ty-lastTouchY;
                if(!scrolling&&(Math.abs(dx)>8*density||Math.abs(dy)>8*density)) scrolling=true;
                if(scrolling&&ty>headerH){
                    if(tx>layerW) scrollX=Math.max(0,scrollX-dx);
                    scrollY=Math.max(0,scrollY-dy);
                    lastTouchX=tx; lastTouchY=ty;
                }
                hoveredFrame=hitFrame(tx,ty);
                hoveredAddBtn=hitAddBtn(tx,ty);
                invalidate(); return true;

            case MotionEvent.ACTION_UP:
                if(!scrolling){
                    // Header buttons
                    if(ty<=headerH){
                        if(btnPlay.contains(tx,ty))  { if(isPlaying) stopPlayback(); else startPlayback(); }
                        if(btnStop.contains(tx,ty))  { stopPlayback(); if(project!=null){project.setCurrentFrame(0);if(listener!=null)listener.onFrameChanged(0);} }
                        if(btnPrev.contains(tx,ty))  { stopPlayback(); if(project!=null){project.prevFrame();if(listener!=null)listener.onFrameChanged(project.getCurrentFrameIdx());} }
                        if(btnNext.contains(tx,ty))  { stopPlayback(); if(project!=null){project.nextFrame();if(listener!=null)listener.onFrameChanged(project.getCurrentFrameIdx());} }
                        if(btnLoop.contains(tx,ty))  { isLooping=!isLooping; }
                        if(btnAddLayer.contains(tx,ty)) { if(project!=null){project.addLayer();if(listener!=null)listener.onLayerChanged(project.getCurrentLayerIdx());} }
                    } else {
                        // Add frame button
                        int addHit=hitAddBtn(tx,ty);
                        if(addHit>=0){ if(project!=null){project.insertFrameAfterCurrent();if(listener!=null)listener.onFrameAdded();} }
                        // Frame select
                        else{
                            int fh=hitFrame(tx,ty);
                            if(fh>=0&&project!=null){ project.setCurrentFrame(fh); if(listener!=null)listener.onFrameChanged(fh); }
                        }
                        // Layer select
                        int lh=hitLayer(ty);
                        if(lh>=0&&tx<=layerW&&project!=null){ project.setCurrentLayer(lh); if(listener!=null)listener.onLayerChanged(lh); }
                    }
                }
                scrolling=false; hoveredAddBtn=-1; invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                scrolling=false; hoveredAddBtn=-1; invalidate(); return true;
        }
        return true;
    }

    private int hitFrame(float x,float y){
        if(project==null) return -1;
        float headerH=HEADER_H_DP*density, layerW=LAYER_W_DP*density, frameW=FRAME_W_DP*density, rowH=ROW_H_DP*density;
        if(x<=layerW||y<=headerH) return -1;
        int fi=(int)((x-layerW+scrollX)/frameW);
        if(fi>=0&&fi<project.getFrameCount()) return fi;
        return -1;
    }

    private int hitAddBtn(float x,float y){
        if(project==null) return -1;
        float headerH=HEADER_H_DP*density, layerW=LAYER_W_DP*density;
        float frameW=FRAME_W_DP*density, rowH=ROW_H_DP*density;
        float plusSz=14*density;
        if(y<=headerH) return -1;
        int li=hitLayer(y);
        if(li!=project.getCurrentLayerIdx()) return -1;
        int fi=(int)((x-layerW+scrollX)/frameW);
        if(fi<0||fi>=project.getFrameCount()) return -1;
        float fx=layerW+fi*frameW-scrollX;
        float fy=headerH+li*rowH-scrollY;
        float plusX=fx+frameW-plusSz-1*density;
        float plusY=fy+1*density;
        if(x>=plusX&&x<=plusX+plusSz&&y>=plusY&&y<=plusY+plusSz) return fi;
        return -1;
    }

    private int hitLayer(float y){
        if(project==null) return -1;
        float headerH=HEADER_H_DP*density, rowH=ROW_H_DP*density;
        if(y<=headerH) return -1;
        int li=(int)((y-headerH+scrollY)/rowH);
        if(li>=0&&li<project.getLayerCount()) return li;
        return -1;
    }
}
