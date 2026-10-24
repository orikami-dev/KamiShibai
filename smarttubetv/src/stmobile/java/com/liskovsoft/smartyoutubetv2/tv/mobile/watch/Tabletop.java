package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

/**
 * Foldables half open with the hinge across the screen (on a table, at any angle the device reports as half open):
 * the watch panel shows the video above the hinge and the controls under it while in fullscreen.
 * Unfolded flat, closed, or with the hinge upright, the hinge is gone and the panel goes back to its usual fullscreen.
 */
public final class Tabletop {
    private final Activity mActivity;
    private final WatchPanel mPanel;
    private final View mRoot;
    private final Consumer<WindowLayoutInfo> mListener = this::onLayoutInfo;
    private WindowInfoTrackerCallbackAdapter mTracker;
    private WindowLayoutInfo mLastInfo;

    public Tabletop(Activity activity, WatchPanel panel, View root) {
        mActivity = activity;
        mPanel = panel;
        mRoot = root;
        // The hinge in root coordinates moves with the window (multi-window, cutout): computed again on layout
        mRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (t != ot || b != ob) {
                v.post(() -> onLayoutInfo(mLastInfo));
            }
        });
    }

    public void start() {
        try {
            if (mTracker == null) {
                mTracker = new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(mActivity));
            }

            mTracker.addWindowLayoutInfoListener(mActivity, ContextCompat.getMainExecutor(mActivity), mListener);
        } catch (RuntimeException | LinkageError e) {
            // No window extensions on this device: never a hinge
            mTracker = null;
        }
    }

    public void stop() {
        if (mTracker != null) {
            mTracker.removeWindowLayoutInfoListener(mListener);
        }
    }

    private void onLayoutInfo(WindowLayoutInfo info) {
        mLastInfo = info;
        int top = -1;
        int bottom = -1;

        if (info != null) {
            for (DisplayFeature feature : info.getDisplayFeatures()) {
                if (!(feature instanceof FoldingFeature)) {
                    continue;
                }

                FoldingFeature fold = (FoldingFeature) feature;

                if (fold.getState() == FoldingFeature.State.HALF_OPENED && fold.getOrientation() == FoldingFeature.Orientation.HORIZONTAL) {
                    // Window coordinates to the coordinates of the watch panel
                    Rect bounds = fold.getBounds();
                    int[] window = new int[2];
                    mRoot.getLocationInWindow(window);
                    top = bounds.top - window[1];
                    bottom = bounds.bottom - window[1];
                }
            }
        }

        // Both halves must be usable
        int min = Math.round(120 * mActivity.getResources().getDisplayMetrics().density);

        if (top < min || mRoot.getHeight() - bottom < min) {
            top = bottom = -1;
        }

        mPanel.setTabletopHinge(top, bottom);
    }
}
