package com.project2d.animation.canvas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.timeline.AnimationProject;
import com.project2d.animation.ui.ImGuiTheme;

public class AnimationCanvasView extends View {

    public enum CanvasPreset {
        HD_1280x720("HD 1280x720",    1280, 720),
        FHD_1920x1080("FHD 1920x1080",1920,1080),
        SQUARE_1080("Square 1080x1080",1080,1080),
        A4_PORTRAIT("A4 Portrait",     794,1123),
        SMALL_640x480("Small 640x480", 640, 480),
        CUSTOM("Custom",0,0);
        public final String label;
        public int width,height;
        CanvasPreset(String l,int w,int h){label=l;width=w;height=h;}
    }

    private final Paint pWS   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pC    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHud  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHudBg=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDraw =new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);

    private int docW=1280,docH=720;
    private CanvasPreset currentPreset=CanvasPreset.HD_1280x720;
    private boolean showGrid=false;

    private final Matrix mat   =new Matrix();
    private final Matrix matGS =new Matrix();
    private final Matrix matInv=new Matrix();
    private float scale=1f,angle=0f;

    private static final float MIN_S=0.05f,MAX_S=32f,SNAP=5f;

    // ── Gesture state ─────────────────────────────────────────────────────────
    /**
     * mode:
     *  0 = idle
     *  1 = drawing (satu jari menggambar)
     *  2 = navigating (dua jari zoom/rotate/pan)
     *
     * Transisi yang diizinkan:
     *  idle  → drawing   : ACTION_DOWN (satu jari)
     *  idle  → navigating: ACTION_POINTER_DOWN (dua jari tanpa stroke)
     *  drawing → navigating: ACTION_POINTER_DOWN → batalkan stroke, masuk nav
     *  navigating → idle : ACTION_POINTER_UP (semua jari lepas)
     *  drawing → idle    : ACTION_UP
     */
    private int mode = 0; // 0=idle, 1=drawing, 2=navigating

    private float sDist=0f,sAngle=0f,sMidX=0f,sMidY=0f;

    // Double-tap
    private long tapMs=0L;
    private float tapX=0f,tapY=0f;
    private static final long TAP_MS=300L;
    private static final float TAP_SLOP=60f;

    // HUD
    private boolean showAngleHint=false;
    private long angleUntil=0L;

    private DrawingEngine    engine;
    private AnimationProject project;
    private boolean playbackMode=false;

    public AnimationCanvasView(Context c)               {super(c);init();}
    public AnimationCanvasView(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        float d=getResources().getDisplayMetrics().density;
        pWS.setColor(ImGuiTheme.COLOR_APP_BG);         pWS.setStyle(Paint.Style.FILL);
        pC.setColor(ImGuiTheme.COLOR_CANVAS_BG);        pC.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_CANVAS_BORDER);  pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pSh.setColor(0x55000000);  pSh.setStyle(Paint.Style.FILL);
        pGrid.setColor(0xFFDDDDDD);pGrid.setStyle(Paint.Style.STROKE);pGrid.setStrokeWidth(0.5f);
        pHud.setColor(0xEEFFFFFF); pHud.setTextSize(12*d); pHud.setAntiAlias(true);
        pHudBg.setColor(0xBB000000);pHudBg.setStyle(Paint.Style.FILL);
        pDraw.setAntiAlias(true);  pDraw.setFilterBitmap(true);
        setClickable(true); setFocusable(true);
    }

    public void setDrawingEngine(DrawingEngine e){ engine=e; }
    public void setProject(AnimationProject p)   { project=p; }
    public void setPlaybackMode(boolean b)        { playbackMode=b; invalidate(); }
    public CanvasPreset getCurrentPreset()        { return currentPreset; }

    public void setCanvasPreset(CanvasPreset p){
        currentPreset=p; docW=p.width; docH=p.height; fitToView(); invalidate();
    }
    public void setCustomSize(int w,int h){
        currentPreset=CanvasPreset.CUSTOM; docW=w; docH=h; fitToView(); invalidate();
    }
    public void setShowGrid(boolean s){showGrid=s;invalidate();}

    public void fitToView(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(),vh=getHeight();
        float s=Math.min(vw*0.85f/docW,vh*0.85f/docH);
        mat.reset(); mat.postScale(s,s);
        mat.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        scale=s; angle=0f; invalidate();
    }

    @Override
    protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh); fitToView();
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e){
        if(playbackMode) return true;

        int count  = e.getPointerCount();
        int action = e.getActionMasked();

        switch(action){

            case MotionEvent.ACTION_DOWN:
                // Jari pertama turun — mulai mungkin gambar
                mode=0;
                checkDTap(e.getX(0),e.getY(0));
                // Hanya mulai stroke jika mode belum navigasi
                if(engine!=null){
                    float[]d=vToD(e.getX(0),e.getY(0));
                    if(inDoc(d[0],d[1])){
                        if(engine.getCurrentTool()==DrawingEngine.Tool.FILL){
                            engine.fill(d[0],d[1]); invalidate();
                            mode=0; // fill selesai seketika
                        } else {
                            engine.startStroke(d[0],d[1]);
                            mode=1; // mode drawing
                            invalidate();
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Jari kedua (atau lebih) turun
                if(count==2){
                    // *** FIX UTAMA: Batalkan stroke tanpa commit ke bitmap ***
                    if(mode==1 && engine!=null){
                        engine.cancelStroke(); // batalkan, tidak disimpan
                        invalidate();
                    }
                    // Masuk mode navigasi
                    mode=2;
                    beginNav(e);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if(mode==2 && count>=2){
                    // Mode navigasi: update zoom/rotate/pan
                    updateNav(e);
                } else if(mode==1 && count==1){
                    // Mode drawing: lanjutkan stroke
                    if(engine!=null){
                        for(int i=0;i<e.getHistorySize();i++){
                            float[]d=vToD(e.getHistoricalX(0,i),e.getHistoricalY(0,i));
                            engine.continueStroke(d[0],d[1]);
                        }
                        float[]d=vToD(e.getX(0),e.getY(0));
                        engine.continueStroke(d[0],d[1]);
                        invalidate();
                    }
                }
                // mode==0: tidak melakukan apapun
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // Salah satu jari navigasi diangkat
                if(mode==2){
                    // Jika tinggal satu jari — kembali idle, snap angle
                    if(count==2){
                        mode=0;
                        snapAngle(); // snap hanya saat keluar dari nav
                    }
                    // Jika masih >=2 jari: tetap navigasi
                }
                break;

            case MotionEvent.ACTION_UP:
                // Jari terakhir diangkat
                if(mode==1 && engine!=null){
                    // Commit stroke ke bitmap
                    float[]d=vToD(e.getX(0),e.getY(0));
                    engine.endStroke(d[0],d[1]);
                    // Tandai frame tidak kosong
                    if(project!=null){
                        AnimationProject.Frame f=project.getCurrentFrame();
                        if(f!=null) f.isEmpty=false;
                    }
                    invalidate();
                }
                mode=0;
                break;

            case MotionEvent.ACTION_CANCEL:
                // Batalkan semua tanpa commit
                if(mode==1 && engine!=null){
                    engine.cancelStroke();
                    invalidate();
                }
                mode=0;
                break;
        }
        return true;
    }

    // ── Navigation (zoom/rotate/pan dua jari) ─────────────────────────────────

    private void beginNav(MotionEvent e){
        sDist=span(e); sAngle=fAngle(e); sMidX=midX(e); sMidY=midY(e);
        matGS.set(mat);
    }

    private void updateNav(MotionEvent e){
        float nd=span(e),na=fAngle(e),nmx=midX(e),nmy=midY(e);
        if(sDist<1f) return;
        float base=mScale(matGS);
        float tgt=Math.max(MIN_S,Math.min(MAX_S,base*(nd/sDist)));
        float sd=tgt/base, rd=normD(na-sAngle);
        mat.set(matGS);
        mat.postTranslate(nmx-sMidX,nmy-sMidY);
        mat.postRotate(rd,nmx,nmy);
        mat.postScale(sd,sd,nmx,nmy);
        scale=tgt; angle=normA(mAngle(mat));
        showAngleHint=true; angleUntil=System.currentTimeMillis()+1500L;
        invalidate();
    }

    // ── Snap angle ────────────────────────────────────────────────────────────
    // Dipanggil HANYA dari ACTION_POINTER_UP saat keluar dari nav

    private void snapAngle(){
        float a=normA(mAngle(mat));
        float bestDiff=Float.MAX_VALUE; float bestC=-1f;
        for(float c:new float[]{0f,90f,180f,270f,360f}){
            float diff=Math.abs(normD(a-c));
            if(diff<bestDiff){ bestDiff=diff; bestC=c%360f; }
        }
        if(bestC>=0f && bestDiff<=SNAP){
            float delta=normD(bestC-a);
            float[]pv=cCenter();
            animSnap(delta,pv[0],pv[1]);
        }
    }

    private void animSnap(float delta,float px,float py){
        if(Math.abs(delta)<0.01f){ angle=normA(mAngle(mat)); return; }
        float[]prev={0f};
        ValueAnimator a=ValueAnimator.ofFloat(0f,delta);
        a.setDuration(150); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{
            float v=(float)va.getAnimatedValue(),s=v-prev[0]; prev[0]=v;
            mat.postRotate(s,px,py); angle=normA(mAngle(mat)); invalidate();
        }); a.start();
    }

    // ── Double-tap reset ──────────────────────────────────────────────────────

    private void checkDTap(float x,float y){
        long now=System.currentTimeMillis();
        float dx=x-tapX,dy=y-tapY;
        if(now-tapMs<TAP_MS && dx*dx+dy*dy<TAP_SLOP*TAP_SLOP){
            animReset(); tapMs=0;
        } else { tapMs=now; tapX=x; tapY=y; }
    }

    private void animReset(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(),vh=getHeight();
        float s=Math.min(vw*0.85f/docW,vh*0.85f/docH);
        Matrix tgt=new Matrix(); tgt.postScale(s,s); tgt.postTranslate((vw-docW*s)/2f,(vh-docH*s)/2f);
        float[]sv=new float[9],tv=new float[9]; mat.getValues(sv); tgt.getValues(tv);
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f); a.setDuration(260); a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(va->{
            float t=(float)va.getAnimatedValue(),iv[]=new float[9];
            for(int i=0;i<9;i++) iv[i]=sv[i]+(tv[i]-sv[i])*t;
            mat.setValues(iv); scale=sv[0]+(s-sv[0])*t; angle=0f; invalidate();
        }); a.start();
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas){
        float vw=getWidth(),vh=getHeight();
        canvas.drawRect(0,0,vw,vh,pWS);
        if(docW==0||docH==0) return;
        canvas.save(); canvas.concat(mat);
        canvas.drawRect(8,8,docW+8,docH+8,pSh);
        canvas.drawRect(0,0,docW,docH,pC);
        Bitmap bm=null;
        if(playbackMode&&project!=null) bm=project.compositeFrame(project.getCurrentFrameIdx());
        else if(engine!=null) bm=engine.getDrawBitmap();
        if(bm!=null&&!bm.isRecycled()) canvas.drawBitmap(bm,0,0,pDraw);
        if(showGrid){ float gs=32f;
            for(float x=0;x<=docW;x+=gs) canvas.drawLine(x,0,x,docH,pGrid);
            for(float y=0;y<=docH;y+=gs) canvas.drawLine(0,y,docW,y,pGrid);
        }
        canvas.drawRect(0,0,docW,docH,pBdr);
        canvas.restore();
        drawHud(canvas,vw,vh);
    }

    private void drawHud(Canvas c,float vw,float vh){
        float d=getResources().getDisplayMetrics().density;
        hud(c,String.format("%.0f%%",scale*100f),vw-8*d,vh-8*d,d);
        if(showAngleHint&&System.currentTimeMillis()<angleUntil){
            float disp=angle>180f?angle-360f:angle;
            hud(c,String.format("%.1f°",disp),vw-8*d,vh-30*d,d); invalidate();
        }
        if(playbackMode) hud(c,"▶ PLAY",vw-8*d,vh-52*d,d);
    }

    private void hud(Canvas c,String t,float r,float b,float d){
        float pad=5*d,tw=pHud.measureText(t);
        Paint.FontMetrics fm=pHud.getFontMetrics(); float th=-fm.ascent;
        float x=r-tw-pad*2,y=b-pad;
        c.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,pHudBg);
        c.drawText(t,x,y,pHud);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float[]vToD(float vx,float vy){
        if(!mat.invert(matInv)) return new float[]{vx,vy};
        float[]p={vx,vy}; matInv.mapPoints(p); return p;
    }
    private boolean inDoc(float dx,float dy){ return dx>=0&&dy>=0&&dx<=docW&&dy<=docH; }
    private static float midX(MotionEvent e){ return(e.getX(0)+e.getX(1))/2f; }
    private static float midY(MotionEvent e){ return(e.getY(0)+e.getY(1))/2f; }
    private static float span(MotionEvent e){ float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1); return(float)Math.sqrt(dx*dx+dy*dy); }
    private static float fAngle(MotionEvent e){ return(float)Math.toDegrees(Math.atan2(e.getY(0)-e.getY(1),e.getX(0)-e.getX(1))); }
    private static float mScale(Matrix m){ float[]v=new float[9]; m.getValues(v); return(float)Math.sqrt(v[0]*v[0]+v[3]*v[3]); }
    private static float mAngle(Matrix m){ float[]v=new float[9]; m.getValues(v); return(float)Math.toDegrees(Math.atan2(v[3],v[0])); }
    private static float normA(float a){ return((a%360f)+360f)%360f; }
    private static float normD(float d){ d=d%360f; if(d>180f)d-=360f; if(d<-180f)d+=360f; return d; }
    private float[]cCenter(){ float[]p={docW/2f,docH/2f}; mat.mapPoints(p); return p; }
}
