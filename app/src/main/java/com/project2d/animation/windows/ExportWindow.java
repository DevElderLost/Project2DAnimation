package com.project2d.animation.windows;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.project2d.animation.ui.ImGuiTheme;

public class ExportWindow extends View {
    public enum ExportTarget { PNG_SINGLE, PNG_ZIP, VIDEO_MP4, VIDEO_MKV }
    public enum VideoCodec { H264, H265 }

    public interface OnExportRequestedListener {
        void onExportRequested(ExportTarget target, VideoCodec codec);
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonHoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF windowRect = new RectF();
    private final RectF titleRect = new RectF();
    private final RectF closeRect = new RectF();
    private final RectF[] optionRects = new RectF[4];
    private final RectF[] codecRects = new RectF[2];
    private final RectF applyRect = new RectF();

    private float density;
    private float winX = 60f;
    private float winY = 100f;
    private float winW;
    private float winH;

    private ExportTarget selectedTarget = ExportTarget.PNG_SINGLE;
    private VideoCodec selectedCodec = VideoCodec.H264;
    private boolean closeHover = false;
    private int hoveredOption = -1;
    private boolean applyHover = false;
    private int hoveredCodec = -1;

    private boolean showH265 = true;
    private OnExportRequestedListener listener;

    public ExportWindow(Context context) {
        super(context);
        init();
    }

    public ExportWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        winW = 300 * density;
        winH = 280 * density;

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
        textPaint.setTextSize(11 * density);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        buttonPaint.setColor(ImGuiTheme.COLOR_BUTTON);
        buttonPaint.setStyle(Paint.Style.FILL);
        buttonHoverPaint.setColor(ImGuiTheme.COLOR_BUTTON_HOVERED);
        buttonHoverPaint.setStyle(Paint.Style.FILL);
        buttonActivePaint.setColor(0xFF2A5A3A);
        buttonActivePaint.setStyle(Paint.Style.FILL);
        buttonTextPaint.setColor(ImGuiTheme.COLOR_BUTTON_TEXT);
        buttonTextPaint.setTextSize(11 * density);
        buttonTextPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        buttonTextPaint.setFakeBoldText(true);
        separatorPaint.setColor(ImGuiTheme.COLOR_FLOAT_WIN_BORDER);
        separatorPaint.setStyle(Paint.Style.STROKE);
        separatorPaint.setStrokeWidth(1f);
        for (int i = 0; i < optionRects.length; i++) optionRects[i] = new RectF();
        for (int i = 0; i < codecRects.length; i++) codecRects[i] = new RectF();
        setClickable(true);
        setVisibility(GONE);
    }

    public void setOnExportRequestedListener(OnExportRequestedListener listener) {
        this.listener = listener;
    }

    public void showWindow() {
        setVisibility(VISIBLE);
        bringToFront();
        invalidate();
    }

    public void hideWindow() {
        setVisibility(GONE);
    }

    public void setPosition(float x, float y) {
        winX = x;
        winY = y;
        invalidate();
    }

    public void setH265Supported(boolean supported) {
        showH265 = supported;
        if (!supported && selectedCodec == VideoCodec.H265) {
            selectedCodec = VideoCodec.H264;
        }
        invalidate();
    }

    public void setSelectedTarget(ExportTarget target) {
        selectedTarget = target;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = ImGuiTheme.BORDER_RADIUS;
        float pad = 12 * density;
        float titleHeight = ImGuiTheme.TITLE_BAR_HEIGHT_DP * density;
        float rowHeight = 32 * density;
        float codecHeight = 28 * density;

        winW = 300 * density;
        winH = titleHeight + pad + rowHeight * 4 + pad + codecHeight + pad + 34 * density + pad;

        winX = Math.max(0, Math.min(winX, getWidth() - winW));
        winY = Math.max(0, Math.min(winY, getHeight() - winH));
        windowRect.set(winX, winY, winX + winW, winY + winH);

        canvas.drawRoundRect(new RectF(winX + 5 * density, winY + 5 * density, winX + winW + 5 * density, winY + winH + 5 * density), r, r, shadowPaint);
        canvas.drawRoundRect(windowRect, r, r, bgPaint);
        canvas.drawRoundRect(windowRect, r, r, borderPaint);

        titleRect.set(winX, winY, winX + winW, winY + titleHeight);
        canvas.drawRoundRect(new RectF(winX, winY, winX + winW, winY + titleHeight + r), r, r, ImGuiTheme.COLOR_FLOAT_WIN_TITLE);

        Paint.FontMetrics titleFm = titlePaint.getFontMetrics();
        canvas.drawText("Export", winX + pad, titleRect.centerY() - (titleFm.ascent + titleFm.descent) / 2f, titlePaint);

        float closeSize = titleHeight * 0.65f;
        closeRect.set(winX + winW - closeSize - 5 * density, winY + (titleHeight - closeSize) / 2f, winX + winW - 5 * density, winY + (titleHeight + closeSize) / 2f);
        if (closeHover) {
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(0xFFEE6666);
            cp.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(closeRect, 2, 2, cp);
        }
        Paint xPaint = new Paint(titlePaint);
        xPaint.setColor(closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);
        xPaint.setTextSize(12 * density);
        Paint.FontMetrics xFm = xPaint.getFontMetrics();
        canvas.drawText("x", closeRect.centerX() - xPaint.measureText("x") / 2f, closeRect.centerY() - (xFm.ascent + xFm.descent) / 2f, xPaint);

        float y = winY + titleHeight + pad;
        String[] labels = new String[]{"Current PNG", "PNG Zip", "MP4", "MKV"};
        for (int i = 0; i < optionRects.length; i++) {
            optionRects[i].set(winX + pad, y, winX + winW - pad, y + rowHeight);
            Paint p = selectedTarget == ExportTarget.values()[i] ? buttonActivePaint : (hoveredOption == i ? buttonHoverPaint : buttonPaint);
            canvas.drawRoundRect(optionRects[i], 4, 4, p);
            drawButtonText(canvas, labels[i], optionRects[i], buttonTextPaint);
            y += rowHeight + 6 * density;
        }

        float codecY = y + 4 * density;
        canvas.drawLine(winX + pad, codecY, winX + winW - pad, codecY, separatorPaint);
        codecY += 10 * density;
        canvas.drawText("Video codec", winX + pad, codecY + 12 * density, textPaint);
        codecY += 16 * density;

        for (int i = 0; i < codecRects.length; i++) {
            if (!showH265 && i == 1) continue;
            codecRects[i].set(winX + pad + (i * (110 * density)), codecY, winX + pad + (i * (110 * density)) + 100 * density, codecY + codecHeight);
            Paint p = selectedCodec == VideoCodec.values()[i] ? buttonActivePaint : (hoveredCodec == i ? buttonHoverPaint : buttonPaint);
            canvas.drawRoundRect(codecRects[i], 4, 4, p);
            drawButtonText(canvas, VideoCodec.values()[i].name(), codecRects[i], buttonTextPaint);
        }

        float bottomY = winY + winH - 42 * density;
        applyRect.set(winX + pad, bottomY, winX + winW - pad, bottomY + 28 * density);
        canvas.drawRoundRect(applyRect, 4, 4, applyHover ? buttonHoverPaint : buttonPaint);
        drawButtonText(canvas, "Export", applyRect, buttonTextPaint);
    }

    private void drawButtonText(Canvas canvas, String text, RectF rect, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, rect.centerX() - paint.measureText(text) / 2f, rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();
        if (!windowRect.contains(tx, ty)) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                closeHover = closeRect.contains(tx, ty);
                hoveredOption = -1;
                for (int i = 0; i < optionRects.length; i++) {
                    if (optionRects[i].contains(tx, ty)) {
                        hoveredOption = i;
                        break;
                    }
                }
                hoveredCodec = -1;
                for (int i = 0; i < codecRects.length; i++) {
                    if (codecRects[i].contains(tx, ty)) {
                        hoveredCodec = i;
                        break;
                    }
                }
                applyHover = applyRect.contains(tx, ty);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                boolean clickedApply = applyRect.contains(tx, ty);
                closeHover = false;
                if (closeRect.contains(tx, ty)) {
                    hideWindow();
                } else if (hoveredOption >= 0 && optionRects[hoveredOption].contains(tx, ty)) {
                    selectedTarget = ExportTarget.values()[hoveredOption];
                } else if (hoveredCodec >= 0 && codecRects[hoveredCodec].contains(tx, ty)) {
                    selectedCodec = VideoCodec.values()[hoveredCodec];
                } else if (clickedApply && listener != null) {
                    listener.onExportRequested(selectedTarget, selectedCodec);
                    hideWindow();
                }
                hoveredOption = -1;
                hoveredCodec = -1;
                applyHover = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                closeHover = false;
                hoveredOption = -1;
                hoveredCodec = -1;
                applyHover = false;
                invalidate();
                return true;
        }
        return false;
    }
}
