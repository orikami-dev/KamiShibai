package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Base of every Kamishibai screen.<br/>
 * Keeps the upstream activity behavior (back handling, view stack, screensaver, locale, PIP...)
 * and only swaps the TV theme for the touch one.
 */
public abstract class MobileActivity extends LeanbackActivity {
    @Override
    protected void initTheme() {
        setTheme(R.style.Kamishibai_Theme);
    }

    @Override
    protected boolean isTvDensityEnabled() {
        return false; // real density: text and touch targets keep their physical size on any screen
    }
}
