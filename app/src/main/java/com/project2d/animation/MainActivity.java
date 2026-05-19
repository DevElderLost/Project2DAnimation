package com.project2d.animation;

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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.drawing.DrawingEngine;
import com.project2d.animation.ui.ImGuiDropdown;
import com.project2d.animation.ui.ImGuiMenuBar;
import com.project2d.animation.ui.ImGuiTheme;
import com.project2d.animation.windows.BrushColorWindow;
import com.project2d.animation.windows.CanvasSizeWindow;
import com.project2d.animation.windows.FloatingWindow;
import com.project2d.animation.windows.ToolPanelWindow;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImGuiMenuBar        menuBar;
    private AnimationCanvasView canvasView;
    private FloatingWindow      floatingWindow;
    private CanvasSizeWindow    canvasSizeWindow;
    private ToolPanelWindow     toolPanelWindow;
    private BrushColorWindow    brushColorWindow;
    private ImGuiDropdown       dropdown;
    private DrawingEngine       drawingEngine;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        density = getResources().getDisplayMetrics().density;

        // Drawing engine
        drawingEngine = new DrawingEngine();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ImGuiTheme.COLOR_APP_BG);

        // Menu bar
        int mH=(int)(ImGuiTheme.MENU_BAR_HEIGHT_DP*density);
        menuBar=new ImGuiMenuBar(this);
        root.addView(menuBar,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,mH));

        // Canvas
        canvasView=new AnimationCanvasView(this);
        canvasView.setDrawingEngine(drawingEngine);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        cp.topMargin=mH;
        root.addView(canvasView,cp);

        // Hello floating window
        floatingWindow=new FloatingWindow(this);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        fp.topMargin=mH;
        root.addView(floatingWindow,fp);
        floatingWindow.setPosition(60*density,80*density);
        floatingWindow.setVisibility(View.GONE); // disembunyikan default

        // Tool Panel
        toolPanelWindow=new ToolPanelWindow(this);
        root.addView(toolPanelWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        toolPanelWindow.setPosition(8*density, mH+8*density);
        toolPanelWindow.setOnToolChanged(new ToolPanelWindow.OnToolChanged(){
            @Override public void onToolSelected(DrawingEngine.Tool t){
                drawingEngine.setTool(t);
                toolPanelWindow.setSelectedTool(t);
            }
            @Override public void onUndoClicked(){
                if(drawingEngine.canUndo()){ drawingEngine.undo(); canvasView.invalidate(); }
            }
            @Override public void onClearClicked(){
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Clear Canvas").setMessage("Hapus semua gambar?")
                    .setPositiveButton("Ya",(d,w)->{drawingEngine.clearCanvas();canvasView.invalidate();})
                    .setNegativeButton("Tidak",null).show();
            }
        });

        // Brush & Color Window
        brushColorWindow=new BrushColorWindow(this);
        root.addView(brushColorWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        brushColorWindow.setPosition(8*density, mH+300*density);
        brushColorWindow.setOnBrushChanged(new BrushColorWindow.OnBrushChanged(){
            @Override public void onColorChanged(int c){drawingEngine.setBrushColor(c);}
            @Override public void onSizeChanged(float s){drawingEngine.setBrushSize(s);}
            @Override public void onOpacityChanged(float o){drawingEngine.setBrushOpacity(o);}
        });

        // Canvas Size Window
        canvasSizeWindow=new CanvasSizeWindow(this);
        root.addView(canvasSizeWindow,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        canvasSizeWindow.setPosition(40*density,mH+20*density);
        canvasSizeWindow.setCurrentPreset(AnimationCanvasView.CanvasPreset.HD_1280x720);
        canvasSizeWindow.setOnCanvasSizeApplied(new CanvasSizeWindow.OnCanvasSizeApplied(){
            @Override public void onPresetSelected(AnimationCanvasView.CanvasPreset p){
                canvasView.setCanvasPreset(p); toast("Canvas: "+p.label);
            }
            @Override public void onCustomSelected(int w,int h){
                canvasView.setCustomSize(w,h); toast("Canvas: "+w+"x"+h);
            }
        });

        // EditText untuk CanvasSizeWindow
        EditText editW=buildEditText("W"); EditText editH=buildEditText("H");
        root.addView(editW,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(editH,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        editW.setVisibility(View.GONE); editH.setVisibility(View.GONE);
        canvasSizeWindow.attachEditTexts(editW,editH);

        // Dropdown
        dropdown=new ImGuiDropdown(this);
        dropdown.setVisibility(View.GONE);
        root.addView(dropdown,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setupMenuBar(); setupDropdown();

        floatingWindow.setEventListener(new FloatingWindow.OnWindowEventListener(){
            @Override public void onTestButtonClicked(){toast("Test!");}
            @Override public void onWindowClosed(){toast("Closed");}
        });
    }

    private EditText buildEditText(String hint){
        EditText et=new EditText(this); et.setHint(hint);
        et.setHintTextColor(ImGuiTheme.COLOR_TEXT_DISABLED);
        et.setTextColor(ImGuiTheme.COLOR_TEXT); et.setTextSize(12);
        et.setTypeface(Typeface.MONOSPACE);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackgroundColor(ImGuiTheme.COLOR_WINDOW_BG); et.setSingleLine(true);
        int p=(int)(5*density); et.setPadding(p,p,p,p); return et;
    }

    private void hideSystemUI(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            WindowInsetsController ctrl=getWindow().getInsetsController();
            if(ctrl!=null){ctrl.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());ctrl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override protected void onResume(){super.onResume();hideSystemUI();}
    @Override public void onWindowFocusChanged(boolean h){super.onWindowFocusChanged(h);if(h)hideSystemUI();}

    private void setupMenuBar(){
        menuBar.setDropdownListener(new ImGuiMenuBar.OnDropdownRequestListener(){
            @Override public void onDropdownOpen(int idx,String lbl,List<String> items,float x,float y){dropdown.show(lbl,items,x,y+menuBar.getHeight());}
            @Override public void onDropdownClose(){dropdown.hide();}
        });
    }

    private void setupDropdown(){
        dropdown.setOnItemSelectedListener(new ImGuiDropdown.OnItemSelectedListener(){
            @Override public void onItemSelected(String menu,String item){menuBar.closeMenu();handleMenuAction(menu,item);}
            @Override public void onDismiss(){menuBar.closeMenu();}
        });
    }

    private void handleMenuAction(String menu,String item){
        switch(menu){
            case "File": handleFile(item); break;
            case "Edit": handleEdit(item); break;
            case "Settings": handleSettings(item); break;
        }
    }

    private void handleFile(String item){
        switch(item){
            case "New": new AlertDialog.Builder(this).setTitle("New").setMessage("Buat canvas baru? Gambar akan dihapus.").setPositiveButton("Ya",(d,w)->{drawingEngine.clearCanvas();canvasView.invalidate();}).setNegativeButton("Tidak",null).show(); break;
            case "Save": toast("Save belum diimplementasi"); break;
            case "Exit": new AlertDialog.Builder(this).setTitle("Exit").setMessage("Keluar?").setPositiveButton("Ya",(d,w)->finish()).setNegativeButton("Tidak",null).show(); break;
            default: toast("File: "+item); break;
        }
    }

    private void handleEdit(String item){
        switch(item){
            case "Undo": if(drawingEngine.canUndo()){drawingEngine.undo();canvasView.invalidate();} break;
            default: toast("Edit: "+item); break;
        }
    }

    private void handleSettings(String item){
        if(item.equals("Canvas Size")){
            if(canvasSizeWindow.getVisibility()==View.VISIBLE) canvasSizeWindow.hideWindow();
            else{ canvasSizeWindow.setCurrentPreset(canvasView.getCurrentPreset()); canvasSizeWindow.showWindow(); }
        } else { toast("Settings: "+item); }
    }

    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){
        if(dropdown.getVisibility()==View.VISIBLE){dropdown.hide();menuBar.closeMenu();return;}
        if(canvasSizeWindow.getVisibility()==View.VISIBLE){canvasSizeWindow.hideWindow();return;}
        super.onBackPressed();
    }
}
