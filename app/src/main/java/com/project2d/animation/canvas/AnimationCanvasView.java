package com.project2d.animation.canvas;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.project2d.animation.ui.ImGuiTheme;
public class AnimationCanvasView extends View {
    public enum CanvasPreset {
        HD_1280x720("HD 1280x720",1280,720),FHD_1920x1080("FHD 1920x1080",1920,1080),
        SQUARE_1080("Square 1080x1080",1080,1080),A4_PORTRAIT("A4 Portrait",794,1123),
        SMALL_640x480("Small 640x480",640,480),CUSTOM("Custom",0,0);
        public final String label;public int width,height;
        CanvasPreset(String l,int w,int h){label=l;width=w;height=h;}
    }
    private final Paint paintWS=new Paint(),paintC=new Paint(),paintB=new Paint(Paint.ANTI_ALIAS_FLAG),paintSh=new Paint(),paintG=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int docW=1280,docH=720;
    private CanvasPreset preset=CanvasPreset.HD_1280x720;
    private boolean showGrid=false;
    private float scale=1f,offX=0f,offY=0f;
    public AnimationCanvasView(Context c){super(c);init();}
    public AnimationCanvasView(Context c,AttributeSet a){super(c,a);init();}
    private void init(){
        paintWS.setColor(ImGuiTheme.COLOR_APP_BG);paintWS.setStyle(Paint.Style.FILL);
        paintC.setColor(ImGuiTheme.COLOR_CANVAS_BG);paintC.setStyle(Paint.Style.FILL);
        paintB.setColor(ImGuiTheme.COLOR_CANVAS_BORDER);paintB.setStyle(Paint.Style.STROKE);paintB.setStrokeWidth(1.5f);
        paintSh.setColor(0x55000000);paintSh.setStyle(Paint.Style.FILL);
        paintG.setColor(0xFFDDDDDD);paintG.setStyle(Paint.Style.STROKE);paintG.setStrokeWidth(0.5f);
    }
    public void setCanvasPreset(CanvasPreset p){preset=p;docW=p.width;docH=p.height;fitToView();invalidate();}
    public void setCustomSize(int w,int h){preset=CanvasPreset.CUSTOM;docW=w;docH=h;fitToView();invalidate();}
    public void setShowGrid(boolean s){showGrid=s;invalidate();}
    public CanvasPreset getCurrentPreset(){return preset;}
    public int getCanvasDocW(){return docW;}
    public int getCanvasDocH(){return docH;}
    public void fitToView(){
        if(getWidth()==0||getHeight()==0||docW==0||docH==0)return;
        float vW=getWidth(),vH=getHeight();
        scale=Math.min(vW*0.85f/docW,vH*0.85f/docH);
        offX=(vW-docW*scale)/2f;offY=(vH-docH*scale)/2f;
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fitToView();}
    @Override protected void onDraw(Canvas canvas){
        float vW=getWidth(),vH=getHeight();
        canvas.drawRect(0,0,vW,vH,paintWS);
        if(docW==0||docH==0)return;
        float cW=docW*scale,cH=docH*scale,cX=offX,cY=offY;
        canvas.drawRect(cX+6,cY+6,cX+cW+6,cY+cH+6,paintSh);
        canvas.drawRect(cX,cY,cX+cW,cY+cH,paintC);
        if(showGrid){float gs=32*scale;for(float x=cX;x<=cX+cW;x+=gs)canvas.drawLine(x,cY,x,cY+cH,paintG);for(float y=cY;y<=cY+cH;y+=gs)canvas.drawLine(cX,y,cX+cW,y,paintG);}
        canvas.drawRect(cX,cY,cX+cW,cY+cH,paintB);
    }
}
