package com.project2d.animation.canvas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.project2d.animation.ui.ImGuiTheme;

/**
 * 2D Animation Canvas — gesture fix v3
 *
 * Semua transform HANYA dua jari:
 *   Dua jari pinch   → zoom in/out (0.05x ~ 32x)
 *   Dua jari twist   → rotate bebas
 *   Dua jari move    → pan/translate canvas
 *   Semua bisa bersamaan dalam satu gesture
 *
 * Satu jari          → tidak melakukan apapun pada canvas
 * Double-tap         → reset animated (fit to screen, angle 0)
 *
 * Bug fix v3:
 *   - trySnapAngle() HANYA dipanggil saat jari KEDUA diangkat (semua jari
 *     sudah lepas), bukan saat ACTION_UP satu jari biasa
 *   - Snap hanya jika delta angle ke cardinal <= SNAP_THRESH
 *   - gestureStartAngle di-normalize agar delta rotate tidak loncat +/-180
 *   - Tidak ada pan satu jari sama sekali
 */
public class AnimationCanvasView extends View {

    // ── Preset ────────────────────────────────────────────────────────────────

    public enum CanvasPreset {
        HD_1280x720("HD 1280x720",     1280,  720),
        FHD_1920x1080("FHD 1920x1080", 1920, 1080),
        SQUARE_1080("Square 1080x1080", 1080, 1080),
        A4_PORTRAIT("A4 Portrait",      794, 1123),
        SMALL_640x480("Small 640x480",  640,  480),
        CUSTOM("Custom", 0, 0);
        public final String label;
        public int width, height;
        CanvasPreset(String l, int w, int h) { label=l; width=w; height=h; }
    }

    // ── Paints ────────────────────────────────────────────────────────────────

    private final Paint paintWS     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCanvas = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHud    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHudBg  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Document ──────────────────────────────────────────────────────────────

    private int docW = 1280, docH = 720;
    private CanvasPreset currentPreset = CanvasPreset.HD_1280x720;
    private boolean showGrid = false;

    // ── Transform matrix ──────────────────────────────────────────────────────

    private final Matrix matrix               = new Matrix();
    private final Matrix matrixAtGestureStart = new Matrix();
    private float currentScale = 1f;
    private float currentAngle = 0f;  // degrees, 0-360

    private static final float MIN_SCALE   = 0.05f;
    private static final float MAX_SCALE   = 32f;
    private static final float SNAP_THRESH = 5f;

    // ── Gesture state ─────────────────────────────────────────────────────────

    // inTwoFinger: true hanya saat >= 2 jari aktif dan gesture sudah dimulai
    private boolean inTwoFinger = false;

    // Nilai snapshot saat gesture DUA JARI dimulai
    private float startDist  = 0f;  // jarak antar jari
    private float startAngle = 0f;  // sudut antar jari (degrees)
    private float startMidX  = 0f;  // midpoint X
    private float startMidY  = 0f;  // midpoint Y

    // Double-tap
    private long  lastTapMs = 0L;
    private float lastTapX  = 0f, lastTapY = 0f;
    private static final long  DTAP_MS   = 300L;
    private static final float DTAP_SLOP = 60f;

    // HUD angle hint
    private boolean showAngleHint  = false;
    private long    angleHintUntil = 0L;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AnimationCanvasView(Context c)                { super(c); init(); }
    public AnimationCanvasView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        paintWS.setColor(ImGuiTheme.COLOR_APP_BG);         paintWS.setStyle(Paint.Style.FILL);
        paintCanvas.setColor(ImGuiTheme.COLOR_CANVAS_BG);  paintCanvas.setStyle(Paint.Style.FILL);
        paintBorder.setColor(ImGuiTheme.COLOR_CANVAS_BORDER);
        paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setStrokeWidth(1.5f);
        paintShadow.setColor(0x55000000); paintShadow.setStyle(Paint.Style.FILL);
        paintGrid.setColor(0xFFDDDDDD);   paintGrid.setStyle(Paint.Style.STROKE); paintGrid.setStrokeWidth(0.5f);
        paintHud.setColor(0xEEFFFFFF);    paintHud.setTextSize(12*d); paintHud.setAntiAlias(true);
        paintHudBg.setColor(0xBB000000);  paintHudBg.setStyle(Paint.Style.FILL);
        setClickable(true); setFocusable(true);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setCanvasPreset(CanvasPreset p) { currentPreset=p; docW=p.width; docH=p.height; fitToView(); invalidate(); }
    public void setCustomSize(int w, int h)     { currentPreset=CanvasPreset.CUSTOM; docW=w; docH=h; fitToView(); invalidate(); }
    public void setShowGrid(boolean s)          { showGrid=s; invalidate(); }
    public CanvasPreset getCurrentPreset()      { return currentPreset; }
    public int getCanvasDocW()                  { return docW; }
    public int getCanvasDocH()                  { return docH; }

    public void fitToView() {
        if (getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(), vh=getHeight();
        float s=Math.min(vw*0.85f/docW, vh*0.85f/docH);
        matrix.reset();
        matrix.postScale(s,s);
        matrix.postTranslate((vw-docW*s)/2f, (vh-docH*s)/2f);
        currentScale=s; currentAngle=0f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w,h,ow,oh);
        fitToView();
    }

    // ── Touch handling ────────────────────────────────────────────────────────
    //
    // Strategi:
    //   ACTION_DOWN         → catat untuk double-tap saja, TIDAK mulai pan
    //   ACTION_POINTER_DOWN → jika count==2, mulai dua-jari gesture
    //   ACTION_MOVE         → jika inTwoFinger, update transform
    //   ACTION_POINTER_UP   → jika jari kedua diangkat (count turun ke 1),
    //                         akhiri gesture DAN coba snap
    //   ACTION_UP           → akhiri semua state
    //   ACTION_CANCEL       → akhiri semua state

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int count  = e.getPointerCount();
        int action = e.getActionMasked();

        switch (action) {

            case MotionEvent.ACTION_DOWN:
                // Satu jari turun — hanya catat untuk double-tap
                inTwoFinger = false;
                checkDoubleTap(e.getX(0), e.getY(0));
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Jari kedua turun — mulai gesture dua jari
                if (count == 2) {
                    beginTwoFingerGesture(e);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (inTwoFinger && count >= 2) {
                    updateTwoFingerGesture(e);
                }
                // Satu jari: tidak ada pan — diabaikan
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // Salah satu jari diangkat
                if (inTwoFinger) {
                    inTwoFinger = false;
                    // Snap hanya saat gesture dua jari baru selesai
                    trySnapAngle();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                inTwoFinger = false;
                break;
        }
        return true;
    }

    // ── Two-finger gesture ────────────────────────────────────────────────────

    private void beginTwoFingerGesture(MotionEvent e) {
        inTwoFinger  = true;
        startDist    = pointerSpan(e);
        startAngle   = pointerAngle(e);
        startMidX    = pointerMidX(e);
        startMidY    = pointerMidY(e);
        // Snapshot matrix saat gesture mulai
        matrixAtGestureStart.set(matrix);
    }

    private void updateTwoFingerGesture(MotionEvent e) {
        if (!inTwoFinger || e.getPointerCount() < 2) return;

        float newDist  = pointerSpan(e);
        float newAngle = pointerAngle(e);
        float newMidX  = pointerMidX(e);
        float newMidY  = pointerMidY(e);

        if (startDist < 1f) return;

        // ── Scale delta, clamped ──────────────────────────────────────────
        float baseScale   = extractScale(matrixAtGestureStart);
        float targetScale = baseScale * (newDist / startDist);
        targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale));
        float scaleDelta  = targetScale / baseScale;

        // ── Rotate delta — normalize agar tidak loncat saat ±180 boundary ─
        float rotateDelta = normalizeAngleDelta(newAngle - startAngle);

        // ── Pan delta (midpoint bergerak) ─────────────────────────────────
        float panDx = newMidX - startMidX;
        float panDy = newMidY - startMidY;

        // ── Terapkan ke snapshot matrix ───────────────────────────────────
        // Urutan: pan dulu, lalu rotate & scale di pivot midpoint baru
        matrix.set(matrixAtGestureStart);
        matrix.postTranslate(panDx, panDy);
        matrix.postRotate(rotateDelta, newMidX, newMidY);
        matrix.postScale(scaleDelta, scaleDelta, newMidX, newMidY);

        // Update cached values
        currentScale   = targetScale;
        currentAngle   = normAngle(extractAngle(matrix));
        showAngleHint  = true;
        angleHintUntil = System.currentTimeMillis() + 1800L;

        invalidate();
    }

    // ── Snap rotation ke sudut cardinal ──────────────────────────────────────
    //
    // Dipanggil HANYA saat gesture dua jari selesai (POINTER_UP).
    // Snap hanya jika selisih ke cardinal <= SNAP_THRESH derajat.

    private void trySnapAngle() {
        float angle = normAngle(extractAngle(matrix));
        // Cardinal angles: 0, 90, 180, 270
        float[] cardinals = {0f, 90f, 180f, 270f, 360f};
        float bestDelta = Float.MAX_VALUE;
        float bestCardinal = -1f;
        for (float c : cardinals) {
            // Jarak terpendek ke cardinal (mempertimbangkan wrap 360)
            float diff = Math.abs(normalizeAngleDelta(angle - c));
            if (diff < bestDelta) {
                bestDelta    = diff;
                bestCardinal = c % 360f;
            }
        }
        if (bestCardinal >= 0 && bestDelta <= SNAP_THRESH) {
            float delta    = normalizeAngleDelta(bestCardinal - angle);
            float[] pivot  = canvasCenterInView();
            animateSnapRotation(delta, pivot[0], pivot[1]);
        }
    }

    private void animateSnapRotation(float deltaDeg, float px, float py) {
        if (Math.abs(deltaDeg) < 0.01f) {
            currentAngle = normAngle(extractAngle(matrix));
            return;
        }
        final float[] prev = {0f};
        ValueAnimator anim = ValueAnimator.ofFloat(0f, deltaDeg);
        anim.setDuration(150);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            float v    = (float) va.getAnimatedValue();
            float step = v - prev[0];
            prev[0]    = v;
            matrix.postRotate(step, px, py);
            currentAngle = normAngle(extractAngle(matrix));
            invalidate();
        });
        anim.start();
    }

    // ── Double-tap reset ──────────────────────────────────────────────────────

    private void checkDoubleTap(float x, float y) {
        long now = System.currentTimeMillis();
        float dx = x - lastTapX, dy = y - lastTapY;
        boolean sameSpot = dx*dx + dy*dy < DTAP_SLOP * DTAP_SLOP;
        if (now - lastTapMs < DTAP_MS && sameSpot) {
            animateReset();
            lastTapMs = 0;
        } else {
            lastTapMs = now;
            lastTapX  = x;
            lastTapY  = y;
        }
    }

    private void animateReset() {
        if (getWidth()==0||getHeight()==0||docW==0||docH==0) return;
        float vw=getWidth(), vh=getHeight();
        float s=Math.min(vw*0.85f/docW, vh*0.85f/docH);
        Matrix tgt=new Matrix();
        tgt.postScale(s,s);
        tgt.postTranslate((vw-docW*s)/2f, (vh-docH*s)/2f);

        float[] sv=new float[9], tv=new float[9];
        matrix.getValues(sv); tgt.getValues(tv);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            float t    = (float) va.getAnimatedValue();
            float[] iv = new float[9];
            for (int i=0; i<9; i++) iv[i] = sv[i] + (tv[i]-sv[i]) * t;
            matrix.setValues(iv);
            currentScale = sv[0] + (s - sv[0]) * t;
            currentAngle = 0f;
            invalidate();
        });
        anim.start();
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        float vw=getWidth(), vh=getHeight();
        canvas.drawRect(0,0,vw,vh,paintWS);
        if (docW==0||docH==0) return;

        canvas.save();
        canvas.concat(matrix);
        canvas.drawRect(8,8,docW+8,docH+8,paintShadow);
        canvas.drawRect(0,0,docW,docH,paintCanvas);
        if (showGrid) {
            float gs=32f;
            for (float x=0; x<=docW; x+=gs) canvas.drawLine(x,0,x,docH,paintGrid);
            for (float y=0; y<=docH; y+=gs) canvas.drawLine(0,y,docW,y,paintGrid);
        }
        canvas.drawRect(0,0,docW,docH,paintBorder);
        canvas.restore();

        drawHud(canvas, vw, vh);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHud(Canvas canvas, float vw, float vh) {
        float d = getResources().getDisplayMetrics().density;
        // Zoom % — selalu tampil pojok kanan bawah
        hudPill(canvas, String.format("%.0f%%", currentScale*100f), vw-8*d, vh-8*d, d);
        // Angle — tampil sebentar setelah rotate
        if (showAngleHint && System.currentTimeMillis() < angleHintUntil) {
            float disp = currentAngle > 180f ? currentAngle-360f : currentAngle;
            hudPill(canvas, String.format("%.1f\u00b0", disp), vw-8*d, vh-30*d, d);
            invalidate();
        }
    }

    private void hudPill(Canvas canvas, String text, float right, float bottom, float d) {
        float pad=5*d, tw=paintHud.measureText(text);
        Paint.FontMetrics fm=paintHud.getFontMetrics(); float th=-fm.ascent;
        float x=right-tw-pad*2, y=bottom-pad;
        canvas.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,paintHudBg);
        canvas.drawText(text,x,y,paintHud);
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    /** Jarak antara pointer 0 dan 1 */
    private static float pointerSpan(MotionEvent e) {
        float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
        return (float)Math.sqrt(dx*dx + dy*dy);
    }

    /** Sudut garis antara pointer 0 dan 1 (degrees) */
    private static float pointerAngle(MotionEvent e) {
        return (float)Math.toDegrees(
            Math.atan2(e.getY(0)-e.getY(1), e.getX(0)-e.getX(1)));
    }

    /** Midpoint X antara pointer 0 dan 1 */
    private static float pointerMidX(MotionEvent e) { return (e.getX(0)+e.getX(1))/2f; }

    /** Midpoint Y antara pointer 0 dan 1 */
    private static float pointerMidY(MotionEvent e) { return (e.getY(0)+e.getY(1))/2f; }

    /** Ekstrak scale dari matrix */
    private static float extractScale(Matrix m) {
        float[] v=new float[9]; m.getValues(v);
        return (float)Math.sqrt(v[0]*v[0] + v[3]*v[3]);
    }

    /** Ekstrak rotation angle (degrees) dari matrix */
    private static float extractAngle(Matrix m) {
        float[] v=new float[9]; m.getValues(v);
        return (float)Math.toDegrees(Math.atan2(v[3], v[0]));
    }

    /** Normalisasi angle ke 0–360 */
    private static float normAngle(float a) { return ((a%360f)+360f)%360f; }

    /**
     * Normalisasi delta angle ke -180 sampai +180.
     * Mencegah loncat besar saat crossing ±180 boundary.
     */
    private static float normalizeAngleDelta(float delta) {
        delta = delta % 360f;
        if (delta > 180f)  delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    /** Pusat canvas dalam koordinat view */
    private float[] canvasCenterInView() {
        float[] p = {docW/2f, docH/2f};
        matrix.mapPoints(p);
        return p;
    }
}
