package com.project2d.animation.ui;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.project2d.animation.canvas.AnimationCanvasView;
public class CanvasSizeDialog extends Dialog {
    public interface OnCanvasSizeSelected{void onPresetSelected(AnimationCanvasView.CanvasPreset p);void onCustomSelected(int w,int h);}
    private OnCanvasSizeSelected listener;
    private AnimationCanvasView.CanvasPreset currentPreset;
    private EditText editW,editH;
    public CanvasSizeDialog(Context c,AnimationCanvasView.CanvasPreset cur,OnCanvasSizeSelected l){super(c,android.R.style.Theme_Black_NoTitleBar_Fullscreen);currentPreset=cur;listener=l;}
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);requestWindowFeature(Window.FEATURE_NO_TITLE);
        float d=getContext().getResources().getDisplayMetrics().density;
        Window win=getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.setDimAmount(0.6f);win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        ScrollView sv=new ScrollView(getContext());sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(ImGuiTheme.COLOR_POPUP_BG);
        int p8=(int)(8*d),p12=(int)(12*d),p16=(int)(16*d);root.setPadding(p16,p12,p16,p16);
        TextView title=new TextView(getContext());title.setText("Canvas Size");title.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);title.setTextSize(15);title.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);title.setPadding(0,0,0,p12);root.addView(title);
        View sep=new View(getContext());sep.setBackgroundColor(ImGuiTheme.COLOR_BORDER);root.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));
        root.addView(vspace(p8,d));TextView lbl=new TextView(getContext());lbl.setText("Preset:");lbl.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);lbl.setTextSize(12);lbl.setTypeface(Typeface.MONOSPACE);root.addView(lbl);root.addView(vspace(p8,d));
        RadioGroup rg=new RadioGroup(getContext());rg.setOrientation(RadioGroup.VERTICAL);
        for(AnimationCanvasView.CanvasPreset p:AnimationCanvasView.CanvasPreset.values()){
            if(p==AnimationCanvasView.CanvasPreset.CUSTOM)continue;
            RadioButton rb=new RadioButton(getContext());rb.setText(p.label);rb.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);rb.setTextSize(12);rb.setTypeface(Typeface.MONOSPACE);rb.setTag(p);if(p==currentPreset)rb.setChecked(true);rg.addView(rb);
        }
        root.addView(rg);root.addView(vspace(p12,d));
        View sep2=new View(getContext());sep2.setBackgroundColor(ImGuiTheme.COLOR_BORDER);root.addView(sep2,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));
        root.addView(vspace(p8,d));TextView lbl2=new TextView(getContext());lbl2.setText("Custom:");lbl2.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);lbl2.setTextSize(12);lbl2.setTypeface(Typeface.MONOSPACE);root.addView(lbl2);root.addView(vspace(p8,d));
        LinearLayout ir=new LinearLayout(getContext());ir.setOrientation(LinearLayout.HORIZONTAL);
        editW=new EditText(getContext());editW.setHint("Width");editW.setHintTextColor(0xFF666688);editW.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);editW.setTextSize(12);editW.setTypeface(Typeface.MONOSPACE);editW.setInputType(InputType.TYPE_CLASS_NUMBER);editW.setBackgroundColor(ImGuiTheme.COLOR_WINDOW_BG);int ep=(int)(6*d);editW.setPadding(ep,ep,ep,ep);
        editH=new EditText(getContext());editH.setHint("Height");editH.setHintTextColor(0xFF666688);editH.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);editH.setTextSize(12);editH.setTypeface(Typeface.MONOSPACE);editH.setInputType(InputType.TYPE_CLASS_NUMBER);editH.setBackgroundColor(ImGuiTheme.COLOR_WINDOW_BG);editH.setPadding(ep,ep,ep,ep);
        TextView xl=new TextView(getContext());xl.setText(" x ");xl.setTextColor(ImGuiTheme.COLOR_MENU_ITEM_TEXT);xl.setGravity(Gravity.CENTER_VERTICAL);
        ir.addView(editW,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));ir.addView(xl);ir.addView(editH,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        root.addView(ir);root.addView(vspace(p16,d));
        LinearLayout br=new LinearLayout(getContext());br.setOrientation(LinearLayout.HORIZONTAL);br.setGravity(Gravity.END);
        TextView btnC=mkbtn("Cancel",d);TextView btnA=mkbtn("Apply",d);
        br.addView(btnC);br.addView(hspace((int)(8*d)));br.addView(btnA);root.addView(br);
        btnC.setOnClickListener(v->dismiss());
        btnA.setOnClickListener(v->{
            String ws=editW.getText().toString().trim(),hs=editH.getText().toString().trim();
            if(!ws.isEmpty()&&!hs.isEmpty()){try{int w=Integer.parseInt(ws),h=Integer.parseInt(hs);if(w>0&&h>0&&listener!=null){listener.onCustomSelected(w,h);dismiss();return;}}catch(NumberFormatException ignored){}}
            int cid=rg.getCheckedRadioButtonId();if(cid!=-1){RadioButton rb=rg.findViewById(cid);AnimationCanvasView.CanvasPreset p=(AnimationCanvasView.CanvasPreset)rb.getTag();if(listener!=null)listener.onPresetSelected(p);}
            dismiss();
        });
        int dW=(int)(Math.min(300*d,getContext().getResources().getDisplayMetrics().widthPixels*0.85f));
        sv.addView(root,new ViewGroup.LayoutParams(dW,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv,new ViewGroup.LayoutParams(dW,ViewGroup.LayoutParams.WRAP_CONTENT));
        if(win!=null){win.setLayout(dW,WindowManager.LayoutParams.WRAP_CONTENT);win.setGravity(Gravity.CENTER);}
    }
    private View vspace(int px,float d){View v=new View(getContext());v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,px));return v;}
    private View hspace(int px){View v=new View(getContext());v.setLayoutParams(new LinearLayout.LayoutParams(px,ViewGroup.LayoutParams.WRAP_CONTENT));return v;}
    private TextView mkbtn(String lbl,float d){TextView t=new TextView(getContext());t.setText(lbl);t.setTextColor(ImGuiTheme.COLOR_BUTTON_TEXT);t.setTextSize(12);t.setTypeface(Typeface.MONOSPACE);t.setGravity(Gravity.CENTER);int pH=(int)(14*d),pV=(int)(7*d);t.setPadding(pH,pV,pH,pV);t.setBackgroundColor(ImGuiTheme.COLOR_BUTTON);t.setClickable(true);t.setFocusable(true);return t;}
}
