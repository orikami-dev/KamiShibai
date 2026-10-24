package com.liskovsoft.smartyoutubetv2.tv.mobile.dialog;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * Where the next menu comes from: the button that was tapped and the block it belongs to (the video, the page
 * under it). The menu then grows out of that button and stays inside that block, like the search field does
 * in the top bar. Kept for a moment only: a menu opened later by something else is shown the usual way.
 */
public final class DialogAnchor {
    private static final long FRESH_MS = 1500;
    private static Rect sButton;
    private static Rect sBlock;
    private static long sTime;

    final Rect button;
    final Rect block;

    private DialogAnchor(Rect button, Rect block) {
        this.button = button;
        this.block = block;
    }

    /**
     * Both rectangles are kept in screen coordinates, the block cut to its part in sight.
     */
    public static void set(View button, View block) {
        if (button == null || block == null || !block.isShown()) {
            clear();
            return;
        }

        sButton = screenRect(button, false);
        sBlock = screenRect(block, true);
        sTime = SystemClock.uptimeMillis();

        if (sBlock == null || sBlock.isEmpty()) {
            clear();
        }
    }

    public static void clear() {
        sButton = null;
        sBlock = null;
    }

    /**
     * The anchor of the menu being opened, if a button set one just before. Used once.
     */
    static DialogAnchor take() {
        DialogAnchor anchor = sButton != null && SystemClock.uptimeMillis() - sTime < FRESH_MS ? new DialogAnchor(sButton, sBlock) : null;
        clear();
        return anchor;
    }

    private static Rect screenRect(View view, boolean visiblePart) {
        int[] screen = new int[2];
        view.getLocationOnScreen(screen);

        if (!visiblePart) {
            return new Rect(screen[0], screen[1], screen[0] + view.getWidth(), screen[1] + view.getHeight());
        }

        Rect visible = new Rect();

        if (!view.getGlobalVisibleRect(visible)) {
            return null;
        }

        // Window coordinates to screen coordinates
        int[] window = new int[2];
        view.getLocationInWindow(window);
        visible.offset(screen[0] - window[0], screen[1] - window[1]);
        return visible;
    }
}
