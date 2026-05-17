package com.project2d.animation.canvas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.project2d.animation.ui.ImGuiTheme;

public class AnimationCanvasView extends View {

    public enum CanvasPreset {
        HD_1280x720("HD 1280x720",    1280,  720),
        FHD_1920x1080("FHD 1920x1080", 1920, 1080),
        SQUARE_1080("Square 1080x1080", 1080, 1080),
        A4_PORTRAIT("A4 Portrait",    794,  1123),
        SMALL_640x480("Small 640x480",  640,   480),
        CUSTOM("Custom", 0, 0);
        public final String label;
        public int width, height;
        CanvasPreset(String l, int w, int h) { label=l; width=w; height=h; }
    }

    private final Paint paintWS     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCanvas = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHud    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHudBg  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int docW = 1280, docH = 720;
    private CanvasPreset currentPreset = CanvasPreset.HD_1280x720;
    private boolean showGrid = false;

    // Single Matrix for all transforms (pan + zoom + rotate)
    private final Matrix matrix               = new Matrix();
    private final Matrix matrixAtGestureStart = new Matrix();
    private float currentScale = 1f;
    private float currentAngle = 0f;

    private static final float MIN_SCALE   = 0.05f;
    private static final float MAX_SCALE   = 32f;
    private static final float SNAP_THRESH = 5f;

    // Gesture state
    private boolean inTwoFinger = false;
    private boolean inSinglePan = false;
    private float gestureStartDist=0f, gestureStartAngle=0f;
    private float gestureStartMidX=0f, gestureStartMidY=0f;
    private float lastPanX=0f, lastPanY=0f;

    // Double-tap
    private long  lastTapMs = 0L;
    private float lastTapX=0f, lastTapY=0f;
    private static final long  DTAP_MS   = 280L;
    private static final float DTAP_SLOP = 50f;

    // HUD
    private boolean showAngleHint  = false;
    private long    angleHintUntil = 0L;

    public AnimationCanvasView(Context c)               { super(c); init(); }
    public AnimationCanvasView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        paintWS.setColor(ImGuiTheme.COLOR_APP_BG); paintWS.setStyle(Paint.Style.FILL);
        paintCanvas.setColor(ImGuiTheme.COLOR_CANVAS_BG); paintCanvas.setStyle(Paint.Style.FILL);
        paintBorder.setColor(ImGuiTheme.COLOR_CANVAS_BORDER); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setStrokeWidth(1.5f);
        paintShadow.setColor(0x55000000); paintShadow.setStyle(Paint.Style.FILL);
        paintGrid.setColor(0xFFDDDDDD); paintGrid.setStyle(Paint.Style.STROKE); paintGrid.setStrokeWidth(0.5f);
        paintHud.setColor(0xEEFFFFFF); paintHud.setTextSize(12*d); paintHud.setAntiAlias(true);
        paintHudBg.setColor(0xBB000000); paintHudBg.setStyle(Paint.Style.FILL);
        setClickable(true); setFocusable(true);
    }

    public void setCanvasPreset(CanvasPreset p){ currentPreset=p; docW=p.width; docH=p.height; fitToView(); invalidate(); }
    public void setCustomSize(int w,int h){ currentPreset=CanvasPreset.CUSTOM; docW=w; docH=h; fitToView(); invalidate(); }
    public void setShowGrid(boolean s){ showGrid=s; invalidate(); }
    public CanvasPreset getCurrentPreset(){ return currentPreset; }
    public int getCanvasDocW(){ return docW; }
    public int getCanvasDocH(){ return docH; }

    public void fitToView() {
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(), vh=getHeight();
        float s=Math.min(vw*0.85f/docW, vh*0.85f/docH);
        matrix.reset();
        matrix.postScale(s,s);
        matrix.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        currentScale=s; currentAngle=0f;
        invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){ super.onSizeChanged(w,h,ow,oh); fitToView(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int count=e.getPointerCount(), action=e.getActionMasked();
        switch(action){
            case MotionEvent.ACTION_DOWN:
                inSinglePan=true; lastPanX=e.getX(); lastPanY=e.getY();
                handleDoubleTap(e.getX(),e.getY());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(count==2){ inSinglePan=false; startTwoFinger(e); }
                break;
            case MotionEvent.ACTION_MOVE:
                if(count>=2&&inTwoFinger){ updateTwoFinger(e); }
                else if(count==1&&inSinglePan){
                    float dx=e.getX()-lastPanX, dy=e.getY()-lastPanY;
                    matrix.postTranslate(dx,dy);
                    lastPanX=e.getX(); lastPanY=e.getY();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                inTwoFinger=false;
                if(e.getPointerCount()==2){ int k=e.getActionIndex()==0?1:0; lastPanX=e.getX(k); lastPanY=e.getY(k); inSinglePan=true; }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                inSinglePan=false; inTwoFinger=false; trySnapAngle(); break;
        }
        return true;
    }

    private void startTwoFinger(MotionEvent e){
        inTwoFinger=true;
        gestureStartDist=span(e); gestureStartAngle=fingerAngle(e);
        gestureStartMidX=midX(e); gestureStartMidY=midY(e);
        matrixAtGestureStart.set(matrix);
    }

    private void updateTwoFinger(MotionEvent e){
        float newDist=span(e), newAngle=fingerAngle(e), newMX=midX(e), newMY=midY(e);
        if(gestureStartDist<1f) return;
        float baseScale=mScale(matrixAtGestureStart);
        float target=Math.max(MIN_SCALE,Math.min(MAX_SCALE,baseScale*(newDist/gestureStartDist)));
        float scaleDelta=target/baseScale;
        float rotateDelta=newAngle-gestureStartAngle;
        matrix.set(matrixAtGestureStart);
        matrix.postTranslate(newMX-gestureStartMidX, newMY-gestureStartMidY);
        matrix.postRotate(rotateDelta, newMX, newMY);
        matrix.postScale(scaleDelta, scaleDelta, newMX, newMY);
        currentScale=target;
        currentAngle=normAngle(mAngle(matrix));
        showAngleHint=true; angleHintUntil=System.currentTimeMillis()+1800L;
        invalidate();
    }

    private void trySnapAngle(){
        float a=normAngle(mAngle(matrix));
        for(float c:new float[]{0f,90f,180f,270f,360f}){
            if(Math.abs(a-c)<=SNAP_THRESH){
                float[] pv=canvasCenter();
                animSnap((c%360f)-a, pv[0], pv[1]);
                return;
            }
        }
    }

    private void animSnap(float delta, float px, float py){
        if(Math.abs(delta)<0.01f){currentAngle=0f;return;}
        float[] prev={0f};
        ValueAnimator a=ValueAnimator.ofFloat(0f,delta);
        a.setDuration(160); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{ float v=(float)va.getAnimatedValue(),s=v-prev[0]; prev[0]=v; matrix.postRotate(s,px,py); currentAngle=normAngle(mAngle(matrix)); invalidate(); });
        a.start();
    }

    private void handleDoubleTap(float x,float y){
        long now=System.currentTimeMillis();
        float dx=x-lastTapX,dy=y-lastTapY;
        if(now-lastTapMs<DTAP_MS&&dx*dx+dy*dy<DTAP_SLOP*DTAP_SLOP){ animReset(); lastTapMs=0; }
        else{ lastTapMs=now; lastTapX=x; lastTapY=y; }
    }

    private void animReset(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(),vh=getHeight();
        float s=Math.min(vw*0.85f/docW,vh*0.85f/docH);
        Matrix tgt=new Matrix(); tgt.postScale(s,s); tgt.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        float[] sv=new float[9],tv=new float[9]; matrix.getValues(sv); tgt.getValues(tv);
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f); a.setDuration(260); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{ float t=(float)va.getAnimatedValue(); float[] iv=new float[9]; for(int i=0;i<9;i++)iv[i]=sv[i]+(tv[i]-sv[i])*t; matrix.setValues(iv); currentScale=s*t+mScale(matrix)*(1f-t); currentAngle=0f; invalidate(); });
        a.start();
    }

    @Override protected void onDraw(Canvas canvas){
        float vw=getWidth(),vh=getHeight();
        canvas.drawRect(0,0,vw,vh,paintWS);
        if(docW==0||docH==0) return;
        canvas.save();
        canvas.concat(matrix);
        canvas.drawRect(8,8,docW+8,docH+8,paintShadow);
        canvas.drawRect(0,0,docW,docH,paintCanvas);
        if(showGrid){ float gs=32f; for(float x=0;x<=docW;x+=gs)canvas.drawLine(x,0,x,docH,paintGrid); for(float y=0;y<=docH;y+=gs)canvas.drawLine(0,y,docW,y,paintGrid); }
        canvas.drawRect(0,0,docW,docH,paintBorder);
        canvas.restore();
        drawHud(canvas,vw,vh);
    }

    private void drawHud(Canvas canvas,float vw,float vh){
        float d=getResources().getDisplayMetrics().density;
        hudPill(canvas, String.format("%.0f%%",currentScale*100f), vw-8*d, vh-8*d, d);
        boolean show=showAngleHint&&System.currentTimeMillis()<angleHintUntil;
        if(show){
            float disp=currentAngle>180f?currentAngle-360f:currentAngle;
            hudPill(canvas, String.format("%.1f\u00b0",disp), vw-8*d, vh-30*d, d);
            invalidate();
        }
    }

    private void hudPill(Canvas canvas,String text,float right,float bottom,float d){
        float pad=5*d, tw=paintHud.measureText(text);
        Paint.FontMetrics fm=paintHud.getFontMetrics(); float th=-fm.ascent;
        float x=right-tw-pad*2, y=bottom-pad;
        canvas.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,paintHudBg);
        canvas.drawText(text,x,y,paintHud);
    }

    // Math helpers
    private static float midX(MotionEvent e){ return (e.getX(0)+e.getX(1))/2f; }
    private static float midY(MotionEvent e){ return (e.getY(0)+e.getY(1))/2f; }
    private static float span(MotionEvent e){ float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1); return (float)Math.sqrt(dx*dx+dy*dy); }
    private static float fingerAngle(MotionEvent e){ return (float)Math.toDegrees(Math.atan2(e.getY(0)-e.getY(1),e.getX(0)-e.getX(1))); }
    private static float mScale(Matrix m){ float[] v=new float[9]; m.getValues(v); return (float)Math.sqrt(v[0]*v[0]+v[3]*v[3]); }
    private static float mAngle(Matrix m){ float[] v=new float[9]; m.getValues(v); return (float)Math.toDegrees(Math.atan2(v[3],v[0])); }
    private static float normAngle(float a){ return ((a%360f)+360f)%360f; }
    private float[] canvasCenter(){ float[] p={docW/2f,docH/2f}; matrix.mapPoints(p); return p; }
}
