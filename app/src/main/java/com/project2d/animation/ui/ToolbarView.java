package com.project2d.animation.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Toolbar icon row di bawah menu bar.
 * Icon: Undo | Redo | --- | Tools | Brush | Timeline
 * Tap icon → callback.
 */
public class ToolbarView extends View {

    public interface OnToolbarAction {
        void onUndo();
        void onRedo();
        void onToggleTools();
        void onToggleBrush();
        void onToggleTimeline();
    }

    private static final String[] ICONS  = { "\u21B6", "\u21B7", "|", "\u2692", "\u270F", "\u25A4" };
    // ↶ undo, ↷ redo, separator, ⚒ tools, ✏ brush, ▤ timeline
    private static final boolean[] IS_SEP = { false, false, true, false, false, false };
    private static final int IDX_UNDO     = 0;
    private static final int IDX_REDO     = 1;
    private static final int IDX_TOOLS    = 3;
    private static final int IDX_BRUSH    = 4;
    private static final int IDX_TIMELINE = 5;

    private final RectF[] iconRects = new RectF[6];
    private final Paint pBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcon  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHov   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAct   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int hoveredIdx = -1;
    private boolean toolsActive=true, brushActive=true, timelineActive=false;
    private float density;
    private OnToolbarAction listener;

    public ToolbarView(Context c)               { super(c); init(); }
    public ToolbarView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init(){
        density=getResources().getDisplayMetrics().density;
        for(int i=0;i<6;i++) iconRects[i]=new RectF();
        pBg.setColor(0xFF1E1E2E);   pBg.setStyle(Paint.Style.FILL);
        pIcon.setColor(0xFFCCCCDD); pIcon.setTextSize(14*density); pIcon.setTypeface(Typeface.MONOSPACE); pIcon.setAntiAlias(true);
        pHov.setColor(0xFF2A2A4A);  pHov.setStyle(Paint.Style.FILL);
        pAct.setColor(0xFF3A3A6A);  pAct.setStyle(Paint.Style.FILL);
        pSep.setColor(0xFF3A3A5A);  pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pBdr.setColor(0xFF2A2A3A);  pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1f);
        setClickable(true);
    }

    public void setOnToolbarAction(OnToolbarAction l){ listener=l; }
    public void setToolsActive(boolean a){ toolsActive=a; invalidate(); }
    public void setBrushActive(boolean a){ brushActive=a; invalidate(); }
    public void setTimelineActive(boolean a){ timelineActive=a; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas){
        int w=getWidth(),h=getHeight();
        canvas.drawRect(0,0,w,h,pBg);
        canvas.drawLine(0,h-1,w,h-1,pBdr);

        float pad=6*density, iconW=h-pad, x=pad;
        for(int i=0;i<ICONS.length;i++){
            if(IS_SEP[i]){
                canvas.drawLine(x+4*density,pad,x+4*density,h-pad,pSep);
                iconRects[i].setEmpty();
                x+=14*density; continue;
            }
            iconRects[i].set(x,pad/2f,x+iconW,h-pad/2f);
            boolean active=(i==IDX_TOOLS&&toolsActive)||(i==IDX_BRUSH&&brushActive)||(i==IDX_TIMELINE&&timelineActive);
            if(active) canvas.drawRoundRect(iconRects[i],3,3,pAct);
            else if(i==hoveredIdx) canvas.drawRoundRect(iconRects[i],3,3,pHov);
            Paint.FontMetrics fm=pIcon.getFontMetrics();
            float tx=iconRects[i].centerX()-pIcon.measureText(ICONS[i])/2f;
            float ty=iconRects[i].centerY()-(fm.ascent+fm.descent)/2f;
            canvas.drawText(ICONS[i],tx,ty,pIcon);
            x+=iconW+pad;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_MOVE:
                hoveredIdx=hitIcon(tx,ty); invalidate(); return true;
            case MotionEvent.ACTION_UP:
                int hit=hitIcon(tx,ty);
                hoveredIdx=-1;
                if(hit>=0&&listener!=null){
                    switch(hit){
                        case IDX_UNDO:     listener.onUndo(); break;
                        case IDX_REDO:     listener.onRedo(); break;
                        case IDX_TOOLS:    toolsActive=!toolsActive; listener.onToggleTools(); break;
                        case IDX_BRUSH:    brushActive=!brushActive; listener.onToggleBrush(); break;
                        case IDX_TIMELINE: timelineActive=!timelineActive; listener.onToggleTimeline(); break;
                    }
                }
                invalidate(); return true;
            case MotionEvent.ACTION_CANCEL: hoveredIdx=-1; invalidate(); return true;
        }
        return false;
    }

    private int hitIcon(float x,float y){
        for(int i=0;i<iconRects.length;i++) if(!iconRects[i].isEmpty()&&iconRects[i].contains(x,y)) return i;
        return -1;
    }
}
