package com.project2d.animation;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.ui.ImGuiDropdown;
import com.project2d.animation.ui.ImGuiMenuBar;
import com.project2d.animation.ui.ImGuiTheme;
import com.project2d.animation.windows.CanvasSizeWindow;
import com.project2d.animation.windows.FloatingWindow;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImGuiMenuBar        menuBar;
    private AnimationCanvasView canvasView;
    private FloatingWindow      floatingWindow;
    private CanvasSizeWindow    canvasSizeWindow;
    private ImGuiDropdown       dropdown;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        density=getResources().getDisplayMetrics().density;

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(ImGuiTheme.COLOR_APP_BG);

        // Menu bar
        int mH=(int)(ImGuiTheme.MENU_BAR_HEIGHT_DP*density);
        menuBar=new ImGuiMenuBar(this);
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,mH);
        root.addView(menuBar,mp);

        // Canvas - isi sisa layar di bawah menu bar
        canvasView=new AnimationCanvasView(this);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        cp.topMargin=mH;
        root.addView(canvasView,cp);

        // Hello floating window - MATCH_PARENT tapi touch di luar winRect diteruskan ke canvas
        floatingWindow=new FloatingWindow(this);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        fp.topMargin=mH;
        root.addView(floatingWindow,fp);
        floatingWindow.setPosition(60*density,80*density);

        // Canvas Size floating window - WRAP_CONTENT, posisi di pojok kiri atas
        canvasSizeWindow=new CanvasSizeWindow(this);
        root.addView(canvasSizeWindow,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
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

        // Dropdown overlay - paling atas
        dropdown=new ImGuiDropdown(this);
        dropdown.setVisibility(android.view.View.GONE);
        root.addView(dropdown,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setupMenuBar();
        setupFloatingWindow();
        setupDropdown();
    }

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
            case "Edit":     toast("Edit: "+item); break;
            case "Settings": handleSettings(item); break;
        }
    }

    private void handleFile(String item){
        switch(item){
            case "New":       toast("New project"); break;
            case "Open":      toast("Open...");     break;
            case "Save":      toast("Saved");       break;
            case "Save As...":toast("Save As...");  break;
            case "Exit":
                new AlertDialog.Builder(this)
                    .setTitle("Exit").setMessage("Exit?")
                    .setPositiveButton("Yes",(d,w)->finish())
                    .setNegativeButton("No",null).show();
                break;
        }
    }

    private void handleSettings(String item){
        if(item.equals("Canvas Size")){
            // Toggle: buka jika tersembunyi, tutup jika sudah terbuka
            if(canvasSizeWindow.getVisibility()==android.view.View.VISIBLE){
                canvasSizeWindow.hideWindow();
            } else {
                canvasSizeWindow.setCurrentPreset(canvasView.getCurrentPreset());
                canvasSizeWindow.showWindow();
            }
        } else {
            toast("Settings: "+item);
        }
    }

    private void setupFloatingWindow(){
        floatingWindow.setEventListener(new FloatingWindow.OnWindowEventListener(){
            @Override public void onTestButtonClicked(){ toast("Test clicked!"); }
            @Override public void onWindowClosed()     { toast("Window closed"); }
        });
    }

    private void toast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }

    @Override
    public void onBackPressed(){
        if(dropdown.getVisibility()==android.view.View.VISIBLE){
            dropdown.hide(); menuBar.closeMenu(); return;
        }
        if(canvasSizeWindow.getVisibility()==android.view.View.VISIBLE){
            canvasSizeWindow.hideWindow(); return;
        }
        super.onBackPressed();
    }
}
