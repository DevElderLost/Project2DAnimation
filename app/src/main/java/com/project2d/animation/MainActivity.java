package com.project2d.animation;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.timeline.AnimationProject;
import com.project2d.animation.timeline.TimelineView;
import com.project2d.animation.ui.ImGuiDropdown;
import com.project2d.animation.ui.ImGuiMenuBar;
import com.project2d.animation.ui.ImGuiTheme;
import com.project2d.animation.ui.ToolbarView;
import com.project2d.animation.windows.BrushColorWindow;
import com.project2d.animation.windows.CanvasSizeWindow;
import com.project2d.animation.windows.FloatingWindow;
import com.project2d.animation.windows.ToolPanelWindow;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private ImGuiMenuBar        menuBar;
    private ToolbarView         toolbar;
    private AnimationCanvasView canvasView;
    private TimelineView        timelineView;
    private ToolPanelWindow     toolPanelWindow;
    private BrushColorWindow    brushColorWindow;
    private CanvasSizeWindow    canvasSizeWindow;
    private FloatingWindow      floatingWindow;
    private ImGuiDropdown       dropdown;

    // ── Engine / Project ──────────────────────────────────────────────────────
    private DrawingEngine       drawingEngine;
    private AnimationProject    project;

    // ── Layout refs ───────────────────────────────────────────────────────────
    private FrameLayout root;
    private float density;
    private int   menuBarH, toolbarH, timelineH;

    // Timeline slide state
    private boolean timelineVisible = false;
    private ValueAnimator timelineAnim;

    // Undo for menu bar Edit
    private String undoBuffer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        density   = getResources().getDisplayMetrics().density;
        menuBarH  = (int)(ImGuiTheme.MENU_BAR_HEIGHT_DP * density);
        toolbarH  = (int)(32 * density);
        timelineH = (int)(200 * density);

        // Project & engine
        project       = new AnimationProject(1280, 720);
        drawingEngine = new DrawingEngine();
        drawingEngine.initBitmap(1280, 720);

        // Sync first frame bitmap with DrawingEngine
        syncEngineToCurrentFrame();

        root = new FrameLayout(this);
        root.setBackgroundColor(ImGuiTheme.COLOR_APP_BG);

        // ── Menu bar ──────────────────────────────────────────────────────────
        menuBar = new ImGuiMenuBar(this);
        root.addView(menuBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, menuBarH));

        // ── Toolbar ───────────────────────────────────────────────────────────
        toolbar = new ToolbarView(this);
        FrameLayout.LayoutParams tbp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, toolbarH);
        tbp.topMargin = menuBarH;
        root.addView(toolbar, tbp);
        setupToolbar();

        // ── Canvas ────────────────────────────────────────────────────────────
        canvasView = new AnimationCanvasView(this);
        canvasView.setDrawingEngine(drawingEngine);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cp.topMargin    = menuBarH + toolbarH;
        cp.bottomMargin = 0;
        root.addView(canvasView, cp);

        // ── Timeline (slide up dari bawah) ────────────────────────────────────
        timelineView = new TimelineView(this);
        timelineView.setProject(project);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, timelineH);
        // Mulai tersembunyi di bawah layar
        tlp.topMargin = getScreenHeight();
        root.addView(timelineView, tlp);
        setupTimeline();

        // ── Tool Panel ────────────────────────────────────────────────────────
        toolPanelWindow = new ToolPanelWindow(this);
        root.addView(toolPanelWindow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolPanelWindow.setPosition(8*density, menuBarH+toolbarH+8*density);
        toolPanelWindow.setOnToolChanged(new ToolPanelWindow.OnToolChanged(){
            @Override public void onToolSelected(DrawingEngine.Tool t){
                drawingEngine.setTool(t); toolPanelWindow.setSelectedTool(t);
            }
            @Override public void onUndoClicked(){
                if(drawingEngine.canUndo()){ drawingEngine.undo(); canvasView.invalidate(); }
            }
            @Override public void onClearClicked(){
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Clear").setMessage("Hapus semua gambar di frame ini?")
                    .setPositiveButton("Ya",(d,w)->{ drawingEngine.clearCanvas(); canvasView.invalidate(); })
                    .setNegativeButton("Tidak",null).show();
            }
        });

        // ── Brush & Color ─────────────────────────────────────────────────────
        brushColorWindow = new BrushColorWindow(this);
        root.addView(brushColorWindow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        brushColorWindow.setPosition(8*density, menuBarH+toolbarH+220*density);
        brushColorWindow.setOnBrushChanged(new BrushColorWindow.OnBrushChanged(){
            @Override public void onColorChanged(int c){ drawingEngine.setBrushColor(c); }
            @Override public void onSizeChanged(float s){ drawingEngine.setBrushSize(s); }
            @Override public void onOpacityChanged(float o){ drawingEngine.setBrushOpacity(o); }
        });

        // ── Canvas Size Window ────────────────────────────────────────────────
        canvasSizeWindow = new CanvasSizeWindow(this);
        root.addView(canvasSizeWindow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        canvasSizeWindow.setPosition(40*density, menuBarH+toolbarH+20*density);
        canvasSizeWindow.setCurrentPreset(AnimationCanvasView.CanvasPreset.HD_1280x720);
        canvasSizeWindow.setOnCanvasSizeApplied(new CanvasSizeWindow.OnCanvasSizeApplied(){
            @Override public void onPresetSelected(AnimationCanvasView.CanvasPreset p){
                canvasView.setCanvasPreset(p); project.resize(p.width,p.height); toast("Canvas: "+p.label);
            }
            @Override public void onCustomSelected(int w, int h){
                canvasView.setCustomSize(w,h); project.resize(w,h); toast("Canvas: "+w+"x"+h);
            }
        });
        EditText editW=buildEditText("W"); EditText editH=buildEditText("H");
        root.addView(editW,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(editH,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        editW.setVisibility(View.GONE); editH.setVisibility(View.GONE);
        canvasSizeWindow.attachEditTexts(editW,editH);

        // ── Hello Window (hidden default) ─────────────────────────────────────
        floatingWindow = new FloatingWindow(this);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        fp.topMargin=menuBarH+toolbarH;
        root.addView(floatingWindow,fp);
        floatingWindow.setPosition(60*density,80*density);
        floatingWindow.setVisibility(View.GONE);
        floatingWindow.setEventListener(new FloatingWindow.OnWindowEventListener(){
            @Override public void onTestButtonClicked(){ toast("Test!"); }
            @Override public void onWindowClosed(){ }
        });

        // ── Dropdown ──────────────────────────────────────────────────────────
        dropdown = new ImGuiDropdown(this);
        dropdown.setVisibility(View.GONE);
        root.addView(dropdown,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setupMenuBar();
        setupDropdown();
    }

    // ── Sync engine bitmap ↔ project frame ────────────────────────────────────

    private void syncEngineToCurrentFrame(){
        AnimationProject.Frame f = project.getCurrentFrame();
        if(f!=null) drawingEngine.setBitmapRef(f.bitmap, f.isEmpty);
    }

    // ── Timeline slide animation ───────────────────────────────────────────────

    private void toggleTimeline(){
        timelineVisible=!timelineVisible;
        animateTimeline(timelineVisible);
        toolbar.setTimelineActive(timelineVisible);
    }

    private void animateTimeline(boolean show){
        if(timelineAnim!=null) timelineAnim.cancel();
        int screenH=getScreenHeight();
        int targetTop = show ? screenH-timelineH : screenH;
        View tl=timelineView;
        float startY=tl.getTranslationY();
        // Use translation instead of layoutparams for smooth animation
        // Position base: top = screenH (hidden below screen)
        // We use translationY: 0 = at screenH-timelineH position, -timelineH = hidden
        float endTrans = show ? 0f : timelineH;
        // Set initial translation if needed
        if(!show && tl.getTranslationY()==0 && !timelineVisible) tl.setTranslationY(timelineH);

        // Reposition layout to screenH - timelineH and animate with translationY
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)tl.getLayoutParams();
        lp.topMargin=screenH-timelineH;
        tl.setLayoutParams(lp);
        tl.setTranslationY(show?timelineH:0);

        timelineAnim=ValueAnimator.ofFloat(show?timelineH:0, show?0:timelineH);
        timelineAnim.setDuration(280);
        timelineAnim.setInterpolator(new DecelerateInterpolator());
        timelineAnim.addUpdateListener(va->{
            tl.setTranslationY((float)va.getAnimatedValue());
            // Shrink canvas bottom margin
            FrameLayout.LayoutParams clp=(FrameLayout.LayoutParams)canvasView.getLayoutParams();
            float prog=1f-(float)va.getAnimatedValue()/timelineH;
            clp.bottomMargin=show?(int)(timelineH*prog):0;
            canvasView.setLayoutParams(clp);
        });
        timelineAnim.start();
    }

    private int getScreenHeight(){
        return getResources().getDisplayMetrics().heightPixels;
    }

    // ── Toolbar setup ─────────────────────────────────────────────────────────

    private void setupToolbar(){
        toolbar.setOnToolbarAction(new ToolbarView.OnToolbarAction(){
            @Override public void onUndo(){
                if(drawingEngine.canUndo()){ drawingEngine.undo(); canvasView.invalidate(); }
            }
            @Override public void onRedo(){ toast("Redo belum diimplementasi"); }
            @Override public void onToggleTools(){
                int v=toolPanelWindow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;
                toolPanelWindow.setVisibility(v);
                toolbar.setToolsActive(v==View.VISIBLE);
            }
            @Override public void onToggleBrush(){
                int v=brushColorWindow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;
                brushColorWindow.setVisibility(v);
                toolbar.setBrushActive(v==View.VISIBLE);
            }
            @Override public void onToggleTimeline(){ toggleTimeline(); }
        });
    }

    // ── Timeline setup ────────────────────────────────────────────────────────

    private void setupTimeline(){
        timelineView.setOnTimelineEvent(new TimelineView.OnTimelineEvent(){
            @Override public void onFrameChanged(int frameIdx){
                // Simpan gambar frame lama, load frame baru
                project.setCurrentFrame(frameIdx);
                syncEngineToCurrentFrame();
                canvasView.invalidate();
                timelineView.invalidate();
            }
            @Override public void onLayerChanged(int layerIdx){
                project.setCurrentLayer(layerIdx);
                syncEngineToCurrentFrame();
                canvasView.invalidate();
                timelineView.invalidate();
            }
            @Override public void onFrameAdded(){
                timelineView.invalidate();
            }
            @Override public void onPlayStateChanged(boolean playing){
                // During playback, composite all layers
                if(playing) startCompositePlayback();
                else stopCompositePlayback();
            }
            @Override public void onRequestRedraw(){ canvasView.invalidate(); }
        });
    }

    private void startCompositePlayback(){
        // Playback sudah ditangani oleh TimelineView.tickPlayback()
        // Di sini kita hanya perlu update canvas setiap frame change
    }
    private void stopCompositePlayback(){ canvasView.invalidate(); }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    private void setupMenuBar(){
        menuBar.setDropdownListener(new ImGuiMenuBar.OnDropdownRequestListener(){
            @Override public void onDropdownOpen(int idx,String lbl,List<String> items,float x,float y){
                dropdown.show(lbl,items,x,y+menuBar.getHeight());
            }
            @Override public void onDropdownClose(){ dropdown.hide(); }
        });
    }

    private void setupDropdown(){
        dropdown.setOnItemSelectedListener(new ImGuiDropdown.OnItemSelectedListener(){
            @Override public void onItemSelected(String menu,String item){
                menuBar.closeMenu(); handleMenuAction(menu,item);
            }
            @Override public void onDismiss(){ menuBar.closeMenu(); }
        });
    }

    private void handleMenuAction(String menu,String item){
        switch(menu){
            case "File":     handleFile(item);     break;
            case "Edit":     handleEdit(item);     break;
            case "Settings": handleSettings(item); break;
        }
    }

    private void handleFile(String item){
        switch(item){
            case "New":
                new AlertDialog.Builder(this).setTitle("New").setMessage("Buat project baru?")
                    .setPositiveButton("Ya",(d,w)->{ drawingEngine.clearCanvas(); canvasView.invalidate(); })
                    .setNegativeButton("Tidak",null).show(); break;
            case "Exit":
                new AlertDialog.Builder(this).setTitle("Exit").setMessage("Keluar?")
                    .setPositiveButton("Ya",(d,w)->finish()).setNegativeButton("Tidak",null).show(); break;
            default: toast("File: "+item); break;
        }
    }

    private void handleEdit(String item){
        switch(item){
            case "Undo": if(drawingEngine.canUndo()){ drawingEngine.undo(); canvasView.invalidate(); } break;
            case "Redo": toast("Redo belum diimplementasi"); break;
            default: toast("Edit: "+item); break;
        }
    }

    private void handleSettings(String item){
        if(item.equals("Canvas Size")){
            if(canvasSizeWindow.getVisibility()==View.VISIBLE) canvasSizeWindow.hideWindow();
            else{ canvasSizeWindow.setCurrentPreset(canvasView.getCurrentPreset()); canvasSizeWindow.showWindow(); }
        } else toast("Settings: "+item);
    }

    // ── Fullscreen ────────────────────────────────────────────────────────────

    private void hideSystemUI(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){ c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
    @Override protected void onResume(){ super.onResume(); hideSystemUI(); }
    @Override public void onWindowFocusChanged(boolean h){ super.onWindowFocusChanged(h); if(h) hideSystemUI(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EditText buildEditText(String hint){
        EditText et=new EditText(this); et.setHint(hint);
        et.setHintTextColor(ImGuiTheme.COLOR_TEXT_DISABLED);
        et.setTextColor(ImGuiTheme.COLOR_TEXT); et.setTextSize(12);
        et.setTypeface(Typeface.MONOSPACE); et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackgroundColor(ImGuiTheme.COLOR_WINDOW_BG); et.setSingleLine(true);
        int p=(int)(5*density); et.setPadding(p,p,p,p); return et;
    }

    private void toast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed(){
        if(dropdown.getVisibility()==View.VISIBLE){ dropdown.hide(); menuBar.closeMenu(); return; }
        if(canvasSizeWindow.getVisibility()==View.VISIBLE){ canvasSizeWindow.hideWindow(); return; }
        if(timelineVisible){ toggleTimeline(); return; }
        super.onBackPressed();
    }
}
