package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.project2d.animation.ui.ImGuiTheme;

public class ProgressWindow extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF windowRect = new RectF();
    private final RectF progressRect = new RectF();

    private float density;
    private float progress = 0f;
    private String title = "Processing";
    private String status = "Please wait...";

    public ProgressWindow(Context context) {
        super(context);
        init();
    }

    public ProgressWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        bgPaint.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BG);
        bgPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x44000000);
        shadowPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);
        titlePaint.setColor(ImGuiTheme.COLOR_TEXT);
        titlePaint.setTextSize(13 * density);
        titlePaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        titlePaint.setFakeBoldText(true);
        textPaint.setColor(ImGuiTheme.COLOR_TEXT);
        textPaint.setTextSize(12 * density);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        progressBgPaint.setColor(0xFF2C2C3C);
        progressBgPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);
        progressPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
        setVisibility(GONE);
    }

    public void showWindow() {
        setVisibility(VISIBLE);
        bringToFront();
        invalidate();
    }

    public void hideWindow() {
        setVisibility(GONE);
    }

    public void setTitleText(String text) {
        title = text == null ? "Processing" : text;
        invalidate();
    }

    public void setStatusText(String text) {
        status = text == null ? "Please wait..." : text;
        invalidate();
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float winW = 300 * density;
        float winH = 128 * density;
        float left = (w - winW) / 2f;
        float top = (h - winH) / 2f;
        float r = ImGuiTheme.BORDER_RADIUS;

        windowRect.set(left, top, left + winW, top + winH);
        canvas.drawRoundRect(new RectF(left + 5 * density, top + 5 * density, left + winW + 5 * density, top + winH + 5 * density), r, r, shadowPaint);
        canvas.drawRoundRect(windowRect, r, r, bgPaint);
        canvas.drawRoundRect(windowRect, r, r, borderPaint);

        float pad = 16 * density;
        float barTop = top + 66 * density;
        float barHeight = 16 * density;
        float barWidth = winW - pad * 2f;
        progressRect.set(left + pad, barTop, left + pad + barWidth, barTop + barHeight);

        canvas.drawRoundRect(progressRect, 8 * density, 8 * density, progressBgPaint);
        float fillWidth = barWidth * progress;
        if (fillWidth > 0f) {
            canvas.drawRoundRect(new RectF(progressRect.left, progressRect.top, progressRect.left + fillWidth, progressRect.bottom), 8 * density, 8 * density, progressPaint);
        }

        Paint.FontMetrics titleFm = titlePaint.getFontMetrics();
        canvas.drawText(title, left + pad, top + 28 * density - titleFm.ascent, titlePaint);

        Paint.FontMetrics textFm = textPaint.getFontMetrics();
        canvas.drawText(status, left + pad, top + 52 * density - textFm.ascent, textPaint);

        String percent = String.format(java.util.Locale.US, "%d%%", Math.round(progress * 100f));
        canvas.drawText(percent, left + winW - pad - textPaint.measureText(percent), top + 52 * density - textFm.ascent, textPaint);
    }
}
