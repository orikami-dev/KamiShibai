package com.liskovsoft.smartyoutubetv2.tv.mobile;

import android.app.Activity;

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
import com.liskovsoft.smartyoutubetv2.tv.mobile.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;

/**
 * Kamishibai entry point.<br/>
 * The whole SmartTube logic (presenters, player, settings, accounts) is kept as is.
 * Only the views that are painful to use with a finger are swapped for touch-first ones.
 */
public class KamishibaiApplication extends MainApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        setupMobileViews();
    }

    private void setupMobileViews() {
        ViewManager viewManager = ViewManager.instance(this);
        Class<? extends Activity> root = MobileBrowseActivity.class;

        viewManager.setRoot(root);
        viewManager.register(BrowseView.class, root); // no parent, because it's root activity
        viewManager.register(SearchView.class, MobileSearchActivity.class, root);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, root);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, root);

        // Untouched upstream screens. Only their parent changes.
        reparent(viewManager, PlaybackView.class, root);
        reparent(viewManager, AppDialogView.class, root);
        reparent(viewManager, SignInView.class, root);
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
