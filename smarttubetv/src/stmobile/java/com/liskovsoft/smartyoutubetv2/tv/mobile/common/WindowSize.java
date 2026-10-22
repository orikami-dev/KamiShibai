package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.view.View;

/**
 * Material window size classes, computed from the real size of a view.<br/>
 * Works the same on a phone, a tablet, a foldable (folded or not) and in split screen,
 * and follows every change live (fold/unfold, rotation, window resize).
 */
public final class WindowSize {
    public static final int COMPACT = 0; // phones in portrait, folded foldables
    public static final int MEDIUM = 1; // unfolded foldables, small tablets, phones in landscape
    public static final int EXPANDED = 2; // tablets, large unfolded foldables in landscape

    private static final float MEDIUM_MIN_DP = 600;
    private static final float EXPANDED_MIN_DP = 840;
    private static final float SHORT_HEIGHT_DP = 480; // phone in landscape

    public final float widthDp;
    public final float heightDp;
    public final int widthClass;

    private WindowSize(float widthDp, float heightDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.widthClass = widthDp < MEDIUM_MIN_DP ? COMPACT : widthDp < EXPANDED_MIN_DP ? MEDIUM : EXPANDED;
    }

    public static WindowSize of(View view) {
        float density = density(view.getContext());
        return new WindowSize(view.getWidth() / density, view.getHeight() / density);
    }

    public boolean isCompact() {
        return widthClass == COMPACT;
    }

    public boolean isExpanded() {
        return widthClass == EXPANDED;
    }

    /**
     * Not enough height for both a top bar and a bottom bar (phone in landscape).
     */
    public boolean isShort() {
        return heightDp > 0 && heightDp < SHORT_HEIGHT_DP;
    }

    public boolean sameAs(WindowSize other) {
        return other != null && Math.abs(other.widthDp - widthDp) < 1 && Math.abs(other.heightDp - heightDp) < 1;
    }

    public static float density(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * density(context));
    }

    /**
     * How many cards of the given min width fit in the given width.
     */
    public static int columns(float availableWidthDp, float minCardWidthDp, int maxColumns) {
        int columns = (int) (availableWidthDp / minCardWidthDp);
        return Math.max(1, Math.min(columns, maxColumns));
    }
}
