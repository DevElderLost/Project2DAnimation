package com.project2d.animation;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.ui.*;
import com.project2d.animation.windows.FloatingWindow;
import java.util.List;
public class MainActivity extends AppCompatActivity {
    private ImGuiMenuBar menuBar;
    private AnimationCanvasView canvasView;
    private FloatingWindow floatingWindow;
    private ImGuiDropdown dropdown;
    private float density;
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        density=getResources().getDisplayMetrics().density;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(ImGuiTheme.COLOR_APP_BG);
        int mH=(int)(ImGuiTheme.MENU_BAR_HEIGHT_DP*density);
        menuBar=new ImGuiMenuBar(this);
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,mH);mp.topMargin=0;root.addView(menuBar,mp);
        canvasView=new AnimationCanvasView(this);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);cp.topMargin=mH;root.addView(canvasView,cp);
        floatingWindow=new FloatingWindow(this);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);fp.topMargin=mH;root.addView(floatingWindow,fp);
        floatingWindow.setPosition(60*density,80*density);
        dropdown=new ImGuiDropdown(this);dropdown.setVisibility(android.view.View.GONE);
        root.addView(dropdown,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        setupMenuBar();setupFloatingWindow();setupDropdown();
    }
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
        switch(menu){case "File":handleFile(item);break;case "Edit":toast("Edit: "+item);break;case "Settings":handleSettings(item);break;}
    }
    private void handleFile(String item){
        switch(item){
            case "New":toast("New project");break;case "Open":toast("Open...");break;case "Save":toast("Saved");break;case "Save As...":toast("Save As...");break;
            case "Exit":new AlertDialog.Builder(this).setTitle("Exit").setMessage("Exit?").setPositiveButton("Yes",(d,w)->finish()).setNegativeButton("No",null).show();break;
        }
    }
    private void handleSettings(String item){if(item.equals("Canvas Size"))showCanvasSizeDialog();else toast("Settings: "+item);}
    private void setupFloatingWindow(){
        floatingWindow.setEventListener(new FloatingWindow.OnWindowEventListener(){
            @Override public void onTestButtonClicked(){toast("Test clicked!");}
            @Override public void onWindowClosed(){toast("Window closed");}
        });
    }
    private void showCanvasSizeDialog(){
        new CanvasSizeDialog(this,canvasView.getCurrentPreset(),new CanvasSizeDialog.OnCanvasSizeSelected(){
            @Override public void onPresetSelected(AnimationCanvasView.CanvasPreset p){canvasView.setCanvasPreset(p);toast("Canvas: "+p.label);}
            @Override public void onCustomSelected(int w,int h){canvasView.setCustomSize(w,h);toast("Canvas: "+w+"x"+h);}
        }).show();
    }
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){if(dropdown.getVisibility()==android.view.View.VISIBLE){dropdown.hide();menuBar.closeMenu();return;}super.onBackPressed();}
}
