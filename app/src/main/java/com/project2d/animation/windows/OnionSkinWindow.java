package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.drawing.OnionSkinSettings;
import com.project2d.animation.ui.ImGuiTheme;

/**
 * Onion Skin Settings Window — arsitektur identik FloatingWindow.
 *
 * Bug fixes:
 *  - winRect sekarang dihitung di awal onDraw DAN di onTouchEvent (tidak hanya di onDraw)
 *  - winH dihitung dengan padding yang cukup agar swatch warna tidak keluar
 *  - prevColorRect/nextColorRect disimpan dalam koordinat view (winX+...) bukan koordinat lokal
 *  - Drag: onTouchEvent tidak lagi mengecek winRect.contains() tapi langsung return true
 *    karena View ini MATCH_PARENT, touch selalu masuk — cukup cek apakah dalam window saja
 *    untuk logika interaksi, bukan untuk accept/reject event
 */
public class OnionSkinWindow extends View {

    public interface OnSettingsChanged { void onChange(); }

    // ── Window geometry ───────────────────────────────────────────────────────
    private float winX=40f, winY=60f, winW;
    // winH dihitung dari konten, disimpan agar bisa dipakai di onTouchEvent
    private float winH=0f;

    // ── Drag ──────────────────────────────────────────────────────────────────
    private boolean isDragging=false, longPressTriggered=false;
    private float touchDownX=0f, touchDownY=0f;
    private float dragOffsetX=0f, dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint pBg     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitleA =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnOn  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTrack  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep    =new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Hit rects — koordinat VIEW (absolut) ──────────────────────────────────
    private final RectF winRect       =new RectF();
    private final RectF titleRect     =new RectF();
    private final RectF closeBtnRect  =new RectF();
    private final RectF toggleRect    =new RectF();
    private final RectF prevTrack     =new RectF();
    private final RectF nextTrack     =new RectF();
    private final RectF opacTrack     =new RectF();
    private final RectF fadeToggle    =new RectF();
    private final RectF prevColorRect =new RectF();
    private final RectF nextColorRect =new RectF();

    private boolean closeHov=false, toggleHov=false, fadeHov=false;
    private boolean draggingPrev=false, draggingNext=false, draggingOpac=false;

    private float density;
    private OnionSkinSettings settings;
    private OnSettingsChanged listener;

    // Warna preset
    private static final int[] PREV_COLORS={0xFFFF3333,0xFFFF8800,0xFFFFDD00,0xFFFF44AA};
    private static final int[] NEXT_COLORS={0xFF3388FF,0xFF33CC55,0xFF00CCCC,0xFF8855FF};
    private int prevColorIdx=0, nextColorIdx=0;

    public OnionSkinWindow(Context c)               {super(c);init();}
    public OnionSkinWindow(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=210*density;

        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);          pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);     pTitle.setStyle(Paint.Style.FILL);
        pTitleA.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE); pTitleA.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT);                  pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT);                  pSub.setTextSize(11*density); pSub.setTypeface(Typeface.MONOSPACE);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);                pBtn.setStyle(Paint.Style.FILL);
        pBtnOn.setColor(0xFF226622);                           pBtnOn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);       pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);          pBtnT.setTextSize(11*density); pBtnT.setTypeface(Typeface.MONOSPACE);
        pTrack.setColor(ImGuiTheme.COLOR_WINDOW_BG);           pTrack.setStyle(Paint.Style.FILL);
        pSh.setColor(0x44000000);                              pSh.setStyle(Paint.Style.FILL);
        pSep.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);

        setClickable(true);
        setVisibility(GONE);
    }

    public void setSettings(OnionSkinSettings s){ settings=s; invalidate(); }
    public void setOnSettingsChanged(OnSettingsChanged l){ listener=l; }
    public void setPosition(float x,float y){ winX=x; winY=y; invalidate(); }
    public void showWindow(){ setVisibility(VISIBLE); bringToFront(); invalidate(); }
    public void hideWindow(){ setVisibility(GONE); }

    // ── Layout constants ──────────────────────────────────────────────────────
    // Semua row heights dalam pixel
    private float rowH()    { return 24*density; }
    private float sliderH() { return 12*density; }
    private float swH()     { return 22*density; }
    private float pad()     { return 10*density; }
    private float tH()      { return ImGuiTheme.TITLE_BAR_HEIGHT_DP*density; }
    private float innerW()  { return winW-pad()*2; }

    /**
     * Hitung total tinggi window berdasarkan konten.
     * Dipanggil sebelum draw dan sebelum hit test.
     */
    private float computeWinH(){
        return tH()+pad()
            +rowH()+pad()*0.5f          // toggle ON/OFF
            +1+pad()*0.5f               // sep
            +14*density+4*density+sliderH()+pad()*0.5f  // prev label+slider
            +14*density+4*density+sliderH()+pad()*0.5f  // next label+slider
            +14*density+4*density+sliderH()+pad()*0.5f  // opac label+slider
            +1+pad()*0.5f               // sep
            +rowH()+pad()*0.5f          // fade toggle
            +14*density+pad()*0.5f      // color label
            +swH()                      // color swatches
            +pad();                     // bottom padding
    }

    // ── onDraw ────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas){
        if(settings==null) return;

        // ── Hitung winH & clamp posisi ────────────────────────────────────────
        winH=computeWinH();
        winX=Math.max(0, Math.min(winX, getWidth()-winW));
        winY=Math.max(0, Math.min(winY, getHeight()-winH));
        winRect.set(winX, winY, winX+winW, winY+winH);

        float r   = ImGuiTheme.BORDER_RADIUS;
        float pad = pad();
        float tH  = tH();

        // Shadow
        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);

        // Window bg
        canvas.drawRoundRect(winRect,r,r,pBg);

        // Title bar
        titleRect.set(winX,winY,winX+winW,winY+tH);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,isDragging?pTitleA:pTitle);
        canvas.restore();

        // Title text
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        float tY=titleRect.centerY()-(fm.ascent+fm.descent)/2f;
        canvas.drawText("Onion Skin", winX+pad, tY, pTxt);

        // Close button
        float cSz=tH*0.65f;
        closeBtnRect.set(
            winX+winW-cSz-5*density, winY+(tH-cSz)/2f,
            winX+winW-5*density,     winY+(tH+cSz)/2f);
        if(closeHov){
            Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(0xFFEE6666); cp.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(closeBtnRect,2,2,cp);
        }
        Paint xP=new Paint(pTxt); xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",
            closeBtnRect.centerX()-xP.measureText("x")/2f,
            closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f, xP);

        // Window border
        canvas.drawRoundRect(winRect,r,r,pBdr);

        // ── Content ───────────────────────────────────────────────────────────
        float y = winY+tH+pad;
        Paint.FontMetrics sfm = pSub.getFontMetrics();
        float lH = 14*density; // label height

        // Toggle ON/OFF
        toggleRect.set(winX+pad,y,winX+pad+innerW(),y+rowH());
        boolean en=settings.isEnabled();
        canvas.drawRoundRect(toggleRect,3,3,en?pBtnOn:(toggleHov?pBtnH:pBtn));
        ct(canvas,(en?"\u2713 Onion Skin ON":"Onion Skin OFF"),toggleRect,pBtnT);
        y+=rowH()+pad*0.5f;

        // Separator
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Prev frames
        canvas.drawText("Prev Frames: "+settings.getPrevFrames(), winX+pad, y+lH, pSub); y+=lH+4*density;
        prevTrack.set(winX+pad,y,winX+pad+innerW(),y+sliderH());
        drawSlider(canvas,prevTrack,(settings.getPrevFrames()-1)/4f,0xFFFF5555); y+=sliderH()+pad*0.5f;

        // Next frames
        canvas.drawText("Next Frames: "+settings.getNextFrames(), winX+pad, y+lH, pSub); y+=lH+4*density;
        nextTrack.set(winX+pad,y,winX+pad+innerW(),y+sliderH());
        drawSlider(canvas,nextTrack,(settings.getNextFrames()-1)/4f,0xFF5588FF); y+=sliderH()+pad*0.5f;

        // Opacity
        canvas.drawText(String.format("Opacity: %.0f%%",settings.getMaxOpacity()*100f), winX+pad, y+lH, pSub); y+=lH+4*density;
        opacTrack.set(winX+pad,y,winX+pad+innerW(),y+sliderH());
        drawSlider(canvas,opacTrack,settings.getMaxOpacity(),0xFFCCCCCC); y+=sliderH()+pad*0.5f;

        // Separator
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Fade toggle
        fadeToggle.set(winX+pad,y,winX+pad+innerW(),y+rowH());
        boolean fade=settings.isFadeOpacity();
        canvas.drawRoundRect(fadeToggle,3,3,fade?pBtnOn:(pBtn));
        ct(canvas,(fade?"\u2713 Fade Opacity":"  Fade Opacity"),fadeToggle,pBtnT);
        y+=rowH()+pad*0.5f;

        // Color label
        canvas.drawText("Prev Color          Next Color", winX+pad, y+lH, pSub); y+=lH+pad*0.5f;

        // Color swatches — dua kotak side by side, dalam winRect
        float gap=6*density;
        float swW=(innerW()-gap)/2f;
        // prevColorRect & nextColorRect dalam koordinat VIEW
        prevColorRect.set(winX+pad,         y, winX+pad+swW,      y+swH());
        nextColorRect.set(winX+pad+swW+gap, y, winX+pad+innerW(), y+swH());

        // Draw prev swatch
        Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG);
        pc.setColor(settings.getPrevColor()); pc.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(prevColorRect,3,3,pc);
        canvas.drawRoundRect(prevColorRect,3,3,pBdr);
        // Cycle hint
        Paint hint=new Paint(pSub); hint.setTextSize(8*density); hint.setColor(0xAAFFFFFF);
        canvas.drawText("tap",
            prevColorRect.centerX()-hint.measureText("tap")/2f,
            prevColorRect.centerY()-(hint.getFontMetrics().ascent+hint.getFontMetrics().descent)/2f,
            hint);

        // Draw next swatch
        Paint nc=new Paint(Paint.ANTI_ALIAS_FLAG);
        nc.setColor(settings.getNextColor()); nc.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(nextColorRect,3,3,nc);
        canvas.drawRoundRect(nextColorRect,3,3,pBdr);
        canvas.drawText("tap",
            nextColorRect.centerX()-hint.measureText("tap")/2f,
            nextColorRect.centerY()-(hint.getFontMetrics().ascent+hint.getFontMetrics().descent)/2f,
            hint);
    }

    private void drawSlider(Canvas canvas,RectF track,float value,int color){
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pTrack);
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pBdr);
        float fillW=track.width()*value;
        if(fillW>2){
            Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG); fp.setColor(color); fp.setAlpha(180); fp.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(track.left,track.top,track.left+fillW,track.bottom),
                track.height()/2f,track.height()/2f,fp);
        }
        float cx=Math.min(track.left+fillW,track.right);
        Paint tp=new Paint(Paint.ANTI_ALIAS_FLAG); tp.setColor(color); tp.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx,track.centerY(),track.height()*0.9f,tp);
    }

    private void ct(Canvas c,String t,RectF r,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(t,r.centerX()-p.measureText(t)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event){
        // Hitung ulang winH & winRect agar hit test konsisten dengan onDraw
        winH=computeWinH();
        winX=Math.max(0, Math.min(winX, getWidth()-winW));
        winY=Math.max(0, Math.min(winY, getHeight()-winH));
        winRect.set(winX, winY, winX+winW, winY+winH);

        float tx=event.getX(), ty=event.getY();

        // Touch di luar window → pass through ke bawah
        if(!winRect.contains(tx,ty)) return false;

        boolean inTitle=(ty>=winY && ty<=winY+tH());

        switch(event.getActionMasked()){

            case MotionEvent.ACTION_DOWN:
                isDragging=false; longPressTriggered=false;
                touchDownX=tx; touchDownY=ty;
                dragOffsetX=tx-winX; dragOffsetY=ty-winY;
                closeHov=closeBtnRect.contains(tx,ty);
                toggleHov=toggleRect.contains(tx,ty);
                fadeHov=fadeToggle.contains(tx,ty);
                draggingPrev=prevTrack.contains(tx,ty);
                draggingNext=nextTrack.contains(tx,ty);
                draggingOpac=opacTrack.contains(tx,ty);
                if(inTitle && !closeBtnRect.contains(tx,ty))
                    lpHandler.postDelayed(lpRunnable,LP_MS);
                // Slider immediate feedback on down
                if(draggingPrev){ updatePrevSlider(tx); }
                if(draggingNext){ updateNextSlider(tx); }
                if(draggingOpac){ updateOpacSlider(tx); }
                invalidate(); return true;

            case MotionEvent.ACTION_MOVE:
                float mdx=tx-touchDownX, mdy=ty-touchDownY;
                if(!isDragging && mdx*mdx+mdy*mdy>(8*density)*(8*density))
                    lpHandler.removeCallbacks(lpRunnable);
                if(isDragging){
                    View p=(View)getParent();
                    winX=Math.max(0,Math.min(tx-dragOffsetX, p.getWidth()-winW));
                    winY=Math.max(0,Math.min(ty-dragOffsetY, p.getHeight()-winH));
                    invalidate();
                } else {
                    if(draggingPrev) updatePrevSlider(tx);
                    if(draggingNext) updateNextSlider(tx);
                    if(draggingOpac) updateOpacSlider(tx);
                    closeHov=closeBtnRect.contains(tx,ty);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; longPressTriggered=false;
                draggingPrev=false; draggingNext=false; draggingOpac=false;
                if(!wd){
                    if(closeBtnRect.contains(tx,ty))  { hideWindow(); }
                    else if(toggleRect.contains(tx,ty)){ settings.setEnabled(!settings.isEnabled()); notifyChange(); }
                    else if(fadeToggle.contains(tx,ty)){ settings.setFadeOpacity(!settings.isFadeOpacity()); notifyChange(); }
                    else if(prevColorRect.contains(tx,ty)){ cyclePrevColor(); }
                    else if(nextColorRect.contains(tx,ty)){ cycleNextColor(); }
                }
                closeHov=false; toggleHov=false; fadeHov=false;
                invalidate(); return true;

            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable);
                isDragging=false; longPressTriggered=false;
                draggingPrev=false; draggingNext=false; draggingOpac=false;
                closeHov=false; toggleHov=false; fadeHov=false;
                invalidate(); return true;
        }
        return false;
    }

    private void updatePrevSlider(float tx){
        float ratio=Math.max(0f,Math.min(1f,(tx-prevTrack.left)/prevTrack.width()));
        settings.setPrevFrames(1+(int)(ratio*4+0.5f)); notifyChange(); invalidate();
    }
    private void updateNextSlider(float tx){
        float ratio=Math.max(0f,Math.min(1f,(tx-nextTrack.left)/nextTrack.width()));
        settings.setNextFrames(1+(int)(ratio*4+0.5f)); notifyChange(); invalidate();
    }
    private void updateOpacSlider(float tx){
        float ratio=Math.max(0.05f,Math.min(1f,(tx-opacTrack.left)/opacTrack.width()));
        settings.setMaxOpacity(ratio); notifyChange(); invalidate();
    }

    private void cyclePrevColor(){
        prevColorIdx=(prevColorIdx+1)%PREV_COLORS.length;
        settings.setPrevColor(PREV_COLORS[prevColorIdx]); notifyChange(); invalidate();
    }
    private void cycleNextColor(){
        nextColorIdx=(nextColorIdx+1)%NEXT_COLORS.length;
        settings.setNextColor(NEXT_COLORS[nextColorIdx]); notifyChange(); invalidate();
    }

    private void notifyChange(){ if(listener!=null) listener.onChange(); }
}
