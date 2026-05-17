package com.project2d.animation.ui;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;
public class ImGuiDropdown extends View {
    public interface OnItemSelectedListener{void onItemSelected(String menuLabel,String item);void onDismiss();}
    private final Paint paintBg=new Paint(Paint.ANTI_ALIAS_FLAG),paintBorder=new Paint(Paint.ANTI_ALIAS_FLAG),paintHover=new Paint(Paint.ANTI_ALIAS_FLAG),paintText=new Paint(Paint.ANTI_ALIAS_FLAG),paintSep=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String menuLabel="";
    private List<String> items=new ArrayList<>();
    private List<RectF> itemRects=new ArrayList<>();
    private float anchorX=0f,anchorY=0f;
    private int hoveredIndex=-1;
    private OnItemSelectedListener listener;
    private float density;
    public ImGuiDropdown(Context c){super(c);init();}
    public ImGuiDropdown(Context c,AttributeSet a){super(c,a);init();}
    private void init(){
        density=getResources().getDisplayMetrics().density;
        paintBg.setColor(ImGuiTheme.COLOR_POPUP_BG);paintBg.setStyle(Paint.Style.FILL);
        paintBorder.setColor(ImGuiTheme.COLOR_POPUP_BORDER);paintBorder.setStyle(Paint.Style.STROKE);paintBorder.setStrokeWidth(1.5f);
        paintHover.setColor(ImGuiTheme.COLOR_MENU_ITEM_HOVERED);paintHover.setStyle(Paint.Style.FILL);
        paintText.setColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);paintText.setTextSize(13*density);paintText.setTypeface(Typeface.MONOSPACE);
        paintSep.setColor(ImGuiTheme.COLOR_MENU_SEPARATOR);paintSep.setStyle(Paint.Style.STROKE);paintSep.setStrokeWidth(1f);
        setClickable(true);
    }
    public void show(String ml,List<String> it,float ax,float ay){menuLabel=ml;items=it;anchorX=ax;anchorY=ay;hoveredIndex=-1;setVisibility(VISIBLE);invalidate();}
    public void hide(){setVisibility(GONE);hoveredIndex=-1;}
    public void setOnItemSelectedListener(OnItemSelectedListener l){listener=l;}
    @Override protected void onDraw(Canvas canvas){
        if(getVisibility()!=VISIBLE||items.isEmpty())return;
        float pH=16*density,iH=28*density,sH=10*density,minW=160*density;
        float maxTW=0f;
        for(String it:items)if(!it.equals("---"))maxTW=Math.max(maxTW,paintText.measureText(it));
        float pW=Math.max(minW,maxTW+pH*2),totalH=4*density;
        for(String it:items)totalH+=it.equals("---")?sH:iH;
        totalH+=4*density;
        float sW=getWidth(),sH2=getHeight();
        float pX=Math.min(anchorX,sW-pW-4*density),pY=anchorY;
        if(pY+totalH>sH2)pY=anchorY-totalH;
        Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);shadow.setColor(0x44000000);shadow.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(pX+4,pY+4,pX+pW+4,pY+totalH+4),3,3,shadow);
        RectF pr=new RectF(pX,pY,pX+pW,pY+totalH);
        canvas.drawRoundRect(pr,3,3,paintBg);canvas.drawRoundRect(pr,3,3,paintBorder);
        itemRects.clear();float y=pY+4*density;
        Paint.FontMetrics fm=paintText.getFontMetrics();
        for(int i=0;i<items.size();i++){
            String it=items.get(i);
            if(it.equals("---")){float mY=y+sH/2f;canvas.drawLine(pX+6*density,mY,pX+pW-6*density,mY,paintSep);itemRects.add(null);y+=sH;}
            else{RectF ir=new RectF(pX+2*density,y,pX+pW-2*density,y+iH);itemRects.add(ir);
                if(i==hoveredIndex)canvas.drawRoundRect(ir,2,2,paintHover);
                canvas.drawText(it,ir.left+pH*0.5f,ir.centerY()-(fm.ascent+fm.descent)/2f,paintText);y+=iH;}
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_MOVE:hoveredIndex=hitTest(tx,ty);invalidate();return true;
            case MotionEvent.ACTION_UP:int h=hitTest(tx,ty);
                if(h>=0&&h<items.size()){String s=items.get(h);if(!s.equals("---")&&listener!=null)listener.onItemSelected(menuLabel,s);}
                else if(listener!=null)listener.onDismiss();
                hoveredIndex=-1;hide();return true;
            case MotionEvent.ACTION_CANCEL:hoveredIndex=-1;if(listener!=null)listener.onDismiss();hide();return true;
        }
        return true;
    }
    private int hitTest(float x,float y){for(int i=0;i<itemRects.size();i++){RectF r=itemRects.get(i);if(r!=null&&r.contains(x,y))return i;}return -1;}
}
