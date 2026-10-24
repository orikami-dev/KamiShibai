package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Interface scale. By default it follows the screen in use: the cover screen of a foldable keeps the system size,
 * the big inner screen and tablets get slightly larger texts and touch targets.<br/>
 * The user can also pick a fixed scale in the settings.
 */
public final class UiScale {
    public static final int DYNAMIC = 0;
    public static final int[] CHOICES = {DYNAMIC, 85, 100, 115, 130};

    private static final String PREFS = "kamishibai";
    private static final String KEY_SCALE = "ui_scale_percent";
    private static final float REFERENCE_WIDTH_DP = 360f; // a regular phone
    private static final float MIN_DYNAMIC = 0.95f;
    private static final float MAX_DYNAMIC = 1.3f;

    private UiScale() {
    }

    public static int getChoice(Context context) {
        return prefs(context).getInt(KEY_SCALE, DYNAMIC);
    }

    public static void setChoice(Context context, int percent) {
        prefs(context).edit().putInt(KEY_SCALE, percent).apply();
    }

    /**
     * Scale to apply on top of the system density, for the screen described by the (system) configuration.
     */
    public static float forConfiguration(Context context, Configuration system) {
        int choice = getChoice(context);

        if (choice != DYNAMIC) {
            return choice / 100f;
        }

        float smallestWidthDp = system.smallestScreenWidthDp > 0 ? system.smallestScreenWidthDp : REFERENCE_WIDTH_DP;
        float scale = 1f + (smallestWidthDp - REFERENCE_WIDTH_DP) / REFERENCE_WIDTH_DP * 0.2f;

        return Math.max(MIN_DYNAMIC, Math.min(MAX_DYNAMIC, scale));
    }

    /**
     * The same configuration at the scaled density.
     */
    public static Configuration scale(Configuration system, float scale) {
        Configuration scaled = new Configuration(system);

        if (system.densityDpi > 0) {
            scaled.densityDpi = Math.round(system.densityDpi * scale);
        }

        return scaled;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
