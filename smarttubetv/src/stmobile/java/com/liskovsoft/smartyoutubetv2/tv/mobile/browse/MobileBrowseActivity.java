package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiUpdate;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileScreen;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.MobilePlaybackFragment;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.Tabletop;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.WatchController;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.WatchPanel;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.DoubleBackManager2;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * The one screen of Kamishibai: Home (tabs, search, Explore) with the watch page as a panel over it.<br/>
 * Picking a video makes the panel rise over the feed, back makes it go down and Home is back as it was.
 * The activity extends the upstream player activity because the upstream player fragment needs it (PIP, key
 * handling, background modes); it answers the screen stack as {@link BrowseView} or {@link PlaybackView}
 * depending on what is shown (see {@link ViewManager.ViewHost}).
 */
public class MobileBrowseActivity extends PlaybackActivity implements MobilePlaybackFragment.Host, ViewManager.ViewHost {
    private static final String TAG = "KamiBrowse";
    // Right after the panel closed, the video it showed isn't opened again unless picked again
    private static final long REOPEN_GUARD_MS = 1_500;
    // View asked before the screen exists (a link opened from another app): shown once it's created
    private static Class<?> sPendingView;

    /**
     * Stands for the screen while it doesn't exist, so a video asked meanwhile opens in the panel once it's there.
     */
    public static final ViewManager.ViewHost EARLY_HOST = new ViewManager.ViewHost() {
        @Override
        public Class<?> getCurrentView() {
            return null;
        }

        @Override
        public boolean showView(Class<?> viewClass) {
            sPendingView = viewClass;
            return false;
        }
    };

    private MobileBrowseFragment mBrowse;
    private View mBrowseView;
    private MobilePlaybackFragment mPlayer;
    private WatchPanel mPanel;
    private Tabletop mTabletop;
    private DoubleBackManager2 mDoubleBack;
    private boolean mPlayerShown;
    private boolean mResumed;
    private Class<?> mPendingView;
    // The screen the video was opened from (a channel...), shown again when the video closes
    private Class<? extends android.app.Activity> mReturnTo;
    private boolean mClosingApp;
    private int mThemeStamp = -1; // theme the screen was built with
    private long mClosedAtMs;
    private String mClosedVideoId;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MobileScreen.scaled(base));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // inflates fragment_playback: Home and the watch panel

        mDoubleBack = new DoubleBackManager2(this);
        MobileScreen.applySystemBars(this);
        initEdgeToEdge();
        overridePendingTransition(0, 0);

        Fragment browse = getSupportFragmentManager().findFragmentById(R.id.kami_browse_fragment);
        mBrowse = browse instanceof MobileBrowseFragment ? (MobileBrowseFragment) browse : null;
        mBrowseView = findViewById(R.id.kami_browse_fragment);
        Fragment player = getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        mPlayer = player instanceof MobilePlaybackFragment ? (MobilePlaybackFragment) player : null;

        PlaybackPresenter presenter = PlaybackPresenter.instance(this);
        WatchController controller = WatchController.install(presenter);
        mPanel = new WatchPanel(this, findViewById(R.id.kami_watch_root), mPlayer, presenter, controller);
        mTabletop = new Tabletop(this, mPanel, findViewById(R.id.kami_watch_root));

        ViewManager.instance(this).setViewHost(MobileBrowseActivity.class, this);
        mPendingView = sPendingView;
        sPendingView = null;
        KamiUpdate.checkOnStart(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mTabletop.start();
    }

    @Override
    protected void onStop() {
        mTabletop.stop();
        super.onStop();
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(resid);

        if (resid == com.liskovsoft.smartyoutubetv2.common.R.style.FitSystemWindows) {
            // Right after the upstream style: our bars stay transparent and the window doesn't pad itself
            MobileScreen.applyBarsTheme(this);
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Kamishibai_Theme);
        KamiTheme.applyPalette(this);
        mThemeStamp = KamiTheme.stamp(this);
    }

    @Override
    protected boolean isTvDensityEnabled() {
        return false; // the scale is ours (UiScale), not the 1920px based one of the TV
    }

    /**
     * The content frame takes the page color and pads itself by the bars, except while the video is alone on screen.
     */
    private void initEdgeToEdge() {
        MobileScreen.edgeToEdge(this, false);
        View content = findViewById(android.R.id.content);

        if (content == null) {
            return;
        }

        content.setBackgroundColor(KamiTheme.color(this, R.attr.kamiInk));
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            if (mPanel != null && mPanel.isSystemBarsHidden()) {
                v.setPadding(0, 0, 0, 0);
            } else {
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }

            // The keyboard covers the tabs instead of lifting them: Home is told how much of it is over the content
            int keyboard = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - bars.bottom);

            if (mBrowse != null) {
                mBrowse.onKeyboardChanged(keyboard);
            }

            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    public WatchPanel getWatchPanel() {
        return mPanel;
    }

    public boolean isPlayerShown() {
        return mPlayerShown;
    }

    // region Lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        KamiTheme.onStarted(this);

        if (MobileScreen.needsNewScale(this) || mThemeStamp != KamiTheme.stamp(this)) {
            recreate(); // the scale or the theme was changed in the settings
            return;
        }

        mResumed = true;
        MobileScreen.applySystemBars(this);

        if (mPendingView != null) {
            Class<?> pending = mPendingView;
            mPendingView = null;
            showView(pending);
        }

        MobileActivity.setLastResumed(this);

        mPanel.onResume();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        mPanel.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ViewManager.instance(this).getViewHost() == this) {
            ViewManager.instance(this).setViewHost(MobileBrowseActivity.class, EARLY_HOST);
        }

        mPanel.release();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Folded or unfolded: the other screen may want another scale. Start over so every size is resolved again.
        if (MobileScreen.needsNewScale(this, newConfig)) {
            recreate();
            return;
        }

        mPanel.onConfigurationChanged();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        mPanel.onPipChanged();
    }

    // endregion

    // region Player panel

    /**
     * The player fragment lives in the layout (the upstream activity needs it from the start) and stays attached:
     * a fragment declared in a layout can't be detached for real (seen on device, v9). While the panel is hidden
     * it has no engine (see MobilePlaybackFragment.onResume); showing the panel starts one for the picked video.
     */
    private void showPlayer() {
        if (mPlayerShown) {
            mPanel.setMini(false); // a video picked on Home under the mini player
            return;
        }

        mPlayerShown = true;

        if (mBrowse != null) {
            mBrowse.onPlayerShown(true);
        }

        if (mPlayer != null) {
            mPlayer.ensureEngine(); // released when the panel closed: the picked video needs a new one
        }

        mPanel.show(() -> {
            if (mPlayerShown && mBrowseView != null && !mPanel.isShortsShown()) {
                mBrowseView.setVisibility(View.INVISIBLE); // Home keeps its state, it just stops drawing
            }
        });
        mPanel.onResume();
    }

    /**
     * Back, the collapse arrow, the end of a playlist: the video stops, the panel goes down, Home is back.
     */
    public void closePlayer() {
        if (!mPlayerShown) {
            return;
        }

        mPlayerShown = false;
        mPendingView = null;
        Video closed = PlaybackPresenter.instance(this).getVideo();
        mClosedVideoId = closed != null ? closed.videoId : null;
        mClosedAtMs = SystemClock.uptimeMillis();
        mPanel.onPause();

        // The panel goes down first: whatever the engine does while it's released, the screen is Home again
        if (mBrowseView != null) {
            mBrowseView.setVisibility(View.VISIBLE);
        }

        mPanel.hide(() -> {
            if (mBrowse != null) {
                mBrowse.onPlayerShown(false);
            }
        });

        try {
            if (mPlayer != null) {
                mPlayer.blockEngine(false); // no background playback when the panel closes
                mPlayer.onFinish(); // releases the engine, the presenter saves the position
            }
        } catch (Exception e) {
            Log.e(TAG, "closePlayer: player release failed", e);
        }

        if (mReturnTo != null) {
            // Opened from a channel or a list page: back there, as before
            Class<? extends android.app.Activity> returnTo = mReturnTo;
            mReturnTo = null;

            try {
                startActivity(new android.content.Intent(this, returnTo));
                overridePendingTransition(0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void enterPip() {
        if (!Helpers.isPictureInPictureSupported(this) || isInPipMode()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            } else if (Build.VERSION.SDK_INT >= 24) {
                enterPictureInPictureMode();
            }
        } catch (Exception e) {
            // Device doesn't support picture-in-picture mode
            e.printStackTrace();
        }
    }

    // endregion

    // region ViewManager.ViewHost

    @Override
    public Class<?> getCurrentView() {
        return mPlayerShown ? PlaybackView.class : BrowseView.class;
    }

    @Override
    public boolean showView(Class<?> viewClass) {
        if (!mResumed || isFinishing()) {
            // Shown when the activity comes back to the front
            mPendingView = viewClass;

            if (viewClass == PlaybackView.class && !mPlayerShown) {
                android.app.Activity last = MobileActivity.getLastResumed();
                mReturnTo = last != null && last != this && !(last instanceof com.liskovsoft.smartyoutubetv2.tv.mobile.dialog.MobileAppDialogActivity)
                        && !last.isFinishing() ? last.getClass() : null;
            }

            return false;
        }

        if (viewClass == PlaybackView.class) {
            if (isUnwantedReopen()) {
                // Logged with its caller: what asked for it shows in the logs
                Log.w(TAG, "showView: ignored reopen of the video just closed", new Exception("caller"));
                return true;
            }

            showPlayer();
            return true;
        }

        if (viewClass == BrowseView.class) {
            closePlayer();
            return true;
        }

        return false;
    }

    /**
     * Nothing to play, or the video the panel just closed asked again by itself (the user didn't pick it).
     */
    private boolean isUnwantedReopen() {
        if (mPlayerShown) {
            return false;
        }

        Video video = PlaybackPresenter.instance(this).getVideo();

        if (video == null) {
            return true;
        }

        return SystemClock.uptimeMillis() - mClosedAtMs < REOPEN_GUARD_MS && Helpers.equals(video.videoId, mClosedVideoId);
    }

    // endregion

    // region Shorts

    /**
     * Called by the panel when a short starts or ends: Home and its tabs stay under a short, the tabs dark.
     */
    public void onShortsShown(boolean shown) {
        if (mBrowse != null) {
            mBrowse.setDarkTabs(shown);
        }

        if (mBrowseView != null) {
            mBrowseView.setVisibility(shown || !mPlayerShown ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Mini player shown or gone: Home is drawn and usable under the card, hidden again under the full player.
     */
    public void onMiniShown(boolean mini) {
        if (mBrowseView != null) {
            mBrowseView.setVisibility(mini || !mPlayerShown || mPanel.isShortsShown() ? View.VISIBLE : View.INVISIBLE);
        }

        if (mBrowse != null) {
            mBrowse.onPlayerShown(!mini);
        }
    }

    /**
     * A tab tapped: a short under it ends, the mini player goes on.
     */
    public void onTabTapped() {
        if (mPlayerShown && !mPanel.isMini()) {
            closePlayer();
        }
    }

    /**
     * Height of the tabs at the bottom of Home, 0 when the side rail is used instead.
     */
    public int getTabBarHeight() {
        return mBrowse != null ? mBrowse.getTabBarHeight() : 0;
    }

    /**
     * Search button of the Shorts player: the player closes, the search opens on Home.
     */
    public void openSearch() {
        closePlayer();

        if (mBrowse != null) {
            mBrowse.showSearch();
        }
    }

    // endregion

    // region Back, finish, leave

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (mPlayerShown && !mPanel.isMini()) {
            if (!mPanel.onBackPressed()) {
                closePlayer();
            }

            return;
        }

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.kami_browse_fragment);

        // Back from another tab, a filter, a search or "Explore" returns to Home; back from Home follows the upstream exit rule
        if (fragment instanceof MobileBrowseFragment && ((MobileBrowseFragment) fragment).goBack()) {
            return;
        }

        super.onBackPressed();
    }

    /**
     * The upstream player and its controllers call finish() to close the player (back, end of a playlist,
     * the PIP button). Here the player is a panel: it closes and Home stays, except when the engine is kept
     * for the background (PIP button) or when the system closes the PIP window.
     */
    @Override
    public void finish() {
        if (isInPipMode()) {
            // The user closed the PIP window: the app leaves, the engine with it
            if (mPlayer != null) {
                mPlayer.blockEngine(false);
                mPlayer.onFinish();
            }

            mClosingApp = true;
            finishReally();
            return;
        }

        if (mPlayerShown) {
            if (mPlayer != null && mPlayer.isEngineBlocked()) {
                // PIP button: the video goes on in a small window, the app steps back
                enterPip();
            } else {
                closePlayer();
            }

            return;
        }

        finishHome();
    }

    /**
     * Same as the upstream activity for a root screen: back on Home follows the exit shortcut (double back by default).
     */
    private void finishHome() {
        if (!getViewManager().hasParentView(this)) {
            switch (getGeneralData().getAppExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBack.enableDoubleBackExit(this::finishTheApp);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishTheApp();
                    break;
            }
        } else {
            finishReally();
        }
    }

    private void finishTheApp() {
        mClosingApp = true;
        getViewManager().addOnFinish(() -> Utils.forceFinishTheApp(this));
        Utils.properlyFinishTheApp(this);
    }

    /**
     * Upstream closes the player with finishReally() at the end of a video ("close" playback mode):
     * here that only closes the panel. Leaving the app still finishes the screen.
     */
    @Override
    public void finishReally() {
        if (mPlayerShown && !mClosingApp && !getViewManager().isFinished() && !isInPipMode()) {
            closePlayer();
            return;
        }

        super.finishReally();
        overridePendingTransition(0, 0);
    }

    /**
     * Mouse wheel changes the volume in the upstream player: only while a video is shown.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mPlayerShown && super.onGenericMotionEvent(event);
    }

    /**
     * HOME pressed: background playback (PIP, sound) only while a video is shown. Home itself just leaves.
     */
    @Override
    public void onUserLeaveHint() {
        // A menu sheet opening over the video is not leaving the app: no PIP, no engine kept for the background
        if (mPlayerShown && !AppDialogPresenter.instance(this).isDialogShown()) {
            super.onUserLeaveHint();
        }
    }

    // endregion

    // region MobilePlaybackFragment.Host, forwarded to the panel

    @Override
    public void onOverlayShown(boolean shown) {
        mPanel.onOverlayShown(shown);
    }

    @Override
    public void onVideoChanged(Video video) {
        mPanel.onVideoChanged(video);
    }

    @Override
    public void onSuggestionsUpdated(VideoGroup group) {
        mPanel.onSuggestionsUpdated(group);
    }

    @Override
    public void onSuggestionsRemoved(VideoGroup group) {
        mPanel.onSuggestionsRemoved(group);
    }

    @Override
    public void onSuggestionsCleared() {
        mPanel.onSuggestionsCleared();
    }

    @Override
    public void onButtonStateChanged(int buttonId, int buttonState) {
        mPanel.onButtonStateChanged(buttonId, buttonState);
    }

    @Override
    public boolean isPanelShown() {
        return mPlayerShown;
    }

    @Override
    public MotionEvent toPlayerEvent(MotionEvent event) {
        return mPlayerShown ? mPanel.toPlayerEvent(event) : null;
    }

    // endregion
}
