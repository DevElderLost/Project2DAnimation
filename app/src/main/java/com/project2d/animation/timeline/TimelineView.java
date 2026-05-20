package com.project2d.animation.timeline;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.drawing.OnionSkinSettings;
import com.project2d.animation.ui.ImGuiTheme;

public class TimelineView extends View {

    public interface OnTimelineEvent {
        void onFrameChanged(int frameIdx);
        void onLayerChanged(int layerIdx);
        void onFrameAdded();
        void onFrameRemoved();
        void onLayerAdded();
        void onLayerRemoved(int layerIdx);
        void onPlayStateChanged(boolean playing);
        void onOnionSkinLayerToggled(int layerIdx);
    }

    private static final int HEADER_H_DP = 36;
    private static final int LAYER_W_DP  = 110;
    private static final int FRAME_W_DP  = 56;
    private static final int FRAME_H_DP  = 48;
    private static final int ROW_H_DP    = 58;
    private static final int BTN_SZ_DP   = 14;

    private final Paint pBg      =new Paint(); private final Paint pHeader =new Paint();
    private final Paint pBorder  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSubText =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameBg =new Paint(); private final Paint pFrameAct=new Paint();
    private final Paint pFrameHov=new Paint();
    private final Paint pThumb   =new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint pBtn     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnDel  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerBg =new Paint(); private final Paint pLayerSel=new Paint();
    private final Paint pSep     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAddLayer=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumbBg =new Paint();
    private final Paint pLampOn  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLampOff =new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private AnimationProject project;
    private OnionSkinSettings onionSettings;
    private OnTimelineEvent listener;

    private boolean isPlaying=false,isLooping=true;
    private final Handler playHandler=new Handler(Looper.getMainLooper());
    private final Runnable playRunnable=this::tickPlayback;

    private float scrollX=0f,scrollY=0f,lastTouchX=0f,lastTouchY=0f;
    private boolean scrolling=false;

    private final RectF btnPlay=new RectF(),btnStop=new RectF(),btnPrev=new RectF(),btnNext=new RectF(),btnLoop=new RectF(),btnAddLayer=new RectF();
    private final RectF frameAddBtn=new RectF(),frameDelBtn=new RectF();
    private static final int MAX_LAYERS=20;
    private final RectF[] layerDelBtns =new RectF[MAX_LAYERS];
    private final RectF[] layerLampBtns=new RectF[MAX_LAYERS]; // ikon lampu onion skin
    private int hoveredFrame=-1;

    public TimelineView(Context c)               {super(c);init();}
    public TimelineView(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        for(int i=0;i<MAX_LAYERS;i++){layerDelBtns[i]=new RectF();layerLampBtns[i]=new RectF();}

        pBg.setColor(0xFF15151F);   pHeader.setColor(0xFF0E0E1A);
        pBorder.setColor(0xFF3A3A5A);  pBorder.setStyle(Paint.Style.STROKE); pBorder.setStrokeWidth(1f);
        pText.setColor(0xFFCCCCDD);    pText.setTextSize(12*density); pText.setTypeface(Typeface.MONOSPACE);
        pSubText.setColor(0xFF777788); pSubText.setTextSize(9*density); pSubText.setTypeface(Typeface.MONOSPACE);
        pFrameBg.setColor(0xFF1E1E2E); pFrameAct.setColor(0xFF2A2A50); pFrameHov.setColor(0xFF22223A);
        pThumb.setAntiAlias(true); pThumb.setFilterBitmap(true);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON); pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED); pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(0xFFFFFFFF); pBtnT.setTextSize(10*density); pBtnT.setTypeface(Typeface.MONOSPACE); pBtnT.setAntiAlias(true);
        pBtnDel.setColor(0xFF8B2020); pBtnDel.setStyle(Paint.Style.FILL);
        pLayerBg.setColor(0xFF181828); pLayerSel.setColor(0xFF252545);
        pSep.setColor(0xFF252535); pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pCursor.setColor(0xFFFF3333); pCursor.setStyle(Paint.Style.STROKE); pCursor.setStrokeWidth(2f);
        pAddLayer.setColor(0xFF1E4020); pAddLayer.setStyle(Paint.Style.FILL);
        pThumbBg.setColor(Color.WHITE);
        // Lampu ON = kuning terang, OFF = abu
        pLampOn.setColor(0xFFFFDD00);  pLampOn.setStyle(Paint.Style.FILL);
        pLampOff.setColor(0xFF444455); pLampOff.setStyle(Paint.Style.FILL);

        setClickable(true); setWillNotDraw(false);
    }

    public void setProject(AnimationProject p)         {project=p;invalidate();}
    public void setOnionSkinSettings(OnionSkinSettings s){onionSettings=s;}
    public void setOnTimelineEvent(OnTimelineEvent l)  {listener=l;}

    private void tickPlayback(){
        if(!isPlaying||project==null)return;
        int next=project.getCurrentFrameIdx()+1;
        if(next>=project.getFrameCount()){if(isLooping)next=0;else{stopPlayback();return;}}
        project.setCurrentFrame(next); if(listener!=null)listener.onFrameChanged(next);
        invalidate(); playHandler.postDelayed(playRunnable,1000L/Math.max(1,project.getFps()));
    }
    public void startPlayback(){if(isPlaying)return;isPlaying=true;if(listener!=null)listener.onPlayStateChanged(true);playHandler.post(playRunnable);invalidate();}
    public void stopPlayback(){isPlaying=false;playHandler.removeCallbacks(playRunnable);if(listener!=null)listener.onPlayStateChanged(false);invalidate();}
    public boolean isPlaying(){return isPlaying;}

    @Override
    protected void onDraw(Canvas canvas){
        if(project==null)return;
        int w=getWidth(),h=getHeight();
        float hH=HEADER_H_DP*density,lW=LAYER_W_DP*density,fW=FRAME_W_DP*density,rH=ROW_H_DP*density,pad=4*density,bSz=BTN_SZ_DP*density;
        canvas.drawRect(0,0,w,h,pBg);
        canvas.drawRect(0,0,w,hH,pHeader);
        drawHeader(canvas,w,hH);
        canvas.drawLine(0,hH,w,hH,pBorder);
        canvas.drawRect(0,hH,lW,h,pLayerBg);
        canvas.drawLine(lW,hH,lW,h,pBorder);

        // [+] add layer button
        btnAddLayer.set(lW-bSz*2-pad,hH+pad,lW-pad,hH+bSz+pad);
        canvas.drawRoundRect(btnAddLayer,3,3,pAddLayer); drawCentered(canvas,"+",btnAddLayer,pBtnT);

        canvas.save(); canvas.clipRect(0,hH,w,h);
        int layerCount=project.getLayerCount();
        for(int li=0;li<layerCount;li++){
            float ly=hH+li*rH-scrollY;
            if(ly+rH<hH||ly>h)continue;
            AnimationProject.Layer layer=project.getLayer(li);
            boolean sel=(li==project.getCurrentLayerIdx());
            canvas.drawRect(0,ly,lW,ly+rH,sel?pLayerSel:pLayerBg);
            canvas.drawLine(0,ly+rH,lW,ly+rH,pSep);

            // Layer thumbnail
            float thumbSz=rH-pad*2;
            Bitmap thumb=layer.getLayerThumbnail(project.getCurrentFrameIdx(),(int)thumbSz,(int)thumbSz);
            if(thumb!=null){canvas.drawRect(pad,ly+pad,pad+thumbSz,ly+pad+thumbSz,pThumbBg);canvas.drawBitmap(thumb,pad,ly+pad,pThumb);canvas.drawRect(pad,ly+pad,pad+thumbSz,ly+pad+thumbSz,pBorder);}

            // Layer name
            Paint.FontMetrics fm=pText.getFontMetrics();
            float tx=pad+thumbSz+6*density, ty=ly+rH/2f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(truncate(layer.name,7),tx,ty,pText);

            // 💡 Lampu onion skin per layer
            if(li<MAX_LAYERS){
                float lampR=6*density;
                float lampX=lW-bSz*2-pad-lampR-4*density;
                float lampY=ly+rH/2f;
                layerLampBtns[li].set(lampX-lampR,lampY-lampR,lampX+lampR,lampY+lampR);
                boolean lampEnabled=(onionSettings==null||onionSettings.isLayerEnabled(li));
                // Gambar ikon lampu: lingkaran + garis bawah
                Paint lp=lampEnabled?pLampOn:pLampOff;
                canvas.drawCircle(lampX,lampY,lampR,lp);
                Paint lpBdr=new Paint(Paint.ANTI_ALIAS_FLAG); lpBdr.setColor(lampEnabled?0xFFFFAA00:0xFF333344); lpBdr.setStyle(Paint.Style.STROKE); lpBdr.setStrokeWidth(1.5f);
                canvas.drawCircle(lampX,lampY,lampR,lpBdr);
                // Tangkai lampu kecil
                Paint stem=new Paint(Paint.ANTI_ALIAS_FLAG); stem.setColor(lampEnabled?0xFFFFDD00:0xFF444455); stem.setStyle(Paint.Style.STROKE); stem.setStrokeWidth(2f);
                canvas.drawLine(lampX,lampY+lampR,lampX,lampY+lampR+3*density,stem);
            }

            // [x] delete layer
            if(li<MAX_LAYERS){
                layerDelBtns[li].set(lW-bSz-pad,ly+pad,lW-pad,ly+pad+bSz);
                canvas.drawRoundRect(layerDelBtns[li],2,2,pBtnDel); drawCentered(canvas,"x",layerDelBtns[li],pBtnT);
            }

            // Frame strip
            canvas.save(); canvas.clipRect(lW,hH,w,h);
            int fc=project.getFrameCount();
            for(int fi=0;fi<fc;fi++){
                float fx=lW+fi*fW-scrollX; if(fx+fW<lW||fx>w)continue;
                boolean act=(fi==project.getCurrentFrameIdx()); boolean hov=(fi==hoveredFrame&&sel);
                RectF fr=new RectF(fx,ly,fx+fW,ly+rH);
                canvas.drawRect(fr,act?pFrameAct:(hov?pFrameHov:pFrameBg));
                canvas.drawLine(fx+fW,ly,fx+fW,ly+rH,pSep);
                // Thumbnail
                AnimationProject.Frame frame=layer.getFrame(fi);
                if(frame!=null){
                    float tw=fW-pad*2,th=FRAME_H_DP*density-pad*2;
                    if(tw>0&&th>0){
                        canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pThumbBg);
                        if(!frame.isEmpty&&frame.bitmap!=null&&!frame.bitmap.isRecycled()){
                            Matrix m=new Matrix(); float sx=tw/frame.bitmap.getWidth(),sy=th/frame.bitmap.getHeight(),s=Math.min(sx,sy);
                            m.postScale(s,s); m.postTranslate(fx+pad,ly+pad); canvas.drawBitmap(frame.bitmap,m,pThumb);
                        }
                        canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pBorder);
                    }
                }
                canvas.drawText(String.valueOf(fi+1),fx+2*density,ly+rH-3*density,pSubText);
                // [+] dan [-] hanya di frame aktif, layer aktif
                if(act&&sel){
                    frameAddBtn.set(fx+fW-bSz-1,ly+rH-bSz-1,fx+fW-1,ly+rH-1);
                    canvas.drawRoundRect(frameAddBtn,2,2,pBtn); drawCentered(canvas,"+",frameAddBtn,pBtnT);
                    frameDelBtn.set(fx+1,ly+rH-bSz-1,fx+bSz+1,ly+rH-1);
                    canvas.drawRoundRect(frameDelBtn,2,2,pBtnDel); drawCentered(canvas,"-",frameDelBtn,pBtnT);
                }
            }
            canvas.restore();
        }
        // Playback cursor
        float curX=lW+project.getCurrentFrameIdx()*fW+fW/2f-scrollX;
        if(curX>=lW&&curX<=w) canvas.drawLine(curX,hH,curX,h,pCursor);
        canvas.restore();
        canvas.drawLine(0,0,w,0,pBorder);
    }

    private void drawHeader(Canvas canvas,int w,float hH){
        float pad=5*density,bH=hH-pad*2,bW=bH*1.5f; float x=pad;
        btnPrev.set(x,pad,x+bH,pad+bH);x+=bH+pad; drawBtn(canvas,btnPrev,"|<");
        btnPlay.set(x,pad,x+bW,pad+bH);x+=bW+pad; drawBtn(canvas,btnPlay,isPlaying?"||":"▶");
        btnStop.set(x,pad,x+bH,pad+bH);x+=bH+pad; drawBtn(canvas,btnStop,"■");
        btnNext.set(x,pad,x+bH,pad+bH);x+=bH+pad; drawBtn(canvas,btnNext,">|");
        btnLoop.set(x,pad,x+bH*1.2f,pad+bH);x+=bH*1.2f+pad;
        Paint lp=new Paint(isLooping?pBtnH:pBtn);lp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(btnLoop,3,3,lp);drawCentered(canvas,"↺",btnLoop,pBtnT);
        if(project!=null){
            canvas.drawText(project.getFps()+" fps",x+pad,hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pSubText);
            String fc=(project.getCurrentFrameIdx()+1)+"/"+project.getFrameCount();
            canvas.drawText(fc,w-pText.measureText(fc)-pad*2,hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pText);
        }
    }

    private void drawBtn(Canvas c,RectF r,String icon){
        c.drawRoundRect(r,3,3,pBtn); Paint bb=new Paint(pBorder);bb.setColor(0xFF5555AA);c.drawRoundRect(r,3,3,bb); drawCentered(c,icon,r,pBtnT);
    }
    private void drawCentered(Canvas c,String t,RectF r,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics(); c.drawText(t,r.centerX()-p.measureText(t)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);
    }
    private String truncate(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}

    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        float hH=HEADER_H_DP*density,lW=LAYER_W_DP*density,fW=FRAME_W_DP*density,rH=ROW_H_DP*density;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: lastTouchX=tx;lastTouchY=ty;scrolling=false;hoveredFrame=hitFrame(tx,ty);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                float dx=tx-lastTouchX,dy=ty-lastTouchY;
                if(!scrolling&&(Math.abs(dx)>6*density||Math.abs(dy)>6*density))scrolling=true;
                if(scrolling&&ty>hH){if(tx>lW)scrollX=Math.max(0,scrollX-dx);scrollY=Math.max(0,scrollY-dy);lastTouchX=tx;lastTouchY=ty;}
                hoveredFrame=hitFrame(tx,ty);invalidate();return true;
            case MotionEvent.ACTION_UP: if(!scrolling)handleTap(tx,ty);scrolling=false;hoveredFrame=-1;invalidate();return true;
            case MotionEvent.ACTION_CANCEL: scrolling=false;hoveredFrame=-1;invalidate();return true;
        }
        return true;
    }

    private void handleTap(float tx,float ty){
        if(project==null||listener==null)return;
        float hH=HEADER_H_DP*density,lW=LAYER_W_DP*density,fW=FRAME_W_DP*density;
        // Header
        if(ty<=hH){
            if(btnPlay.contains(tx,ty)){if(isPlaying)stopPlayback();else startPlayback();return;}
            if(btnStop.contains(tx,ty)){stopPlayback();project.setCurrentFrame(0);listener.onFrameChanged(0);return;}
            if(btnPrev.contains(tx,ty)){stopPlayback();project.prevFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnNext.contains(tx,ty)){stopPlayback();project.nextFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnLoop.contains(tx,ty)){isLooping=!isLooping;invalidate();return;}
            if(btnAddLayer.contains(tx,ty)){project.addLayer();listener.onLayerAdded();return;}
            return;
        }
        // Layer panel
        if(tx<=lW){
            int li=hitLayer(ty);
            if(li>=0){
                // Lampu toggle
                if(li<MAX_LAYERS&&layerLampBtns[li].contains(tx,ty)){listener.onOnionSkinLayerToggled(li);return;}
                // Delete layer
                if(li<MAX_LAYERS&&layerDelBtns[li].contains(tx,ty)){project.removeLayer(li);listener.onLayerRemoved(li);return;}
                // Select layer
                project.setCurrentLayer(li);listener.onLayerChanged(li);
            }
            return;
        }
        // Frame area
        if(frameAddBtn.contains(tx,ty)){project.insertFrameAfterCurrent();listener.onFrameAdded();return;}
        if(frameDelBtn.contains(tx,ty)){project.removeCurrentFrame();listener.onFrameRemoved();return;}
        int fi=hitFrame(tx,ty);
        if(fi>=0){project.setCurrentFrame(fi);listener.onFrameChanged(fi);}
    }

    private int hitFrame(float x,float y){
        if(project==null)return -1;
        float hH=HEADER_H_DP*density,lW=LAYER_W_DP*density,fW=FRAME_W_DP*density;
        if(x<=lW||y<=hH)return -1;
        int fi=(int)((x-lW+scrollX)/fW);
        return(fi>=0&&fi<project.getFrameCount())?fi:-1;
    }
    private int hitLayer(float y){
        if(project==null)return -1;
        float hH=HEADER_H_DP*density,rH=ROW_H_DP*density;
        if(y<=hH)return -1;
        int li=(int)((y-hH+scrollY)/rH);
        return(li>=0&&li<project.getLayerCount())?li:-1;
    }
}
