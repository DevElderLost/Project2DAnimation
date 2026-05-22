package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.timeline.AnimationProject;
import com.project2d.animation.ui.ImGuiTheme;

/**
 * Frame Settings Window — TVPaint-style:
 *
 * 1. Add Mode:
 *    - Empty Instance : tambah frame kosong baru (default)
 *    - Loop Mode      : salin frame 1..N sebagai frame baru (cycle)
 *
 * 2. Tombol "Add N Frames" untuk batch-add
 *
 * Dipanggil dari tombol "Frame Setting" di toolbar atau timeline.
 */
public class FrameSettingsWindow extends View {

    public enum AddMode { EMPTY_INSTANCE, LOOP }

    public interface OnFrameSettingsApplied {
        /** Tambah satu frame kosong biasa */
        void onAddEmptyFrame();
        /** Tambah N frame dengan loop (salin frame 1..frameCount sebagai siklus) */
        void onAddLoopFrames(int count);
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    private float winX=60f,winY=80f,winW,winH;
    private boolean isDragging=false,longPressTriggered=false;
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
    private final Paint pSh    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep   =new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Hit rects ─────────────────────────────────────────────────────────────
    private final RectF winRect       =new RectF();
    private final RectF titleRect     =new RectF();
    private final RectF closeBtnRect  =new RectF();
    private final RectF emptyModeRect =new RectF();
    private final RectF loopModeRect  =new RectF();
    private final RectF addBtnRect    =new RectF();
    private final RectF minusBtnRect  =new RectF();
    private final RectF applyBtnRect  =new RectF();

    private boolean closeHov=false,emptyHov=false,loopHov=false,addHov=false,minHov=false,applyHov=false;

    // ── State ─────────────────────────────────────────────────────────────────
    private AddMode currentMode = AddMode.EMPTY_INSTANCE;
    private int     addCount    = 1; // jumlah frame yang akan ditambah
    private AnimationProject project;

    private float density;
    private OnFrameSettingsApplied listener;

    public FrameSettingsWindow(Context c)               {super(c);init();}
    public FrameSettingsWindow(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=230*density;

        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);          pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);     pTitle.setStyle(Paint.Style.FILL);
        pTitleA.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE); pTitleA.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT);                  pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT);                  pSub.setTextSize(11*density); pSub.setTypeface(Typeface.MONOSPACE);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);                pBtn.setStyle(Paint.Style.FILL);
        pBtnOn.setColor(0xFF226633);                           pBtnOn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);       pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);          pBtnT.setTextSize(11*density); pBtnT.setTypeface(Typeface.MONOSPACE);
        pSh.setColor(0x44000000);                              pSh.setStyle(Paint.Style.FILL);
        pSep.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);

        setClickable(true); setVisibility(GONE);
    }

    public void setProject(AnimationProject p)          { project=p; }
    public void setOnFrameSettingsApplied(OnFrameSettingsApplied l){ listener=l; }
    public void setPosition(float x,float y)            { winX=x; winY=y; invalidate(); }
    public void showWindow(){ setVisibility(VISIBLE); bringToFront(); invalidate(); }
    public void hideWindow(){ setVisibility(GONE); }

    @Override
    protected void onDraw(Canvas canvas){
        float r   = ImGuiTheme.BORDER_RADIUS;
        float pad = 10*density;
        float tH  = ImGuiTheme.TITLE_BAR_HEIGHT_DP*density;
        float rowH= 30*density;
        float innerW=winW-pad*2;

        winH=tH+pad
            +14*density+pad*0.3f // label "Add Mode"
            +1+pad*0.3f          // sep
            +rowH+pad*0.5f       // Empty Instance button
            +rowH+pad*0.5f       // Loop Mode button
            +1+pad*0.5f          // sep
            +14*density+pad*0.3f // label "Count"
            +rowH+pad*0.5f       // count row [-] [N] [+]
            +1+pad*0.5f          // sep
            +rowH                // Apply button
            +pad;                // bottom

        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);

        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);
        canvas.drawRoundRect(winRect,r,r,pBg);

        // Title
        titleRect.set(winX,winY,winX+winW,winY+tH);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,isDragging?pTitleA:pTitle);
        canvas.restore();
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        canvas.drawText("Frame Settings",winX+pad,titleRect.centerY()-(fm.ascent+fm.descent)/2f,pTxt);

        // Close
        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pTxt);xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA);xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);
        canvas.drawRoundRect(winRect,r,r,pBdr);

        float y=winY+tH+pad;
        Paint.FontMetrics sfm=pSub.getFontMetrics();

        // Label
        canvas.drawText("Add Mode",winX+pad,y+12*density,pSub); y+=14*density+pad*0.3f;
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.3f;

        // Empty Instance button
        emptyModeRect.set(winX+pad,y,winX+pad+innerW,y+rowH);
        boolean emSel=(currentMode==AddMode.EMPTY_INSTANCE);
        canvas.drawRoundRect(emptyModeRect,3,3,emSel?pBtnOn:(emptyHov?pBtnH:pBtn));
        if(emSel){Paint eb=new Paint(pBdr);eb.setColor(0xFF55AA55);canvas.drawRoundRect(emptyModeRect,3,3,eb);}
        drawRowWithCheck(canvas,emptyModeRect,(emSel?"\u2713 ":"  ")+"Empty Instance","Add blank frames",emSel);
        y+=rowH+pad*0.5f;

        // Loop Mode button
        loopModeRect.set(winX+pad,y,winX+pad+innerW,y+rowH);
        boolean loSel=(currentMode==AddMode.LOOP);
        canvas.drawRoundRect(loopModeRect,3,3,loSel?pBtnOn:(loopHov?pBtnH:pBtn));
        if(loSel){Paint lb=new Paint(pBdr);lb.setColor(0xFF55AA55);canvas.drawRoundRect(loopModeRect,3,3,lb);}
        drawRowWithCheck(canvas,loopModeRect,(loSel?"\u2713 ":"  ")+"Loop Mode","Copy frames 1..N",loSel);
        y+=rowH+pad*0.5f;

        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Count label
        canvas.drawText("Frames to add: "+addCount,winX+pad,y+12*density,pSub); y+=14*density+pad*0.3f;

        // [-] [count] [+] row
        float btnSz=rowH;
        minusBtnRect.set(winX+pad,y,winX+pad+btnSz,y+rowH);
        addBtnRect.set(winX+pad+innerW-btnSz,y,winX+pad+innerW,y+rowH);
        canvas.drawRoundRect(minusBtnRect,3,3,minHov?pBtnH:pBtn);
        drawCentered(canvas,"-",minusBtnRect,pBtnT);
        canvas.drawRoundRect(addBtnRect,3,3,addHov?pBtnH:pBtn);
        drawCentered(canvas,"+",addBtnRect,pBtnT);
        // count display
        RectF countRect=new RectF(minusBtnRect.right+pad*0.5f,y,addBtnRect.left-pad*0.5f,y+rowH);
        Paint cbg=new Paint(Paint.ANTI_ALIAS_FLAG);cbg.setColor(0xFF0D0D1A);cbg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(countRect,2,2,cbg);
        canvas.drawRoundRect(countRect,2,2,pBdr);
        Paint ct=new Paint(pSub);ct.setColor(0xFFFFFFFF);ct.setTextSize(13*density);ct.setFakeBoldText(true);
        drawCentered(canvas,String.valueOf(addCount),countRect,ct);
        y+=rowH+pad*0.5f;

        canvas.drawLine(winX+pad,y,winX+winW-pad,y,pSep); y+=1+pad*0.5f;

        // Apply button
        applyBtnRect.set(winX+pad,y,winX+pad+innerW,y+rowH);
        canvas.drawRoundRect(applyBtnRect,3,3,applyHov?pBtnH:pBtn);
        Paint ab=new Paint(pBdr);ab.setColor(0xFF6666AA);canvas.drawRoundRect(applyBtnRect,3,3,ab);
        Paint at=new Paint(pBtnT);at.setTextSize(12*density);at.setFakeBoldText(true);
        String applyLabel=currentMode==AddMode.LOOP?"Add "+addCount+" (Loop)":"Add "+addCount+" Frame"+(addCount>1?"s":"");
        drawCentered(canvas,applyLabel,applyBtnRect,at);
    }

    private void drawRowWithCheck(Canvas canvas,RectF r,String main,String sub,boolean active){
        Paint mt=new Paint(pBtnT);mt.setColor(active?0xFFFFFFFF:0xFFCCCCDD);mt.setTextSize(11*density);
        Paint st=new Paint(pSub);st.setColor(active?0xFFAADDAA:0xFF888899);st.setTextSize(9*density);
        Paint.FontMetrics fm=mt.getFontMetrics();
        float mainY=r.top+(r.height()*0.45f)-(fm.ascent+fm.descent)/2f;
        float subY=r.top+(r.height()*0.75f)-(st.getFontMetrics().ascent+st.getFontMetrics().descent)/2f;
        canvas.drawText(main,r.left+8*density,mainY,mt);
        canvas.drawText(sub, r.left+8*density,subY,st);
    }

    private void drawCentered(Canvas c,String t,RectF r,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(t,r.centerX()-p.measureText(t)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        // Hitung winRect agar hit test konsisten
        winH=estimateWinH();
        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);

        float tx=event.getX(),ty=event.getY();
        if(!winRect.contains(tx,ty)) return false;

        boolean inTitle=(ty>=winY&&ty<=winY+ImGuiTheme.TITLE_BAR_HEIGHT_DP*density);

        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                isDragging=false;longPressTriggered=false;
                touchDownX=tx;touchDownY=ty;dragOffsetX=tx-winX;dragOffsetY=ty-winY;
                closeHov=closeBtnRect.contains(tx,ty);
                emptyHov=emptyModeRect.contains(tx,ty);
                loopHov=loopModeRect.contains(tx,ty);
                addHov=addBtnRect.contains(tx,ty);
                minHov=minusBtnRect.contains(tx,ty);
                applyHov=applyBtnRect.contains(tx,ty);
                if(inTitle&&!closeBtnRect.contains(tx,ty)) lpHandler.postDelayed(lpRunnable,LP_MS);
                invalidate(); return true;

            case MotionEvent.ACTION_MOVE:
                float mdx=tx-touchDownX,mdy=ty-touchDownY;
                if(!isDragging&&mdx*mdx+mdy*mdy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                if(isDragging){
                    View p=(View)getParent();
                    winX=Math.max(0,Math.min(tx-dragOffsetX,p.getWidth()-winW));
                    winY=Math.max(0,Math.min(ty-dragOffsetY,p.getHeight()-winH));
                    invalidate();
                } else {
                    closeHov=closeBtnRect.contains(tx,ty);
                    emptyHov=emptyModeRect.contains(tx,ty);
                    loopHov=loopModeRect.contains(tx,ty);
                    addHov=addBtnRect.contains(tx,ty);
                    minHov=minusBtnRect.contains(tx,ty);
                    applyHov=applyBtnRect.contains(tx,ty);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging;isDragging=false;longPressTriggered=false;
                resetHover();
                if(!wd){
                    if(closeBtnRect.contains(tx,ty))      hideWindow();
                    else if(emptyModeRect.contains(tx,ty)){ currentMode=AddMode.EMPTY_INSTANCE; invalidate(); }
                    else if(loopModeRect.contains(tx,ty)) { currentMode=AddMode.LOOP;           invalidate(); }
                    else if(addBtnRect.contains(tx,ty))   { addCount=Math.min(addCount+1,99);   invalidate(); }
                    else if(minusBtnRect.contains(tx,ty)) { addCount=Math.max(addCount-1,1);    invalidate(); }
                    else if(applyBtnRect.contains(tx,ty)) { applySettings(); }
                }
                invalidate(); return true;

            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable);isDragging=false;longPressTriggered=false;
                resetHover(); invalidate(); return true;
        }
        return false;
    }

    private void resetHover(){closeHov=false;emptyHov=false;loopHov=false;addHov=false;minHov=false;applyHov=false;}

    private void applySettings(){
        if(listener==null) return;
        if(currentMode==AddMode.EMPTY_INSTANCE){
            for(int i=0;i<addCount;i++) listener.onAddEmptyFrame();
        } else {
            // Loop mode: salin frame 1..N sebanyak addCount siklus
            listener.onAddLoopFrames(addCount);
        }
        hideWindow();
    }

    private float estimateWinH(){
        float pad=10*density,tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density,rowH=30*density;
        return tH+pad+(14*density+pad*0.3f)+(1+pad*0.3f)+(rowH+pad*0.5f)*2+(1+pad*0.5f)+(14*density+pad*0.3f)+(rowH+pad*0.5f)+(1+pad*0.5f)+rowH+pad;
    }
}
