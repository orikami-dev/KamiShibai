package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * ORIKAMI fonts, the same as Origami (SIL OFL, see kamishibai/licenses):
 * Black Ops One for the name of the app, Michroma for the small wide labels.
 */
public final class Fonts {
    private static Typeface sDisplay;
    private static Typeface sWide;

    private Fonts() {
    }

    public static Typeface display(Context context) {
        if (sDisplay == null) {
            sDisplay = load(context, R.font.kami_display, Typeface.DEFAULT_BOLD);
        }

        return sDisplay;
    }

    public static Typeface wide(Context context) {
        if (sWide == null) {
            sWide = load(context, R.font.kami_wide, Typeface.DEFAULT);
        }

        return sWide;
    }

    public static void applyDisplay(TextView view) {
        if (view != null) {
            view.setTypeface(display(view.getContext()));
        }
    }

    public static void applyWide(TextView view) {
        if (view != null) {
            view.setTypeface(wide(view.getContext()));
        }
    }

    private static Typeface load(Context context, int fontId, Typeface fallback) {
        try {
            Typeface font = ResourcesCompat.getFont(context.getApplicationContext(), fontId);
            return font != null ? font : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
