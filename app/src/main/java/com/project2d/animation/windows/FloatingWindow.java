package com.project2d.animation.windows;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.project2d.animation.ui.ImGuiTheme;
public class FloatingWindow extends View {
    public interface OnWindowEventListener{void onTestButtonClicked();void onWindowClosed();}
    private float winX=60f,winY=120f,winW,winH;
    private boolean isDragging=false,longPressTriggered=false;
    private float touchDownX=0f,touchDownY=0f,dragOffX=0f,dragOffY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};
    private final Paint pBg=new Paint(Paint.ANTI_ALIAS_FLAG),pTitle=new Paint(Paint.ANTI_ALIAS_FLAG),pBorder=new Paint(Paint.ANTI_ALIAS_FLAG),pText=new Paint(Paint.ANTI_ALIAS_FLAG),pSub=new Paint(Paint.ANTI_ALIAS_FLAG),pBtn=new Paint(Paint.ANTI_ALIAS_FLAG),pBtnH=new Paint(Paint.ANTI_ALIAS_FLAG),pBtnT=new Paint(Paint.ANTI_ALIAS_FLAG),pHint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF titleRect=new RectF(),closeBtnRect=new RectF(),testBtnRect=new RectF();
    private boolean testHov=false,closeHov=false;
    private float density;
    private OnWindowEventListener eventListener;
    public FloatingWindow(Context c){super(c);init();}
    public FloatingWindow(Context c,AttributeSet a){super(c,a);init();}
    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=220*density;winH=160*density;
        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);pTitle.setStyle(Paint.Style.FILL);
        pBorder.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);pBorder.setStyle(Paint.Style.STROKE);pBorder.setStrokeWidth(1.5f);
        pText.setColor(ImGuiTheme.COLOR_TEXT);pText.setTextSize(13*density);pText.setTypeface(Typeface.MONOSPACE);pText.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT);pSub.setTextSize(13*density);pSub.setTypeface(Typeface.MONOSPACE);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);pBtnT.setTextSize(12*density);pBtnT.setTypeface(Typeface.MONOSPACE);
        pHint.setColor(0xAAFFFFFF);pHint.setTextSize(9*density);pHint.setTypeface(Typeface.MONOSPACE);
        setClickable(true);
    }
    public void setEventListener(OnWindowEventListener l){eventListener=l;}
    public void setPosition(float x,float y){winX=x;winY=y;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density,r=ImGuiTheme.BORDER_RADIUS,pad=10*density;
        winX=Math.max(0,Math.min(winX,getWidth()-winW));winY=Math.max(0,Math.min(winY,getHeight()-winH));
        RectF wr=new RectF(winX,winY,winX+winW,winY+winH);
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);sh.setColor(isDragging?0x66000000:0x44000000);sh.setStyle(Paint.Style.FILL);
        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,sh);
        canvas.drawRoundRect(wr,r,r,pBg);
        titleRect.set(winX,winY,winX+winW,winY+tH);
        Paint tp=new Paint(pTitle);if(isDragging)tp.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE);
        canvas.save();canvas.clipRect(wr);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,tp);
        canvas.restore();
        Paint.FontMetrics fm=pText.getFontMetrics();
        float tY=titleRect.centerY()-(fm.ascent+fm.descent)/2f;
        canvas.drawText("Hello Window",winX+pad,tY,pText);
        if(isDragging){canvas.drawText("dragging",winX+winW-pHint.measureText("dragging")-pad,tY,pHint);}
        else{canvas.drawText("hold to drag",winX+winW-pHint.measureText("hold to drag")-pad,tY,pHint);}
        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pText);xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA);xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);
        canvas.drawRoundRect(wr,r,r,pBorder);
        float cY=winY+tH+pad;
        Paint.FontMetrics sfm=pSub.getFontMetrics();
        canvas.drawText("Hello, World!",winX+pad,cY-sfm.ascent,pSub);
        float bW=80*density,bH=ImGuiTheme.BUTTON_HEIGHT_DP*density,bX=winX+pad,bY=cY-sfm.ascent+14*density;
        testBtnRect.set(bX,bY,bX+bW,bY+bH);
        canvas.drawRoundRect(testBtnRect,3,3,testHov?pBtnH:pBtn);
        Paint bb=new Paint(pBorder);bb.setColor(0xFF6666AA);bb.setStrokeWidth(1f);
        canvas.drawRoundRect(testBtnRect,3,3,bb);
        Paint.FontMetrics bfm=pBtnT.getFontMetrics();
        canvas.drawText("Test",testBtnRect.centerX()-pBtnT.measureText("Test")/2f,testBtnRect.centerY()-(bfm.ascent+bfm.descent)/2f,pBtnT);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:
                touchDownX=tx;touchDownY=ty;longPressTriggered=false;isDragging=false;
                if(titleRect.contains(tx,ty)&&!closeBtnRect.contains(tx,ty)){dragOffX=tx-winX;dragOffY=ty-winY;lpHandler.postDelayed(lpRunnable,LP_MS);}
                testHov=testBtnRect.contains(tx,ty);closeHov=closeBtnRect.contains(tx,ty);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(isDragging){winX=tx-dragOffX;winY=ty-dragOffY;invalidate();}
                else{float dx=tx-touchDownX,dy=ty-touchDownY;if(dx*dx+dy*dy>(8*density)*(8*density))lpHandler.removeCallbacks(lpRunnable);testHov=testBtnRect.contains(tx,ty);closeHov=closeBtnRect.contains(tx,ty);invalidate();}
                return true;
            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);boolean wd=isDragging;isDragging=false;longPressTriggered=false;
                if(!wd){if(testBtnRect.contains(tx,ty)&&eventListener!=null)eventListener.onTestButtonClicked();if(closeBtnRect.contains(tx,ty)){if(eventListener!=null)eventListener.onWindowClosed();setVisibility(GONE);}}
                testHov=false;closeHov=false;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:lpHandler.removeCallbacks(lpRunnable);isDragging=false;longPressTriggered=false;testHov=false;closeHov=false;invalidate();return true;
        }
        return false;
    }
}
