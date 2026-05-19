package com.project2d.animation.canvas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.timeline.AnimationProject;
import com.project2d.animation.ui.ImGuiTheme;

public class AnimationCanvasView extends View {

    public enum CanvasPreset {
        HD_1280x720("HD 1280x720",    1280,  720),
        FHD_1920x1080("FHD 1920x1080",1920, 1080),
        SQUARE_1080("Square 1080x1080",1080, 1080),
        A4_PORTRAIT("A4 Portrait",     794, 1123),
        SMALL_640x480("Small 640x480", 640,  480),
        CUSTOM("Custom", 0, 0);
        public final String label;
        public int width, height;
        CanvasPreset(String l,int w,int h){label=l;width=w;height=h;}
    }

    private final Paint paintWS     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCanvas = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHud    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHudBg  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDraw   = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);

    private int docW=1280, docH=720;
    private CanvasPreset currentPreset=CanvasPreset.HD_1280x720;
    private boolean showGrid=false;

    private final Matrix matrix               = new Matrix();
    private final Matrix matrixAtGestureStart = new Matrix();
    private final Matrix invertMatrix         = new Matrix();
    private float currentScale=1f, currentAngle=0f;
    private static final float MIN_SCALE=0.05f, MAX_SCALE=32f, SNAP_THRESH=5f;

    private boolean inTwoFinger=false;
    private float startDist=0f, startAngle=0f, startMidX=0f, startMidY=0f;
    private long  lastTapMs=0L;
    private float lastTapX=0f, lastTapY=0f;
    private static final long DTAP_MS=300L;
    private static final float DTAP_SLOP=60f;
    private boolean showAngleHint=false;
    private long angleHintUntil=0L;

    private DrawingEngine    engine;
    private AnimationProject project;
    private boolean isDrawing=false;

    // Playback mode — saat playing, gambar composite bukan engine bitmap
    private boolean playbackMode=false;

    public AnimationCanvasView(Context c)                {super(c);init();}
    public AnimationCanvasView(Context c,AttributeSet a) {super(c,a);init();}

    private void init(){
        float d=getResources().getDisplayMetrics().density;
        paintWS.setColor(ImGuiTheme.COLOR_APP_BG);         paintWS.setStyle(Paint.Style.FILL);
        paintCanvas.setColor(ImGuiTheme.COLOR_CANVAS_BG);  paintCanvas.setStyle(Paint.Style.FILL);
        paintBorder.setColor(ImGuiTheme.COLOR_CANVAS_BORDER); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setStrokeWidth(1.5f);
        paintShadow.setColor(0x55000000); paintShadow.setStyle(Paint.Style.FILL);
        paintGrid.setColor(0xFFDDDDDD);   paintGrid.setStyle(Paint.Style.STROKE); paintGrid.setStrokeWidth(0.5f);
        paintHud.setColor(0xEEFFFFFF);    paintHud.setTextSize(12*d); paintHud.setAntiAlias(true);
        paintHudBg.setColor(0xBB000000);  paintHudBg.setStyle(Paint.Style.FILL);
        paintDraw.setAntiAlias(true); paintDraw.setFilterBitmap(true);
        setClickable(true); setFocusable(true);
    }

    public void setDrawingEngine(DrawingEngine e){ this.engine=e; }
    public void setProject(AnimationProject p)   { this.project=p; }
    public void setPlaybackMode(boolean b)        { playbackMode=b; invalidate(); }

    public void setCanvasPreset(CanvasPreset p){
        currentPreset=p; docW=p.width; docH=p.height;
        if(engine!=null&&engine.getDrawBitmap()==null) engine.initBitmap(docW,docH);
        fitToView(); invalidate();
    }
    public void setCustomSize(int w,int h){
        currentPreset=CanvasPreset.CUSTOM; docW=w; docH=h;
        if(engine!=null&&engine.getDrawBitmap()==null) engine.initBitmap(docW,docH);
        fitToView(); invalidate();
    }
    public void setShowGrid(boolean s){showGrid=s;invalidate();}
    public CanvasPreset getCurrentPreset(){return currentPreset;}
    public int getCanvasDocW(){return docW;}
    public int getCanvasDocH(){return docH;}

    public void fitToView(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(),vh=getHeight();
        float s=Math.min(vw*0.85f/docW,vh*0.85f/docH);
        matrix.reset(); matrix.postScale(s,s);
        matrix.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        currentScale=s; currentAngle=0f; invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh); fitToView();
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override public boolean onTouchEvent(MotionEvent e){
        int count=e.getPointerCount(), action=e.getActionMasked();
        if(playbackMode) return true; // no drawing during playback

        if(count>=2){
            if(isDrawing){ endDraw(e.getX(0),e.getY(0)); isDrawing=false; }
            handleNavGesture(action,e); return true;
        }

        if(!inTwoFinger){
            switch(action){
                case MotionEvent.ACTION_DOWN:
                    checkDoubleTap(e.getX(0),e.getY(0));
                    if(engine!=null){
                        float[] doc=viewToDoc(e.getX(0),e.getY(0));
                        if(isInsideDoc(doc[0],doc[1])){
                            if(engine.getCurrentTool()==DrawingEngine.Tool.FILL){
                                engine.fill(doc[0],doc[1]); invalidate();
                            } else {
                                engine.startStroke(doc[0],doc[1]); isDrawing=true; invalidate();
                            }
                        }
                    } break;
                case MotionEvent.ACTION_MOVE:
                    if(isDrawing&&engine!=null){
                        for(int i=0;i<e.getHistorySize();i++){
                            float[] d=viewToDoc(e.getHistoricalX(0,i),e.getHistoricalY(0,i));
                            engine.continueStroke(d[0],d[1]);
                        }
                        float[] d=viewToDoc(e.getX(0),e.getY(0));
                        engine.continueStroke(d[0],d[1]); invalidate();
                    } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if(isDrawing&&engine!=null){ endDraw(e.getX(0),e.getY(0)); isDrawing=false; invalidate(); }
                    break;
            }
        }
        return true;
    }

    private void endDraw(float vx,float vy){
        if(engine==null) return;
        float[] d=viewToDoc(vx,vy); engine.endStroke(d[0],d[1]);
        // Mark project frame as modified
        if(project!=null){
            AnimationProject.Frame f=project.getCurrentFrame();
            if(f!=null) f.isEmpty=false;
        }
    }

    private float[] viewToDoc(float vx,float vy){
        if(!matrix.invert(invertMatrix)) return new float[]{vx,vy};
        float[] pts={vx,vy}; invertMatrix.mapPoints(pts); return pts;
    }

    private boolean isInsideDoc(float dx,float dy){ return dx>=0&&dy>=0&&dx<=docW&&dy<=docH; }

    // ── Nav gesture ───────────────────────────────────────────────────────────

    private void handleNavGesture(int action,MotionEvent e){
        switch(action){
            case MotionEvent.ACTION_POINTER_DOWN:
                if(e.getPointerCount()==2){ inTwoFinger=false; beginTwoFinger(e); } break;
            case MotionEvent.ACTION_MOVE:
                if(!inTwoFinger) beginTwoFinger(e); else updateTwoFinger(e); break;
            case MotionEvent.ACTION_POINTER_UP:
                if(inTwoFinger){ inTwoFinger=false; trySnapAngle(); } break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                inTwoFinger=false; break;
        }
    }

    private void beginTwoFinger(MotionEvent e){
        inTwoFinger=true; startDist=pSpan(e); startAngle=pAngle(e);
        startMidX=pMidX(e); startMidY=pMidY(e); matrixAtGestureStart.set(matrix);
    }
    private void updateTwoFinger(MotionEvent e){
        float nd=pSpan(e),na=pAngle(e),nmx=pMidX(e),nmy=pMidY(e);
        if(startDist<1f) return;
        float base=mScale(matrixAtGestureStart);
        float tgt=Math.max(MIN_SCALE,Math.min(MAX_SCALE,base*(nd/startDist)));
        float sd=tgt/base, rd=normDelta(na-startAngle);
        matrix.set(matrixAtGestureStart);
        matrix.postTranslate(nmx-startMidX,nmy-startMidY);
        matrix.postRotate(rd,nmx,nmy); matrix.postScale(sd,sd,nmx,nmy);
        currentScale=tgt; currentAngle=normAngle(mAngle(matrix));
        showAngleHint=true; angleHintUntil=System.currentTimeMillis()+1800L; invalidate();
    }
    private void trySnapAngle(){
        float a=normAngle(mAngle(matrix));
        for(float c:new float[]{0f,90f,180f,270f,360f}){
            if(Math.abs(normDelta(a-c))<=SNAP_THRESH){ float[]pv=canvasCenter(); animSnap((c%360f)-a,pv[0],pv[1]); return; }
        }
    }
    private void animSnap(float delta,float px,float py){
        if(Math.abs(delta)<0.01f){currentAngle=normAngle(mAngle(matrix));return;}
        float[]prev={0f}; ValueAnimator a=ValueAnimator.ofFloat(0f,delta); a.setDuration(150); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{float v=(float)va.getAnimatedValue(),s=v-prev[0];prev[0]=v;matrix.postRotate(s,px,py);currentAngle=normAngle(mAngle(matrix));invalidate();}); a.start();
    }
    private void checkDoubleTap(float x,float y){
        long now=System.currentTimeMillis(); float dx=x-lastTapX,dy=y-lastTapY;
        if(now-lastTapMs<DTAP_MS&&dx*dx+dy*dy<DTAP_SLOP*DTAP_SLOP){animReset();lastTapMs=0;}
        else{lastTapMs=now;lastTapX=x;lastTapY=y;}
    }
    private void animReset(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(),vh=getHeight(),s=Math.min(vw*0.85f/docW,vh*0.85f/docH);
        Matrix tgt=new Matrix(); tgt.postScale(s,s); tgt.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        float[]sv=new float[9],tv=new float[9]; matrix.getValues(sv); tgt.getValues(tv);
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f); a.setDuration(260); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{float t=(float)va.getAnimatedValue(),iv[]=new float[9];for(int i=0;i<9;i++)iv[i]=sv[i]+(tv[i]-sv[i])*t;matrix.setValues(iv);currentScale=sv[0]+(s-sv[0])*t;currentAngle=0f;invalidate();}); a.start();
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override protected void onDraw(Canvas canvas){
        float vw=getWidth(),vh=getHeight();
        canvas.drawRect(0,0,vw,vh,paintWS);
        if(docW==0||docH==0) return;
        canvas.save(); canvas.concat(matrix);
        canvas.drawRect(8,8,docW+8,docH+8,paintShadow);
        canvas.drawRect(0,0,docW,docH,paintCanvas);

        // Gambar bitmap: playback mode → composite, edit mode → engine bitmap
        Bitmap bm=null;
        if(playbackMode&&project!=null){
            bm=project.compositeFrame(project.getCurrentFrameIdx());
        } else if(engine!=null){
            bm=engine.getDrawBitmap();
        }
        if(bm!=null&&!bm.isRecycled()) canvas.drawBitmap(bm,0,0,paintDraw);

        if(showGrid){ float gs=32f; for(float x=0;x<=docW;x+=gs)canvas.drawLine(x,0,x,docH,paintGrid); for(float y=0;y<=docH;y+=gs)canvas.drawLine(0,y,docW,y,paintGrid); }
        canvas.drawRect(0,0,docW,docH,paintBorder);
        canvas.restore();
        drawHud(canvas,vw,vh);
    }

    private void drawHud(Canvas canvas,float vw,float vh){
        float d=getResources().getDisplayMetrics().density;
        hudPill(canvas,String.format("%.0f%%",currentScale*100f),vw-8*d,vh-8*d,d);
        if(showAngleHint&&System.currentTimeMillis()<angleHintUntil){ float disp=currentAngle>180f?currentAngle-360f:currentAngle; hudPill(canvas,String.format("%.1f\u00b0",disp),vw-8*d,vh-30*d,d); invalidate(); }
        if(playbackMode){ hudPill(canvas,"PLAY",vw-8*d,vh-52*d,d); }
    }
    private void hudPill(Canvas canvas,String text,float right,float bottom,float d){
        float pad=5*d,tw=paintHud.measureText(text); Paint.FontMetrics fm=paintHud.getFontMetrics(); float th=-fm.ascent;
        float x=right-tw-pad*2,y=bottom-pad;
        canvas.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,paintHudBg);
        canvas.drawText(text,x,y,paintHud);
    }
    private static float pMidX(MotionEvent e){return(e.getX(0)+e.getX(1))/2f;}
    private static float pMidY(MotionEvent e){return(e.getY(0)+e.getY(1))/2f;}
    private static float pSpan(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return(float)Math.sqrt(dx*dx+dy*dy);}
    private static float pAngle(MotionEvent e){return(float)Math.toDegrees(Math.atan2(e.getY(0)-e.getY(1),e.getX(0)-e.getX(1)));}
    private static float mScale(Matrix m){float[]v=new float[9];m.getValues(v);return(float)Math.sqrt(v[0]*v[0]+v[3]*v[3]);}
    private static float mAngle(Matrix m){float[]v=new float[9];m.getValues(v);return(float)Math.toDegrees(Math.atan2(v[3],v[0]));}
    private static float normAngle(float a){return((a%360f)+360f)%360f;}
    private static float normDelta(float d){d=d%360f;if(d>180f)d-=360f;if(d<-180f)d+=360f;return d;}
    private float[] canvasCenter(){float[]p={docW/2f,docH/2f};matrix.mapPoints(p);return p;}
}
