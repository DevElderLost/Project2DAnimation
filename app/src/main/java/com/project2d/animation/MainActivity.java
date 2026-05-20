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
import com.project2d.animation.drawing.OnionSkinSettings;
import com.project2d.animation.timeline.AnimationProject;
import com.project2d.animation.timeline.TimelineView;
import com.project2d.animation.ui.ImGuiDropdown;
import com.project2d.animation.ui.ImGuiMenuBar;
import com.project2d.animation.ui.ImGuiTheme;
import com.project2d.animation.ui.ToolbarView;
import com.project2d.animation.windows.BrushColorWindow;
import com.project2d.animation.windows.CanvasSizeWindow;
import com.project2d.animation.windows.FloatingWindow;
import com.project2d.animation.windows.OnionSkinWindow;
import com.project2d.animation.windows.ToolPanelWindow;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImGuiMenuBar        menuBar;
    private ToolbarView         toolbar;
    private AnimationCanvasView canvasView;
    private TimelineView        timelineView;
    private ToolPanelWindow     toolPanelWindow;
    private BrushColorWindow    brushColorWindow;
    private CanvasSizeWindow    canvasSizeWindow;
    private OnionSkinWindow     onionSkinWindow;
    private FloatingWindow      floatingWindow;
    private ImGuiDropdown       dropdown;

    private DrawingEngine     drawingEngine;
    private AnimationProject  project;
    private OnionSkinSettings onionSettings;

    private FrameLayout root;
    private float density;
    private int menuBarH,toolbarH,timelineH;
    private boolean timelineVisible=false;
    private ValueAnimator timelineAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        density  =getResources().getDisplayMetrics().density;
        menuBarH =(int)(ImGuiTheme.MENU_BAR_HEIGHT_DP*density);
        toolbarH =(int)(32*density);
        timelineH=(int)(200*density);

        project       =new AnimationProject(1280,720);
        drawingEngine =new DrawingEngine();
        onionSettings =new OnionSkinSettings();
        syncEngineToFrame();

        root=new FrameLayout(this);
        root.setBackgroundColor(ImGuiTheme.COLOR_APP_BG);

        // Menu bar
        menuBar=new ImGuiMenuBar(this);
        root.addView(menuBar,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,menuBarH));

        // Toolbar
        toolbar=new ToolbarView(this);
        FrameLayout.LayoutParams tbp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,toolbarH);
        tbp.topMargin=menuBarH; root.addView(toolbar,tbp);
        setupToolbar();

        // Canvas
        canvasView=new AnimationCanvasView(this);
        canvasView.setDrawingEngine(drawingEngine);
        canvasView.setProject(project);
        canvasView.setOnionSkinSettings(onionSettings);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        cp.topMargin=menuBarH+toolbarH; root.addView(canvasView,cp);

        // Timeline
        timelineView=new TimelineView(this);
        timelineView.setProject(project);
        timelineView.setOnionSkinSettings(onionSettings);
        FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,timelineH);
        tlp.topMargin=getScreenH(); root.addView(timelineView,tlp);
        timelineView.setTranslationY(timelineH);
        setupTimeline();

        // Tool Panel
        toolPanelWindow=new ToolPanelWindow(this);
        root.addView(toolPanelWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        toolPanelWindow.setPosition(8*density,menuBarH+toolbarH+8*density);
        toolPanelWindow.setOnToolChanged(new ToolPanelWindow.OnToolChanged(){
            @Override public void onToolSelected(DrawingEngine.Tool t){drawingEngine.setTool(t);toolPanelWindow.setSelectedTool(t);}
            @Override public void onUndoClicked(){if(drawingEngine.canUndo()){drawingEngine.undo();canvasView.invalidate();}}
            @Override public void onClearClicked(){
                new AlertDialog.Builder(MainActivity.this).setTitle("Clear").setMessage("Hapus gambar frame ini?")
                    .setPositiveButton("Ya",(d,w)->{drawingEngine.clearCanvas();canvasView.invalidate();}).setNegativeButton("Tidak",null).show();
            }
        });

        // Brush & Color
        brushColorWindow=new BrushColorWindow(this);
        root.addView(brushColorWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        brushColorWindow.setPosition(8*density,menuBarH+toolbarH+220*density);
        brushColorWindow.setOnBrushChanged(new BrushColorWindow.OnBrushChanged(){
            @Override public void onColorChanged(int c){drawingEngine.setBrushColor(c);}
            @Override public void onSizeChanged(float s){drawingEngine.setBrushSize(s);}
            @Override public void onOpacityChanged(float o){drawingEngine.setBrushOpacity(o);}
        });

        // Onion Skin Window
        onionSkinWindow=new OnionSkinWindow(this);
        onionSkinWindow.setSettings(onionSettings);
        root.addView(onionSkinWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        onionSkinWindow.setPosition(8*density,menuBarH+toolbarH+120*density);
        onionSkinWindow.setOnSettingsChanged(()->{canvasView.invalidate();timelineView.invalidate();});

        // Canvas Size Window
        canvasSizeWindow=new CanvasSizeWindow(this);
        root.addView(canvasSizeWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        canvasSizeWindow.setPosition(40*density,menuBarH+toolbarH+20*density);
        canvasSizeWindow.setCurrentPreset(AnimationCanvasView.CanvasPreset.HD_1280x720);
        canvasSizeWindow.setOnCanvasSizeApplied(new CanvasSizeWindow.OnCanvasSizeApplied(){
            @Override public void onPresetSelected(AnimationCanvasView.CanvasPreset p){canvasView.setCanvasPreset(p);project.resize(p.width,p.height);toast("Canvas: "+p.label);}
            @Override public void onCustomSelected(int w,int h){canvasView.setCustomSize(w,h);project.resize(w,h);toast("Canvas: "+w+"x"+h);}
        });
        EditText eW=buildET("W"),eH=buildET("H");
        root.addView(eW,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(eH,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        eW.setVisibility(View.GONE); eH.setVisibility(View.GONE);
        canvasSizeWindow.attachEditTexts(eW,eH);

        // Hello window (hidden)
        floatingWindow=new FloatingWindow(this);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        fp.topMargin=menuBarH+toolbarH; root.addView(floatingWindow,fp);
        floatingWindow.setPosition(60*density,80*density);
        floatingWindow.setVisibility(View.GONE);
        floatingWindow.setEventListener(new FloatingWindow.OnWindowEventListener(){
            @Override public void onTestButtonClicked(){toast("Test!");}
            @Override public void onWindowClosed(){}
        });

        // Dropdown
        dropdown=new ImGuiDropdown(this);
        dropdown.setVisibility(View.GONE);
        root.addView(dropdown,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setupMenuBar(); setupDropdown();
    }

    private void syncEngineToFrame(){
        AnimationProject.Frame f=project.getCurrentFrame();
        if(f!=null) drawingEngine.setBitmapRef(f.bitmap,f.isEmpty);
    }

    private void toggleTimeline(){
        timelineVisible=!timelineVisible; toolbar.setTimelineActive(timelineVisible);
        if(timelineAnim!=null)timelineAnim.cancel();
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)timelineView.getLayoutParams();
        lp.topMargin=getScreenH()-timelineH; timelineView.setLayoutParams(lp);
        timelineView.setTranslationY(timelineVisible?timelineH:0);
        timelineAnim=ValueAnimator.ofFloat(timelineVisible?timelineH:0,timelineVisible?0:timelineH);
        timelineAnim.setDuration(260); timelineAnim.setInterpolator(new DecelerateInterpolator());
        timelineAnim.addUpdateListener(va->{
            float v=(float)va.getAnimatedValue(); timelineView.setTranslationY(v);
            FrameLayout.LayoutParams clp=(FrameLayout.LayoutParams)canvasView.getLayoutParams();
            clp.bottomMargin=timelineVisible?(int)(timelineH-v):0; canvasView.setLayoutParams(clp);
        }); timelineAnim.start();
    }

    private int getScreenH(){return getResources().getDisplayMetrics().heightPixels;}

    private void setupToolbar(){
        toolbar.setOnToolbarAction(new ToolbarView.OnToolbarAction(){
            @Override public void onUndo(){if(drawingEngine.canUndo()){drawingEngine.undo();canvasView.invalidate();}}
            @Override public void onRedo(){toast("Redo belum tersedia");}
            @Override public void onToggleTools(){int v=toolPanelWindow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;toolPanelWindow.setVisibility(v);toolbar.setToolsActive(v==View.VISIBLE);}
            @Override public void onToggleBrush(){int v=brushColorWindow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;brushColorWindow.setVisibility(v);toolbar.setBrushActive(v==View.VISIBLE);}
            @Override public void onToggleTimeline(){toggleTimeline();}
            @Override public void onToggleOnionSkin(){
                if(onionSkinWindow.getVisibility()==View.VISIBLE) onionSkinWindow.hideWindow();
                else onionSkinWindow.showWindow();
            }
        });
    }

    private void setupTimeline(){
        timelineView.setOnTimelineEvent(new TimelineView.OnTimelineEvent(){
            @Override public void onFrameChanged(int fi){project.setCurrentFrame(fi);syncEngineToFrame();canvasView.invalidate();timelineView.invalidate();}
            @Override public void onLayerChanged(int li){project.setCurrentLayer(li);syncEngineToFrame();canvasView.invalidate();timelineView.invalidate();}
            @Override public void onFrameAdded(){timelineView.invalidate();}
            @Override public void onFrameRemoved(){syncEngineToFrame();canvasView.invalidate();timelineView.invalidate();}
            @Override public void onLayerAdded(){timelineView.invalidate();}
            @Override public void onLayerRemoved(int li){syncEngineToFrame();canvasView.invalidate();timelineView.invalidate();}
            @Override public void onPlayStateChanged(boolean playing){canvasView.setPlaybackMode(playing);}
            @Override public void onOnionSkinLayerToggled(int li){
                if(onionSettings!=null){onionSettings.toggleLayerEnabled(li);canvasView.invalidate();timelineView.invalidate();}
            }
        });
    }

    private void setupMenuBar(){
        menuBar.setDropdownListener(new ImGuiMenuBar.OnDropdownRequestListener(){
            @Override public void onDropdownOpen(int idx,String lbl,List<String> items,float x,float y){dropdown.show(lbl,items,x,y+menuBar.getHeight());}
            @Override public void onDropdownClose(){dropdown.hide();}
        });
    }
    private void setupDropdown(){
        dropdown.setOnItemSelectedListener(new ImGuiDropdown.OnItemSelectedListener(){
            @Override public void onItemSelected(String menu,String item){menuBar.closeMenu();handleMenu(menu,item);}
            @Override public void onDismiss(){menuBar.closeMenu();}
        });
    }
    private void handleMenu(String menu,String item){
        switch(menu){
            case "File":
                if(item.equals("New")){new AlertDialog.Builder(this).setTitle("New").setMessage("Buat project baru?").setPositiveButton("Ya",(d,w)->{drawingEngine.clearCanvas();canvasView.invalidate();}).setNegativeButton("Tidak",null).show();}
                else if(item.equals("Exit")){new AlertDialog.Builder(this).setTitle("Exit").setMessage("Keluar?").setPositiveButton("Ya",(d,w)->finish()).setNegativeButton("Tidak",null).show();}
                else toast("File: "+item); break;
            case "Edit":
                if(item.equals("Undo")){if(drawingEngine.canUndo()){drawingEngine.undo();canvasView.invalidate();}}
                else toast("Edit: "+item); break;
            case "Settings":
                if(item.equals("Canvas Size")){if(canvasSizeWindow.getVisibility()==View.VISIBLE)canvasSizeWindow.hideWindow();else{canvasSizeWindow.setCurrentPreset(canvasView.getCurrentPreset());canvasSizeWindow.showWindow();}}
                else toast("Settings: "+item); break;
        }
    }

    private void hideSystemUI(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
    @Override protected void onResume(){super.onResume();hideSystemUI();}
    @Override public void onWindowFocusChanged(boolean h){super.onWindowFocusChanged(h);if(h)hideSystemUI();}

    private EditText buildET(String hint){
        EditText et=new EditText(this);et.setHint(hint);et.setHintTextColor(ImGuiTheme.COLOR_TEXT_DISABLED);
        et.setTextColor(ImGuiTheme.COLOR_TEXT);et.setTextSize(12);et.setTypeface(Typeface.MONOSPACE);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);et.setBackgroundColor(ImGuiTheme.COLOR_WINDOW_BG);et.setSingleLine(true);
        int p=(int)(5*density);et.setPadding(p,p,p,p);return et;
    }
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){
        if(dropdown.getVisibility()==View.VISIBLE){dropdown.hide();menuBar.closeMenu();return;}
        if(canvasSizeWindow.getVisibility()==View.VISIBLE){canvasSizeWindow.hideWindow();return;}
        if(onionSkinWindow.getVisibility()==View.VISIBLE){onionSkinWindow.hideWindow();return;}
        if(timelineVisible){toggleTimeline();return;}
        super.onBackPressed();
    }
}
