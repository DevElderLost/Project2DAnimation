package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.ui.ImGuiTheme;

public class CanvasSizeWindow extends FrameLayout {

    public interface OnCanvasSizeApplied {
        void onPresetSelected(AnimationCanvasView.CanvasPreset preset);
        void onCustomSelected(int width, int height);
    }

    private float winX=40f, winY=60f, winW;
    private boolean isDragging=false;
    private float touchDownRawX=0f, touchDownRawY=0f;
    private float dragOffsetX=0f, dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{isDragging=true;invalidateChrome();};

    private final Paint pWinBg=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitleBg=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitleAct=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShadow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitleTxt=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCloseBtn=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCloseBg=new Paint(Paint.ANTI_ALIAS_FLAG);

    private ChromeOverlay chromeOverlay;
    private LinearLayout contentLayout;
    private EditText editW, editH;

    private AnimationCanvasView.CanvasPreset selectedPreset=AnimationCanvasView.CanvasPreset.HD_1280x720;
    private OnCanvasSizeApplied listener;
    private float density;
    private int titleBarH;

    private final RectF closeRect=new RectF();
    private boolean closeHovered=false;

    private final AnimationCanvasView.CanvasPreset[] PRESETS={
        AnimationCanvasView.CanvasPreset.HD_1280x720,
        AnimationCanvasView.CanvasPreset.FHD_1920x1080,
        AnimationCanvasView.CanvasPreset.SQUARE_1080,
        AnimationCanvasView.CanvasPreset.A4_PORTRAIT,
        AnimationCanvasView.CanvasPreset.SMALL_640x480,
    };
    private final View[] presetRows=new View[5];

    public CanvasSizeWindow(Context context){
        super(context);
        density=context.getResources().getDisplayMetrics().density;
        titleBarH=(int)(28*density);
        winW=260*density;
        setWillNotDraw(false);
        initPaints();
        buildLayout(context);
        setVisibility(GONE);
        setClickable(true);
    }

    public CanvasSizeWindow(Context context,AttributeSet attrs){this(context);}

    private void initPaints(){
        pWinBg.setColor(0xFF1A1A1A); pWinBg.setStyle(Paint.Style.FILL);
        pTitleBg.setColor(0xFF131320); pTitleBg.setStyle(Paint.Style.FILL);
        pTitleAct.setColor(0xFF1E1E30); pTitleAct.setStyle(Paint.Style.FILL);
        pBorder.setColor(0xFF3A3A5A); pBorder.setStyle(Paint.Style.STROKE); pBorder.setStrokeWidth(1.5f);
        pShadow.setColor(0x55000000); pShadow.setStyle(Paint.Style.FILL);
        pTitleTxt.setColor(0xFFBBBBCC); pTitleTxt.setTextSize(12*density); pTitleTxt.setTypeface(Typeface.MONOSPACE); pTitleTxt.setFakeBoldText(true); pTitleTxt.setAntiAlias(true);
        pCloseBtn.setColor(0xFF777799); pCloseBtn.setTextSize(13*density); pCloseBtn.setTypeface(Typeface.MONOSPACE); pCloseBtn.setAntiAlias(true);
        pCloseBg.setColor(0xFFCC4444); pCloseBg.setStyle(Paint.Style.FILL);
    }

    private void buildLayout(Context ctx){
        contentLayout=new LinearLayout(ctx);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setBackgroundColor(0xFF1A1A1A);
        FrameLayout.LayoutParams clp=new FrameLayout.LayoutParams((int)winW,ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin=titleBarH;
        addView(contentLayout,clp);
        buildContent(ctx);
        chromeOverlay=new ChromeOverlay(ctx);
        addView(chromeOverlay,new FrameLayout.LayoutParams((int)winW,ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildContent(Context ctx){
        int padH=(int)(10*density);
        contentLayout.addView(vgap(ctx,(int)(4*density)));
        contentLayout.addView(sectionLabel(ctx,"Preset"));
        contentLayout.addView(separator(ctx));
        for(int i=0;i<PRESETS.length;i++){
            PresetRowView row=new PresetRowView(ctx,PRESETS[i]);
            presetRows[i]=row;
            contentLayout.addView(row);
        }
        contentLayout.addView(vgap(ctx,(int)(6*density)));
        contentLayout.addView(sectionLabel(ctx,"Custom"));
        contentLayout.addView(separator(ctx));
        contentLayout.addView(vgap(ctx,(int)(4*density)));
        LinearLayout inputRow=new LinearLayout(ctx);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(padH,0,padH,0);
        editW=buildEditText(ctx,"Width");
        editH=buildEditText(ctx,"Height");
        TextView xLbl=new TextView(ctx); xLbl.setText(" x "); xLbl.setTextColor(0xFF666677); xLbl.setTextSize(12); xLbl.setTypeface(Typeface.MONOSPACE);
        inputRow.addView(editW,new LinearLayout.LayoutParams(0,(int)(30*density),1f));
        inputRow.addView(xLbl);
        inputRow.addView(editH,new LinearLayout.LayoutParams(0,(int)(30*density),1f));
        contentLayout.addView(inputRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        contentLayout.addView(vgap(ctx,(int)(8*density)));
        contentLayout.addView(separator(ctx));
        contentLayout.addView(vgap(ctx,(int)(6*density)));
        LinearLayout btnRow=new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(padH,0,padH,0);
        View btnApply=buildButton(ctx,"Apply");
        btnApply.setOnClickListener(v->handleApply());
        btnRow.addView(btnApply);
        contentLayout.addView(btnRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        contentLayout.addView(vgap(ctx,(int)(6*density)));
        refreshPresetHighlights();
    }

    private void handleApply(){
        String ws=editW.getText().toString().trim(), hs=editH.getText().toString().trim();
        if(!ws.isEmpty()&&!hs.isEmpty()){
            try{int w=Integer.parseInt(ws),h=Integer.parseInt(hs); if(w>0&&h>0&&listener!=null){listener.onCustomSelected(w,h);return;}}
            catch(NumberFormatException ignored){}
        }
        if(listener!=null) listener.onPresetSelected(selectedPreset);
    }

    public void setOnCanvasSizeApplied(OnCanvasSizeApplied l){this.listener=l;}

    public void setCurrentPreset(AnimationCanvasView.CanvasPreset p){
        selectedPreset=p; refreshPresetHighlights();
    }

    public void showWindow(){setVisibility(VISIBLE);bringToFront();}
    public void hideWindow(){setVisibility(GONE);}

    public void setPosition(float x,float y){
        winX=x; winY=y; setTranslationX(winX); setTranslationY(winY);
    }

    private void refreshPresetHighlights(){
        for(int i=0;i<presetRows.length;i++){
            if(presetRows[i] instanceof PresetRowView)
                ((PresetRowView)presetRows[i]).setChecked(PRESETS[i]==selectedPreset);
        }
    }

    private void invalidateChrome(){if(chromeOverlay!=null)chromeOverlay.invalidate();invalidate();}

    @Override
    protected void onMeasure(int wSpec,int hSpec){
        super.onMeasure(MeasureSpec.makeMeasureSpec((int)winW,MeasureSpec.EXACTLY),hSpec);
    }

    @Override
    protected void onDraw(Canvas canvas){
        int w=getWidth(),h=getHeight();
        canvas.drawRoundRect(new RectF(4,4,w+4,h+4),4,4,pShadow);
        canvas.drawRoundRect(new RectF(0,0,w,h),4,4,pWinBg);
        canvas.drawRoundRect(new RectF(0.75f,0.75f,w-0.75f,h-0.75f),4,4,pBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        boolean inTitle=ty>=0&&ty<=titleBarH;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                isDragging=false; touchDownRawX=e.getRawX(); touchDownRawY=e.getRawY();
                dragOffsetX=e.getRawX()-winX; dragOffsetY=e.getRawY()-winY;
                closeHovered=closeRect.contains(tx,ty);
                if(inTitle&&!closeRect.contains(tx,ty)) lpHandler.postDelayed(lpRunnable,LP_MS);
                invalidateChrome(); return true;
            case MotionEvent.ACTION_MOVE:
                float mdx=e.getRawX()-touchDownRawX,mdy=e.getRawY()-touchDownRawY;
                if(!isDragging&&mdx*mdx+mdy*mdy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                if(isDragging){
                    float pW=((View)getParent()).getWidth(),pH=((View)getParent()).getHeight();
                    winX=Math.max(0,Math.min(e.getRawX()-dragOffsetX,pW-getWidth()));
                    winY=Math.max(0,Math.min(e.getRawY()-dragOffsetY,pH-getHeight()));
                    setTranslationX(winX); setTranslationY(winY);
                }
                closeHovered=closeRect.contains(tx,ty); invalidateChrome(); return true;
            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; closeHovered=false;
                if(!wd&&closeRect.contains(tx,ty)) hideWindow();
                invalidateChrome(); return true;
            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable); isDragging=false; closeHovered=false;
                invalidateChrome(); return true;
        }
        return super.onTouchEvent(e);
    }

    private EditText buildEditText(Context ctx,String hint){
        EditText et=new EditText(ctx); et.setHint(hint); et.setHintTextColor(0xFF444455);
        et.setTextColor(0xFFCCCCCC); et.setTextSize(12); et.setTypeface(Typeface.MONOSPACE);
        et.setInputType(InputType.TYPE_CLASS_NUMBER); et.setBackgroundColor(0xFF0D0D16); et.setSingleLine(true);
        int p=(int)(5*density); et.setPadding(p,p,p,p); return et;
    }

    private View buildButton(Context ctx,String lbl){
        TextView tv=new TextView(ctx); tv.setText(lbl);
        tv.setTextColor(0xFFCCCCDD); tv.setTextSize(12); tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.CENTER); tv.setBackgroundColor(0xFF252540);
        int pH=(int)(16*density),pV=(int)(6*density); tv.setPadding(pH,pV,pH,pV);
        tv.setClickable(true); tv.setFocusable(true); return tv;
    }

    private TextView sectionLabel(Context ctx,String text){
        TextView tv=new TextView(ctx); tv.setText(text);
        tv.setTextColor(0xFF6666AA); tv.setTextSize(10); tv.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        int p=(int)(10*density); tv.setPadding(p,(int)(3*density),p,(int)(2*density)); return tv;
    }

    private View separator(Context ctx){
        View v=new View(ctx); v.setBackgroundColor(0xFF252530); return v;
    }

    private View vgap(Context ctx,int h){
        View v=new View(ctx); v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,h)); return v;
    }

    private class ChromeOverlay extends View {
        ChromeOverlay(Context ctx){super(ctx);setClickable(false);}
        @Override public boolean onTouchEvent(MotionEvent e){return false;}
        @Override protected void onDraw(Canvas canvas){
            int w=getWidth(); float tH=titleBarH,r=4f;
            Paint tp=isDragging?pTitleAct:pTitleBg;
            canvas.save(); canvas.clipRect(0,0,w,tH+r);
            canvas.drawRoundRect(new RectF(0,0,w,tH+r),r,r,tp); canvas.restore();
            canvas.drawRect(0,tH-r,w,tH,tp);
            Paint.FontMetrics fm=pTitleTxt.getFontMetrics();
            canvas.drawText("Canvas Size",10*density,tH/2f-(fm.ascent+fm.descent)/2f,pTitleTxt);
            float cSz=tH*0.60f;
            closeRect.set(w-cSz-6*density,(tH-cSz)/2f,w-6*density,(tH+cSz)/2f);
            if(closeHovered){canvas.drawRoundRect(closeRect,2,2,pCloseBg);}
            String x="x"; Paint.FontMetrics xfm=pCloseBtn.getFontMetrics();
            float xY=closeRect.centerY()-(xfm.ascent+xfm.descent)/2f;
            float xX=closeRect.centerX()-pCloseBtn.measureText(x)/2f;
            pCloseBtn.setColor(closeHovered?0xFFFFFFFF:0xFF666688);
            canvas.drawText(x,xX,xY,pCloseBtn);
            Paint sep=new Paint(); sep.setColor(0xFF1E1E30); sep.setStyle(Paint.Style.STROKE); sep.setStrokeWidth(1f);
            canvas.drawLine(0,tH,w,tH,sep);
        }
    }

    private class PresetRowView extends View {
        private final AnimationCanvasView.CanvasPreset preset;
        private boolean checked=false,hovered=false;
        private final Paint pBg=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pHov=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCkBg=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCkBdr=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCkMrk=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pLbl=new Paint(Paint.ANTI_ALIAS_FLAG);

        PresetRowView(Context ctx,AnimationCanvasView.CanvasPreset p){
            super(ctx); this.preset=p;
            pBg.setColor(0xFF1A1A1A); pBg.setStyle(Paint.Style.FILL);
            pHov.setColor(0xFF1F1F2F); pHov.setStyle(Paint.Style.FILL);
            pCkBg.setColor(0xFF0D0D16); pCkBg.setStyle(Paint.Style.FILL);
            pCkBdr.setColor(0xFF4A4A7A); pCkBdr.setStyle(Paint.Style.STROKE); pCkBdr.setStrokeWidth(1.5f);
            pCkMrk.setColor(0xFF6666FF); pCkMrk.setStyle(Paint.Style.FILL);
            pLbl.setColor(0xFFCCCCCC); pLbl.setTextSize(12*density); pLbl.setTypeface(Typeface.MONOSPACE); pLbl.setAntiAlias(true);
            setClickable(true);
        }

        public void setChecked(boolean c){checked=c;invalidate();}

        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_MOVE: hovered=true;invalidate();return true;
                case MotionEvent.ACTION_UP:
                    hovered=false; selectedPreset=preset; refreshPresetHighlights();
                    editW.setText(""); editH.setText(""); return true;
                case MotionEvent.ACTION_CANCEL: hovered=false;invalidate();return true;
            }
            return false;
        }

        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),padH=10*density;
            float bSz=13*density,bY=(h-bSz)/2f,bX=padH;
            canvas.drawRect(0,0,w,h,hovered?pHov:pBg);
            RectF br=new RectF(bX,bY,bX+bSz,bY+bSz);
            canvas.drawRoundRect(br,2,2,pCkBg);
            canvas.drawRoundRect(br,2,2,pCkBdr);
            if(checked){float in=bSz*0.28f;canvas.drawRoundRect(new RectF(br.left+in,br.top+in,br.right-in,br.bottom-in),1,1,pCkMrk);}
            Paint.FontMetrics fm=pLbl.getFontMetrics();
            canvas.drawText(preset.label,bX+bSz+7*density,h/2f-(fm.ascent+fm.descent)/2f,pLbl);
        }

        @Override protected void onMeasure(int wSpec,int hSpec){
            setMeasuredDimension(MeasureSpec.getSize(wSpec),(int)(30*density));
        }
    }
}
