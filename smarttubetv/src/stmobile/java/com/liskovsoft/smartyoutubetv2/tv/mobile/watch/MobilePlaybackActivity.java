package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileScreen;
import com.liskovsoft.smartyoutubetv2.tv.mobile.download.DownloadAction;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Watch page for phones, foldables and tablets.<br/>
 * The video keeps its 16:9 box with the infos, actions, comments card and suggestions below it (portrait),
 * or the suggestions beside it (wide landscape). Fullscreen only on demand, or when a folded phone is turned.
 * Shorts get a vertical page of their own. Playback itself is the upstream player, untouched.
 */
public class MobilePlaybackActivity extends PlaybackActivity implements MobilePlaybackFragment.Host, WatchController.Callback {
    private static final int MODE_STACKED = 0; // portrait: video, then infos and suggestions
    private static final int MODE_SPLIT = 1; // wide landscape: video and infos on the left, suggestions on the right
    private static final int MODE_FULLSCREEN = 2;
    private static final int MODE_SHORTS = 3;

    private static final float SPLIT_MIN_HEIGHT_DP = 480; // lower than that (folded phone turned): fullscreen
    private static final float SPLIT_MAIN_RATIO = 0.64f;
    private static final float TWO_COLUMNS_MIN_DP = 600;
    private static final float PANEL_MIN_WIDTH_DP = 360;
    private static final long PROGRESS_INTERVAL_MS = 500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PlaybackPresenter mPresenter;
    private WatchController mController;
    private MobilePlaybackFragment mPlayer;

    private View mRoot;
    private View mMain;
    private FrameLayout mPlayerBox;
    private RecyclerView mList;
    private RecyclerView mSide;
    private View mControls;
    private boolean mControlsShown;
    private final List<View> mFitted = new ArrayList<>(); // ancestors that padded the content with the system insets
    private boolean mWindowReady; // the first layout is done: from then on the controls animate
    private ProgressBar mLine;
    private SeekBar mSeek;
    private TextView mTime;
    private TextView mChapter;
    private ImageButton mPlay;
    private ImageButton mFullscreenButton;
    private TextView mOverlayTitle;
    private View mFullActions;
    private View mMoreVideos;
    private View mShortsOverlay;
    private ProgressBar mShortsLine;
    private View mShortsPaused;
    private CommentsPanel mComments;

    private VideoCardAdapter mRelated;
    private VideoCardAdapter mInfoOnly; // infos alone, above the video column in split mode
    private final InfoHeader mHeader = new InfoHeader();

    private Video mVideo;
    private MediaItemMetadata mMetadata;
    private int mMode = -1;
    private boolean mUserFullscreen;
    private boolean mSystemBarsHidden;
    private boolean mOverlayShown;
    private boolean mSeeking;
    private boolean mPlayerGesture;
    private boolean mDescriptionExpanded;
    private Boolean mShownPlaying; // state drawn by the play button, null before the first update
    private WindowSize mWindowSize;

    private final Runnable mProgressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MobileScreen.scaled(base));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // inflates the flavor's fragment_playback: the watch page

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        mPlayer = fragment instanceof MobilePlaybackFragment ? (MobilePlaybackFragment) fragment : null;
        mPresenter = PlaybackPresenter.instance(this);
        mController = WatchController.install(mPresenter);

        initViews();
        initOverlay();
        initShortsOverlay();
        initLists();

        mRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            WindowSize size = WindowSize.of(v);

            if (!size.sameAs(mWindowSize)) {
                mWindowSize = size;
                v.post(this::applyLayout);
            }
        });

        mController.setCallback(this);
        mRoot.post(() -> mWindowReady = true);
        MobileScreen.applySystemBars(this);
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected boolean isTvDensityEnabled() {
        return false; // real density: controls keep their physical size
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (MobileScreen.needsNewScale(this)) {
            recreate(); // the scale was changed in the settings
            return;
        }

        mHandler.post(mProgressUpdater);

        if (mPlayer != null) {
            onOverlayShown(mPlayer.isOverlayShown());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mProgressUpdater);
    }

    @Override
    protected void onDestroy() {
        if (mController != null) {
            mController.setCallback(null);
        }

        mComments.release();
        mRelated.release();
        mInfoOnly.release();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (mComments.goBack()) {
            return;
        }

        if (mUserFullscreen) {
            setUserFullscreen(false);
            return;
        }

        if (mMode == MODE_FULLSCREEN && isShortLandscape() && !isInPipMode()) {
            // Fullscreen because the folded phone is turned: back leaves fullscreen, it doesn't leave the video
            leaveFullscreen();
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (MobileScreen.needsNewScale(this, newConfig)) {
            recreate();
            return;
        }

        // The window may keep its size (e.g. 180° turn): layout listener won't fire
        mRoot.post(this::applyLayout);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        mRoot.post(this::applyLayout);
    }

    // region Views

    private void initViews() {
        mRoot = findViewById(R.id.kami_watch_root);
        mMain = findViewById(R.id.kami_watch_main);
        mPlayerBox = findViewById(R.id.kami_player_box);
        mList = findViewById(R.id.kami_watch_list);
        mSide = findViewById(R.id.kami_watch_side);
        mComments = new CommentsPanel(findViewById(R.id.kami_comments), null);
        mComments.setController(mController);
    }

    private void initOverlay() {
        mControls = findViewById(R.id.kami_ov_controls);
        mLine = findViewById(R.id.kami_ov_line);
        mSeek = findViewById(R.id.kami_ov_seek);
        mTime = findViewById(R.id.kami_ov_time);
        mChapter = findViewById(R.id.kami_ov_chapter);
        mPlay = findViewById(R.id.kami_ov_play);
        mFullscreenButton = findViewById(R.id.kami_ov_fullscreen);
        mOverlayTitle = findViewById(R.id.kami_ov_title);
        mFullActions = findViewById(R.id.kami_ov_full_actions);
        mMoreVideos = findViewById(R.id.kami_ov_more_videos);

        // Tap outside the buttons: hide the controls
        mControls.setOnClickListener(v -> hideControls());

        findViewById(R.id.kami_ov_collapse).setOnClickListener(v -> {
            if (mMode == MODE_FULLSCREEN) {
                leaveFullscreen();
            } else {
                onBackPressed();
            }
        });
        findViewById(R.id.kami_ov_subtitles).setOnClickListener(v -> clickButton(R.id.lb_control_closed_captioning));
        findViewById(R.id.kami_ov_subtitles).setOnLongClickListener(v -> longClickButton(R.id.lb_control_closed_captioning));
        findViewById(R.id.kami_ov_quality).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
        findViewById(R.id.kami_ov_pip).setOnClickListener(v -> clickButton(R.id.action_pip));
        findViewById(R.id.kami_ov_previous).setOnClickListener(v -> mPresenter.onPreviousClicked());
        findViewById(R.id.kami_ov_next).setOnClickListener(v -> mPresenter.onNextClicked());
        mPlay.setOnClickListener(v -> togglePlayback());
        mFullscreenButton.setOnClickListener(v -> {
            if (mMode == MODE_FULLSCREEN) {
                leaveFullscreen();
            } else {
                setUserFullscreen(true);
            }
        });

        findViewById(R.id.kami_ov_like).setOnClickListener(v -> clickButton(R.id.action_thumbs_up));
        findViewById(R.id.kami_ov_dislike).setOnClickListener(v -> clickButton(R.id.action_thumbs_down));
        findViewById(R.id.kami_ov_comments).setOnClickListener(v -> openComments());
        findViewById(R.id.kami_ov_save).setOnClickListener(v -> clickButton(R.id.action_playlist_add));
        findViewById(R.id.kami_ov_share).setOnClickListener(v -> clickButton(R.id.action_share));
        mMoreVideos.setOnClickListener(v -> {
            leaveFullscreen();
            // Infos stay on top, the first suggestions right under them
            mList.post(() -> mList.smoothScrollToPosition(mRelated.getItemCount() > 1 ? 1 : 0));
        });

        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mPlayer != null) {
                    mTime.setText(formatTime(progress * mPlayer.getDurationMs() / 1000, mPlayer.getDurationMs()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSeeking = false;

                if (mPlayer != null && mPlayer.getDurationMs() > 0) {
                    long positionMs = seekBar.getProgress() * mPlayer.getDurationMs() / 1000;
                    mPlayer.setPositionMs(positionMs);
                    mPresenter.onSeekPositionChanged(positionMs);
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initShortsOverlay() {
        mShortsOverlay = findViewById(R.id.kami_shorts_overlay);
        mShortsLine = findViewById(R.id.kami_sh_line);
        mShortsPaused = findViewById(R.id.kami_sh_paused);

        // Tap: play/pause. Swipe up: next short. Swipe down: previous one.
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePlayback();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityY) < Math.abs(velocityX) * 1.5f || Math.abs(velocityY) < 600) {
                    return false;
                }

                if (velocityY < 0) {
                    mPresenter.onNextClicked();
                } else {
                    mPresenter.onPreviousClicked();
                }

                return true;
            }
        });
        mShortsOverlay.setOnTouchListener((v, event) -> detector.onTouchEvent(event));

        findViewById(R.id.kami_sh_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.kami_sh_quality).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
        findViewById(R.id.kami_sh_more).setOnClickListener(v -> showVideoMenu());
        findViewById(R.id.kami_sh_like).setOnClickListener(v -> clickButton(R.id.action_thumbs_up));
        findViewById(R.id.kami_sh_dislike).setOnClickListener(v -> clickButton(R.id.action_thumbs_down));
        findViewById(R.id.kami_sh_comments).setOnClickListener(v -> openComments());
        findViewById(R.id.kami_sh_save).setOnClickListener(v -> clickButton(R.id.action_playlist_add));
        findViewById(R.id.kami_sh_share).setOnClickListener(v -> clickButton(R.id.action_share));
        findViewById(R.id.kami_sh_channel).setOnClickListener(v -> clickButton(R.id.action_channel));
        findViewById(R.id.kami_sh_subscribe).setOnClickListener(v -> clickButton(R.id.action_subscribe));
    }

    private void initLists() {
        VideoCardAdapter.Listener listener = new VideoCardAdapter.Listener() {
            @Override
            public void onVideoClicked(Video video) {
                mPresenter.onSuggestionItemClicked(video);
            }

            @Override
            public void onVideoLongClicked(Video video) {
                mPresenter.onSuggestionItemLongClicked(video);
            }

            @Override
            public void onScrollEnd(Video lastVideo) {
                mPresenter.onScrollEnd(lastVideo);
            }
        };

        mRelated = new VideoCardAdapter(VideoCardAdapter.STYLE_GRID, listener);
        mInfoOnly = new VideoCardAdapter(VideoCardAdapter.STYLE_GRID, null);
        mList.setLayoutManager(new GridLayoutManager(this, 1));
        mSide.setLayoutManager(new LinearLayoutManager(this));
    }

    // endregion

    // region Layout: stacked, split, fullscreen, shorts

    private int computeMode() {
        if (isInPipMode()) {
            return MODE_FULLSCREEN;
        }

        if (isShorts()) {
            return MODE_SHORTS;
        }

        if (mUserFullscreen) {
            return MODE_FULLSCREEN;
        }

        if (mWindowSize != null && mWindowSize.isLandscape()) {
            // Folded phone turned: not enough height for infos, the video takes the whole screen
            return mWindowSize.physicalHeightDp < SPLIT_MIN_HEIGHT_DP ? MODE_FULLSCREEN : MODE_SPLIT;
        }

        return MODE_STACKED;
    }

    private void applyLayout() {
        if (mWindowSize == null || mRoot.getWidth() == 0) {
            return;
        }

        mMode = computeMode();
        int rootWidth = mRoot.getWidth();
        int rootHeight = mRoot.getHeight();
        boolean videoOnly = mMode == MODE_FULLSCREEN || mMode == MODE_SHORTS;

        setSystemBarsHidden(videoOnly && !isInPipMode());

        // Video column and player box
        LinearLayout.LayoutParams mainParams = (LinearLayout.LayoutParams) mMain.getLayoutParams();
        LinearLayout.LayoutParams boxParams = (LinearLayout.LayoutParams) mPlayerBox.getLayoutParams();
        LinearLayout.LayoutParams sideParams = (LinearLayout.LayoutParams) mSide.getLayoutParams();

        if (mMode == MODE_SPLIT) {
            int mainWidth = Math.round(rootWidth * SPLIT_MAIN_RATIO);
            mainParams.width = mainWidth;
            mainParams.weight = 0;
            sideParams.weight = 1;
            boxParams.height = Math.round(mainWidth * 9 / 16f);
        } else {
            mainParams.width = 0;
            mainParams.weight = 1;
            sideParams.weight = 0;
            boxParams.height = videoOnly ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(rootWidth * 9 / 16f);
        }

        boxParams.weight = videoOnly ? 1 : 0;
        mMain.setLayoutParams(mainParams);
        mPlayerBox.setLayoutParams(boxParams);
        mSide.setLayoutParams(sideParams);
        mSide.setVisibility(mMode == MODE_SPLIT ? View.VISIBLE : View.GONE);
        mList.setVisibility(videoOnly ? View.GONE : View.VISIBLE);

        // Infos and suggestions
        if (mMode == MODE_SPLIT) {
            mRelated.setHeader(null);
            mInfoOnly.setHeader(mHeader);
            mRelated.setEdgeToEdge(false);
            VideoCardAdapter.applyListPadding(mList, true);
            swapAdapter(mList, mInfoOnly);
            swapAdapter(mSide, mRelated);
            setColumns(1);
        } else if (mMode == MODE_STACKED) {
            mInfoOnly.setHeader(null);
            mRelated.setHeader(mHeader);
            int columns = mWindowSize.physicalWidthDp >= TWO_COLUMNS_MIN_DP ? 2 : 1;
            mRelated.setEdgeToEdge(columns == 1);
            VideoCardAdapter.applyListPadding(mList, columns == 1);
            swapAdapter(mSide, null);
            swapAdapter(mList, mRelated);
            setColumns(columns);
        }

        // Comments panel: under the video in portrait, on the side otherwise
        View panel = findViewById(R.id.kami_comments);
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) panel.getLayoutParams();

        if (mMode == MODE_STACKED) {
            panelParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            panelParams.height = rootHeight - boxParams.height;
            panelParams.gravity = Gravity.BOTTOM;
        } else if (mMode == MODE_SHORTS && rootHeight > rootWidth) {
            panelParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            panelParams.height = Math.round(rootHeight * 0.66f);
            panelParams.gravity = Gravity.BOTTOM;
        } else {
            int minWidth = WindowSize.dpToPx(this, PANEL_MIN_WIDTH_DP);
            int width = mMode == MODE_SPLIT ? rootWidth - mainParams.width : Math.round(rootWidth * 0.4f);
            panelParams.width = Math.min(rootWidth, Math.max(minWidth, width));
            panelParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            panelParams.gravity = Gravity.END;
        }

        panel.setLayoutParams(panelParams);

        // Overlays
        boolean pip = isInPipMode();
        mShortsOverlay.setVisibility(mMode == MODE_SHORTS && !pip ? View.VISIBLE : View.GONE);
        findViewById(R.id.kami_overlay).setVisibility(mMode == MODE_SHORTS || pip ? View.GONE : View.VISIBLE);
        mOverlayTitle.setVisibility(mMode == MODE_FULLSCREEN ? View.VISIBLE : View.INVISIBLE);
        mFullActions.setVisibility(mMode == MODE_FULLSCREEN ? View.VISIBLE : View.GONE);
        mMoreVideos.setVisibility(mMode == MODE_FULLSCREEN && !isShortLandscape() ? View.VISIBLE : View.GONE);
        mFullscreenButton.setImageResource(mMode == MODE_FULLSCREEN ? R.drawable.kami_ic_fullscreen_exit : R.drawable.kami_ic_fullscreen);
        updateOverlayVisibility();
    }

    private void setColumns(int columns) {
        GridLayoutManager manager = (GridLayoutManager) mList.getLayoutManager();
        manager.setSpanCount(columns);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                RecyclerView.Adapter<?> adapter = mList.getAdapter();
                return adapter instanceof VideoCardAdapter && ((VideoCardAdapter) adapter).isHeader(position) ? columns : 1;
            }
        });
    }

    private static void swapAdapter(RecyclerView list, RecyclerView.Adapter<?> adapter) {
        if (list.getAdapter() != adapter) {
            list.setAdapter(adapter);
        }
    }

    private boolean isShortLandscape() {
        return mWindowSize != null && mWindowSize.isLandscape() && mWindowSize.physicalHeightDp < SPLIT_MIN_HEIGHT_DP;
    }

    private boolean isShorts() {
        return mVideo != null && (mVideo.isShorts || (mVideo.getGroup() != null && mVideo.getGroup().isShorts()));
    }

    private void setUserFullscreen(boolean fullscreen) {
        mUserFullscreen = fullscreen;

        // A folded phone held upright: turn the screen for a landscape video, and back
        boolean smallScreen = mWindowSize != null && Math.min(mWindowSize.physicalWidthDp, mWindowSize.physicalHeightDp) < TWO_COLUMNS_MIN_DP;

        if (smallScreen && Build.VERSION.SDK_INT != 26) {
            setRequestedOrientation(fullscreen ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        applyLayout();
    }

    /**
     * Fullscreen button or back arrow while in fullscreen.
     */
    private void leaveFullscreen() {
        if (mUserFullscreen) {
            setUserFullscreen(false);
        } else if (isShortLandscape() && Build.VERSION.SDK_INT != 26) {
            // Fullscreen because the folded phone is turned: put it back upright
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            mHandler.postDelayed(() -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), 3_000);
        }
    }

    @SuppressWarnings("deprecation")
    private void setSystemBarsHidden(boolean hidden) {
        if (mSystemBarsHidden == hidden) {
            return;
        }

        mSystemBarsHidden = hidden;

        if (hidden) {
            enterImmersive();
        } else {
            leaveImmersive();
        }
    }

    /**
     * Edge to edge, edge included: bars hidden, no inset padding left by the screens above the video,
     * the notch area used, and a black background so nothing white shows around the picture.
     */
    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        Window window = getWindow();
        mFitted.clear();

        for (ViewParent parent = mRoot.getParent(); parent instanceof View && parent != window.getDecorView(); parent = parent.getParent()) {
            View view = (View) parent;

            if (view.getFitsSystemWindows()) {
                mFitted.add(view);
            }

            view.setFitsSystemWindows(false);
            view.setPadding(0, 0, 0, 0);
        }

        mRoot.setFitsSystemWindows(false);
        mRoot.setBackgroundColor(Color.BLACK);
        window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        window.getDecorView().requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    private void leaveImmersive() {
        Window window = getWindow();
        int ink = getColor(R.color.kami_ink);

        for (View view : mFitted) {
            view.setFitsSystemWindows(true);
        }

        mFitted.clear();
        mRoot.setBackgroundColor(ink);
        window.setBackgroundDrawable(new ColorDrawable(ink));

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            window.setAttributes(params);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
            WindowInsetsController controller = window.getInsetsController();

            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }

        MobileScreen.applySystemBars(this);
        window.getDecorView().requestApplyInsets();
    }

    // endregion

    // region Controls

    private void clickButton(int buttonId) {
        if (mPlayer != null) {
            mPresenter.onButtonClicked(buttonId, mPlayer.getButtonState(buttonId));
        }
    }

    private boolean longClickButton(int buttonId) {
        if (mPlayer != null) {
            mPresenter.onButtonLongClicked(buttonId, mPlayer.getButtonState(buttonId));
        }

        return true;
    }

    private void showVideoMenu() {
        if (mPlayer != null && mPlayer.getVideo() != null) {
            mPresenter.onSuggestionItemLongClicked(mPlayer.getVideo());
        }
    }

    private void togglePlayback() {
        if (mPlayer == null) {
            return;
        }

        boolean play = !mPlayer.getPlayWhenReady();
        mPlayer.setPlayWhenReady(play);

        if (play) {
            mPresenter.onPlayClicked();
        } else {
            mPresenter.onPauseClicked();
        }

        updateProgress();
    }

    private void hideControls() {
        if (mPlayer != null) {
            mPlayer.showOverlay(false);
        }
    }

    private void updateOverlayVisibility() {
        boolean show = mOverlayShown && mMode != MODE_SHORTS && !isInPipMode();
        mLine.setVisibility(show || mMode == MODE_FULLSCREEN ? View.GONE : View.VISIBLE);

        if (show == mControlsShown) {
            return;
        }

        mControlsShown = show;
        animateControls(show);
    }

    /**
     * Controls fade in while the top bar slides down, the play buttons grow a little and the bottom bar slides up.
     * They fade out faster than they came.
     */
    private void animateControls(boolean show) {
        View top = findViewById(R.id.kami_ov_top);
        View center = findViewById(R.id.kami_ov_center);
        float slide = 12 * getResources().getDisplayMetrics().density;

        mControls.animate().cancel();
        top.animate().cancel();
        center.animate().cancel();

        if (!mWindowReady || !mControls.isAttachedToWindow()) {
            mControls.setAlpha(1f);
            mControls.setVisibility(show ? View.VISIBLE : View.GONE);
            return;
        }

        if (show) {
            mControls.setAlpha(0f);
            mControls.setVisibility(View.VISIBLE);
            top.setTranslationY(-slide);
            center.setScaleX(0.88f);
            center.setScaleY(0.88f);
            mControls.animate().alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            top.animate().translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            center.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            top.animate().translationY(-slide / 2).setDuration(140).start();
            mControls.animate().alpha(0f).setDuration(140).setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        mControls.setVisibility(View.GONE);
                        top.setTranslationY(0f);
                        mControls.setAlpha(1f);
                    }).start();
        }
    }

    private void updateProgress() {
        if (mPlayer == null) {
            return;
        }

        long duration = mPlayer.getDurationMs();
        long position = mPlayer.getPositionMs();
        int progress = duration > 0 ? (int) (position * 1000 / duration) : 0;

        if (!mSeeking) {
            mSeek.setProgress(progress);
            mTime.setText(formatTime(position, duration));
        }

        mLine.setProgress(progress);
        mShortsLine.setProgress(progress);

        boolean playing = mPlayer.getPlayWhenReady();

        if (mShownPlaying == null || mShownPlaying != playing) {
            // Twice a second is often: only touch the drawables when the state really changed
            mShownPlaying = playing;
            mPlay.setImageResource(playing ? R.drawable.kami_ic_pause : R.drawable.kami_ic_play);
            mShortsPaused.setVisibility(playing ? View.GONE : View.VISIBLE);
        }

        String chapter = findChapter(position);
        mChapter.setVisibility(chapter != null ? View.VISIBLE : View.GONE);
        mChapter.setText(chapter);
    }

    private String findChapter(long positionMs) {
        List<ChapterItem> chapters = mMetadata != null ? mMetadata.getChapters() : null;

        if (chapters == null || chapters.isEmpty()) {
            return null;
        }

        String title = null;

        for (ChapterItem chapter : chapters) {
            if (chapter.getStartTimeMs() <= positionMs) {
                title = chapter.getTitle();
            } else {
                break;
            }
        }

        return title;
    }

    private static String formatTime(long positionMs, long durationMs) {
        if (durationMs <= 0) {
            return "";
        }

        return toTime(positionMs) + " / " + toTime(durationMs);
    }

    private static String toTime(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long hours = totalSec / 3600;
        long minutes = totalSec % 3600 / 60;
        long seconds = totalSec % 60;

        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%d:%02d", minutes, seconds);
    }

    private void openComments() {
        String message = null;

        if (mMetadata == null) {
            message = getString(R.string.kami_no_comments);
        } else if (mMetadata.getLiveChatKey() != null) {
            message = getString(R.string.kami_comments_live);
        } else if (mMetadata.getCommentsKey() == null) {
            message = getString(R.string.kami_comments_disabled);
        }

        hideControls();
        mComments.show(message);
    }

    // endregion

    // region MobilePlaybackFragment.Host

    @Override
    public void onOverlayShown(boolean shown) {
        mOverlayShown = shown;

        if (mControls != null) {
            updateOverlayVisibility();
        }
    }

    @Override
    public void onVideoChanged(Video video) {
        boolean wasShorts = isShorts();
        mVideo = video;

        if (wasShorts != isShorts()) {
            applyLayout();
        }

        mHeader.refresh();
    }

    @Override
    public void onSuggestionsUpdated(VideoGroup group) {
        if (group.isEmpty() || isChapters(group)) {
            return;
        }

        if (group.getAction() == VideoGroup.ACTION_SYNC) {
            mRelated.getData().sync(group);
        } else if (group.getAction() == VideoGroup.ACTION_REPLACE) {
            mRelated.getData().remove(group);
            mRelated.getData().add(group);
        } else {
            mRelated.getData().add(group);
        }
    }

    @Override
    public void onSuggestionsRemoved(VideoGroup group) {
        mRelated.getData().remove(group);
    }

    @Override
    public void onSuggestionsCleared() {
        mRelated.clear();
    }

    @Override
    public void onButtonStateChanged(int buttonId, int buttonState) {
        if (buttonId == R.id.action_thumbs_up || buttonId == R.id.action_thumbs_down || buttonId == R.id.action_subscribe) {
            mHeader.refresh();
        }
    }

    @Override
    public MotionEvent toPlayerEvent(MotionEvent event) {
        if (mMode == MODE_SHORTS || mPlayer == null || mPlayer.getView() == null) {
            return null; // shorts have their own gestures
        }

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            mPlayerGesture = isInside(mPlayerBox, event) && !(mComments.isShown() && isInside(findViewById(R.id.kami_comments), event));
        }

        if (!mPlayerGesture) {
            return null;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mPlayerGesture = false;
        }

        int[] location = new int[2];
        mPlayer.getView().getLocationInWindow(location);
        MotionEvent playerEvent = MotionEvent.obtain(event);
        playerEvent.offsetLocation(-location[0], -location[1]);

        return playerEvent;
    }

    private static boolean isInside(View view, MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        int[] location = new int[2];
        view.getLocationInWindow(location);
        float x = event.getX();
        float y = event.getY();

        return x >= location[0] && x < location[0] + view.getWidth() && y >= location[1] && y < location[1] + view.getHeight();
    }

    private static boolean isChapters(VideoGroup group) {
        List<Video> videos = group.getVideos();
        return videos != null && !videos.isEmpty() && videos.get(0).isChapter;
    }

    // endregion

    // region WatchController.Callback

    @Override
    public void onNewVideo(Video video) {
        mMetadata = null;
        mDescriptionExpanded = false;
        mComments.hide();
        mComments.reset(null);
        mHeader.refresh();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mMetadata = metadata;
        mComments.reset(metadata != null && metadata.getLiveChatKey() == null ? metadata.getCommentsKey() : null);
        mComments.preload(mHeader::refresh);
        mHeader.refresh();
        updateProgress();
    }

    // endregion

    /**
     * Infos under the video. Shared by the stacked list (above the suggestions) and the split layout (alone).
     */
    private final class InfoHeader implements VideoCardAdapter.Header {
        private View mView;

        @Override
        public View onCreateView(ViewGroup parent) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_watch_header, parent, false);
            tintChip(view, R.id.kami_w_like);
            tintChip(view, R.id.kami_w_share);
            tintChip(view, R.id.kami_w_save);
            tintChip(view, R.id.kami_w_quality);
            tintChip(view, R.id.kami_w_download);
            tintChip(view, R.id.kami_w_pip);
            tintChip(view, R.id.kami_w_more);

            view.findViewById(R.id.kami_w_title_block).setOnClickListener(v -> {
                mDescriptionExpanded = !mDescriptionExpanded;
                refresh();
            });
            view.findViewById(R.id.kami_w_channel).setOnClickListener(v -> clickButton(R.id.action_channel));
            view.findViewById(R.id.kami_w_subscribe).setOnClickListener(v -> clickButton(R.id.action_subscribe));
            view.findViewById(R.id.kami_w_subscribe).setOnLongClickListener(v -> longClickButton(R.id.action_subscribe));
            view.findViewById(R.id.kami_w_like).setOnClickListener(v -> clickButton(R.id.action_thumbs_up));
            view.findViewById(R.id.kami_w_dislike).setOnClickListener(v -> clickButton(R.id.action_thumbs_down));
            view.findViewById(R.id.kami_w_share).setOnClickListener(v -> clickButton(R.id.action_share));
            view.findViewById(R.id.kami_w_save).setOnClickListener(v -> clickButton(R.id.action_playlist_add));
            view.findViewById(R.id.kami_w_quality).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
            view.findViewById(R.id.kami_w_download).setOnClickListener(v -> DownloadAction.start(MobilePlaybackActivity.this, mVideo));
            view.findViewById(R.id.kami_w_pip).setOnClickListener(v -> clickButton(R.id.action_pip));
            view.findViewById(R.id.kami_w_more).setOnClickListener(v -> showVideoMenu());
            view.findViewById(R.id.kami_w_comments).setOnClickListener(v -> openComments());

            return view;
        }

        @Override
        public void onBind(View view) {
            mView = view;
            bind(view);
        }

        void refresh() {
            if (mView != null && mView.getParent() != null) {
                bind(mView);
            }

            bindOverlays();
        }

        private void bind(View view) {
            MediaItemMetadata metadata = mMetadata;
            Video video = mVideo;

            String title = metadata != null && metadata.getTitle() != null ? metadata.getTitle() : video != null ? video.getTitle() : null;
            setText(view, R.id.kami_w_title, title);
            ((TextView) view.findViewById(R.id.kami_w_title)).setMaxLines(mDescriptionExpanded ? 6 : 2);

            String meta = join(metadata != null ? metadata.getViewCount() : null, metadata != null ? metadata.getPublishedDate() : null);

            if (meta == null && video != null && video.secondTitle != null) {
                meta = video.secondTitle.toString();
            }

            String description = metadata != null ? metadata.getDescription() : null;
            boolean hasDescription = description != null && !description.isEmpty();
            String more = getString(R.string.kami_show_more);
            setText(view, R.id.kami_w_meta, meta != null && hasDescription && !mDescriptionExpanded ? meta + "  " + more : meta);
            TextView descriptionView = view.findViewById(R.id.kami_w_description);
            descriptionView.setVisibility(mDescriptionExpanded && hasDescription ? View.VISIBLE : View.GONE);
            descriptionView.setText(description);

            String author = metadata != null && metadata.getAuthor() != null ? metadata.getAuthor() : video != null ? video.getAuthor() : null;
            setText(view, R.id.kami_w_author, author);
            setText(view, R.id.kami_w_subscribers, metadata != null ? metadata.getSubscriberCount() : null);
            CommentsPanel.loadAvatar(view.findViewById(R.id.kami_w_avatar), metadata != null ? metadata.getAuthorImageUrl() : null);

            boolean subscribed = getState(R.id.action_subscribe) == PlayerUI.BUTTON_ON;
            TextView subscribe = view.findViewById(R.id.kami_w_subscribe);
            subscribe.setSelected(subscribed);
            subscribe.setText(subscribed ? R.string.kami_subscribed : R.string.kami_subscribe);

            boolean liked = getState(R.id.action_thumbs_up) == PlayerUI.BUTTON_ON;
            boolean disliked = getState(R.id.action_thumbs_down) == PlayerUI.BUTTON_ON;
            TextView like = view.findViewById(R.id.kami_w_like);
            String likeCount = metadata != null ? metadata.getLikeCount() : null;
            like.setText(likeCount != null ? likeCount : getString(R.string.kami_like));
            tint(like, liked);
            ((ImageView) view.findViewById(R.id.kami_w_dislike)).setColorFilter(color(disliked ? R.color.kami_accent : R.color.kami_text));

            // Comments card
            TextView commentsTitle = view.findViewById(R.id.kami_w_comments_title);
            View topComment = view.findViewById(R.id.kami_w_top_comment);
            CommentItem top = mComments.getTopComment();
            boolean live = metadata != null && metadata.getLiveChatKey() != null;
            boolean off = metadata != null && !live && metadata.getCommentsKey() == null;
            view.findViewById(R.id.kami_w_comments).setVisibility(live ? View.GONE : View.VISIBLE);
            commentsTitle.setText(off ? getString(R.string.kami_comments_disabled) : getString(R.string.kami_comments));
            topComment.setVisibility(top != null && !off ? View.VISIBLE : View.GONE);

            if (top != null) {
                setText(view, R.id.kami_w_top_text, top.getMessage());
                CommentsPanel.loadAvatar(view.findViewById(R.id.kami_w_top_avatar), top.getAuthorPhoto());
            }
        }

        /**
         * Fullscreen and shorts controls show the same counts and states.
         */
        private void bindOverlays() {
            MediaItemMetadata metadata = mMetadata;
            Video video = mVideo;
            String likeCount = metadata != null ? metadata.getLikeCount() : null;
            boolean liked = getState(R.id.action_thumbs_up) == PlayerUI.BUTTON_ON;
            boolean disliked = getState(R.id.action_thumbs_down) == PlayerUI.BUTTON_ON;
            boolean subscribed = getState(R.id.action_subscribe) == PlayerUI.BUTTON_ON;
            int accent = color(R.color.kami_accent);
            String title = metadata != null && metadata.getTitle() != null ? metadata.getTitle() : video != null ? video.getTitle() : null;
            String author = metadata != null && metadata.getAuthor() != null ? metadata.getAuthor() : video != null ? video.getAuthor() : null;

            mOverlayTitle.setText(title);
            ((TextView) findViewById(R.id.kami_ov_like_count)).setText(likeCount);
            ((ImageView) findViewById(R.id.kami_ov_like)).setColorFilter(liked ? accent : 0xFFFFFFFF);
            ((ImageView) findViewById(R.id.kami_ov_dislike)).setColorFilter(disliked ? accent : 0xFFFFFFFF);

            TextView shortsLike = findViewById(R.id.kami_sh_like);
            shortsLike.setText(likeCount != null ? likeCount : getString(R.string.kami_like));
            TextViewCompat.setCompoundDrawableTintList(shortsLike, ColorStateList.valueOf(liked ? accent : 0xFFFFFFFF));
            TextViewCompat.setCompoundDrawableTintList(findViewById(R.id.kami_sh_dislike), ColorStateList.valueOf(disliked ? accent : 0xFFFFFFFF));
            ((TextView) findViewById(R.id.kami_sh_title)).setText(title);
            ((TextView) findViewById(R.id.kami_sh_author)).setText(author);
            TextView shortsSubscribe = findViewById(R.id.kami_sh_subscribe);
            shortsSubscribe.setSelected(subscribed);
            shortsSubscribe.setText(subscribed ? R.string.kami_subscribed : R.string.kami_subscribe);
            CommentsPanel.loadAvatar(findViewById(R.id.kami_sh_avatar), metadata != null ? metadata.getAuthorImageUrl() : null);
        }

        private int getState(int buttonId) {
            return mPlayer != null ? mPlayer.getButtonState(buttonId) : PlayerUI.BUTTON_OFF;
        }

        private void tintChip(View root, int id) {
            tint(root.findViewById(id), false);
        }

        private void tint(TextView view, boolean active) {
            TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color(active ? R.color.kami_accent : R.color.kami_text)));
        }

        private void setText(View root, int id, CharSequence text) {
            ((TextView) root.findViewById(id)).setText(text);
        }

        private String join(String first, String second) {
            boolean hasFirst = first != null && !first.isEmpty();
            boolean hasSecond = second != null && !second.isEmpty();

            if (hasFirst && hasSecond) {
                return first + " · " + second;
            }

            return hasFirst ? first : hasSecond ? second : null;
        }
    }

    private int color(int colorResId) {
        return ContextCompat.getColor(this, colorResId);
    }
}
