package com.project2d.animation.drawing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.util.ArrayList;

/**
 * DrawingEngine dengan cancelStroke() untuk membatalkan stroke
 * tanpa menyimpan ke bitmap — digunakan saat jari kedua turun.
 */
public class DrawingEngine {

    public enum Tool { BRUSH, ERASER, FILL }

    private Tool  currentTool  = Tool.BRUSH;
    private int   brushColor   = Color.BLACK;
    private float brushSize    = 8f;
    private float brushOpacity = 1f;

    private Bitmap drawBitmap;
    private Canvas drawCanvas;

    // Snapshot bitmap sebelum stroke dimulai — untuk cancel
    private Bitmap strokeSnapshot = null;

    private final Paint brushPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path  currentPath = new Path();
    private float lastX=0f,lastY=0f;
    private boolean pathStarted=false;

    private static final int MAX_UNDO = 20;
    private final ArrayList<Bitmap> undoStack = new ArrayList<>();
    private final ArrayList<Bitmap> redoStack = new ArrayList<>();

    public DrawingEngine(){ setupPaints(); }

    private void setupPaints(){
        brushPaint.setStyle(Paint.Style.STROKE);
        brushPaint.setStrokeCap(Paint.Cap.ROUND);
        brushPaint.setStrokeJoin(Paint.Join.ROUND);
        brushPaint.setAntiAlias(true);

        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeCap(Paint.Cap.ROUND);
        eraserPaint.setStrokeJoin(Paint.Join.ROUND);
        eraserPaint.setAntiAlias(true);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    // ── Bitmap management ─────────────────────────────────────────────────────

    public void initBitmap(int w,int h){
        if(w<=0||h<=0) return;
        Bitmap nb=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        if(drawBitmap!=null&&!drawBitmap.isRecycled())
            new Canvas(nb).drawBitmap(drawBitmap,0,0,null);
        drawBitmap=nb; drawCanvas=new Canvas(drawBitmap);
        clearHistory();
    }

    /**
     * Sync ke bitmap milik AnimationProject.Frame.
     * Dipanggil setiap ganti frame atau layer.
     */
    public void setBitmapRef(Bitmap bm,boolean isEmpty){
        if(bm==null) return;
        drawBitmap=bm; drawCanvas=new Canvas(drawBitmap);
        clearHistory();
        strokeSnapshot=null; pathStarted=false;
    }

    // ── Stroke ────────────────────────────────────────────────────────────────

    public void startStroke(float x,float y){
        if(drawCanvas==null) return;
        clearRedo();
        strokeSnapshot=drawBitmap.copy(Bitmap.Config.ARGB_8888,false);
        currentPath.reset();
        currentPath.moveTo(x,y);
        lastX=x; lastY=y; pathStarted=true;
    }

    public void continueStroke(float x,float y){
        if(!pathStarted||drawCanvas==null) return;
        float mx=(lastX+x)/2f,my=(lastY+y)/2f;
        currentPath.quadTo(lastX,lastY,mx,my);
        lastX=x; lastY=y;
        drawStroke();
    }

    public void endStroke(float x,float y){
        if(!pathStarted||drawCanvas==null) return;
        currentPath.lineTo(x,y);
        drawStroke();
        currentPath.reset();
        pathStarted=false;
        saveUndo(strokeSnapshot);
        pushRedo(snapshotCurrent());
        strokeSnapshot=null; // commit berhasil, buang snapshot cancel
        markModified();
    }

    /**
     * Batalkan stroke yang sedang berjalan — restore ke snapshot sebelum stroke.
     * Dipanggil saat jari kedua turun (switch ke navigasi).
     */
    public void cancelStroke(){
        if(!pathStarted) return;
        currentPath.reset();
        pathStarted=false;
        if(strokeSnapshot!=null&&drawCanvas!=null&&!strokeSnapshot.isRecycled()){
            drawCanvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            drawCanvas.drawBitmap(strokeSnapshot,0,0,null);
        }
        strokeSnapshot=null;
    }

    private void drawStroke(){
        if(currentTool==Tool.BRUSH){
            brushPaint.setColor(brushColor);
            brushPaint.setAlpha((int)(brushOpacity*255));
            brushPaint.setStrokeWidth(brushSize);
            drawCanvas.drawPath(currentPath,brushPaint);
        } else if(currentTool==Tool.ERASER){
            eraserPaint.setStrokeWidth(brushSize*2f);
            drawCanvas.drawPath(currentPath,eraserPaint);
        }
    }

    // ── Frame state callback ──────────────────────────────────────────────────

    private FrameStateListener frameStateListener;
    public interface FrameStateListener{ void onFrameModified(); }
    public void setFrameStateListener(FrameStateListener l){ frameStateListener=l; }
    private void markModified(){ if(frameStateListener!=null) frameStateListener.onFrameModified(); }

    // ── Fill ──────────────────────────────────────────────────────────────────

    public void fill(float x,float y){
        if(drawBitmap==null) return;
        saveUndo(snapshotCurrent());
        clearRedo();
        int px=(int)x,py=(int)y;
        if(px<0||py<0||px>=drawBitmap.getWidth()||py>=drawBitmap.getHeight()) return;
        int from=drawBitmap.getPixel(px,py);
        if(from==brushColor) return;
        floodFill(drawBitmap,px,py,from,brushColor);
        markModified();
    }

    private void floodFill(Bitmap bm,int x,int y,int from,int to){
        int w=bm.getWidth(),h=bm.getHeight();
        int[]px=new int[w*h]; bm.getPixels(px,0,w,0,0,w,h);
        int[]stack=new int[w*h*2]; int sp=0;
        stack[sp++]=x; stack[sp++]=y;
        while(sp>0){
            int cy=stack[--sp],cx=stack[--sp];
            if(cx<0||cy<0||cx>=w||cy>=h) continue;
            if(px[cy*w+cx]!=from) continue;
            px[cy*w+cx]=to;
            stack[sp++]=cx+1;stack[sp++]=cy;
            stack[sp++]=cx-1;stack[sp++]=cy;
            stack[sp++]=cx;stack[sp++]=cy+1;
            stack[sp++]=cx;stack[sp++]=cy-1;
        }
        bm.setPixels(px,0,w,0,0,w,h);
    }

    // ── Undo ──────────────────────────────────────────────────────────────────

    private Bitmap snapshotCurrent(){
        if(drawBitmap==null) return null;
        return drawBitmap.copy(Bitmap.Config.ARGB_8888,false);
    }

    private void clearHistory(){
        for(Bitmap bitmap:undoStack){ if(bitmap!=null&&!bitmap.isRecycled()) bitmap.recycle(); }
        for(Bitmap bitmap:redoStack){ if(bitmap!=null&&!bitmap.isRecycled()) bitmap.recycle(); }
        undoStack.clear();
        redoStack.clear();
    }

    private void clearRedo(){
        for(Bitmap bitmap:redoStack){ if(bitmap!=null&&!bitmap.isRecycled()) bitmap.recycle(); }
        redoStack.clear();
    }

    private void saveUndo(Bitmap snap){
        if(snap==null) return;
        if(undoStack.size()==MAX_UNDO){
            Bitmap oldest=undoStack.remove(0);
            if(oldest!=null&&!oldest.isRecycled()) oldest.recycle();
        }
        undoStack.add(snap);
    }

    private void pushRedo(Bitmap snap){
        if(snap==null) return;
        if(redoStack.size()==MAX_UNDO){
            Bitmap oldest=redoStack.remove(0);
            if(oldest!=null&&!oldest.isRecycled()) oldest.recycle();
        }
        redoStack.add(snap);
    }

    public boolean canUndo(){ return !undoStack.isEmpty(); }
    public boolean canRedo(){ return !redoStack.isEmpty(); }

    public void undo(){
        if(!canUndo()||drawBitmap==null) return;
        Bitmap current=snapshotCurrent();
        Bitmap prev=undoStack.remove(undoStack.size()-1);
        pushRedo(current);
        if(prev!=null&&!prev.isRecycled()){
            drawCanvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            drawCanvas.drawBitmap(prev,0,0,null);
        }
        markModified();
    }

    public void redo(){
        if(!canRedo()||drawBitmap==null) return;
        Bitmap current=snapshotCurrent();
        Bitmap next=redoStack.remove(redoStack.size()-1);
        saveUndo(current);
        if(next!=null&&!next.isRecycled()){
            drawCanvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            drawCanvas.drawBitmap(next,0,0,null);
        }
        markModified();
    }

    public void clearCanvas(){
        if(drawCanvas==null) return;
        saveUndo(snapshotCurrent());
        clearRedo();
        drawCanvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        markModified();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Bitmap getDrawBitmap()   { return drawBitmap; }
    public Tool   getCurrentTool()  { return currentTool; }
    public int    getBrushColor()   { return brushColor; }
    public float  getBrushSize()    { return brushSize; }
    public float  getBrushOpacity() { return brushOpacity; }

    public void setTool(Tool t)          { currentTool=t; }
    public void setBrushColor(int c)     { brushColor=c; }
    public void setBrushSize(float s)    { brushSize=Math.max(1f,Math.min(200f,s)); }
    public void setBrushOpacity(float o) { brushOpacity=Math.max(0.05f,Math.min(1f,o)); }
}
