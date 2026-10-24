package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Base of every Kamishibai screen.<br/>
 * Keeps the upstream activity behavior (back handling, view stack, screensaver, locale, PIP...)
 * and only swaps the TV theme for the touch one, with a dynamic interface scale,
 * transparent system bars and no window animation between screens.
 */
public abstract class MobileActivity extends LeanbackActivity {
    // Last screen in front, weakly held: the main screen goes back to it when a video opened from it closes
    private static java.lang.ref.WeakReference<android.app.Activity> sLastResumed = new java.lang.ref.WeakReference<>(null);

    public static void setLastResumed(android.app.Activity activity) {
        sLastResumed = new java.lang.ref.WeakReference<>(activity);
    }

    public static android.app.Activity getLastResumed() {
        return sLastResumed.get();
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MobileScreen.scaled(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MobileScreen.applyBarsTheme(this);
        MobileScreen.applySystemBars(this);
        MobileScreen.edgeToEdge(this, paintsBehindBars());
        overridePendingTransition(0, 0);
    }

    /**
     * True when the screen fills the window with the app background, bars included.
     * A see-through window (menu sheet) answers false and pads its own root.
     */
    protected boolean paintsBehindBars() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setLastResumed(this);

        if (MobileScreen.needsNewScale(this)) {
            recreate(); // the scale was changed in the settings
            return;
        }

        MobileScreen.applySystemBars(this);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void finishReally() {
        super.finishReally();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Folded or unfolded: the other screen may want another scale. Start over so every size is resolved again.
        if (MobileScreen.needsNewScale(this, newConfig)) {
            recreate();
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Kamishibai_Theme);
    }

    @Override
    protected boolean isTvDensityEnabled() {
        return false; // the scale is ours (UiScale), not the 1920px based one of the TV
    }
}
