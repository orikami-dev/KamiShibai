package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

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

        return base.createConfigurationContext(KamiTheme.applyMode(base, UiScale.scale(system, UiScale.forConfiguration(base, system))));
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
     * The upstream activity applies a theme that makes both bars translucent (a tint over the app).
     * Call right after super.onCreate, before the content is set.
     */
    public static void applyBarsTheme(Activity activity) {
        activity.getTheme().applyStyle(R.style.Kamishibai_Bars, true);
    }

    /**
     * The screen draws under the status and navigation bars, in its own color.<br/>
     * The window stops padding its content (the padded strips showed the black window background):
     * the content frame gets the app background and pads itself by the bars, the notch and the keyboard,
     * so every layout keeps working unchanged while the strips take the color of the page.
     *
     * @param paint false for a see-through window (the sheet of a menu), which pads its own root
     */
    public static void edgeToEdge(Activity activity, boolean paint) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        if (!paint) {
            return;
        }

        View content = window.findViewById(android.R.id.content);

        if (content != null) {
            content.setBackgroundColor(KamiTheme.color(activity, R.attr.kamiInk));
            padForBars(content, true);
        }
    }

    /**
     * Keeps the view's content clear of the bars and the notch, and of the keyboard when asked.
     */
    public static void padForBars(View view, boolean withKeyboard) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int bottom = bars.bottom;

            if (withKeyboard) {
                bottom = Math.max(bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }

            v.setPadding(bars.left, bars.top, bars.right, bottom);

            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Hides both bars until the user swipes from an edge: for a sheet shown over the fullscreen video.
     */
    public static void hideSystemBars(Activity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
    }

    /**
     * Status and navigation bars let the app show through, with icons that stay readable in day and night.
     */
    @SuppressWarnings("deprecation")
    public static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
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
