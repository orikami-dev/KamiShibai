package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.WindowInsetsControllerCompat;

/**
 * What every Kamishibai activity does the same way: interface scale, transparent system bars.
 * Shared by {@link MobileActivity} and the watch page, which has to extend the upstream player activity.
 */
public final class MobileScreen {
    private MobileScreen() {
    }

    /**
     * Context at the interface scale of the screen in use.
     */
    public static Context scaled(Context base) {
        if (base == null) {
            return null;
        }

        Configuration system = base.getResources().getConfiguration();

        return base.createConfigurationContext(UiScale.scale(system, UiScale.forConfiguration(base, system)));
    }

    /**
     * True when the other screen (fold/unfold) needs another scale than the one the activity was created with.
     * The configuration is the system one, as received in onConfigurationChanged.
     */
    public static boolean needsNewScale(Context context, Configuration system) {
        float current = context.getResources().getConfiguration().densityDpi / (float) Math.max(1, system.densityDpi);

        return Math.abs(UiScale.forConfiguration(context, system) - current) > 0.02f;
    }

    /**
     * Same check at any time, e.g. when coming back from the settings where the scale may have been changed.
     */
    public static boolean needsNewScale(Context context) {
        return needsNewScale(context, android.content.res.Resources.getSystem().getConfiguration());
    }

    /**
     * Status and navigation bars let the app show through, with icons that stay readable in day and night.
     */
    @SuppressWarnings("deprecation")
    public static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        View decor = window.getDecorView();
        boolean night = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }
}
