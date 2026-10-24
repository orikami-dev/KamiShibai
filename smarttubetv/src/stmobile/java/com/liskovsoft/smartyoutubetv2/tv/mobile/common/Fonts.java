package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Font of the name of the app in the top bar: Oswald Medium (SIL OFL, see kamishibai/licenses),
 * a free condensed sans. The rest of the app keeps the system font.
 */
public final class Fonts {
    private static Typeface sBrand;

    private Fonts() {
    }

    public static Typeface brand(Context context) {
        if (sBrand == null) {
            try {
                sBrand = ResourcesCompat.getFont(context.getApplicationContext(), R.font.kami_brand);
            } catch (RuntimeException e) {
                // Font missing or unreadable
            }

            if (sBrand == null) {
                sBrand = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            }
        }

        return sBrand;
    }

    public static void applyBrand(TextView view) {
        if (view != null) {
            view.setTypeface(brand(view.getContext()));
        }
    }
}
