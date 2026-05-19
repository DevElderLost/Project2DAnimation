package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.ui.ImGuiTheme;

/**
 * Brush & Color Panel Window.
 * - Slider ukuran brush
 * - Slider opacity
 * - Color swatches (12 warna)
 * - Preview warna aktif
 * Arsitektur sama persis FloatingWindow.
 */
public class BrushColorWindow extends View {

    public interface OnBrushChanged {
        void onColorChanged(int color);
        void onSizeChanged(float size);
        void onOpacityChanged(float opacity);
    }

    private float winX=10f, winY=10f;
    private float winW, winH;
    private boolean isDragging=false, longPressTriggered=false;
    private float touchDownX=0f, touchDownY=0f, dragOffsetX=0f, dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};

    private final Paint pBg   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTrack=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumb=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh   =new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF winRect      =new RectF();
    private final RectF titleRect    =new RectF();
    private final RectF closeBtnRect =new RectF();

    // Sliders
    private final RectF sizeTrackRect    =new RectF();
    private final RectF opacTrackRect    =new RectF();
    private boolean draggingSizeSlider   =false;
    private boolean draggingOpacSlider   =false;

    // Color swatches
    private static final int[] COLORS = {
        0xFF000000,0xFFFFFFFF,0xFFEE4444,0xFF44BB44,
        0xFF4488FF,0xFFFFCC00,0xFFFF8800,0xFFAA44CC,
        0xFF44CCCC,0xFFFF44AA,0xFF884400,0xFF888888
    };
    private final RectF[] swatchRects=new RectF[12];
    private final RectF   previewRect=new RectF();

    private boolean closeHov=false;
    private int hoveredSwatch=-1;

    // State
    private int   currentColor  =0xFF000000;
    private float brushSize     =8f;   // 1-100
    private float brushOpacity  =1f;   // 0-1

    private float density;
    private OnBrushChanged listener;

    public BrushColorWindow(Context c){super(c);init();}
    public BrushColorWindow(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=200*density;
        for(int i=0;i<12;i++) swatchRects[i]=new RectF();

        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);          pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);     pTitle.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT);                  pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT);                  pSub.setTextSize(11*density); pSub.setTypeface(Typeface.MONOSPACE);
        pTrack.setColor(ImGuiTheme.COLOR_WINDOW_BG);           pTrack.setStyle(Paint.Style.FILL);
        pThumb.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);      pThumb.setStyle(Paint.Style.FILL);
        pSh.setColor(0x44000000);                              pSh.setStyle(Paint.Style.FILL);
        setClickable(true); setVisibility(VISIBLE);
    }

    public void setOnBrushChanged(OnBrushChanged l){listener=l;}
    public void setPosition(float x,float y){winX=x;winY=y;invalidate();}
    public void syncFromEngine(DrawingEngine e){
        currentColor=e.getBrushColor(); brushSize=e.getBrushSize(); brushOpacity=e.getBrushOpacity(); invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        float r=ImGuiTheme.BORDER_RADIUS, pad=10*density;
        float tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density;
        float sliderH=14*density, labelH=16*density, swSz=22*density, gap=6*density;
        float innerW=winW-pad*2;

        winH=tH+pad
            +labelH+gap+sliderH+gap   // size slider
            +labelH+gap+sliderH+gap   // opacity slider
            +gap+swSz*2+gap*1         // 2 rows swatches (6 per row * 2 = 12)
            +pad+swSz+pad;            // preview

        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);

        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);
        canvas.drawRoundRect(winRect,r,r,pBg);

        // Title bar
        titleRect.set(winX,winY,winX+winW,winY+tH);
        Paint tp=new Paint(pTitle); if(isDragging) tp.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,tp);
        canvas.restore();
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        canvas.drawText("Brush & Color",winX+pad,titleRect.centerY()-(fm.ascent+fm.descent)/2f,pTxt);

        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pTxt); xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);
        canvas.drawRoundRect(winRect,r,r,pBdr);

        float y=winY+tH+pad;

        // ── Size slider ────────────────────────────────────────────────────
        Paint.FontMetrics sfm=pSub.getFontMetrics();
        canvas.drawText(String.format("Size: %.0f",brushSize),winX+pad,y-sfm.ascent,pSub);
        y+=labelH+gap;
        sizeTrackRect.set(winX+pad,y,winX+pad+innerW,y+sliderH);
        drawSlider(canvas,sizeTrackRect,brushSize/100f);
        y+=sliderH+gap;

        // ── Opacity slider ─────────────────────────────────────────────────
        canvas.drawText(String.format("Opacity: %.0f%%",brushOpacity*100f),winX+pad,y-sfm.ascent,pSub);
        y+=labelH+gap;
        opacTrackRect.set(winX+pad,y,winX+pad+innerW,y+sliderH);
        drawSlider(canvas,opacTrackRect,brushOpacity);
        y+=sliderH+gap*2;

        // ── Color swatches ─────────────────────────────────────────────────
        float swX=winX+pad;
        for(int i=0;i<12;i++){
            int row=i/6, col=i%6;
            float sx=swX+col*(swSz+gap);
            float sy=y+row*(swSz+gap);
            swatchRects[i].set(sx,sy,sx+swSz,sy+swSz);
            Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(COLORS[i]); sp.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(swatchRects[i],3,3,sp);
            // Border — tebal jika dipilih
            Paint sb=new Paint(Paint.ANTI_ALIAS_FLAG); sb.setStyle(Paint.Style.STROKE);
            sb.setColor(COLORS[i]==currentColor?0xFFFFFFFF:0xFF444444);
            sb.setStrokeWidth(COLORS[i]==currentColor?2.5f:1f);
            canvas.drawRoundRect(swatchRects[i],3,3,sb);
        }
        y+=swSz*2+gap+gap*2;

        // ── Preview warna aktif ────────────────────────────────────────────
        float prevW=40*density, prevH=swSz;
        previewRect.set(winX+pad,y,winX+pad+prevW,y+prevH);
        Paint pp=new Paint(Paint.ANTI_ALIAS_FLAG); pp.setColor(currentColor); pp.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(previewRect,3,3,pp);
        Paint pb=new Paint(pBdr); canvas.drawRoundRect(previewRect,3,3,pb);
        // Hex label
        String hex=String.format("#%06X",currentColor&0xFFFFFF);
        canvas.drawText(hex,winX+pad+prevW+8*density,y+prevH/2f-(sfm.ascent+sfm.descent)/2f,pSub);
    }

    private void drawSlider(Canvas canvas,RectF track,float value){
        // Track
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pTrack);
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pBdr);
        // Fill
        float fillW=track.width()*value;
        if(fillW>0){
            RectF fill=new RectF(track.left,track.top,track.left+fillW,track.bottom);
            canvas.drawRoundRect(fill,track.height()/2f,track.height()/2f,pThumb);
        }
        // Thumb
        float tx=track.left+fillW;
        float ty=track.centerY();
        float tr=track.height()*0.8f;
        canvas.drawCircle(Math.min(tx,track.right),ty,tr,pThumb);
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        float tx=event.getX(),ty=event.getY();
        if(!winRect.contains(tx,ty)) return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                touchDownX=tx; touchDownY=ty; longPressTriggered=false; isDragging=false;
                if(titleRect.contains(tx,ty)&&!closeBtnRect.contains(tx,ty)){dragOffsetX=tx-winX;dragOffsetY=ty-winY;lpHandler.postDelayed(lpRunnable,LP_MS);}
                draggingSizeSlider=sizeTrackRect.contains(tx,ty);
                draggingOpacSlider=opacTrackRect.contains(tx,ty);
                if(draggingSizeSlider) updateSizeSlider(tx);
                if(draggingOpacSlider) updateOpacSlider(tx);
                closeHov=closeBtnRect.contains(tx,ty);
                hoveredSwatch=hitSwatch(tx,ty);
                invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if(isDragging){winX=tx-dragOffsetX;winY=ty-dragOffsetY;invalidate();}
                else{
                    float dx=tx-touchDownX,dy=ty-touchDownY;
                    if(dx*dx+dy*dy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                    if(draggingSizeSlider) updateSizeSlider(tx);
                    if(draggingOpacSlider) updateOpacSlider(tx);
                    closeHov=closeBtnRect.contains(tx,ty);
                    hoveredSwatch=hitSwatch(tx,ty);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; longPressTriggered=false;
                draggingSizeSlider=false; draggingOpacSlider=false;
                if(!wd){
                    if(closeBtnRect.contains(tx,ty)){setVisibility(GONE);}
                    int hs=hitSwatch(tx,ty);
                    if(hs>=0){currentColor=COLORS[hs];if(listener!=null)listener.onColorChanged(currentColor);}
                }
                closeHov=false; hoveredSwatch=-1; invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable); isDragging=false; longPressTriggered=false;
                draggingSizeSlider=false; draggingOpacSlider=false;
                closeHov=false; hoveredSwatch=-1; invalidate(); return true;
        }
        return false;
    }

    private void updateSizeSlider(float tx){
        float ratio=Math.max(0f,Math.min(1f,(tx-sizeTrackRect.left)/sizeTrackRect.width()));
        brushSize=1f+ratio*99f;
        if(listener!=null) listener.onSizeChanged(brushSize);
        invalidate();
    }
    private void updateOpacSlider(float tx){
        float ratio=Math.max(0f,Math.min(1f,(tx-opacTrackRect.left)/opacTrackRect.width()));
        brushOpacity=Math.max(0.05f,ratio);
        if(listener!=null) listener.onOpacityChanged(brushOpacity);
        invalidate();
    }
    private int hitSwatch(float x,float y){for(int i=0;i<swatchRects.length;i++)if(swatchRects[i].contains(x,y))return i;return -1;}
}
