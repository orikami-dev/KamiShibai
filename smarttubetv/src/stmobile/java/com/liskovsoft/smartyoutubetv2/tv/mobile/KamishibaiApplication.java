package com.liskovsoft.smartyoutubetv2.tv.mobile;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adddevice.MobileAddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiIcon;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiLinkActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.logs.KamiLogs;
import com.liskovsoft.smartyoutubetv2.tv.mobile.dialog.MobileAppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.signin.MobileSignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;

/**
 * Kamishibai entry point.<br/>
 * The whole SmartTube logic (presenters, player, settings, accounts) is kept as is.
 * Only the views that are painful to use with a finger are swapped for touch-first ones.
 */
public class KamishibaiApplication extends MainApplication {
    private static final String TAG = "Kamishibai";
    private static final String TV_UI_PACKAGE = "com.liskovsoft.smartyoutubetv2.tv.ui.";
    private static final String MOBILE_PACKAGE = "com.liskovsoft.smartyoutubetv2.tv.mobile.";
    private static final String PREFS = "kamishibai";
    private static final String KEY_DEFAULTS_VERSION = "defaults_version";
    private static final int DEFAULTS_VERSION = 2;

    @Override
    public void onCreate() {
        super.onCreate();

        KamiLogs.installCrashHandler(this);
        applyPhoneDefaults();
        setupMobileViews();
        MainUISettingsPresenter.sColorSchemesShown = false; // replaced by the palettes of Settings > Theme
        registerActivityLifecycleCallbacks(new IconSwitcher());
        registerActivityLifecycleCallbacks(new TvScreenGuard());
    }

    /**
     * Safety net: no SmartTube TV screen is ever shown. They are made for a remote and leave a phone stuck.
     * If one still starts (an upstream path we don't know yet), it closes at once and Home opens instead.
     * The splash screen stays: it is the launcher entry and it only routes to Home.
     */
    private static final class TvScreenGuard implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            String name = activity.getClass().getName();

            if (!name.startsWith(TV_UI_PACKAGE) || name.endsWith(".SplashActivity")) {
                return;
            }

            Log.w(TAG, "TV screen blocked: " + activity.getClass().getSimpleName());
            KamiLogs.note("TV screen blocked: " + activity.getClass().getSimpleName());
            Intent home = new Intent(activity, MobileBrowseActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(home);
            activity.finish();
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }

    /**
     * The app icon follows the palette once no screen of the app is visible (see {@link KamiIcon}).
     */
    private static final class IconSwitcher implements Application.ActivityLifecycleCallbacks {
        private int mStarted;

        @Override
        public void onActivityStarted(Activity activity) {
            mStarted++;
        }

        @Override
        public void onActivityStopped(Activity activity) {
            mStarted = Math.max(0, mStarted - 1);

            if (mStarted == 0 && !activity.isChangingConfigurations()) {
                KamiIcon.sync(activity.getApplicationContext());
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }

    /**
     * Upstream defaults suit a TV. On a phone the status and navigation bars stay visible:
     * only the player goes fullscreen, on demand. Applied once, the user can still change it in the settings.
     */
    private void applyPhoneDefaults() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        int version = prefs.getInt(KEY_DEFAULTS_VERSION, 0);

        if (version >= DEFAULTS_VERSION) {
            return;
        }

        if (version < 1) {
            GeneralData.instance(this).setFullscreenModeEnabled(false);
        }

        if (version < 2 && Helpers.isPictureInPictureSupported(this)) {
            // Leaving the app while a video plays keeps it in a small window
            PlayerData.instance(this).setBackgroundMode(PlayerConstants.BACKGROUND_MODE_PIP);
            GeneralData.instance(this).setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
        }

        prefs.edit().putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION).apply();
    }

    private void setupMobileViews() {
        ViewManager viewManager = ViewManager.instance(this);
        Class<? extends Activity> root = MobileBrowseActivity.class;

        viewManager.setRoot(root);
        viewManager.register(BrowseView.class, root); // no parent, because it's root activity
        viewManager.register(SearchView.class, MobileSearchActivity.class, root);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, root);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, root);
        // The player is a panel of the main screen, see MobileBrowseActivity (a ViewManager.ViewHost)
        viewManager.register(PlaybackView.class, root);
        viewManager.setViewHost(root, MobileBrowseActivity.EARLY_HOST);
        viewManager.register(SignInView.class, MobileSignInActivity.class, root);

        viewManager.register(AppDialogView.class, MobileAppDialogActivity.class, root);
        viewManager.register(AddDeviceView.class, MobileAddDeviceActivity.class, root);
        // Links open in the browser of the phone, not in the TV web page screen
        viewManager.register(WebBrowserView.class, KamiLinkActivity.class);

        forgetTvParents(viewManager);
    }

    /**
     * The upstream setup also gave every TV screen its parent, and those links stay in the ViewManager.
     * Home extends the TV player screen, so "the parent of the player" (the TV Home) was taken for its parent too:
     * back from Home, or leaving the app, opened the SmartTube TV interface. Only our own links are kept.
     */
    @SuppressWarnings("unchecked")
    private static void forgetTvParents(ViewManager viewManager) {
        try {
            Field field = ViewManager.class.getDeclaredField("mParentMapping");
            field.setAccessible(true);
            Map<Class<?>, Class<?>> parents = (Map<Class<?>, Class<?>>) field.get(viewManager);
            Iterator<Class<?>> iterator = parents.keySet().iterator();

            while (iterator.hasNext()) {
                if (!iterator.next().getName().startsWith(MOBILE_PACKAGE)) {
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            // TvScreenGuard still closes a TV screen that would open
            Log.e(TAG, "Can't clear the TV parents: " + e);
        }
    }
}
