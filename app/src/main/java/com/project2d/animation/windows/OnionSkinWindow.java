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
 * Onion Skin Settings Window — arsitektur sama persis FloatingWindow (single View, onDraw).
 *
 * Konten:
 *  - Toggle ON/OFF onion skin
 *  - Slider prev frames (1-5)
 *  - Slider next frames (1-5)
 *  - Slider max opacity
 *  - Toggle fade opacity
 *  - Color swatch prev (merah) & next (biru)
 */
public class OnionSkinWindow extends View {

    public interface OnSettingsChanged {
        void onChange();
    }

    // ── Drag ──────────────────────────────────────────────────────────────────
    private float winX=40f, winY=60f, winW, winH;
    private boolean isDragging=false, longPressTriggered=false;
    private float touchDownX=0f,touchDownY=0f,dragOffsetX=0f,dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint pBg    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitleA=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnOn =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTrack =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumb =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep   =new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Hit rects ─────────────────────────────────────────────────────────────
    private final RectF winRect      =new RectF();
    private final RectF titleRect    =new RectF();
    private final RectF closeBtnRect =new RectF();
    private final RectF toggleRect   =new RectF();
    private final RectF prevTrack    =new RectF();
    private final RectF nextTrack    =new RectF();
    private final RectF opacTrack    =new RectF();
    private final RectF fadeToggle   =new RectF();
    private final RectF prevColorRect=new RectF();
    private final RectF nextColorRect=new RectF();

    private boolean closeHov=false, toggleHov=false, fadeHov=false;
    private boolean draggingPrev=false, draggingNext=false, draggingOpac=false;

    private float density;
    private OnionSkinSettings settings;
    private OnSettingsChanged listener;

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
        pThumb.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);      pThumb.setStyle(Paint.Style.FILL);
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

    // ── onDraw ────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas){
        if(settings==null) return;
        float r=ImGuiTheme.BORDER_RADIUS, pad=10*density;
        float tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density;
        float rowH=22*density, sliderH=12*density, swSz=20*density;
        float innerW=winW-pad*2;

        winH = tH+pad
            +rowH+pad*0.5f          // toggle ON/OFF
            +1+pad*0.5f             // separator
            +rowH+pad*0.5f          // prev frames slider
            +rowH+pad*0.5f          // next frames slider
            +rowH+pad*0.5f          // opacity slider
            +1+pad*0.5f             // separator
            +rowH+pad*0.5f          // fade toggle
            +rowH+pad*0.5f          // color swatches
            +pad;

        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);

        // Shadow
        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);

        // BG + border
        canvas.drawRoundRect(winRect,r,r,pBg);

        // Title bar
        titleRect.set(winX,winY,winX+winW,winY+tH);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,isDragging?pTitleA:pTitle);
        canvas.restore();

        // Title icon 💡 + text
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        float tY=titleRect.centerY()-(fm.ascent+fm.descent)/2f;
        canvas.drawText("\uD83D\uDCA1 Onion Skin", winX+pad, tY, pTxt);

        // Close
        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pTxt); xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);

        canvas.drawRoundRect(winRect,r,r,pBdr);

        // ── Content ───────────────────────────────────────────────────────────
        float y=winY+tH+pad;
        Paint.FontMetrics sfm=pSub.getFontMetrics();

        // Toggle ON/OFF
        toggleRect.set(winX+pad,y,winX+pad+innerW,y+rowH);
        boolean en=settings.isEnabled();
        canvas.drawRoundRect(toggleRect,3,3,en?pBtnOn:(toggleHov?pBtnH:pBtn));
        drawCentered(canvas,(en?"\u2713 ON":"OFF")+" Onion Skin",toggleRect,pBtnT);
        y+=rowH+pad*0.5f;

        // Separator
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Prev frames slider
        canvas.drawText("Prev Frames: "+settings.getPrevFrames(),winX+pad,y-sfm.ascent,pSub); y+=rowH*0.6f;
        prevTrack.set(winX+pad,y,winX+pad+innerW,y+sliderH);
        drawSlider(canvas,prevTrack,(settings.getPrevFrames()-1)/4f,0xFFFF5555);
        y+=sliderH+pad*0.8f;

        // Next frames slider
        canvas.drawText("Next Frames: "+settings.getNextFrames(),winX+pad,y-sfm.ascent,pSub); y+=rowH*0.6f;
        nextTrack.set(winX+pad,y,winX+pad+innerW,y+sliderH);
        drawSlider(canvas,nextTrack,(settings.getNextFrames()-1)/4f,0xFF5588FF);
        y+=sliderH+pad*0.8f;

        // Opacity slider
        canvas.drawText(String.format("Opacity: %.0f%%",settings.getMaxOpacity()*100f),winX+pad,y-sfm.ascent,pSub); y+=rowH*0.6f;
        opacTrack.set(winX+pad,y,winX+pad+innerW,y+sliderH);
        drawSlider(canvas,opacTrack,settings.getMaxOpacity(),0xFFCCCCCC);
        y+=sliderH+pad*0.8f;

        // Separator
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Fade toggle
        fadeToggle.set(winX+pad,y,winX+pad+innerW,y+rowH);
        boolean fade=settings.isFadeOpacity();
        canvas.drawRoundRect(fadeToggle,3,3,fade?pBtnOn:(fadeHov?pBtnH:pBtn));
        drawCentered(canvas,(fade?"\u2713 ":"  ")+"Fade Opacity",fadeToggle,pBtnT);
        y+=rowH+pad*0.5f;

        // Color swatches
        canvas.drawText("Prev Color",winX+pad,y-sfm.ascent,pSub);
        canvas.drawText("Next Color",winX+pad+innerW/2f,y-sfm.ascent,pSub);
        y+=rowH*0.5f;
        prevColorRect.set(winX+pad,y,winX+pad+innerW/2f-pad,y+swSz);
        nextColorRect.set(winX+pad+innerW/2f,y,winX+pad+innerW,y+swSz);
        Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(settings.getPrevColor()); pc.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(prevColorRect,3,3,pc);
        canvas.drawRoundRect(prevColorRect,3,3,pBdr);
        Paint nc=new Paint(Paint.ANTI_ALIAS_FLAG); nc.setColor(settings.getNextColor()); nc.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(nextColorRect,3,3,nc);
        canvas.drawRoundRect(nextColorRect,3,3,pBdr);
    }

    private void drawSlider(Canvas canvas,RectF track,float value,int thumbColor){
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pTrack);
        canvas.drawRoundRect(track,track.height()/2f,track.height()/2f,pBdr);
        float fillW=track.width()*value;
        if(fillW>2){
            Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG); fp.setColor(thumbColor); fp.setStyle(Paint.Style.FILL); fp.setAlpha(180);
            canvas.drawRoundRect(new RectF(track.left,track.top,track.left+fillW,track.bottom),track.height()/2f,track.height()/2f,fp);
        }
        float tx=Math.min(track.left+fillW,track.right), ty=track.centerY(), tr=track.height()*0.9f;
        Paint tp=new Paint(Paint.ANTI_ALIAS_FLAG); tp.setColor(thumbColor); tp.setStyle(Paint.Style.FILL);
        canvas.drawCircle(tx,ty,tr,tp);
    }

    private void drawCentered(Canvas canvas,String text,RectF r,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics();
        canvas.drawText(text,r.centerX()-p.measureText(text)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event){
        float tx=event.getX(),ty=event.getY();
        if(!winRect.contains(tx,ty)) return false;
        boolean inTitle=ty>=winY&&ty<=winY+ImGuiTheme.TITLE_BAR_HEIGHT_DP*density;

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
                if(inTitle&&!closeBtnRect.contains(tx,ty)) lpHandler.postDelayed(lpRunnable,LP_MS);
                if(draggingPrev) updatePrevSlider(tx);
                if(draggingNext) updateNextSlider(tx);
                if(draggingOpac) updateOpacSlider(tx);
                invalidate(); return true;

            case MotionEvent.ACTION_MOVE:
                float mdx=tx-touchDownX,mdy=ty-touchDownY;
                if(!isDragging&&mdx*mdx+mdy*mdy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                if(isDragging){
                    View p=(View)getParent();
                    winX=Math.max(0,Math.min(tx-dragOffsetX,p.getWidth()-getWidth()));
                    winY=Math.max(0,Math.min(ty-dragOffsetY,p.getHeight()-getHeight()));
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
                    if(closeBtnRect.contains(tx,ty))  hideWindow();
                    if(toggleRect.contains(tx,ty))    { settings.setEnabled(!settings.isEnabled()); notifyChange(); }
                    if(fadeToggle.contains(tx,ty))    { settings.setFadeOpacity(!settings.isFadeOpacity()); notifyChange(); }
                    // Tap color swatch — cycle warna preset
                    if(prevColorRect.contains(tx,ty)) cyclePrevColor();
                    if(nextColorRect.contains(tx,ty)) cycleNextColor();
                }
                closeHov=false; toggleHov=false; fadeHov=false;
                invalidate(); return true;

            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable); isDragging=false; longPressTriggered=false;
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

    // Cycle warna preset prev: merah → oranye → kuning → merah
    private static final int[] PREV_COLORS={0xFFFF3333,0xFFFF8800,0xFFFFDD00,0xFFFF44AA};
    private int prevColorIdx=0;
    private void cyclePrevColor(){
        prevColorIdx=(prevColorIdx+1)%PREV_COLORS.length;
        settings.setPrevColor(PREV_COLORS[prevColorIdx]); notifyChange(); invalidate();
    }

    // Cycle warna preset next: biru → hijau → cyan
    private static final int[] NEXT_COLORS={0xFF3388FF,0xFF33CC55,0xFF00CCCC,0xFF8855FF};
    private int nextColorIdx=0;
    private void cycleNextColor(){
        nextColorIdx=(nextColorIdx+1)%NEXT_COLORS.length;
        settings.setNextColor(NEXT_COLORS[nextColorIdx]); notifyChange(); invalidate();
    }

    private void notifyChange(){ if(listener!=null) listener.onChange(); }
}
