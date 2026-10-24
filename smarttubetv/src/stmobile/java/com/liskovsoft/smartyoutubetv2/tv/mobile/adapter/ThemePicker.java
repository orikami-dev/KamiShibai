package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.browse.MobileBrowseFragment;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

/**
 * The Theme entry: a tap unfolds the palettes, side by side, each one a small Kamishibai screen painted with it.<br/>
 * A tap on a palette saves it and rebuilds the screen at once; the Settings tab comes back with the palettes unfolded.
 */
final class ThemePicker {
    private static boolean sOpen; // kept while the app runs, so that the palettes stay unfolded after a change

    private ThemePicker() {
    }

    static void bind(View row) {
        Context context = row.getContext();
        View scroll = row.findViewById(R.id.kami_theme_scroll);
        View chevron = row.findViewById(R.id.kami_theme_chevron);
        ((TextView) row.findViewById(R.id.kami_theme_current)).setText(KamiTheme.paletteName(context, KamiTheme.getPalette(context)));
        scroll.setVisibility(sOpen ? View.VISIBLE : View.GONE);
        chevron.setRotation(sOpen ? 180 : 0);
        row.findViewById(R.id.kami_theme_header).setOnClickListener(v -> {
            sOpen = !sOpen;
            scroll.setVisibility(sOpen ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(sOpen ? 180 : 0).setDuration(200).start();

            if (sOpen) {
                scroll.post(() -> showChosen(scroll));
            }
        });

        ViewGroup cards = row.findViewById(R.id.kami_theme_cards);
        cards.removeAllViews();
        int current = KamiTheme.getPalette(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int palette : KamiTheme.PALETTES) {
            View card = inflater.inflate(R.layout.kami_item_palette, cards, false);
            paint(card, KamiTheme.paletteContext(context, palette), palette == current);
            ((TextView) card.findViewById(R.id.kami_pal_name)).setText(KamiTheme.paletteName(context, palette));
            ((TextView) card.findViewById(R.id.kami_pal_note)).setText(KamiTheme.paletteNote(context, palette));
            card.setActivated(palette == current);
            card.setContentDescription(KamiTheme.paletteName(context, palette) + ", " + KamiTheme.paletteNote(context, palette));
            card.setOnClickListener(v -> choose(v.getContext(), palette));
            cards.addView(card);
        }

        if (sOpen) {
            scroll.post(() -> showChosen(scroll));
        }
    }

    /**
     * The chosen palette is in sight even when it is the last one.
     */
    private static void showChosen(View scroll) {
        ViewGroup cards = scroll.findViewById(R.id.kami_theme_cards);

        for (int i = 0; i < cards.getChildCount(); i++) {
            View card = cards.getChildAt(i);

            if (card.isActivated()) {
                scroll.scrollTo(Math.max(0, card.getLeft() - card.getWidth()), 0);
            }
        }
    }

    private static void paint(View card, Context palette, boolean chosen) {
        int ink = KamiTheme.color(palette, R.attr.kamiInk);
        int accent = KamiTheme.color(palette, R.attr.kamiAccent);
        int text = KamiTheme.color(palette, R.attr.kamiText);
        int secondary = KamiTheme.color(palette, R.attr.kamiTextSecondary);
        int outline = KamiTheme.color(palette, R.attr.kamiOutline);

        card.findViewById(R.id.kami_pal_frame).setBackground(chosen ? ring(card, accent) : null);
        GradientDrawable screen = round(card, ink, 12);
        screen.setStroke(WindowSize.dpToPx(card.getContext(), 1), outline);
        card.findViewById(R.id.kami_pal_screen).setBackground(screen);
        card.findViewById(R.id.kami_pal_screen).setClipToOutline(true);

        ((ImageView) card.findViewById(R.id.kami_pal_logo)).setImageDrawable(ContextCompat.getDrawable(palette, logo(palette)));
        card.findViewById(R.id.kami_pal_brand).setBackground(round(card, text, 3));
        card.findViewById(R.id.kami_pal_thumb).setBackground(round(card, KamiTheme.color(palette, R.attr.kamiSurfaceHigh), 6));
        card.findViewById(R.id.kami_pal_line1).setBackground(round(card, text, 3));
        card.findViewById(R.id.kami_pal_line2).setBackground(round(card, secondary, 3));
        card.findViewById(R.id.kami_pal_button).setBackground(round(card, accent, 6));
        card.findViewById(R.id.kami_pal_bar).setBackgroundColor(KamiTheme.color(palette, R.attr.kamiBar));
        card.findViewById(R.id.kami_pal_tab_on).setBackground(round(card, accent, 2));

        for (int id : new int[] {R.id.kami_pal_tab_a, R.id.kami_pal_tab_b, R.id.kami_pal_tab_c}) {
            card.findViewById(id).setBackground(round(card, secondary, 2));
        }

        ImageView check = card.findViewById(R.id.kami_pal_check);
        check.setVisibility(chosen ? View.VISIBLE : View.GONE);
        check.setBackground(oval(accent));
        check.setImageTintList(ColorStateList.valueOf(KamiTheme.color(palette, R.attr.kamiOnAccent)));
    }

    private static int logo(Context palette) {
        android.util.TypedValue value = new android.util.TypedValue();
        palette.getTheme().resolveAttribute(R.attr.kamiLogo, value, true);
        return value.resourceId != 0 ? value.resourceId : R.drawable.kami_logo;
    }

    private static void choose(Context context, int palette) {
        if (palette == KamiTheme.getPalette(context)) {
            return;
        }

        KamiTheme.setPalette(context, palette);
        Activity activity = activityOf(context);

        if (activity != null) {
            MobileBrowseFragment.reopenSettings();
            activity.recreate();
        }
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }

            context = ((ContextWrapper) context).getBaseContext();
        }

        return null;
    }

    private static GradientDrawable round(View view, int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(WindowSize.dpToPx(view.getContext(), radiusDp));
        return shape;
    }

    private static GradientDrawable ring(View view, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0);
        shape.setCornerRadius(WindowSize.dpToPx(view.getContext(), 15));
        shape.setStroke(WindowSize.dpToPx(view.getContext(), 2), color);
        return shape;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }
}
