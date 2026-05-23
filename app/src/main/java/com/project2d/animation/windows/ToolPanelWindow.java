package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.ui.ImGuiTheme;

/**
 * Tool Panel Window — pilih tool: Brush, Eraser, Fill, Undo, Clear
 * Arsitektur sama persis dengan FloatingWindow (single View, onDraw).
 */
public class ToolPanelWindow extends View {

    public interface OnToolChanged {
        void onToolSelected(DrawingEngine.Tool tool);
        void onClearClicked();
    }

    private float winX=10f, winY=200f;
    private float winW, winH;
    private boolean isDragging=false, longPressTriggered=false;
    private float touchDownX=0f, touchDownY=0f, dragOffsetX=0f, dragOffsetY=0f;
    private static final long LP_MS=400L;
    private final Handler lpHandler=new Handler(Looper.getMainLooper());
    private final Runnable lpRunnable=()->{longPressTriggered=true;isDragging=true;invalidate();};

    private final Paint pBg    =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn   =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnSel=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT  =new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh    =new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF winRect      =new RectF();
    private final RectF titleRect    =new RectF();
    private final RectF closeBtnRect =new RectF();

    // Tool buttons
    private static final String[] TOOL_LABELS  = {"Brush","Eraser","Fill"};
    private static final String[] ACTION_LABELS = {"Clear"};
    private final RectF[] toolRects   = new RectF[3];
    private final RectF[] actionRects = new RectF[1];

    private boolean closeHov=false;
    private int hoveredTool=-1, hoveredAction=-1;
    private DrawingEngine.Tool selectedTool=DrawingEngine.Tool.BRUSH;

    private float density;
    private OnToolChanged listener;

    public ToolPanelWindow(Context c){super(c);init();}
    public ToolPanelWindow(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        density=getResources().getDisplayMetrics().density;
        winW=110*density;
        for(int i=0;i<3;i++) toolRects[i]=new RectF();
        for(int i=0;i<1;i++) actionRects[i]=new RectF();

        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);          pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);     pTitle.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT);                  pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);                pBtn.setStyle(Paint.Style.FILL);
        pBtnSel.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);     pBtnSel.setStyle(Paint.Style.FILL);
        pBtnH.setColor(0xFF3A3A5A);                            pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);          pBtnT.setTextSize(11*density); pBtnT.setTypeface(Typeface.MONOSPACE);
        pSh.setColor(0x44000000);                              pSh.setStyle(Paint.Style.FILL);
        setClickable(true); setVisibility(VISIBLE);
    }

    public void setOnToolChanged(OnToolChanged l){listener=l;}
    public void setSelectedTool(DrawingEngine.Tool t){selectedTool=t;invalidate();}
    public void setPosition(float x,float y){winX=x;winY=y;invalidate();}

    @Override protected void onDraw(Canvas canvas){
        float r=ImGuiTheme.BORDER_RADIUS, pad=8*density;
        float tH=ImGuiTheme.TITLE_BAR_HEIGHT_DP*density;
        float btnH=30*density, btnW=winW-pad*2;
        float gap=4*density;

        winH=tH+pad+(btnH+gap)*3+gap+(btnH+gap)*2+pad;
        winX=Math.max(0,Math.min(winX,getWidth()-winW));
        winY=Math.max(0,Math.min(winY,getHeight()-winH));
        winRect.set(winX,winY,winX+winW,winY+winH);

        // Shadow
        float so=isDragging?8*density:4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so),r,r,pSh);

        // Window bg
        canvas.drawRoundRect(winRect,r,r,pBg);

        // Title bar
        titleRect.set(winX,winY,winX+winW,winY+tH);
        Paint tp=new Paint(pTitle); if(isDragging) tp.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r),r,r,tp);
        canvas.restore();
        Paint.FontMetrics fm=pTxt.getFontMetrics();
        canvas.drawText("Tools",winX+pad,titleRect.centerY()-(fm.ascent+fm.descent)/2f,pTxt);

        // Close
        float cSz=tH*0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density,winY+(tH-cSz)/2f,winX+winW-5*density,winY+(tH+cSz)/2f);
        if(closeHov){Paint cp=new Paint(Paint.ANTI_ALIAS_FLAG);cp.setColor(0xFFEE6666);cp.setStyle(Paint.Style.FILL);canvas.drawRoundRect(closeBtnRect,2,2,cp);}
        Paint xP=new Paint(pTxt); xP.setColor(closeHov?0xFFFFFFFF:0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm=xP.getFontMetrics();
        canvas.drawText("x",closeBtnRect.centerX()-xP.measureText("x")/2f,closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f,xP);

        // Border
        canvas.drawRoundRect(winRect,r,r,pBdr);

        // Tool buttons
        float y=winY+tH+pad;
        for(int i=0;i<TOOL_LABELS.length;i++){
            toolRects[i].set(winX+pad,y,winX+pad+btnW,y+btnH);
            boolean sel=(selectedTool==DrawingEngine.Tool.values()[i]);
            canvas.drawRoundRect(toolRects[i],3,3,sel?pBtnSel:(i==hoveredTool?pBtnH:pBtn));
            Paint bb=new Paint(pBdr); bb.setColor(sel?0xFF8888FF:0xFF6666AA); bb.setStrokeWidth(1f);
            canvas.drawRoundRect(toolRects[i],3,3,bb);
            Paint.FontMetrics bfm=pBtnT.getFontMetrics();
            canvas.drawText(TOOL_LABELS[i],toolRects[i].centerX()-pBtnT.measureText(TOOL_LABELS[i])/2f,toolRects[i].centerY()-(bfm.ascent+bfm.descent)/2f,pBtnT);
            y+=btnH+gap;
        }

        // Separator
        y+=gap;
        Paint sep=new Paint(); sep.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER); sep.setStyle(Paint.Style.STROKE); sep.setStrokeWidth(1f);
        canvas.drawLine(winX+pad,y,winX+winW-pad,y,sep);
        y+=gap*2;

        // Action buttons (Undo, Clear)
        for(int i=0;i<ACTION_LABELS.length;i++){
            actionRects[i].set(winX+pad,y,winX+pad+btnW,y+btnH);
            canvas.drawRoundRect(actionRects[i],3,3,i==hoveredAction?pBtnH:pBtn);
            Paint bb=new Paint(pBdr); bb.setColor(0xFF6666AA); bb.setStrokeWidth(1f);
            canvas.drawRoundRect(actionRects[i],3,3,bb);
            Paint.FontMetrics bfm=pBtnT.getFontMetrics();
            canvas.drawText(ACTION_LABELS[i],actionRects[i].centerX()-pBtnT.measureText(ACTION_LABELS[i])/2f,actionRects[i].centerY()-(bfm.ascent+bfm.descent)/2f,pBtnT);
            y+=btnH+gap;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        float tx=event.getX(),ty=event.getY();
        if(!winRect.contains(tx,ty)) return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                touchDownX=tx; touchDownY=ty; longPressTriggered=false; isDragging=false;
                if(titleRect.contains(tx,ty)&&!closeBtnRect.contains(tx,ty)){dragOffsetX=tx-winX;dragOffsetY=ty-winY;lpHandler.postDelayed(lpRunnable,LP_MS);}
                closeHov=closeBtnRect.contains(tx,ty); hoveredTool=hitTool(tx,ty); hoveredAction=hitAction(tx,ty);
                invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if(isDragging){winX=tx-dragOffsetX;winY=ty-dragOffsetY;invalidate();}
                else{float dx=tx-touchDownX,dy=ty-touchDownY;if(dx*dx+dy*dy>(8*density)*(8*density))lpHandler.removeCallbacks(lpRunnable);closeHov=closeBtnRect.contains(tx,ty);hoveredTool=hitTool(tx,ty);hoveredAction=hitAction(tx,ty);invalidate();}
                return true;
            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; longPressTriggered=false;
                if(!wd){
                    if(closeBtnRect.contains(tx,ty)){setVisibility(GONE);}
                    int ht=hitTool(tx,ty);
                    if(ht>=0){selectedTool=DrawingEngine.Tool.values()[ht];if(listener!=null)listener.onToolSelected(selectedTool);}
                    int ha=hitAction(tx,ty);
                    if(ha==0&&listener!=null) listener.onClearClicked();
                }
                closeHov=false;hoveredTool=-1;hoveredAction=-1;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable);isDragging=false;longPressTriggered=false;closeHov=false;hoveredTool=-1;hoveredAction=-1;invalidate();return true;
        }
        return false;
    }

    private int hitTool(float x,float y){for(int i=0;i<toolRects.length;i++)if(toolRects[i].contains(x,y))return i;return -1;}
    private int hitAction(float x,float y){for(int i=0;i<actionRects.length;i++)if(actionRects[i].contains(x,y))return i;return -1;}
}
