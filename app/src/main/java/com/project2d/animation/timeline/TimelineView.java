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
        void onLayerAdded(boolean isBackground);
        void onLayerRemoved(int layerIdx);
        void onPlayStateChanged(boolean playing);
        void onOnionSkinLayerToggled(int layerIdx);
        void onExposureChanged(int frameIdx, int newExposure);
        void onBlendModeChanged(int layerIdx, AnimationProject.BlendMode mode);
    }

    private static final int HEADER_H_DP  = 36;
    private static final int LAYER_HDR_DP = 24;
    private static final int LAYER_W_DP   = 130; // lebih lebar untuk blend mode button
    private static final int FRAME_W_DP   = 52;
    private static final int ROW_H_DP     = 72;  // lebih tinggi untuk nama + type + blend btn
    private static final int BTN_SZ_DP    = 14;

    private final Paint pBg      =new Paint();  private final Paint pHeader  =new Paint();
    private final Paint pLayerHdr=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSubText =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameBg =new Paint();  private final Paint pFrameAct=new Paint();
    private final Paint pFrameHov=new Paint();
    private final Paint pThumb   =new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint pBtn     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnDel  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnAdd  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnBlend=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerBg =new Paint();  private final Paint pLayerSel=new Paint();
    private final Paint pSep     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumbBg =new Paint();
    private final Paint pLampOn  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLampOff =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pExpLine =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pExpDrag =new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private AnimationProject  project;
    private OnionSkinSettings onionSettings;
    private OnTimelineEvent   listener;

    // Playback
    private boolean isPlaying=false, isLooping=true;
    private int     playTick=0;
    private final Handler  playHandler  = new Handler(Looper.getMainLooper());
    private final Runnable playRunnable = this::tickPlayback;

    // Scroll
    private float scrollX=0f,scrollY=0f,lastTouchX=0f,lastTouchY=0f;
    private boolean scrolling=false;

    // Header buttons
    private final RectF btnPlay=new RectF(),btnStop=new RectF();
    private final RectF btnPrev=new RectF(),btnNext=new RectF(),btnLoop=new RectF();
    private final RectF btnAddLayer=new RectF();

    // Per-frame
    private final RectF frameAddBtn=new RectF(),frameDelBtn=new RectF();

    // Per-layer
    private static final int MAX_LAYERS=20;
    private final RectF[] layerDelBtns   = new RectF[MAX_LAYERS];
    private final RectF[] layerLampBtns  = new RectF[MAX_LAYERS];
    private final RectF[] layerBlendBtns = new RectF[MAX_LAYERS]; // blend mode button

    private int hoveredFrame=-1;

    // Add layer popup
    private boolean showAddLayerPopup=false;
    private final RectF popupRect     =new RectF();
    private final RectF popupNormalBtn=new RectF();
    private final RectF popupBgBtn    =new RectF();

    // Blend mode popup
    private boolean showBlendPopup   = false;
    private int     blendPopupLayerIdx= -1;
    private final RectF blendPopupRect=new RectF();
    private final RectF[] blendModeRects=new RectF[AnimationProject.BlendMode.values().length];

    // Exposure drag
    private boolean exposureDragActive=false;
    private int     exposureDragFrameIdx=-1;
    private float   exposureDragStartX=0f;
    private int     exposureDragOriginal=1;
    private static final long EXP_LP_MS=350L;
    private final Handler  expLpHandler =new Handler(Looper.getMainLooper());
    private final Runnable expLpRunnable=this::startExposureDrag;

    public TimelineView(Context c)               {super(c);init();}
    public TimelineView(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        for(int i=0;i<MAX_LAYERS;i++){
            layerDelBtns[i]  =new RectF();
            layerLampBtns[i] =new RectF();
            layerBlendBtns[i]=new RectF();
        }
        for(int i=0;i<blendModeRects.length;i++) blendModeRects[i]=new RectF();

        pBg.setColor(0xFF15151F);      pHeader.setColor(0xFF0E0E1A);
        pLayerHdr.setColor(0xFF111122); pLayerHdr.setStyle(Paint.Style.FILL);
        pBorder.setColor(0xFF3A3A5A);   pBorder.setStyle(Paint.Style.STROKE); pBorder.setStrokeWidth(1f);
        pText.setColor(0xFFCCCCDD);     pText.setTextSize(11*density); pText.setTypeface(Typeface.MONOSPACE);
        pSubText.setColor(0xFF777788);  pSubText.setTextSize(9*density); pSubText.setTypeface(Typeface.MONOSPACE);
        pFrameBg.setColor(0xFF1E1E2E);  pFrameAct.setColor(0xFF2A2A50); pFrameHov.setColor(0xFF22223A);
        pThumb.setAntiAlias(true); pThumb.setFilterBitmap(true);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);           pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);  pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(0xFFFFFFFF);     pBtnT.setTextSize(9*density); pBtnT.setTypeface(Typeface.MONOSPACE); pBtnT.setAntiAlias(true);
        pBtnDel.setColor(0xFF8B2020);   pBtnDel.setStyle(Paint.Style.FILL);
        pBtnAdd.setColor(0xFF1E4020);   pBtnAdd.setStyle(Paint.Style.FILL);
        pBtnBlend.setColor(0xFF2A2A5A); pBtnBlend.setStyle(Paint.Style.FILL);
        pLayerBg.setColor(0xFF181828);  pLayerSel.setColor(0xFF252545);
        pSep.setColor(0xFF252535);      pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pCursor.setColor(0xFFFF3333);   pCursor.setStyle(Paint.Style.STROKE); pCursor.setStrokeWidth(2f);
        pThumbBg.setColor(Color.WHITE);
        pLampOn.setColor(0xFFFFDD00);   pLampOn.setStyle(Paint.Style.FILL);
        pLampOff.setColor(0xFF444455);  pLampOff.setStyle(Paint.Style.FILL);
        pExpLine.setColor(0xFF5555AA);  pExpLine.setStyle(Paint.Style.STROKE); pExpLine.setStrokeWidth(2f);
        pExpDrag.setColor(0x554466FF);  pExpDrag.setStyle(Paint.Style.FILL);

        setClickable(true); setWillNotDraw(false);
    }

    public void setProject(AnimationProject p)          {project=p;invalidate();}
    public void setOnionSkinSettings(OnionSkinSettings s){onionSettings=s;}
    public void setOnTimelineEvent(OnTimelineEvent l)    {listener=l;}

    // ── Playback ──────────────────────────────────────────────────────────────
    private void tickPlayback(){
        if(!isPlaying||project==null) return;
        int totalTicks=project.getTotalTicks();
        playTick++;
        if(playTick>=totalTicks){if(isLooping)playTick=0;else{stopPlayback();return;}}
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l!=null){
            int t=0;
            for(int fi=0;fi<l.getFrameCount();fi++){
                AnimationProject.Frame f=l.getFrame(fi); if(f==null)continue;
                if(playTick>=t&&playTick<t+f.exposure){
                    if(fi!=project.getCurrentFrameIdx()){project.setCurrentFrame(fi);if(listener!=null)listener.onFrameChanged(fi);}
                    break;
                }
                t+=f.exposure;
            }
        }
        invalidate();
        playHandler.postDelayed(playRunnable,1000L/Math.max(1,project.getFps()));
    }
    public void startPlayback(){
        if(isPlaying)return;
        AnimationProject.Layer l=project.getCurrentLayer();
        playTick=l!=null?l.getTickStart(project.getCurrentFrameIdx()):0;
        isPlaying=true; if(listener!=null)listener.onPlayStateChanged(true);
        playHandler.post(playRunnable); invalidate();
    }
    public void stopPlayback(){
        isPlaying=false; playHandler.removeCallbacks(playRunnable);
        if(listener!=null)listener.onPlayStateChanged(false); invalidate();
    }
    public boolean isPlaying(){return isPlaying;}

    // ── onDraw ────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas){
        if(project==null)return;
        int w=getWidth(),h=getHeight();
        float hH    =HEADER_H_DP*density;
        float lHdrH =LAYER_HDR_DP*density;
        float lW    =LAYER_W_DP*density;
        float fW    =FRAME_W_DP*density;
        float rH    =ROW_H_DP*density;
        float pad   =4*density;
        float bSz   =BTN_SZ_DP*density;

        canvas.drawRect(0,0,w,h,pBg);
        canvas.drawRect(0,0,w,hH,pHeader);
        drawPlaybackHeader(canvas,w,hH);
        canvas.drawLine(0,hH,w,hH,pBorder);

        float lHdrTop=hH,lHdrBot=hH+lHdrH;
        canvas.drawRect(0,lHdrTop,lW,lHdrBot,pLayerHdr);
        canvas.drawLine(0,lHdrBot,w,lHdrBot,pBorder);
        canvas.drawLine(lW,lHdrTop,lW,h,pBorder);

        float ap=3*density;
        btnAddLayer.set(ap,lHdrTop+ap,lW-ap,lHdrBot-ap);
        canvas.drawRoundRect(btnAddLayer,3,3,pBtnAdd);
        Paint addBdr=new Paint(pBorder);addBdr.setColor(0xFF336633);
        canvas.drawRoundRect(btnAddLayer,3,3,addBdr);
        drawCentered(canvas,"+ Add Layer",btnAddLayer,pBtnT);

        float listTop=lHdrBot;
        canvas.save(); canvas.clipRect(0,listTop,w,h);

        int layerCount=project.getLayerCount();
        for(int li=0;li<layerCount;li++){
            float ly=listTop+li*rH-scrollY;
            if(ly+rH<listTop||ly>h)continue;
            AnimationProject.Layer layer=project.getLayer(li);
            boolean sel=(li==project.getCurrentLayerIdx());

            canvas.drawRect(0,ly,lW,ly+rH,sel?pLayerSel:pLayerBg);
            canvas.drawLine(0,ly+rH,lW,ly+rH,pSep);

            // ── Thumbnail kiri ────────────────────────────────────────────────
            float thumbSz=rH-pad*2;
            Bitmap thumb=layer.getLayerThumbnail(project.getCurrentFrameIdx(),(int)thumbSz,(int)thumbSz);
            if(thumb!=null){
                canvas.drawRect(pad,ly+pad,pad+thumbSz,ly+pad+thumbSz,pThumbBg);
                canvas.drawBitmap(thumb,pad,ly+pad,pThumb);
                canvas.drawRect(pad,ly+pad,pad+thumbSz,ly+pad+thumbSz,pBorder);
            }

            float infoX=pad+thumbSz+5*density;
            float infoW=lW-infoX-bSz-pad*2; // lebar area info

            // ── Nama layer (baris 1) ───────────────────────────────────────────
            Paint.FontMetrics fm=pText.getFontMetrics();
            float nameY=ly+rH*0.25f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(truncate(layer.name,9),infoX,nameY,pText);

            // ── Type label (baris 2) ──────────────────────────────────────────
            Paint tp=new Paint(pSubText);
            tp.setColor(layer.name.startsWith("BG")?0xFF66AAFF:0xFF888899);
            canvas.drawText(layer.name.startsWith("BG")?"background":"normal",infoX,ly+rH*0.48f,tp);

            // ── Blend mode button (baris 3 — di bawah nama & type) ────────────
            float blendBtnH=16*density;
            float blendBtnY=ly+rH*0.58f;
            float blendBtnW=infoW;
            if(li<MAX_LAYERS){
                layerBlendBtns[li].set(infoX, blendBtnY, infoX+blendBtnW, blendBtnY+blendBtnH);
                // Highlight jika blend popup terbuka untuk layer ini
                Paint bbg=new Paint(blendPopupLayerIdx==li?pBtnH:pBtnBlend);
                canvas.drawRoundRect(layerBlendBtns[li],2,2,bbg);
                Paint bbdr=new Paint(pBorder); bbdr.setColor(0xFF5555AA); bbdr.setStrokeWidth(1f);
                canvas.drawRoundRect(layerBlendBtns[li],2,2,bbdr);
                // Label mode aktif
                Paint bt=new Paint(pBtnT); bt.setTextSize(8*density);
                canvas.drawText(layer.blendMode.label,
                    layerBlendBtns[li].centerX()-bt.measureText(layer.blendMode.label)/2f,
                    layerBlendBtns[li].centerY()-(bt.getFontMetrics().ascent+bt.getFontMetrics().descent)/2f,
                    bt);
            }

            // ── Lampu onion skin ──────────────────────────────────────────────
            if(li<MAX_LAYERS){
                float lr=4*density;
                float lx=lW-bSz-pad*2-lr-2*density, lyc=ly+rH/2f;
                layerLampBtns[li].set(lx-lr-2*density,lyc-lr-2*density,lx+lr+2*density,lyc+lr+2*density);
                boolean on=(onionSettings==null||onionSettings.isLayerEnabled(li));
                canvas.drawCircle(lx,lyc,lr,on?pLampOn:pLampOff);
                Paint lb=new Paint(Paint.ANTI_ALIAS_FLAG);lb.setStyle(Paint.Style.STROKE);
                lb.setColor(on?0xFFFFAA00:0xFF333344);lb.setStrokeWidth(1.2f);
                canvas.drawCircle(lx,lyc,lr,lb);
            }

            // ── [x] delete ────────────────────────────────────────────────────
            if(li<MAX_LAYERS){
                layerDelBtns[li].set(lW-bSz-pad,ly+pad,lW-pad,ly+pad+bSz);
                canvas.drawRoundRect(layerDelBtns[li],2,2,pBtnDel);
                drawCentered(canvas,"x",layerDelBtns[li],pBtnT);
            }

            // ── Frame strip ───────────────────────────────────────────────────
            canvas.save(); canvas.clipRect(lW,listTop,w,h);
            float fx=lW-scrollX;
            for(int fi=0;fi<layer.getFrameCount();fi++){
                AnimationProject.Frame frame=layer.getFrame(fi);
                if(frame==null){fx+=fW;continue;}
                float cellW=frame.exposure*fW;
                if(fx+cellW<lW){fx+=cellW;continue;}
                if(fx>w)break;

                boolean act=(fi==project.getCurrentFrameIdx());
                boolean isDragFr=(exposureDragActive&&fi==exposureDragFrameIdx);
                RectF cellRect=new RectF(fx,ly,fx+cellW,ly+rH);
                if(isDragFr) canvas.drawRect(cellRect,pExpDrag);
                else         canvas.drawRect(cellRect,act&&sel?pFrameAct:pFrameBg);

                float tw=cellW-pad*2,th=rH-pad*2-bSz-2*density;
                if(tw>0&&th>0){
                    canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pThumbBg);
                    if(!frame.isEmpty&&frame.bitmap!=null&&!frame.bitmap.isRecycled())
                        canvas.drawBitmap(frame.bitmap,null,new RectF(fx+pad,ly+pad,fx+pad+tw,ly+pad+th),pThumb);
                    canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pBorder);
                }
                String lbl=String.valueOf(fi+1)+(frame.exposure>1?" x"+frame.exposure:"");
                canvas.drawText(lbl,fx+3*density,ly+rH-3*density,pSubText);

                Paint sp=new Paint(pExpLine);sp.setAlpha(frame.exposure>1?255:80);
                canvas.drawLine(fx+cellW,ly,fx+cellW,ly+rH,sp);

                if(act&&sel){
                    frameAddBtn.set(fx+cellW-bSz-1,ly+rH-bSz-1,fx+cellW-1,ly+rH-1);
                    canvas.drawRoundRect(frameAddBtn,2,2,exposureDragActive?pBtnH:pBtn);
                    drawCentered(canvas,exposureDragActive?"↔":"+",frameAddBtn,pBtnT);
                    frameDelBtn.set(fx+1,ly+rH-bSz-1,fx+bSz+1,ly+rH-1);
                    canvas.drawRoundRect(frameDelBtn,2,2,pBtnDel);
                    drawCentered(canvas,"-",frameDelBtn,pBtnT);
                    if(frame.exposure>1){
                        String es="×"+frame.exposure; float ew=pSubText.measureText(es);
                        canvas.drawText(es,fx+cellW-ew-3*density,ly+rH-bSz-3*density,pSubText);
                    }
                }
                fx+=cellW;
            }
            canvas.restore();
        }

        // Cursor
        AnimationProject.Layer curLayer=project.getCurrentLayer();
        float ct=curLayer!=null?curLayer.getTickStart(project.getCurrentFrameIdx()):project.getCurrentFrameIdx();
        float curX=lW+ct*fW+fW/2f-scrollX;
        if(curX>=lW&&curX<=w) canvas.drawLine(curX,listTop,curX,h,pCursor);

        canvas.restore();
        canvas.drawLine(0,0,w,0,pBorder);

        // Popups (digambar terakhir agar di atas semua)
        if(showAddLayerPopup) drawAddLayerPopup(canvas,lW,lHdrBot);
        if(showBlendPopup)    drawBlendModePopup(canvas,lW,listTop);
    }

    // ── Blend mode popup ──────────────────────────────────────────────────────
    private void drawBlendModePopup(Canvas canvas,float lW,float listTop){
        AnimationProject.BlendMode[] modes=AnimationProject.BlendMode.values();
        float pad=5*density, btnH=20*density, popW=lW*1.1f;
        float popH=pad+(btnH+pad*0.4f)*modes.length+pad;
        float popX=0, popY=listTop+blendPopupLayerIdx*(ROW_H_DP*density)-scrollY
                   +LAYER_HDR_DP*density;
        // Clamp Y
        if(popY+popH>getHeight()) popY=getHeight()-popH-4*density;
        if(popY<listTop)          popY=listTop;
        blendPopupRect.set(popX,popY,popX+popW,popY+popH);

        // Shadow
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);sh.setColor(0x77000000);sh.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(popX+3,popY+3,popX+popW+3,popY+popH+3),5,5,sh);

        // Background
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setColor(0xFF1A1A2E);bg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(blendPopupRect,5,5,bg);
        Paint bd=new Paint(pBorder);bd.setColor(0xFF5555BB);
        canvas.drawRoundRect(blendPopupRect,5,5,bd);

        float by=popY+pad;
        AnimationProject.Layer selLayer=project.getLayer(blendPopupLayerIdx);
        for(int i=0;i<modes.length;i++){
            AnimationProject.BlendMode m=modes[i];
            blendModeRects[i].set(popX+pad*0.5f,by,popX+popW-pad*0.5f,by+btnH);

            boolean isActive=(selLayer!=null&&selLayer.blendMode==m);
            Paint mbg=new Paint(Paint.ANTI_ALIAS_FLAG);
            mbg.setColor(isActive?0xFF3A3A7A:0xFF222235);mbg.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(blendModeRects[i],2,2,mbg);
            if(isActive){Paint mb=new Paint(pBorder);mb.setColor(0xFF7777FF);canvas.drawRoundRect(blendModeRects[i],2,2,mb);}

            Paint mt=new Paint(pBtnT);mt.setTextSize(9*density);
            if(isActive) mt.setColor(0xFFFFFFFF); else mt.setColor(0xFFCCCCDD);
            // Checkmark jika aktif
            String label=(isActive?"\u2713 ":"")+m.label;
            canvas.drawText(label,
                blendModeRects[i].left+5*density,
                blendModeRects[i].centerY()-(mt.getFontMetrics().ascent+mt.getFontMetrics().descent)/2f,
                mt);
            by+=btnH+pad*0.4f;
        }
    }

    private void drawAddLayerPopup(Canvas canvas,float lW,float anchorY){
        float pad=8*density,popW=lW-pad*2,btnH=28*density;
        float popH=pad+btnH+pad*0.5f+btnH+pad;
        float popX=pad,popY=anchorY;
        if(popY+popH>getHeight()) popY=getHeight()-popH-4*density;
        popupRect.set(popX,popY,popX+popW,popY+popH);
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);sh.setColor(0x66000000);sh.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(popX+3,popY+3,popX+popW+3,popY+popH+3),5,5,sh);
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setColor(0xFF1E1E2E);bg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(popupRect,5,5,bg);
        Paint bd=new Paint(pBorder);bd.setColor(0xFF5555AA);canvas.drawRoundRect(popupRect,5,5,bd);
        float bx=popX+pad*0.5f,bw=popW-pad;
        popupNormalBtn.set(bx,popY+pad,bx+bw,popY+pad+btnH);
        canvas.drawRoundRect(popupNormalBtn,3,3,pBtn); drawCentered(canvas,"Normal Layer",popupNormalBtn,pBtnT);
        float by2=popY+pad+btnH+pad*0.5f;
        popupBgBtn.set(bx,by2,bx+bw,by2+btnH);
        Paint bgb=new Paint(Paint.ANTI_ALIAS_FLAG);bgb.setColor(0xFF2A3A5A);bgb.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(popupBgBtn,3,3,bgb);
        Paint bgbd=new Paint(pBorder);bgbd.setColor(0xFF4466AA);canvas.drawRoundRect(popupBgBtn,3,3,bgbd);
        drawCentered(canvas,"Background Layer",popupBgBtn,pBtnT);
    }

    private void drawPlaybackHeader(Canvas canvas,int w,float hH){
        float pad=5*density,bH=hH-pad*2,bW=bH*1.5f;float x=pad;
        btnPrev.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnPrev,"|<");
        btnPlay.set(x,pad,x+bW,pad+bH);x+=bW+pad;drawBtn(canvas,btnPlay,isPlaying?"||":"▶");
        btnStop.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnStop,"■");
        btnNext.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnNext,">|");
        btnLoop.set(x,pad,x+bH*1.2f,pad+bH);x+=bH*1.2f+pad;
        Paint lp=new Paint(isLooping?pBtnH:pBtn);lp.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnLoop,3,3,lp);drawCentered(canvas,"↺",btnLoop,pBtnT);
        if(project!=null){
            canvas.drawText(project.getFps()+" fps",x+pad,hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pSubText);
            String fc=(project.getCurrentFrameIdx()+1)+"/"+project.getFrameCount();
            canvas.drawText(fc,w-pText.measureText(fc)-pad*2,hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pText);
        }
    }

    private void drawBtn(Canvas c,RectF r,String icon){
        c.drawRoundRect(r,3,3,pBtn);Paint bb=new Paint(pBorder);bb.setColor(0xFF5555AA);
        c.drawRoundRect(r,3,3,bb);drawCentered(c,icon,r,pBtnT);
    }
    private void drawCentered(Canvas c,String t,RectF r,Paint p){
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(t,r.centerX()-p.measureText(t)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);
    }
    private String truncate(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}

    private void startExposureDrag(){
        if(project==null||exposureDragFrameIdx<0)return;
        exposureDragActive=true;
        exposureDragOriginal=project.getCurrentExposure();
        invalidate();
    }
    private void updateExposureDrag(float cx){
        if(!exposureDragActive||project==null)return;
        float fW=FRAME_W_DP*density;
        int add=(int)((cx-exposureDragStartX)/fW);
        int nExp=Math.max(1,exposureDragOriginal+add);
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l!=null){AnimationProject.Frame f=l.getFrame(exposureDragFrameIdx);
            if(f!=null&&f.exposure!=nExp){f.exposure=Math.min(nExp,99);if(listener!=null)listener.onExposureChanged(exposureDragFrameIdx,f.exposure);invalidate();}}
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        float hH=HEADER_H_DP*density,lHdrH=LAYER_HDR_DP*density;
        float lW=LAYER_W_DP*density,fW=FRAME_W_DP*density,rH=ROW_H_DP*density;
        float lHdrBot=hH+lHdrH;

        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastTouchX=tx;lastTouchY=ty;scrolling=false;
                hoveredFrame=hitFrame(tx,ty,lW,lHdrBot,fW);
                if(frameAddBtn.contains(tx,ty)&&project!=null){
                    exposureDragFrameIdx=project.getCurrentFrameIdx();
                    exposureDragStartX=tx;
                    expLpHandler.postDelayed(expLpRunnable,EXP_LP_MS);
                } else {expLpHandler.removeCallbacks(expLpRunnable);exposureDragFrameIdx=-1;}
                invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                float dx=tx-lastTouchX,dy=ty-lastTouchY;
                if(exposureDragActive){updateExposureDrag(tx);return true;}
                if(exposureDragFrameIdx>=0&&Math.abs(dy)>8*density){expLpHandler.removeCallbacks(expLpRunnable);exposureDragFrameIdx=-1;}
                if(!scrolling&&(Math.abs(dx)>6*density||Math.abs(dy)>6*density))scrolling=true;
                if(scrolling&&ty>lHdrBot){if(tx>lW)scrollX=Math.max(0,scrollX-dx);scrollY=Math.max(0,scrollY-dy);lastTouchX=tx;lastTouchY=ty;}
                hoveredFrame=hitFrame(tx,ty,lW,lHdrBot,fW);invalidate();return true;
            case MotionEvent.ACTION_UP:
                expLpHandler.removeCallbacks(expLpRunnable);
                boolean wd=exposureDragActive;exposureDragActive=false;exposureDragFrameIdx=-1;
                if(!scrolling&&!wd) handleTap(tx,ty,lW,hH,lHdrBot,fW,rH);
                scrolling=false;hoveredFrame=-1;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:
                expLpHandler.removeCallbacks(expLpRunnable);
                exposureDragActive=false;exposureDragFrameIdx=-1;
                scrolling=false;hoveredFrame=-1;invalidate();return true;
        }
        return true;
    }

    private void handleTap(float tx,float ty,float lW,float hH,float lHdrBot,float fW,float rH){
        if(project==null||listener==null)return;

        // Blend popup
        if(showBlendPopup){
            if(blendPopupRect.contains(tx,ty)){
                AnimationProject.BlendMode[] modes=AnimationProject.BlendMode.values();
                for(int i=0;i<modes.length;i++){
                    if(i<blendModeRects.length&&blendModeRects[i].contains(tx,ty)){
                        AnimationProject.Layer l=project.getLayer(blendPopupLayerIdx);
                        if(l!=null){l.blendMode=modes[i];listener.onBlendModeChanged(blendPopupLayerIdx,modes[i]);}
                        showBlendPopup=false;blendPopupLayerIdx=-1;invalidate();return;
                    }
                }
            }
            showBlendPopup=false;blendPopupLayerIdx=-1;invalidate();return;
        }

        // Add layer popup
        if(showAddLayerPopup){
            if(popupNormalBtn.contains(tx,ty)){showAddLayerPopup=false;listener.onLayerAdded(false);invalidate();return;}
            if(popupBgBtn.contains(tx,ty))    {showAddLayerPopup=false;listener.onLayerAdded(true);invalidate();return;}
            if(!popupRect.contains(tx,ty))    {showAddLayerPopup=false;invalidate();return;}
            return;
        }

        // Header
        if(ty<=hH){
            if(btnPlay.contains(tx,ty)){if(isPlaying)stopPlayback();else startPlayback();return;}
            if(btnStop.contains(tx,ty)){stopPlayback();project.setCurrentFrame(0);listener.onFrameChanged(0);return;}
            if(btnPrev.contains(tx,ty)){stopPlayback();project.prevFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnNext.contains(tx,ty)){stopPlayback();project.nextFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnLoop.contains(tx,ty)){isLooping=!isLooping;invalidate();return;}
            return;
        }

        // Layer header Add
        if(ty>hH&&ty<=lHdrBot&&tx<=lW){
            if(btnAddLayer.contains(tx,ty)){showAddLayerPopup=true;invalidate();return;}
            return;
        }

        // Layer panel
        if(tx<=lW){
            int li=hitLayer(ty,lHdrBot,rH);
            if(li>=0){
                if(li<MAX_LAYERS&&layerLampBtns[li].contains(tx,ty)){listener.onOnionSkinLayerToggled(li);return;}
                if(li<MAX_LAYERS&&layerDelBtns[li].contains(tx,ty)) {project.removeLayer(li);listener.onLayerRemoved(li);return;}
                if(li<MAX_LAYERS&&layerBlendBtns[li].contains(tx,ty)){
                    // Buka blend mode popup untuk layer ini
                    showBlendPopup=true; blendPopupLayerIdx=li;
                    showAddLayerPopup=false; invalidate(); return;
                }
                project.setCurrentLayer(li);listener.onLayerChanged(li);
            }
            return;
        }

        // Frame area
        if(frameAddBtn.contains(tx,ty)){project.insertFrameAfterCurrent();listener.onFrameAdded();return;}
        if(frameDelBtn.contains(tx,ty)){project.removeCurrentFrame();listener.onFrameRemoved();return;}
        int fi=hitFrame(tx,ty,lW,lHdrBot,fW);
        if(fi>=0){project.setCurrentFrame(fi);listener.onFrameChanged(fi);}
    }

    private int hitFrame(float x,float y,float lW,float listTop,float fW){
        if(project==null||x<=lW||y<=listTop)return -1;
        float fx=lW-scrollX;
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l==null)return -1;
        for(int fi=0;fi<l.getFrameCount();fi++){
            AnimationProject.Frame f=l.getFrame(fi);if(f==null){fx+=fW;continue;}
            float cW=f.exposure*fW;
            if(x>=fx&&x<fx+cW)return fi;
            fx+=cW;
        }
        return -1;
    }
    private int hitLayer(float y,float listTop,float rH){
        if(project==null||y<=listTop)return -1;
        int li=(int)((y-listTop+scrollY)/rH);
        return(li>=0&&li<project.getLayerCount())?li:-1;
    }
}
