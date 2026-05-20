package com.project2d.animation.drawing;

import android.graphics.Color;

/**
 * Model data pengaturan Onion Skin.
 * Disimpan global per-project, bisa diakses dari mana saja.
 */
public class OnionSkinSettings {

    // Aktif/tidak global
    private boolean enabled = false;

    // Berapa frame ke belakang yang ditampilkan (1-5)
    private int prevFrames = 2;

    // Berapa frame ke depan yang ditampilkan (1-5)
    private int nextFrames = 1;

    // Warna onion skin frame sebelumnya (merah default seperti TVPaint)
    private int prevColor = Color.parseColor("#FFFF3333");

    // Warna onion skin frame berikutnya (biru/hijau default)
    private int nextColor = Color.parseColor("#FF3388FF");

    // Opacity maksimum frame terdekat (0.0 - 1.0)
    private float maxOpacity = 0.4f;

    // Apakah opacity berkurang semakin jauh (fade)
    private boolean fadeOpacity = true;

    // Per-layer: apakah layer ini tampil di onion skin
    // Index = layer index, value = enabled
    private final boolean[] layerEnabled = new boolean[20];

    public OnionSkinSettings() {
        // Semua layer aktif secara default
        for (int i = 0; i < layerEnabled.length; i++) layerEnabled[i] = true;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public boolean isEnabled()          { return enabled; }
    public void    setEnabled(boolean b){ enabled = b; }

    public int  getPrevFrames()         { return prevFrames; }
    public void setPrevFrames(int n)    { prevFrames = Math.max(1, Math.min(5, n)); }

    public int  getNextFrames()         { return nextFrames; }
    public void setNextFrames(int n)    { nextFrames = Math.max(1, Math.min(5, n)); }

    public int  getPrevColor()          { return prevColor; }
    public void setPrevColor(int c)     { prevColor = c; }

    public int  getNextColor()          { return nextColor; }
    public void setNextColor(int c)     { nextColor = c; }

    public float getMaxOpacity()        { return maxOpacity; }
    public void  setMaxOpacity(float o) { maxOpacity = Math.max(0.05f, Math.min(1f, o)); }

    public boolean isFadeOpacity()       { return fadeOpacity; }
    public void    setFadeOpacity(boolean f){ fadeOpacity = f; }

    public boolean isLayerEnabled(int layerIdx) {
        if (layerIdx < 0 || layerIdx >= layerEnabled.length) return true;
        return layerEnabled[layerIdx];
    }

    public void setLayerEnabled(int layerIdx, boolean b) {
        if (layerIdx >= 0 && layerIdx < layerEnabled.length) layerEnabled[layerIdx] = b;
    }

    public void toggleLayerEnabled(int layerIdx) {
        setLayerEnabled(layerIdx, !isLayerEnabled(layerIdx));
    }

    /**
     * Hitung opacity untuk frame ke offset dari frame aktif.
     * offset = -1 → satu frame sebelumnya, +1 → satu frame berikutnya
     */
    public float getOpacityForOffset(int offset) {
        if (!enabled) return 0f;
        int absOffset = Math.abs(offset);
        if (absOffset == 0) return 0f;
        int maxFrames = offset < 0 ? prevFrames : nextFrames;
        if (absOffset > maxFrames) return 0f;
        if (!fadeOpacity) return maxOpacity;
        // Fade: frame terdekat paling terang, makin jauh makin transparan
        float ratio = 1f - (float)(absOffset - 1) / Math.max(1, maxFrames);
        return maxOpacity * ratio;
    }
}
