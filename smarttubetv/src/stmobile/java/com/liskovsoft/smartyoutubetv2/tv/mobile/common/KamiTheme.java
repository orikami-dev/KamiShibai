package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Interface theme chosen in the settings: the mode (system, light, dark) and the palette.<br/>
 * The mode is applied with the configuration of each screen (see {@link MobileScreen#scaled}), the palette as a
 * style over the activity theme: every color of the interface is a theme attribute (kamiInk, kamiAccent...).
 */
public final class KamiTheme {
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int[] MODES = {MODE_SYSTEM, MODE_LIGHT, MODE_DARK};

    public static final int PALETTE_WASHI = 0;
    public static final int PALETTE_SUMI = 1;
    public static final int PALETTE_AI = 2;
    public static final int PALETTE_MATCHA = 3;
    public static final int PALETTE_SAKURA = 4;
    public static final int PALETTE_KIN = 5;
    public static final int[] PALETTES = {PALETTE_WASHI, PALETTE_SUMI, PALETTE_AI, PALETTE_MATCHA, PALETTE_SAKURA, PALETTE_KIN};

    private static final int[] PALETTE_STYLES = {
            R.style.Kamishibai_Palette_Washi, R.style.Kamishibai_Palette_Sumi, R.style.Kamishibai_Palette_Ai,
            R.style.Kamishibai_Palette_Matcha, R.style.Kamishibai_Palette_Sakura, R.style.Kamishibai_Palette_Kin
    };
    private static final int[] PALETTE_NAMES = {
            R.string.kami_palette_washi, R.string.kami_palette_sumi, R.string.kami_palette_ai,
            R.string.kami_palette_matcha, R.string.kami_palette_sakura, R.string.kami_palette_kin
    };
    private static final int[] MODE_NAMES = {R.string.kami_theme_system, R.string.kami_theme_light, R.string.kami_theme_dark};

    private static final String PREFS = "kamishibai";
    private static final String KEY_MODE = "theme_mode";
    private static final String KEY_PALETTE = "theme_palette";

    private KamiTheme() {
    }

    public static int getMode(Context context) {
        return clamp(prefs(context).getInt(KEY_MODE, MODE_SYSTEM), MODES.length);
    }

    public static int getPalette(Context context) {
        return clamp(prefs(context).getInt(KEY_PALETTE, PALETTE_WASHI), PALETTES.length);
    }

    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply();
    }

    public static void setPalette(Context context, int palette) {
        prefs(context).edit().putInt(KEY_PALETTE, palette).apply();
    }

    public static String modeName(Context context, int mode) {
        return context.getString(MODE_NAMES[clamp(mode, MODES.length)]);
    }

    public static String paletteName(Context context, int palette) {
        return context.getString(PALETTE_NAMES[clamp(palette, PALETTES.length)]);
    }

    /**
     * What a screen was built with: compared in onResume, the screen is rebuilt when the theme changed meanwhile.
     */
    public static int stamp(Context context) {
        return getMode(context) * 100 + getPalette(context);
    }

    /**
     * Day or night forced by the chosen mode, on the configuration the screen is built with.
     */
    public static Configuration applyMode(Context context, Configuration config) {
        int mode = getMode(context);

        if (mode == MODE_SYSTEM) {
            return config;
        }

        int night = mode == MODE_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;

        return config;
    }

    /**
     * The palette over the theme of the activity. Call right after setTheme(), before the content is set.
     */
    public static void applyPalette(Activity activity) {
        activity.getTheme().applyStyle(PALETTE_STYLES[getPalette(activity)], true);
    }

    /**
     * A color of the palette, e.g. {@code R.attr.kamiAccent}.
     */
    public static int color(Context context, int attr) {
        TypedValue value = new TypedValue();

        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return ContextCompat.getColor(context, R.color.kami_text); // not a themed context
        }

        return value.resourceId != 0 ? ContextCompat.getColor(context, value.resourceId) : value.data;
    }

    private static int clamp(int value, int size) {
        return value >= 0 && value < size ? value : 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
