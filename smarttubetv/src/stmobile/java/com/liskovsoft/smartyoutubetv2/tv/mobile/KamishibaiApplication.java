package com.liskovsoft.smartyoutubetv2.tv.mobile;

import android.app.Activity;
import android.content.SharedPreferences;

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
import com.liskovsoft.smartyoutubetv2.tv.mobile.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.dialog.MobileAppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.signin.MobileSignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;

/**
 * Kamishibai entry point.<br/>
 * The whole SmartTube logic (presenters, player, settings, accounts) is kept as is.
 * Only the views that are painful to use with a finger are swapped for touch-first ones.
 */
public class KamishibaiApplication extends MainApplication {
    private static final String PREFS = "kamishibai";
    private static final String KEY_DEFAULTS_VERSION = "defaults_version";
    private static final int DEFAULTS_VERSION = 2;

    @Override
    public void onCreate() {
        super.onCreate();

        applyPhoneDefaults();
        setupMobileViews();
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
        viewManager.register(PlaybackView.class, MobilePlaybackActivity.class, root);
        viewManager.register(SignInView.class, MobileSignInActivity.class, root);

        // Untouched upstream screens. Only their parent changes.
        viewManager.register(AppDialogView.class, MobileAppDialogActivity.class, root);
        reparent(viewManager, AddDeviceView.class, root);
        reparent(viewManager, WebBrowserView.class, root);
    }

    private static void reparent(ViewManager viewManager, Class<?> viewClass, Class<? extends Activity> parent) {
        Class<? extends Activity> activity = viewManager.getActivity(viewClass);

        if (activity != null) {
            viewManager.register(viewClass, activity, parent);
        }
    }
}
