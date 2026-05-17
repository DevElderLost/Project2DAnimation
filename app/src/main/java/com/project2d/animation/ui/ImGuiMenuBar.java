package com.project2d.animation.ui;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;
public class ImGuiMenuBar extends View {
    public interface MenuItemClickListener { void onMenuItemClick(String menu, String item); }
    public interface OnDropdownRequestListener {
        void onDropdownOpen(int menuIndex, String menuLabel, List<String> items, float x, float y);
        void onDropdownClose();
    }
    private static class MenuItem { String label; List<String> items; MenuItem(String l, List<String> i){label=l;items=i;} }
    private final Paint paintBg=new Paint(Paint.ANTI_ALIAS_FLAG),paintText=new Paint(Paint.ANTI_ALIAS_FLAG),paintHover=new Paint(Paint.ANTI_ALIAS_FLAG),paintBorder=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MenuItem> menus=new ArrayList<>();
    private final List<RectF> menuRects=new ArrayList<>();
    private int openMenuIndex=-1,hoveredMenuIndex=-1;
    private OnDropdownRequestListener dropdownListener;
    public ImGuiMenuBar(Context c){super(c);init();}
    public ImGuiMenuBar(Context c,AttributeSet a){super(c,a);init();}
    private void init(){
        float d=getResources().getDisplayMetrics().density;
        paintBg.setColor(ImGuiTheme.COLOR_MENU_BAR_BG);paintBg.setStyle(Paint.Style.FILL);
        paintText.setColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);paintText.setTextSize(13*d);paintText.setTypeface(Typeface.MONOSPACE);
        paintHover.setColor(ImGuiTheme.COLOR_MENU_ITEM_HOVERED);paintHover.setStyle(Paint.Style.FILL);
        paintBorder.setColor(ImGuiTheme.COLOR_BORDER);paintBorder.setStyle(Paint.Style.STROKE);paintBorder.setStrokeWidth(1f);
        List<String> fi=new ArrayList<>(Arrays.asList("New","Open","Save","Save As...","---","Exit"));
        List<String> ei=new ArrayList<>(Arrays.asList("Undo","Redo","---","Cut","Copy","Paste","---","Select All"));
        List<String> si=new ArrayList<>(Arrays.asList("Canvas Size","Grid Settings","---","Preferences"));
        menus.add(new MenuItem("File",fi));menus.add(new MenuItem("Edit",ei));menus.add(new MenuItem("Settings",si));
    }
    public void setDropdownListener(OnDropdownRequestListener l){dropdownListener=l;}
    public void closeMenu(){openMenuIndex=-1;hoveredMenuIndex=-1;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        canvas.drawRect(0,0,w,h,paintBg);
        canvas.drawLine(0,h-1,w,h-1,paintBorder);
        menuRects.clear();
        float pH=ImGuiTheme.MENU_ITEM_PADDING_H*d,x=pH*0.5f;
        for(int i=0;i<menus.size();i++){
            String lbl=menus.get(i).label;
            float tw=paintText.measureText(lbl),iw=tw+pH*2;
            RectF r=new RectF(x,1,x+iw,h-1);menuRects.add(r);
            if(i==openMenuIndex||i==hoveredMenuIndex)canvas.drawRoundRect(r,3,3,paintHover);
            Paint.FontMetrics fm=paintText.getFontMetrics();
            canvas.drawText(lbl,r.left+pH,r.centerY()-(fm.ascent+fm.descent)/2f,paintText);
            x+=iw+2;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        float tx=e.getX(),ty=e.getY();
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_MOVE:hoveredMenuIndex=hitTest(tx,ty);invalidate();break;
            case MotionEvent.ACTION_UP:
                int h=hitTest(tx,ty);
                if(h>=0){if(openMenuIndex==h){openMenuIndex=-1;if(dropdownListener!=null)dropdownListener.onDropdownClose();}
                else{openMenuIndex=h;RectF r=menuRects.get(h);if(dropdownListener!=null)dropdownListener.onDropdownOpen(h,menus.get(h).label,menus.get(h).items,r.left,getBottom());}}
                else{openMenuIndex=-1;if(dropdownListener!=null)dropdownListener.onDropdownClose();}
                hoveredMenuIndex=-1;invalidate();break;
            case MotionEvent.ACTION_CANCEL:hoveredMenuIndex=-1;invalidate();break;
        }
        return true;
    }
    private int hitTest(float x,float y){for(int i=0;i<menuRects.size();i++)if(menuRects.get(i).contains(x,y))return i;return -1;}
}
