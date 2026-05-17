package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.ui.ImGuiTheme;

public class FloatingWindow extends View {

    public interface OnWindowEventListener {
        void onTestButtonClicked();
        void onWindowClosed();
    }

    private float winX=60f, winY=120f, winW, winH;
    private boolean isDragging=false, longPressTriggered=false;
    private float touchDownX=0f, touchDownY=0f, dragOffsetX=0f, dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};

    private final Paint pBg=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh=new Paint(Paint.ANTI_ALIAS_FLAG);

    private RectF titleRect=new RectF();
    private RectF closeBtnRect=new RectF();
    private RectF testBtnRect=new RectF();
    private RectF winRect=new RectF();

    private boolean testHov=false, closeHov=false;
    private float density;
    private OnWindowEventListener eventListener;

    public FloatingWindow(Context c){super(c);init();}
    public FloatingWindow(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=220*density; winH=160*density;
        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG); pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE); pTitle.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER); pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT); pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT); pSub.setTextSize(13*density); pSub.setTypeface(Typeface.MONOSPACE);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON); pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED); pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT); pBtnT.setTextSize(12*density); pBtnT.setTypeface(Typeface.MONOSPACE);
        pSh.setColor(0x44000000); pSh.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    public void setEventListener(OnWindowEventListener l){eventListener=l;}
    public void setPosition(float x,float y){winX=x;winY=y;invalidate();}

    @Override
    protected void onDraw(Canvas canvas){
        float tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density,r=ImGuiTheme.BORDER_RADIUS,pad=10*density;
        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);
        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);
        canvas.drawRoundRect(winRect,r,r,pBg);
        titleRect.set(winX,winY,winX+winW,winY+tH);
        Paint tp=new Paint(pTitle); if(isDragging) tp.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,tp);
        canvas.restore();
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        float tY=titleRect.centerY()-(fm.ascent+fm.descent)/2f;
        canvas.drawText("Hello Window",winX+pad,tY,pTxt);
        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pTxt); xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);
        canvas.drawRoundRect(winRect,r,r,pBdr);
        float cY=winY+tH+pad;
        Paint.FontMetrics sfm=pSub.getFontMetrics();
        canvas.drawText("Hello, World!",winX+pad,cY-sfm.ascent,pSub);
        float bW=80*density,bH=ImGuiTheme.BUTTON_HEIGHT_DP*density,bX=winX+pad,bY=cY-sfm.ascent+14*density;
        testBtnRect.set(bX,bY,bX+bW,bY+bH);
        canvas.drawRoundRect(testBtnRect,3,3,testHov?pBtnH:pBtn);
        Paint bb=new Paint(pBdr); bb.setColor(0xFF6666AA); bb.setStrokeWidth(1f);
        canvas.drawRoundRect(testBtnRect,3,3,bb);
        Paint.FontMetrics bfm=pBtnT.getFontMetrics();
        canvas.drawText("Test",testBtnRect.centerX()-pBtnT.measureText("Test")/2f,testBtnRect.centerY()-(bfm.ascent+bfm.descent)/2f,pBtnT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        float tx=event.getX(),ty=event.getY();
        // Touches outside window rect pass through to canvas below
        if(!winRect.contains(tx,ty)) return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                touchDownX=tx; touchDownY=ty;
                longPressTriggered=false; isDragging=false;
                if(titleRect.contains(tx,ty)&&!closeBtnRect.contains(tx,ty)){
                    dragOffsetX=tx-winX; dragOffsetY=ty-winY;
                    lpHandler.postDelayed(lpRunnable,LP_MS);
                }
                testHov=testBtnRect.contains(tx,ty); closeHov=closeBtnRect.contains(tx,ty);
                invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if(isDragging){
                    winX=tx-dragOffsetX; winY=ty-dragOffsetY; invalidate();
                } else {
                    float dx=tx-touchDownX,dy=ty-touchDownY;
                    if(dx*dx+dy*dy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                    testHov=testBtnRect.contains(tx,ty); closeHov=closeBtnRect.contains(tx,ty);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; longPressTriggered=false;
                if(!wd){
                    if(testBtnRect.contains(tx,ty)&&eventListener!=null) eventListener.onTestButtonClicked();
                    if(closeBtnRect.contains(tx,ty)){if(eventListener!=null)eventListener.onWindowClosed();setVisibility(GONE);}
                }
                testHov=false; closeHov=false; invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable);
                isDragging=false; longPressTriggered=false;
                testHov=false; closeHov=false; invalidate(); return true;
        }
        return false;
    }
}
