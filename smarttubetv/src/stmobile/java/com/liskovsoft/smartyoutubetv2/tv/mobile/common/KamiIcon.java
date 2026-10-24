package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * App icon in the colors of the chosen palette: one launcher entry per palette (activity-alias in the manifest),
 * only the one of the palette is enabled.<br/>
 * Switched when the app goes to the background: Android may close an app whose launcher entry changes
 * while it is on screen.
 */
public final class KamiIcon {
    private static final String TAG = "KamiIcon";
    // Same order as KamiTheme.PALETTES
    private static final String[] ALIASES = {
            "com.liskovsoft.smartyoutubetv2.tv.mobile.IconWashi", "com.liskovsoft.smartyoutubetv2.tv.mobile.IconSumi",
            "com.liskovsoft.smartyoutubetv2.tv.mobile.IconAi", "com.liskovsoft.smartyoutubetv2.tv.mobile.IconMatcha",
            "com.liskovsoft.smartyoutubetv2.tv.mobile.IconSakura", "com.liskovsoft.smartyoutubetv2.tv.mobile.IconKin"
    };

    private KamiIcon() {
    }

    public static void sync(Context context) {
        PackageManager manager = context.getPackageManager();
        int wanted = KamiTheme.getPalette(context);

        try {
            // The new entry first, so that the app always has one
            setEnabled(context, manager, ALIASES[wanted], true);

            for (int i = 0; i < ALIASES.length; i++) {
                if (i != wanted) {
                    setEnabled(context, manager, ALIASES[i], false);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Icon not switched", e);
        }
    }

    private static void setEnabled(Context context, PackageManager manager, String alias, boolean enabled) {
        ComponentName component = new ComponentName(context, alias);
        int state = manager.getComponentEnabledSetting(component);
        // The manifest enables Washi only
        boolean current = state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                ? alias.equals(ALIASES[0]) : state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

        if (current != enabled) {
            manager.setComponentEnabledSetting(component, enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
