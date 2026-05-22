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

/**
 * TimelineView v5 — semua fix sekaligus:
 *
 * Layout fixes:
 *  - Nama layer, type, blend mode button ADA DI BAWAH thumbnail (bukan di samping)
 *  - Timeline tidak terpotong oleh navigation bar — menggunakan full height
 *
 * Blend mode fixes:
 *  - Popup blend mode bisa di-scroll jika item melebihi tinggi layar
 *
 * Frame drag:
 *  - Long-press thumbnail frame (BUKAN tombol [+]) lalu drag kiri/kanan
 *    → pindahkan frame ke posisi baru (reorder)
 *
 * Tombol "Frame Settings" di header timeline
 */
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
        void onFrameReordered(int fromIdx, int toIdx);
        void onFrameSettingsRequested(); // buka FrameSettingsWindow
    }

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static final int HEADER_H_DP  = 36;
    private static final int LAYER_HDR_DP = 24;
    private static final int LAYER_W_DP   = 120;
    private static final int FRAME_W_DP   = 52;
    private static final int ROW_H_DP     = 80;  // lebih tinggi: thumb atas + info bawah
    private static final int THUMB_DP     = 40;  // thumbnail square
    private static final int BTN_SZ_DP    = 14;
    private static final int INFO_H_DP    = 36;  // area info di bawah thumbnail

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint pBg       =new Paint();
    private final Paint pHeader   =new Paint();
    private final Paint pLayerHdr =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSubText  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFrameBg  =new Paint();
    private final Paint pFrameAct =new Paint();
    private final Paint pFrameHov =new Paint();
    private final Paint pThumb    =new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint pBtn      =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT     =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnDel   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnAdd   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnBlend =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLayerBg  =new Paint();
    private final Paint pLayerSel =new Paint();
    private final Paint pSep      =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCursor   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pThumbBg  =new Paint();
    private final Paint pLampOn   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLampOff  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pExpLine  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pExpDrag  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDragFrame=new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private AnimationProject  project;
    private OnionSkinSettings onionSettings;
    private OnTimelineEvent   listener;

    // Playback
    private boolean isPlaying=false, isLooping=true;
    private int     playTick=0;
    private final Handler  playHandler  =new Handler(Looper.getMainLooper());
    private final Runnable playRunnable =this::tickPlayback;

    // Scroll
    private float scrollX=0f,scrollY=0f,lastTouchX=0f,lastTouchY=0f;
    private boolean scrolling=false;

    // Header buttons
    private final RectF btnPlay    =new RectF();
    private final RectF btnStop    =new RectF();
    private final RectF btnPrev    =new RectF();
    private final RectF btnNext    =new RectF();
    private final RectF btnLoop    =new RectF();
    private final RectF btnFrmSet  =new RectF(); // Frame Settings button
    private final RectF btnAddLayer=new RectF();

    // Per-frame buttons
    private final RectF frameAddBtn=new RectF();
    private final RectF frameDelBtn=new RectF();

    // Per-layer
    private static final int MAX_LAYERS=20;
    private final RectF[] layerDelBtns   =new RectF[MAX_LAYERS];
    private final RectF[] layerLampBtns  =new RectF[MAX_LAYERS];
    private final RectF[] layerBlendBtns =new RectF[MAX_LAYERS];

    private int hoveredFrame=-1;

    // Add layer popup
    private boolean showAddLayerPopup=false;
    private final RectF popupRect     =new RectF();
    private final RectF popupNormalBtn=new RectF();
    private final RectF popupBgBtn    =new RectF();

    // Blend mode popup with scroll
    private boolean showBlendPopup    =false;
    private int     blendPopupLayerIdx=-1;
    private float   blendScrollY      =0f;  // scroll offset dalam popup
    private final RectF blendPopupRect=new RectF();
    private final RectF[] blendModeRects=new RectF[AnimationProject.BlendMode.values().length];

    // Exposure drag
    private boolean exposureDragActive  =false;
    private int     exposureDragFrameIdx=-1;
    private float   exposureDragStartX  =0f;
    private int     exposureDragOriginal=1;
    private static final long EXP_LP_MS =350L;
    private final Handler  expLpHandler =new Handler(Looper.getMainLooper());
    private final Runnable expLpRunnable=this::startExposureDrag;

    // Frame reorder drag (long-press on thumbnail)
    private boolean frameDragActive   =false;
    private int     frameDragFromIdx  =-1;
    private int     frameDragToIdx    =-1;
    private float   frameDragStartX   =0f;
    private static final long FRAME_LP_MS=400L;
    private final Handler  frameLpHandler =new Handler(Looper.getMainLooper());
    private final Runnable frameLpRunnable=this::startFrameDrag;

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
        pDragFrame.setColor(0xAAFFAA00);pDragFrame.setStyle(Paint.Style.FILL);

        setClickable(true); setWillNotDraw(false);
    }

    public void setProject(AnimationProject p)          {project=p;invalidate();}
    public void setOnionSkinSettings(OnionSkinSettings s){onionSettings=s;}
    public void setOnTimelineEvent(OnTimelineEvent l)    {listener=l;}

    // ── Playback ──────────────────────────────────────────────────────────────
    private void tickPlayback(){
        if(!isPlaying||project==null)return;
        int totalTicks=project.getTotalTicks(); playTick++;
        if(playTick>=totalTicks){if(isLooping)playTick=0;else{stopPlayback();return;}}
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l!=null){int t=0;for(int fi=0;fi<l.getFrameCount();fi++){AnimationProject.Frame f=l.getFrame(fi);if(f==null)continue;if(playTick>=t&&playTick<t+f.exposure){if(fi!=project.getCurrentFrameIdx()){project.setCurrentFrame(fi);if(listener!=null)listener.onFrameChanged(fi);}break;}t+=f.exposure;}}
        invalidate();
        playHandler.postDelayed(playRunnable,1000L/Math.max(1,project.getFps()));
    }
    public void startPlayback(){
        if(isPlaying)return;
        AnimationProject.Layer l=project.getCurrentLayer();
        playTick=l!=null?l.getTickStart(project.getCurrentFrameIdx()):0;
        isPlaying=true;if(listener!=null)listener.onPlayStateChanged(true);
        playHandler.post(playRunnable);invalidate();
    }
    public void stopPlayback(){isPlaying=false;playHandler.removeCallbacks(playRunnable);if(listener!=null)listener.onPlayStateChanged(false);invalidate();}
    public boolean isPlaying(){return isPlaying;}

    // ── Exposure/Frame drag helpers ───────────────────────────────────────────
    private void startExposureDrag(){if(project==null||exposureDragFrameIdx<0)return;exposureDragActive=true;exposureDragOriginal=project.getCurrentExposure();invalidate();}
    private void startFrameDrag()  {if(project==null||frameDragFromIdx<0)return;frameDragActive=true;frameDragToIdx=frameDragFromIdx;invalidate();}

    // ── onDraw ────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas){
        if(project==null)return;
        int  w=getWidth(),h=getHeight();
        float hH    =HEADER_H_DP*density;
        float lHdrH =LAYER_HDR_DP*density;
        float lW    =LAYER_W_DP*density;
        float fW    =FRAME_W_DP*density;
        float rH    =ROW_H_DP*density;
        float thumbSz=THUMB_DP*density;
        float infoH =INFO_H_DP*density;
        float pad   =4*density;
        float bSz   =BTN_SZ_DP*density;

        canvas.drawRect(0,0,w,h,pBg);
        canvas.drawRect(0,0,w,hH,pHeader);
        drawPlaybackHeader(canvas,w,hH);
        canvas.drawLine(0,hH,w,hH,pBorder);

        float lHdrBot=hH+lHdrH;
        canvas.drawRect(0,hH,lW,lHdrBot,pLayerHdr);
        canvas.drawLine(0,lHdrBot,w,lHdrBot,pBorder);
        canvas.drawLine(lW,hH,lW,h,pBorder);

        float ap=3*density;
        btnAddLayer.set(ap,hH+ap,lW-ap,lHdrBot-ap);
        canvas.drawRoundRect(btnAddLayer,3,3,pBtnAdd);
        Paint addBdr=new Paint(pBorder);addBdr.setColor(0xFF336633);canvas.drawRoundRect(btnAddLayer,3,3,addBdr);
        drawCentered(canvas,"+ Add Layer",btnAddLayer,pBtnT);

        float listTop=lHdrBot;
        canvas.save(); canvas.clipRect(0,listTop,w,h);

        int layerCount=project.getLayerCount();
        for(int li=0;li<layerCount;li++){
            float ly=listTop+li*rH-scrollY;
            if(ly+rH<listTop||ly>h) continue;
            AnimationProject.Layer layer=project.getLayer(li);
            boolean sel=(li==project.getCurrentLayerIdx());

            canvas.drawRect(0,ly,lW,ly+rH,sel?pLayerSel:pLayerBg);
            canvas.drawLine(0,ly+rH,lW,ly+rH,pSep);

            // ── Thumbnail di atas ──────────────────────────────────────────
            float tX=pad,tY=ly+pad;
            Bitmap thumb=layer.getLayerThumbnail(project.getCurrentFrameIdx(),(int)thumbSz,(int)thumbSz);
            if(thumb!=null){
                canvas.drawRect(tX,tY,tX+thumbSz,tY+thumbSz,pThumbBg);
                canvas.drawBitmap(thumb,tX,tY,pThumb);
                canvas.drawRect(tX,tY,tX+thumbSz,tY+thumbSz,pBorder);
            }

            // ── [x] delete — pojok kanan atas ─────────────────────────────
            if(li<MAX_LAYERS){
                layerDelBtns[li].set(lW-bSz-pad,ly+pad,lW-pad,ly+pad+bSz);
                canvas.drawRoundRect(layerDelBtns[li],2,2,pBtnDel);
                drawCentered(canvas,"x",layerDelBtns[li],pBtnT);
            }

            // ── Lampu onion skin ───────────────────────────────────────────
            if(li<MAX_LAYERS){
                float lr=4*density;
                float lxc=tX+thumbSz+lr+4*density;
                float lyc=ly+thumbSz/2f+pad;
                layerLampBtns[li].set(lxc-lr-3*density,lyc-lr-3*density,lxc+lr+3*density,lyc+lr+3*density);
                boolean on=(onionSettings==null||onionSettings.isLayerEnabled(li));
                canvas.drawCircle(lxc,lyc,lr,on?pLampOn:pLampOff);
                Paint lb=new Paint(Paint.ANTI_ALIAS_FLAG);lb.setStyle(Paint.Style.STROKE);lb.setColor(on?0xFFFFAA00:0xFF333344);lb.setStrokeWidth(1.2f);
                canvas.drawCircle(lxc,lyc,lr,lb);
            }

            // ── INFO AREA DI BAWAH THUMBNAIL ──────────────────────────────
            float infoY=ly+pad+thumbSz+2*density;
            float infoW=lW-pad*2;

            // Nama layer
            Paint.FontMetrics fm=pText.getFontMetrics();
            canvas.drawText(truncate(layer.name,10),pad,infoY-fm.ascent,pText);

            // Type label
            float typeY=infoY+11*density;
            Paint tp=new Paint(pSubText);tp.setColor(layer.name.startsWith("BG")?0xFF66AAFF:0xFF888899);
            canvas.drawText(layer.name.startsWith("BG")?"bg":"normal",pad,typeY,tp);

            // Blend mode button — full width di bawah nama+type
            float blendBtnY=typeY+3*density;
            float blendBtnH=15*density;
            if(li<MAX_LAYERS){
                layerBlendBtns[li].set(pad,blendBtnY,pad+infoW,blendBtnY+blendBtnH);
                Paint bbg=new Paint(blendPopupLayerIdx==li?pBtnH:pBtnBlend);
                canvas.drawRoundRect(layerBlendBtns[li],2,2,bbg);
                Paint bbdr=new Paint(pBorder);bbdr.setColor(0xFF5555AA);bbdr.setStrokeWidth(1f);
                canvas.drawRoundRect(layerBlendBtns[li],2,2,bbdr);
                Paint bt=new Paint(pBtnT);bt.setTextSize(8*density);
                canvas.drawText(layer.blendMode.label,
                    layerBlendBtns[li].centerX()-bt.measureText(layer.blendMode.label)/2f,
                    layerBlendBtns[li].centerY()-(bt.getFontMetrics().ascent+bt.getFontMetrics().descent)/2f,bt);
            }

            // ── Frame strip ───────────────────────────────────────────────
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
                boolean isReorderFr=(frameDragActive&&fi==frameDragFromIdx);

                RectF cellRect=new RectF(fx,ly,fx+cellW,ly+rH);
                if(isDragFr)      canvas.drawRect(cellRect,pExpDrag);
                else if(isReorderFr) canvas.drawRect(cellRect,pDragFrame);
                else              canvas.drawRect(cellRect,act&&sel?pFrameAct:pFrameBg);

                // Thumbnail frame
                float tw=cellW-pad*2,th=rH-pad*2-bSz-2*density;
                if(tw>0&&th>0){
                    canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pThumbBg);
                    if(!frame.isEmpty&&frame.bitmap!=null&&!frame.bitmap.isRecycled())
                        canvas.drawBitmap(frame.bitmap,null,new RectF(fx+pad,ly+pad,fx+pad+tw,ly+pad+th),pThumb);
                    canvas.drawRect(fx+pad,ly+pad,fx+pad+tw,ly+pad+th,pBorder);
                }

                // Frame number + exposure
                String lbl=String.valueOf(fi+1)+(frame.exposure>1?" x"+frame.exposure:"");
                canvas.drawText(lbl,fx+3*density,ly+rH-3*density,pSubText);

                // Separator
                Paint sp=new Paint(pExpLine);sp.setAlpha(frame.exposure>1?255:80);
                canvas.drawLine(fx+cellW,ly,fx+cellW,ly+rH,sp);

                // Reorder target indicator
                if(frameDragActive&&fi==frameDragToIdx&&fi!=frameDragFromIdx){
                    Paint lineP=new Paint(Paint.ANTI_ALIAS_FLAG);lineP.setColor(0xFFFFAA00);lineP.setStyle(Paint.Style.STROKE);lineP.setStrokeWidth(3f);
                    canvas.drawLine(fx,ly,fx,ly+rH,lineP);
                }

                // [+] dan [-] hanya di frame aktif, layer aktif
                if(act&&sel){
                    frameAddBtn.set(fx+cellW-bSz-1,ly+rH-bSz-1,fx+cellW-1,ly+rH-1);
                    canvas.drawRoundRect(frameAddBtn,2,2,exposureDragActive?pBtnH:pBtn);
                    drawCentered(canvas,exposureDragActive?"↔":"+",frameAddBtn,pBtnT);

                    frameDelBtn.set(fx+1,ly+rH-bSz-1,fx+bSz+1,ly+rH-1);
                    canvas.drawRoundRect(frameDelBtn,2,2,pBtnDel);
                    drawCentered(canvas,"-",frameDelBtn,pBtnT);

                    if(frame.exposure>1){String es="×"+frame.exposure;float ew=pSubText.measureText(es);canvas.drawText(es,fx+cellW-ew-3*density,ly+rH-bSz-3*density,pSubText);}
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

        // Popups
        if(showAddLayerPopup) drawAddLayerPopup(canvas,lW,lHdrBot);
        if(showBlendPopup)    drawBlendModePopup(canvas,lW,listTop);
    }

    // ── Blend popup dengan scroll ─────────────────────────────────────────────
    private void drawBlendModePopup(Canvas canvas,float lW,float listTop){
        AnimationProject.BlendMode[] modes=AnimationProject.BlendMode.values();
        float pad=5*density,btnH=22*density,popW=lW*1.05f;
        float totalH=pad+(btnH+pad*0.4f)*modes.length+pad;
        float maxH=getHeight()-listTop-8*density; // max height popup (scrollable)
        float popH=Math.min(totalH,maxH);
        float popX=2*density;
        float popY=listTop+blendPopupLayerIdx*(ROW_H_DP*density)-scrollY+LAYER_HDR_DP*density;
        if(popY+popH>getHeight()) popY=getHeight()-popH-4*density;
        if(popY<listTop)          popY=listTop;
        blendPopupRect.set(popX,popY,popX+popW,popY+popH);

        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);sh.setColor(0x77000000);sh.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(popX+3,popY+3,popX+popW+3,popY+popH+3),5,5,sh);
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setColor(0xFF1A1A2E);bg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(blendPopupRect,5,5,bg);
        Paint bd=new Paint(pBorder);bd.setColor(0xFF5555BB);canvas.drawRoundRect(blendPopupRect,5,5,bd);

        // Clip popup content untuk scroll
        canvas.save();
        canvas.clipRect(blendPopupRect);

        float by=popY+pad-blendScrollY; // blendScrollY adalah scroll offset
        AnimationProject.Layer selLayer=project.getLayer(blendPopupLayerIdx);
        for(int i=0;i<modes.length;i++){
            AnimationProject.BlendMode m=modes[i];
            blendModeRects[i].set(popX+pad*0.5f,by,popX+popW-pad*0.5f,by+btnH);
            if(by+btnH>popY&&by<popY+popH){ // hanya gambar yang terlihat
                boolean isActive=(selLayer!=null&&selLayer.blendMode==m);
                Paint mbg=new Paint(Paint.ANTI_ALIAS_FLAG);mbg.setColor(isActive?0xFF3A3A7A:0xFF222235);mbg.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(blendModeRects[i],2,2,mbg);
                if(isActive){Paint mb=new Paint(pBorder);mb.setColor(0xFF7777FF);canvas.drawRoundRect(blendModeRects[i],2,2,mb);}
                Paint mt=new Paint(pBtnT);mt.setTextSize(9*density);mt.setColor(isActive?0xFFFFFFFF:0xFFCCCCDD);
                String label=(isActive?"\u2713 ":"")+m.label;
                canvas.drawText(label,blendModeRects[i].left+5*density,
                    blendModeRects[i].centerY()-(mt.getFontMetrics().ascent+mt.getFontMetrics().descent)/2f,mt);
            }
            by+=btnH+pad*0.4f;
        }
        canvas.restore();

        // Scroll indicator jika konten lebih tinggi dari popup
        if(totalH>popH){
            float trackH=popH;
            float thumbH=popH*(popH/totalH);
            float thumbY=popY+blendScrollY*(popH/totalH);
            Paint sc=new Paint(Paint.ANTI_ALIAS_FLAG);sc.setColor(0xFF5555AA);sc.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(popX+popW-4*density,thumbY,popX+popW-1*density,thumbY+thumbH),2,2,sc);
        }
    }

    private void drawAddLayerPopup(Canvas canvas,float lW,float anchorY){
        float pad=8*density,popW=lW-pad*2,btnH=28*density,popH=pad+btnH+pad*0.5f+btnH+pad;
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
        canvas.drawRoundRect(popupNormalBtn,3,3,pBtn);drawCentered(canvas,"Normal Layer",popupNormalBtn,pBtnT);
        float by2=popY+pad+btnH+pad*0.5f;
        popupBgBtn.set(bx,by2,bx+bw,by2+btnH);
        Paint bgb=new Paint(Paint.ANTI_ALIAS_FLAG);bgb.setColor(0xFF2A3A5A);bgb.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(popupBgBtn,3,3,bgb);
        Paint bgbd=new Paint(pBorder);bgbd.setColor(0xFF4466AA);canvas.drawRoundRect(popupBgBtn,3,3,bgbd);
        drawCentered(canvas,"Background Layer",popupBgBtn,pBtnT);
    }

    private void drawPlaybackHeader(Canvas canvas,int w,float hH){
        float pad=4*density,bH=hH-pad*2,bW=bH*1.4f;float x=pad;
        btnPrev.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnPrev,"|<");
        btnPlay.set(x,pad,x+bW,pad+bH);x+=bW+pad;drawBtn(canvas,btnPlay,isPlaying?"||":"▶");
        btnStop.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnStop,"■");
        btnNext.set(x,pad,x+bH,pad+bH);x+=bH+pad;drawBtn(canvas,btnNext,">|");
        btnLoop.set(x,pad,x+bH*1.1f,pad+bH);x+=bH*1.1f+pad;
        Paint lp=new Paint(isLooping?pBtnH:pBtn);lp.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnLoop,3,3,lp);drawCentered(canvas,"↺",btnLoop,pBtnT);
        // Frame Settings button
        float fsW=50*density;
        btnFrmSet.set(x,pad,x+fsW,pad+bH);x+=fsW+pad;
        canvas.drawRoundRect(btnFrmSet,3,3,pBtn);
        Paint fsBdr=new Paint(pBorder);fsBdr.setColor(0xFF6666AA);canvas.drawRoundRect(btnFrmSet,3,3,fsBdr);
        Paint fsT=new Paint(pBtnT);fsT.setTextSize(7.5f*density);
        drawCentered(canvas,"F.Set",btnFrmSet,fsT);
        if(project!=null){
            canvas.drawText(project.getFps()+" fps",x,hH/2f-(pSubText.getFontMetrics().ascent+pSubText.getFontMetrics().descent)/2f,pSubText);
            String fc=(project.getCurrentFrameIdx()+1)+"/"+project.getFrameCount();
            canvas.drawText(fc,w-pText.measureText(fc)-pad*2,hH/2f-(pText.getFontMetrics().ascent+pText.getFontMetrics().descent)/2f,pText);
        }
    }

    private void drawBtn(Canvas c,RectF r,String icon){c.drawRoundRect(r,3,3,pBtn);Paint bb=new Paint(pBorder);bb.setColor(0xFF5555AA);c.drawRoundRect(r,3,3,bb);drawCentered(c,icon,r,pBtnT);}
    private void drawCentered(Canvas c,String t,RectF r,Paint p){Paint.FontMetrics fm=p.getFontMetrics();c.drawText(t,r.centerX()-p.measureText(t)/2f,r.centerY()-(fm.ascent+fm.descent)/2f,p);}
    private String truncate(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}

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

                // Exposure drag: long press [+] button
                if(frameAddBtn.contains(tx,ty)&&project!=null){
                    exposureDragFrameIdx=project.getCurrentFrameIdx();
                    exposureDragStartX=tx;
                    expLpHandler.postDelayed(expLpRunnable,EXP_LP_MS);
                    frameLpHandler.removeCallbacks(frameLpRunnable);
                } else {
                    expLpHandler.removeCallbacks(expLpRunnable);
                    exposureDragFrameIdx=-1;
                    // Frame reorder: long press thumbnail (bukan [+] atau [-])
                    int fh=hitFrame(tx,ty,lW,lHdrBot,fW);
                    if(fh>=0&&!frameDelBtn.contains(tx,ty)){
                        frameDragFromIdx=fh;
                        frameDragStartX=tx;
                        frameLpHandler.postDelayed(frameLpRunnable,FRAME_LP_MS);
                    } else {
                        frameLpHandler.removeCallbacks(frameLpRunnable);
                        frameDragFromIdx=-1;
                    }
                }
                invalidate();return true;

            case MotionEvent.ACTION_MOVE:
                float dx=tx-lastTouchX,dy=ty-lastTouchY;

                // Exposure drag
                if(exposureDragActive){updateExposureDrag(tx);return true;}
                // Frame reorder drag
                if(frameDragActive){updateFrameDrag(tx,lW,lHdrBot,fW);return true;}

                if(exposureDragFrameIdx>=0&&Math.abs(dy)>8*density){expLpHandler.removeCallbacks(expLpRunnable);exposureDragFrameIdx=-1;}
                if(frameDragFromIdx>=0&&(Math.abs(dx)>10*density||Math.abs(dy)>10*density)){frameLpHandler.removeCallbacks(frameLpRunnable);}

                // Blend popup scroll
                if(showBlendPopup&&blendPopupRect.contains(tx,ty)){
                    blendScrollY=Math.max(0,blendScrollY-dy);
                    // Clamp scroll
                    AnimationProject.BlendMode[]modes=AnimationProject.BlendMode.values();
                    float totalH=5*density+(22*density+2*density)*modes.length+5*density;
                    float popH=blendPopupRect.height();
                    blendScrollY=Math.min(blendScrollY,Math.max(0,totalH-popH));
                    lastTouchX=tx;lastTouchY=ty;invalidate();return true;
                }

                if(!scrolling&&(Math.abs(dx)>6*density||Math.abs(dy)>6*density))scrolling=true;
                if(scrolling&&ty>lHdrBot){if(tx>lW)scrollX=Math.max(0,scrollX-dx);scrollY=Math.max(0,scrollY-dy);lastTouchX=tx;lastTouchY=ty;}
                hoveredFrame=hitFrame(tx,ty,lW,lHdrBot,fW);invalidate();return true;

            case MotionEvent.ACTION_UP:
                expLpHandler.removeCallbacks(expLpRunnable);
                frameLpHandler.removeCallbacks(frameLpRunnable);
                boolean wasExp=exposureDragActive,wasFrDrag=frameDragActive;
                exposureDragActive=false;exposureDragFrameIdx=-1;

                if(wasFrDrag&&frameDragToIdx>=0&&frameDragToIdx!=frameDragFromIdx){
                    // Eksekusi reorder
                    reorderFrame(frameDragFromIdx,frameDragToIdx);
                }
                frameDragActive=false;frameDragFromIdx=-1;frameDragToIdx=-1;

                if(!scrolling&&!wasExp&&!wasFrDrag) handleTap(tx,ty,lW,hH,lHdrBot,fW,rH);
                scrolling=false;hoveredFrame=-1;invalidate();return true;

            case MotionEvent.ACTION_CANCEL:
                expLpHandler.removeCallbacks(expLpRunnable);frameLpHandler.removeCallbacks(frameLpRunnable);
                exposureDragActive=false;exposureDragFrameIdx=-1;frameDragActive=false;frameDragFromIdx=-1;frameDragToIdx=-1;
                scrolling=false;hoveredFrame=-1;invalidate();return true;
        }
        return true;
    }

    private void updateExposureDrag(float cx){
        if(!exposureDragActive||project==null)return;
        float fW=FRAME_W_DP*density;int add=(int)((cx-exposureDragStartX)/fW);
        int nExp=Math.max(1,exposureDragOriginal+add);
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l!=null){AnimationProject.Frame f=l.getFrame(exposureDragFrameIdx);
            if(f!=null&&f.exposure!=nExp){f.exposure=Math.min(nExp,99);if(listener!=null)listener.onExposureChanged(exposureDragFrameIdx,f.exposure);invalidate();}}
    }

    private void updateFrameDrag(float cx,float lW,float listTop,float fW){
        if(!frameDragActive||project==null)return;
        // Hitung posisi target berdasarkan x
        float relX=cx-lW+scrollX;
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l==null)return;
        float fx2=0;
        for(int fi=0;fi<l.getFrameCount();fi++){
            AnimationProject.Frame f=l.getFrame(fi);if(f==null){fx2+=fW;continue;}
            float cW=f.exposure*fW;
            if(relX>=fx2-fW/2f&&relX<fx2+cW/2f){frameDragToIdx=fi;invalidate();return;}
            fx2+=cW;
        }
        // Jika di luar semua frame, set ke akhir
        frameDragToIdx=l.getFrameCount()-1;invalidate();
    }

    private void reorderFrame(int from,int to){
        if(project==null||listener==null)return;
        AnimationProject.Layer l=project.getCurrentLayer();
        if(l==null||from<0||to<0||from>=l.getFrameCount()||to>=l.getFrameCount()||from==to)return;
        // Swap frames in list
        AnimationProject.Frame moved=l.frames.remove(from);
        l.frames.add(to,moved);
        project.setCurrentFrame(to);
        listener.onFrameReordered(from,to);
    }

    private void handleTap(float tx,float ty,float lW,float hH,float lHdrBot,float fW,float rH){
        if(project==null||listener==null)return;

        // Blend popup
        if(showBlendPopup){
            if(blendPopupRect.contains(tx,ty)){
                AnimationProject.BlendMode[]modes=AnimationProject.BlendMode.values();
                for(int i=0;i<modes.length;i++){
                    if(i<blendModeRects.length&&blendModeRects[i].contains(tx,ty)){
                        // Adjust for scroll
                        RectF adj=new RectF(blendModeRects[i]);
                        adj.offset(0,-blendScrollY);// already adjusted in draw
                        AnimationProject.Layer l=project.getLayer(blendPopupLayerIdx);
                        if(l!=null){l.blendMode=modes[i];listener.onBlendModeChanged(blendPopupLayerIdx,modes[i]);}
                        showBlendPopup=false;blendPopupLayerIdx=-1;blendScrollY=0;invalidate();return;
                    }
                }
            }
            showBlendPopup=false;blendPopupLayerIdx=-1;blendScrollY=0;invalidate();return;
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
            if(btnPlay.contains(tx,ty))   {if(isPlaying)stopPlayback();else startPlayback();return;}
            if(btnStop.contains(tx,ty))   {stopPlayback();project.setCurrentFrame(0);listener.onFrameChanged(0);return;}
            if(btnPrev.contains(tx,ty))   {stopPlayback();project.prevFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnNext.contains(tx,ty))   {stopPlayback();project.nextFrame();listener.onFrameChanged(project.getCurrentFrameIdx());return;}
            if(btnLoop.contains(tx,ty))   {isLooping=!isLooping;invalidate();return;}
            if(btnFrmSet.contains(tx,ty)) {if(listener!=null)listener.onFrameSettingsRequested();return;}
            return;
        }

        // Layer header
        if(ty>hH&&ty<=lHdrBot&&tx<=lW){if(btnAddLayer.contains(tx,ty)){showAddLayerPopup=true;invalidate();}return;}

        // Layer panel
        if(tx<=lW){
            int li=hitLayer(ty,lHdrBot,rH);
            if(li>=0){
                if(li<MAX_LAYERS&&layerLampBtns[li].contains(tx,ty)){listener.onOnionSkinLayerToggled(li);return;}
                if(li<MAX_LAYERS&&layerDelBtns[li].contains(tx,ty)) {project.removeLayer(li);listener.onLayerRemoved(li);return;}
                if(li<MAX_LAYERS&&layerBlendBtns[li].contains(tx,ty)){
                    showBlendPopup=true;blendPopupLayerIdx=li;blendScrollY=0;
                    showAddLayerPopup=false;invalidate();return;
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
