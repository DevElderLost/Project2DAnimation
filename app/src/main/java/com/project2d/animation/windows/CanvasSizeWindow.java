package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.project2d.animation.canvas.AnimationCanvasView;
import com.project2d.animation.ui.ImGuiTheme;

/**
 * Canvas Size Window — arsitektur sama persis dengan FloatingWindow.
 * Satu View, semua digambar di onDraw dengan Canvas API.
 * EditText untuk custom size di-overlay secara programatik.
 */
public class CanvasSizeWindow extends View {

    public interface OnCanvasSizeApplied {
        void onPresetSelected(AnimationCanvasView.CanvasPreset preset);
        void onCustomSelected(int width, int height);
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    private float winX = 40f, winY = 60f;
    private float winW, winH;

    // ── Drag ──────────────────────────────────────────────────────────────────
    private boolean isDragging         = false;
    private boolean longPressTriggered = false;
    private float   touchDownX = 0f, touchDownY = 0f;
    private float   dragOffsetX = 0f, dragOffsetY = 0f;
    private static final long LP_MS   = 400L;
    private final Handler   lpHandler  = new Handler(Looper.getMainLooper());
    private final Runnable  lpRunnable = () -> { longPressTriggered=true; isDragging=true; invalidate(); };

    // ── Paints — sama persis FloatingWindow ───────────────────────────────────
    private final Paint pBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBdr   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnH  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnT  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSh    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCheck = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSep   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Hit rects ─────────────────────────────────────────────────────────────
    private final RectF winRect      = new RectF();
    private final RectF titleRect    = new RectF();
    private final RectF closeBtnRect = new RectF();
    private final RectF applyBtnRect = new RectF();
    private final RectF[] presetRects = new RectF[5];

    private boolean closeHov = false, applyHov = false;
    private int     hoveredPreset = -1;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AnimationCanvasView.CanvasPreset[] PRESETS = {
        AnimationCanvasView.CanvasPreset.HD_1280x720,
        AnimationCanvasView.CanvasPreset.FHD_1920x1080,
        AnimationCanvasView.CanvasPreset.SQUARE_1080,
        AnimationCanvasView.CanvasPreset.A4_PORTRAIT,
        AnimationCanvasView.CanvasPreset.SMALL_640x480,
    };
    private int selectedPresetIdx = 0;

    // ── EditText untuk custom W/H (overlay di atas View) ─────────────────────
    private EditText editW, editH;
    private final RectF editWRect = new RectF();
    private final RectF editHRect = new RectF();

    private float density;
    private OnCanvasSizeApplied listener;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CanvasSizeWindow(Context c)               { super(c); init(); }
    public CanvasSizeWindow(Context c, AttributeSet a){ super(c,a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        // Ukuran window — sama lebar dengan FloatingWindow (220dp)
        winW = 220 * density;
        // winH dihitung dinamis di onDraw berdasarkan konten

        // Paints — sama persis FloatingWindow
        pBg.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);          pBg.setStyle(Paint.Style.FILL);
        pTitle.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE);     pTitle.setStyle(Paint.Style.FILL);
        pBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pBdr.setStyle(Paint.Style.STROKE); pBdr.setStrokeWidth(1.5f);
        pTxt.setColor(ImGuiTheme.COLOR_TEXT);                  pTxt.setTextSize(13*density); pTxt.setTypeface(Typeface.MONOSPACE); pTxt.setFakeBoldText(true);
        pSub.setColor(ImGuiTheme.COLOR_TEXT);                  pSub.setTextSize(12*density); pSub.setTypeface(Typeface.MONOSPACE);
        pBtn.setColor(ImGuiTheme.COLOR_BUTTON);                pBtn.setStyle(Paint.Style.FILL);
        pBtnH.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);       pBtnH.setStyle(Paint.Style.FILL);
        pBtnT.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);          pBtnT.setTextSize(12*density); pBtnT.setTypeface(Typeface.MONOSPACE);
        pSh.setColor(0x44000000);                              pSh.setStyle(Paint.Style.FILL);
        pCheck.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);      pCheck.setStyle(Paint.Style.FILL);
        pSep.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);      pSep.setStyle(Paint.Style.STROKE); pSep.setStrokeWidth(1f);
        pHint.setColor(ImGuiTheme.COLOR_TEXT_DISABLED);        pHint.setTextSize(10*density); pHint.setTypeface(Typeface.MONOSPACE);

        for (int i=0; i<5; i++) presetRects[i] = new RectF();

        setClickable(true);
        setVisibility(GONE);
    }

    // ── EditText overlay — dipanggil dari Activity setelah addView ────────────

    public void attachEditTexts(EditText ew, EditText eh) {
        this.editW = ew;
        this.editH = eh;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setOnCanvasSizeApplied(OnCanvasSizeApplied l) { this.listener = l; }

    public void setCurrentPreset(AnimationCanvasView.CanvasPreset p) {
        for (int i=0; i<PRESETS.length; i++) {
            if (PRESETS[i] == p) { selectedPresetIdx = i; break; }
        }
        invalidate();
    }

    public void showWindow() { setVisibility(VISIBLE); bringToFront(); invalidate(); }
    public void hideWindow() {
        setVisibility(GONE);
        hideKeyboard();
    }

    public void setPosition(float x, float y) { winX=x; winY=y; invalidate(); }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && editW != null) imm.hideSoftInputFromWindow(editW.getWindowToken(), 0);
    }

    // ── onDraw — sama strukturnya dengan FloatingWindow ───────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        float r   = ImGuiTheme.BORDER_RADIUS;
        float pad = 10 * density;
        float tH  = ImGuiTheme.TITLE_BAR_HEIGHT_DP * density;
        float rowH = 26 * density;
        float ckSz = 12 * density;

        // Hitung tinggi window dinamis
        float contentH =
            tH                      // title bar
            + pad * 0.5f            // gap
            + 14 * density          // label "Preset"
            + 4 * density           // gap
            + 1                     // separator
            + rowH * 5              // 5 preset rows
            + pad                   // gap
            + 14 * density          // label "Custom"
            + 4 * density           // gap
            + 1                     // separator
            + pad * 0.5f            // gap
            + 30 * density          // input row W x H
            + pad                   // gap
            + 1                     // separator
            + pad                   // gap
            + 28 * density          // Apply button
            + pad;                  // bottom padding

        winH = contentH;

        // Clamp posisi agar tidak keluar layar
        winX = Math.max(0, Math.min(winX, getWidth()  - winW));
        winY = Math.max(0, Math.min(winY, getHeight() - winH));
        winRect.set(winX, winY, winX+winW, winY+winH);

        // Shadow — sama dengan FloatingWindow
        float so = isDragging ? 8*density : 4*density;
        canvas.drawRoundRect(new RectF(winX+so,winY+so,winX+winW+so,winY+winH+so), r,r, pSh);

        // Window background
        canvas.drawRoundRect(winRect, r, r, pBg);

        // Title bar
        titleRect.set(winX, winY, winX+winW, winY+tH);
        Paint tp = new Paint(pTitle);
        if (isDragging) tp.setColor(ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE);
        canvas.save(); canvas.clipRect(winRect);
        canvas.drawRoundRect(new RectF(winX,winY,winX+winW,winY+tH+r), r,r, tp);
        canvas.restore();

        // Title text
        Paint.FontMetrics fm = pTxt.getFontMetrics();
        float tY = titleRect.centerY() - (fm.ascent+fm.descent)/2f;
        canvas.drawText("Canvas Size", winX+pad, tY, pTxt);

        // Close button × — sama persis FloatingWindow
        float cSz = tH * 0.65f;
        closeBtnRect.set(winX+winW-cSz-5*density, winY+(tH-cSz)/2f,
                         winX+winW-5*density,      winY+(tH+cSz)/2f);
        if (closeHov) {
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(0xFFEE6666); cp.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(closeBtnRect, 2,2, cp);
        }
        Paint xP = new Paint(pTxt);
        xP.setColor(closeHov ? 0xFFFFFFFF : 0xFFAAAAAA); xP.setTextSize(12*density);
        Paint.FontMetrics xfm = xP.getFontMetrics();
        canvas.drawText("x",
            closeBtnRect.centerX()-xP.measureText("x")/2f,
            closeBtnRect.centerY()-(xfm.ascent+xfm.descent)/2f, xP);

        // Window border
        canvas.drawRoundRect(winRect, r, r, pBdr);

        // ── Konten di bawah title bar ─────────────────────────────────────────
        float y = winY + tH + pad*0.5f;

        // Label "Preset"
        canvas.drawText("PRESET", winX+pad, y + 12*density, pHint);
        y += 14*density + 4*density;

        // Separator
        canvas.drawLine(winX+pad, y, winX+winW-pad, y, pSep);
        y += 1;

        // Preset rows dengan checkbox
        Paint.FontMetrics sfm = pSub.getFontMetrics();
        for (int i=0; i<PRESETS.length; i++) {
            presetRects[i].set(winX+2, y, winX+winW-2, y+rowH);

            // Row highlight
            if (i == hoveredPreset || i == selectedPresetIdx) {
                Paint rowBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                rowBg.setStyle(Paint.Style.FILL);
                rowBg.setColor(i==selectedPresetIdx
                    ? ImGuiTheme.COLOR_FLOAT_WIN_TITLE_ACTIVE
                    : ImGuiTheme.COLOR_FLOAT_WIN_TITLE);
                canvas.drawRoundRect(presetRects[i], 2,2, rowBg);
            }

            // Checkbox kotak
            float bX = winX + pad;
            float bY = y + (rowH-ckSz)/2f;
            RectF ckRect = new RectF(bX, bY, bX+ckSz, bY+ckSz);
            Paint ckBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            ckBg.setColor(ImGuiTheme.COLOR_WINDOW_BG); ckBg.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(ckRect, 2,2, ckBg);
            Paint ckBdr = new Paint(Paint.ANTI_ALIAS_FLAG);
            ckBdr.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER); ckBdr.setStyle(Paint.Style.STROKE); ckBdr.setStrokeWidth(1.5f);
            canvas.drawRoundRect(ckRect, 2,2, ckBdr);
            // Checkmark
            if (i == selectedPresetIdx) {
                float inn = ckSz * 0.28f;
                canvas.drawRoundRect(new RectF(ckRect.left+inn,ckRect.top+inn,ckRect.right-inn,ckRect.bottom-inn),1,1,pCheck);
            }

            // Label preset
            float labelY = y + rowH/2f - (sfm.ascent+sfm.descent)/2f;
            canvas.drawText(PRESETS[i].label, bX+ckSz+6*density, labelY, pSub);

            y += rowH;
        }

        y += pad;

        // Label "Custom"
        canvas.drawText("CUSTOM", winX+pad, y + 12*density, pHint);
        y += 14*density + 4*density;

        // Separator
        canvas.drawLine(winX+pad, y, winX+winW-pad, y, pSep);
        y += 1 + pad*0.5f;

        // Input row W x H — posisi disimpan untuk EditText overlay
        float inputH = 30 * density;
        float inputW = (winW - pad*2 - 20*density) / 2f;
        editWRect.set(winX+pad, y, winX+pad+inputW, y+inputH);
        editHRect.set(winX+winW-pad-inputW, y, winX+winW-pad, y+inputH);

        // Gambar kotak input background
        Paint inputBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        inputBg.setColor(ImGuiTheme.COLOR_WINDOW_BG); inputBg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(editWRect, 3,3, inputBg);
        canvas.drawRoundRect(editHRect, 3,3, inputBg);
        canvas.drawRoundRect(editWRect, 3,3, pBdr);
        canvas.drawRoundRect(editHRect, 3,3, pBdr);

        // Label " x " di tengah
        String xStr = " x ";
        float xStrW = pSub.measureText(xStr);
        float xStrX = editWRect.right + (editHRect.left-editWRect.right-xStrW)/2f;
        canvas.drawText(xStr, xStrX, y+inputH/2f-(sfm.ascent+sfm.descent)/2f, pSub);

        // Posisikan EditText overlay
        positionEditTexts(y, inputH, inputW, pad);

        y += inputH + pad;

        // Separator
        canvas.drawLine(winX+pad, y, winX+winW-pad, y, pSep);
        y += 1 + pad;

        // Apply button — sama dengan button FloatingWindow
        float btnW = 70*density, btnH = 26*density;
        float btnX = winX+winW-pad-btnW;
        applyBtnRect.set(btnX, y, btnX+btnW, y+btnH);
        canvas.drawRoundRect(applyBtnRect, 3,3, applyHov?pBtnH:pBtn);
        Paint bb = new Paint(pBdr); bb.setColor(0xFF6666AA); bb.setStrokeWidth(1f);
        canvas.drawRoundRect(applyBtnRect, 3,3, bb);
        Paint.FontMetrics bfm = pBtnT.getFontMetrics();
        canvas.drawText("Apply",
            applyBtnRect.centerX()-pBtnT.measureText("Apply")/2f,
            applyBtnRect.centerY()-(bfm.ascent+bfm.descent)/2f,
            pBtnT);
    }

    // ── Posisikan EditText overlay sesuai koordinat yang digambar ─────────────

    private void positionEditTexts(float y, float inputH, float inputW, float pad) {
        if (editW == null || editH == null) return;
        // Koordinat relatif terhadap parent (FrameLayout)
        float tx = getTranslationX();
        float ty = getTranslationY();
        // editW
        editW.setX(tx + editWRect.left);
        editW.setY(ty + editWRect.top);
        editW.getLayoutParams().width  = (int)inputW;
        editW.getLayoutParams().height = (int)inputH;
        editW.requestLayout();
        // editH
        editH.setX(tx + editHRect.left);
        editH.setY(ty + editHRect.top);
        editH.getLayoutParams().width  = (int)inputW;
        editH.getLayoutParams().height = (int)inputH;
        editH.requestLayout();
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    private void handleApply() {
        if (editW!=null && editH!=null) {
            String ws=editW.getText().toString().trim();
            String hs=editH.getText().toString().trim();
            if (!ws.isEmpty()&&!hs.isEmpty()) {
                try {
                    int w=Integer.parseInt(ws), h=Integer.parseInt(hs);
                    if (w>0&&h>0&&listener!=null) { listener.onCustomSelected(w,h); hideKeyboard(); return; }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (listener!=null) listener.onPresetSelected(PRESETS[selectedPresetIdx]);
        hideKeyboard();
    }

    // ── Touch — sama strukturnya dengan FloatingWindow ────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx=event.getX(), ty=event.getY();

        // Touch di luar window → diteruskan ke bawah
        if (!winRect.contains(tx,ty)) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX=tx; touchDownY=ty;
                longPressTriggered=false; isDragging=false;
                if (titleRect.contains(tx,ty)&&!closeBtnRect.contains(tx,ty)) {
                    dragOffsetX=tx-winX; dragOffsetY=ty-winY;
                    lpHandler.postDelayed(lpRunnable,LP_MS);
                }
                closeHov=closeBtnRect.contains(tx,ty);
                applyHov=applyBtnRect.contains(tx,ty);
                hoveredPreset=hitPreset(tx,ty);
                invalidate(); return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    winX=tx-dragOffsetX; winY=ty-dragOffsetY; invalidate();
                } else {
                    float dx=tx-touchDownX, dy=ty-touchDownY;
                    if (dx*dx+dy*dy>(8*density)*(8*density)) lpHandler.removeCallbacks(lpRunnable);
                    closeHov=closeBtnRect.contains(tx,ty);
                    applyHov=applyBtnRect.contains(tx,ty);
                    hoveredPreset=hitPreset(tx,ty);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                lpHandler.removeCallbacks(lpRunnable);
                boolean wd=isDragging; isDragging=false; longPressTriggered=false;
                if (!wd) {
                    if (closeBtnRect.contains(tx,ty)) { hideWindow(); }
                    else if (applyBtnRect.contains(tx,ty)) { handleApply(); }
                    else {
                        int hit=hitPreset(tx,ty);
                        if (hit>=0) { selectedPresetIdx=hit; if(editW!=null)editW.setText(""); if(editH!=null)editH.setText(""); }
                    }
                }
                closeHov=false; applyHov=false; hoveredPreset=-1;
                invalidate(); return true;

            case MotionEvent.ACTION_CANCEL:
                lpHandler.removeCallbacks(lpRunnable);
                isDragging=false; longPressTriggered=false;
                closeHov=false; applyHov=false; hoveredPreset=-1;
                invalidate(); return true;
        }
        return false;
    }

    private int hitPreset(float x, float y) {
        for (int i=0; i<presetRects.length; i++)
            if (presetRects[i]!=null && presetRects[i].contains(x,y)) return i;
        return -1;
    }
}
