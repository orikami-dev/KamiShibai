package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Interface theme chosen in the settings: the palette.<br/>
 * Light or dark follows the system. The palette is a style over the activity theme: every color of the interface is a theme attribute (kamiInk, kamiAccent...).
 */
public final class KamiTheme {
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
    private static final int[] PALETTE_NOTES = {
            R.string.kami_palette_washi_note, R.string.kami_palette_sumi_note, R.string.kami_palette_ai_note,
            R.string.kami_palette_matcha_note, R.string.kami_palette_sakura_note, R.string.kami_palette_kin_note
    };

    private static final String PREFS = "kamishibai";
    private static final String KEY_MODE = "theme_mode"; // light or dark forced, before v12: no longer offered
    private static final String KEY_PALETTE = "theme_palette";
    private static final String KEY_STARTING = "theme_starting";
    private static final String TAG = "KamiTheme";

    private KamiTheme() {
    }

    public static int getPalette(Context context) {
        return clamp(prefs(context).getInt(KEY_PALETTE, PALETTE_WASHI), PALETTES.length);
    }

    public static void setPalette(Context context, int palette) {
        prefs(context).edit().putInt(KEY_PALETTE, palette).remove(KEY_MODE).apply();
    }

    public static String paletteName(Context context, int palette) {
        return context.getString(PALETTE_NAMES[clamp(palette, PALETTES.length)]);
    }

    public static String paletteNote(Context context, int palette) {
        return context.getString(PALETTE_NOTES[clamp(palette, PALETTES.length)]);
    }

    /**
     * What a screen was built with: compared in onResume, the screen is rebuilt when the theme changed meanwhile.
     */
    public static int stamp(Context context) {
        return getPalette(context);
    }

    /**
     * The palette over the theme of the activity. Call right after setTheme(), before the content is set.
     */
    public static void applyPalette(Activity activity) {
        SharedPreferences prefs = prefs(activity);

        if (prefs.getBoolean(KEY_STARTING, false)) {
            // The last screen built with the theme never reached onResume: back to the default theme, so that a theme
            // can never keep the app from starting
            Log.w(TAG, "Previous start failed, theme reset (palette " + getPalette(activity) + ")");
            prefs.edit().remove(KEY_PALETTE).commit();
        }

        prefs.edit().putBoolean(KEY_STARTING, true).commit(); // written now: the process may die before onResume
        activity.getTheme().applyStyle(PALETTE_STYLES[getPalette(activity)], true);
    }

    /**
     * The screen was built with the theme. Call in onResume, before the screen may be recreated.
     */
    public static void onStarted(Context context) {
        SharedPreferences prefs = prefs(context);

        if (prefs.getBoolean(KEY_STARTING, false)) {
            prefs.edit().putBoolean(KEY_STARTING, false).apply();
        }
    }

    /**
     * A context that shows a palette other than the current one: the previews of the theme picker.
     */
    public static Context paletteContext(Context base, int palette) {
        ContextThemeWrapper context = new ContextThemeWrapper(base, R.style.Kamishibai_Theme);
        context.getTheme().applyStyle(PALETTE_STYLES[clamp(palette, PALETTES.length)], true);
        return context;
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
