package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.FeedSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.GridSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.SettingsSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch version of the upstream BrowseFragment: same {@link BrowseView} contract, same {@link BrowsePresenter}.<br/>
 * Four main tabs (Home, Shorts, Subscriptions, You): in a bottom bar when the phone is folded,
 * in a rail when it's unfolded or in landscape. Tablets get a drawer with every section.<br/>
 * Topic feeds (Music, Gaming, News, Live...) are filters on top of Home, the rest lives in the "You" page.
 * The layout follows every size change live.
 */
public class MobileBrowseFragment extends Fragment implements BrowseView {
    /**
     * Pseudo section of the "You" page.
     */
    private static final int ID_YOU = Integer.MIN_VALUE;
    private static final int TYPE_YOU = Integer.MIN_VALUE;
    private static final float DRAWER_MIN_DP = 1000;

    private final List<BrowseSection> mSections = new ArrayList<>();
    // Derived from mSections each time it changes
    private final List<BrowseSection> mTabs = new ArrayList<>();
    private final List<BrowseSection> mChips = new ArrayList<>();
    private final List<BrowseSection> mYouSections = new ArrayList<>();
    private BrowseSection mYouTab;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private BrowsePresenter mBrowsePresenter;
    private CrashRestorer mCrashRestorer;
    private boolean mIsFragmentCreated;

    private View mRoot;
    private TextView mTitle;
    private View mSettingsButton;
    private RecyclerView mNavRail;
    private RecyclerView mNavBar;
    private View mBottom;
    private View mSearchButton;
    private RecyclerView mChipsView;
    private FilterChipsAdapter mChipsAdapter;
    private SwipeRefreshLayout mRefresh;
    private RecyclerView mList;
    private View mErrorPanel;
    private TextView mErrorMessage;
    private Button mErrorAction;
    private ProgressBar mProgress;

    private NavAdapter mRailAdapter;
    private NavAdapter mBarAdapter;
    private int mNavLayout = -1;
    private WindowSize mWindowSize;

    private MobileSection mContent;
    private int mContentType = -1;
    private int mSelectedSectionId = -1;
    private int mPendingItemIndex = -1;
    private Video mPendingItem;
    private boolean mIsProgressShowing;
    private boolean mIsYouShowing;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);

        if (getContext() == null) {
            return;
        }

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRoot = inflater.inflate(R.layout.kami_fragment_browse, container, false);

        mTitle = mRoot.findViewById(R.id.kami_title);
        mSettingsButton = mRoot.findViewById(R.id.kami_action_settings);
        mNavRail = mRoot.findViewById(R.id.kami_nav_rail);
        mNavBar = mRoot.findViewById(R.id.kami_nav_bar);
        mBottom = mRoot.findViewById(R.id.kami_bottom);
        mSearchButton = mRoot.findViewById(R.id.kami_action_search);
        mChipsView = mRoot.findViewById(R.id.kami_chips);
        mRefresh = mRoot.findViewById(R.id.kami_refresh);
        mList = mRoot.findViewById(R.id.kami_list);
        mErrorPanel = mRoot.findViewById(R.id.kami_error);
        mErrorMessage = mRoot.findViewById(R.id.kami_error_message);
        mErrorAction = mRoot.findViewById(R.id.kami_error_action);
        mProgress = mRoot.findViewById(R.id.kami_progress);

        mSearchButton.setOnClickListener(v -> SearchPresenter.instance(getContext()).startSearch(null));
        mRoot.findViewById(R.id.kami_bar_search).setOnClickListener(v -> SearchPresenter.instance(getContext()).startSearch(null));
        mSettingsButton.setOnClickListener(v -> openSettingsSection());

        mYouTab = new BrowseSection(ID_YOU, getString(R.string.kami_you), BrowseSection.TYPE_GRID, R.drawable.kami_ic_person);
        mChipsAdapter = new FilterChipsAdapter(mChips, mNavListener);
        mChipsView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mChipsView.setAdapter(mChipsAdapter);

        mRefresh.setColorSchemeResources(R.color.kami_accent);
        mRefresh.setProgressBackgroundColorSchemeResource(R.color.kami_surface_high);
        mRefresh.setOnRefreshListener(() -> {
            mRefresh.setRefreshing(false);
            mBrowsePresenter.refresh(false);
        });

        mList.setHasFixedSize(true);

        // Fold/unfold, rotation, split screen: everything goes through a new layout pass
        mRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                mHandler.post(this::applyWindowSize);
            }
        });

        return mRoot;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBrowsePresenter.onViewInitialized();

        // Restore state after crash
        mCrashRestorer.restoreHeader((idx, video) -> {
            selectSection(idx, true);
            selectSectionItem(video);
        });
        mCrashRestorer.restorePlayback();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        mCrashRestorer.persistHeaderIndex(outState, indexOfSection(mSelectedSectionId));
        mCrashRestorer.persistVideo(outState, mBrowsePresenter.getCurrentVideo());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacksAndMessages(null);

        if (mContent != null) {
            mContent.detach();
            mContent = null;
            mContentType = -1;
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.onViewDestroyed();
    }

    // region Window size

    private void applyWindowSize() {
        if (mRoot == null || mRoot.getWidth() == 0) {
            return;
        }

        WindowSize size = WindowSize.of(mRoot);

        if (size.sameAs(mWindowSize)) {
            return;
        }

        mWindowSize = size;

        int navLayout;

        if (size.isCompact() && !size.isShort()) {
            navLayout = NavAdapter.LAYOUT_BAR;
        } else if (size.widthDp >= DRAWER_MIN_DP && !size.isShort()) {
            // Real tablets only: an unfolded Fold stays on the rail with the four tabs, even in landscape
            navLayout = NavAdapter.LAYOUT_DRAWER;
        } else {
            navLayout = NavAdapter.LAYOUT_RAIL;
        }

        if (navLayout != mNavLayout) {
            mNavLayout = navLayout;
            setupNavigation();
        }

        updateContentWidth();
    }

    private final NavAdapter.Listener mNavListener = new NavAdapter.Listener() {
        @Override
        public void onSectionClicked(BrowseSection section) {
            onNavSectionClicked(section);
        }

        @Override
        public void onSectionLongClicked(BrowseSection section) {
            if (section.getId() != ID_YOU) {
                mBrowsePresenter.onSectionLongPressed(section.getId());
            }
        }
    };

    private void setupNavigation() {
        boolean isBar = mNavLayout == NavAdapter.LAYOUT_BAR;
        boolean isDrawer = mNavLayout == NavAdapter.LAYOUT_DRAWER;

        mBottom.setVisibility(isBar ? View.VISIBLE : View.GONE);
        mNavRail.setVisibility(isBar ? View.GONE : View.VISIBLE);

        if (isBar) {
            mBarAdapter = new NavAdapter(mTabs, NavAdapter.LAYOUT_BAR, mNavListener);
            mNavBar.setLayoutManager(new GridLayoutManager(getContext(), Math.max(1, mTabs.size())));
            mNavBar.setAdapter(mBarAdapter);
            mRailAdapter = null;
            mNavRail.setAdapter(null);
        } else {
            ViewGroup.LayoutParams lp = mNavRail.getLayoutParams();
            lp.width = WindowSize.dpToPx(getContext(), isDrawer ? 240 : 88);
            mNavRail.setLayoutParams(lp);
            // Tablets have room for every section, the rail keeps the four tabs
            mRailAdapter = new NavAdapter(isDrawer ? mSections : mTabs, mNavLayout, mNavListener);
            mNavRail.setLayoutManager(new LinearLayoutManager(getContext()));
            mNavRail.setAdapter(mRailAdapter);
            mBarAdapter = null;
            mNavBar.setAdapter(null);
        }

        if (mIsYouShowing && isDrawer) {
            // Nothing left to show in the "You" page
            selectSection(Math.max(0, indexOfSection(MediaGroup.TYPE_HOME)), true);
        }

        notifyNavigationChanged();
    }

    /**
     * Splits the upstream sections into main tabs, Home filters and "You" entries.
     */
    private void rebuildGroups() {
        mTabs.clear();
        mChips.clear();
        mYouSections.clear();

        BrowseSection home = findSection(MediaGroup.TYPE_HOME);
        BrowseSection shorts = findSection(MediaGroup.TYPE_SHORTS);
        BrowseSection subscriptions = findSection(MediaGroup.TYPE_SUBSCRIPTIONS);

        if (subscriptions == null) {
            subscriptions = findSection(MediaGroup.TYPE_CHANNEL_UPLOADS);
        }

        for (BrowseSection section : new BrowseSection[] {home, shorts, subscriptions}) {
            if (section != null) {
                mTabs.add(section);
            }
        }

        if (home != null) {
            mChips.add(new BrowseSection(home.getId(), getString(R.string.kami_all), home.getType(), home.getResId()));
        }

        for (BrowseSection section : mSections) {
            if (mTabs.contains(section)) {
                continue;
            }

            if (isTopic(section)) {
                mChips.add(section);
            } else {
                mYouSections.add(section);
            }
        }

        if (mChips.size() < 2) {
            mChips.clear();
        }

        mTabs.add(mYouTab);
    }

    /**
     * Upstream topic feeds: shown as filters of Home instead of separate pages.
     */
    private static boolean isTopic(BrowseSection section) {
        if (section.getType() != BrowseSection.TYPE_ROW || section.getIconUrl() != null) {
            return false;
        }

        switch (section.getId()) {
            case MediaGroup.TYPE_MUSIC:
            case MediaGroup.TYPE_GAMING:
            case MediaGroup.TYPE_NEWS:
            case MediaGroup.TYPE_LIVE:
            case MediaGroup.TYPE_TRENDING:
            case MediaGroup.TYPE_SPORTS:
            case MediaGroup.TYPE_KIDS_HOME:
            case MediaGroup.TYPE_MOVIES:
                return true;
            default:
                return false;
        }
    }

    private boolean isChip(int sectionId) {
        for (BrowseSection chip : mChips) {
            if (chip.getId() == sectionId) {
                return true;
            }
        }

        return false;
    }

    private boolean isTab(int sectionId) {
        for (BrowseSection tab : mTabs) {
            if (tab.getId() == sectionId) {
                return true;
            }
        }

        return false;
    }

    private void notifyNavigationChanged() {
        rebuildGroups();

        boolean isBar = mNavLayout == NavAdapter.LAYOUT_BAR;
        boolean isDrawer = mNavLayout == NavAdapter.LAYOUT_DRAWER;

        // Which tab lights up: a Home filter keeps Home on, anything else that isn't a tab is in "You"
        int tabId;

        if (mIsYouShowing) {
            tabId = ID_YOU;
        } else if (isChip(mSelectedSectionId)) {
            tabId = MediaGroup.TYPE_HOME;
        } else if (isTab(mSelectedSectionId)) {
            tabId = mSelectedSectionId;
        } else {
            tabId = ID_YOU;
        }

        if (mBarAdapter != null) {
            ((GridLayoutManager) mNavBar.getLayoutManager()).setSpanCount(Math.max(1, mTabs.size()));
            mBarAdapter.setSelectedId(tabId);
        }

        if (mRailAdapter != null) {
            mRailAdapter.setSelectedId(isDrawer ? mSelectedSectionId : tabId);
        }

        boolean showChips = !isDrawer && !mIsYouShowing && !mChips.isEmpty() && isChip(mSelectedSectionId);
        mChipsView.setVisibility(showChips ? View.VISIBLE : View.GONE);
        mChipsAdapter.setSelectedId(mSelectedSectionId);

        // Search lives next to the bar when folded, settings live in the "You" page
        mSearchButton.setVisibility(isBar ? View.GONE : View.VISIBLE);
        mSettingsButton.setVisibility(isDrawer && findSettingsSection() != null ? View.VISIBLE : View.GONE);

        if (mContent instanceof YouSection) {
            ((YouSection) mContent).notifyChanged();
        }
    }

    private void updateContentWidth() {
        if (mContent == null || mWindowSize == null) {
            return;
        }

        float navDp = 0;

        if (mNavLayout == NavAdapter.LAYOUT_RAIL) {
            navDp = 88;
        } else if (mNavLayout == NavAdapter.LAYOUT_DRAWER) {
            navDp = 240;
        }

        mContent.onWidthChanged(mWindowSize.widthDp - navDp);
    }

    // endregion

    // region Navigation

    private void onNavSectionClicked(BrowseSection section) {
        if (section.getId() == ID_YOU) {
            showYouPage();
            return;
        }

        int index = indexOfSection(section.getId());

        if (section.getId() == mSelectedSectionId && !mIsYouShowing) {
            // Second tap: back to the top, like every phone app
            if (mList.computeVerticalScrollOffset() > 0) {
                mList.smoothScrollToPosition(0);
            } else {
                selectSection(index, true); // refresh
            }
            return;
        }

        selectSection(index, true);
    }

    private void showYouPage() {
        if (mIsYouShowing) {
            mList.smoothScrollToPosition(0);
            return;
        }

        mIsYouShowing = true;
        mHandler.removeCallbacks(mSectionFocused);
        mTitle.setText(R.string.kami_you);
        showContent();
        ensureContent(TYPE_YOU);
        mList.scrollToPosition(0);
        notifyNavigationChanged();
    }

    private BrowseSection findSettingsSection() {
        for (BrowseSection section : mSections) {
            if (section.getType() == BrowseSection.TYPE_SETTINGS_GRID) {
                return section;
            }
        }

        return null;
    }

    private void openSettingsSection() {
        BrowseSection settings = findSettingsSection();

        if (settings != null) {
            selectSection(indexOfSection(settings.getId()), true);
        }
    }

    private int indexOfSection(int sectionId) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    private BrowseSection findSection(int sectionId) {
        int index = indexOfSection(sectionId);
        return index != -1 ? mSections.get(index) : null;
    }

    // endregion

    // region Content

    private MobileSection ensureContent(int type) {
        if (mContent != null && mContentType == type) {
            return mContent;
        }

        if (mContent != null) {
            mContent.detach();
        }

        switch (type) {
            case TYPE_YOU:
                mContent = new YouSection(getContext(), mYouSections, mNavListener);
                break;
            case BrowseSection.TYPE_ROW:
                // One vertical feed of big cards, instead of the TV rows you swipe sideways
                mContent = new FeedSection(getContext(), mBrowsePresenter, FeedSection.MODE_BLOCKS);
                break;
            case BrowseSection.TYPE_SHORTS_GRID:
                mContent = new GridSection(getContext(), mBrowsePresenter, true, false);
                break;
            case BrowseSection.TYPE_MULTI_GRID:
                mContent = new GridSection(getContext(), mBrowsePresenter, false, true);
                break;
            case BrowseSection.TYPE_SETTINGS_GRID:
                mContent = new SettingsSection(getContext());
                break;
            case BrowseSection.TYPE_GRID:
            default:
                mContent = new GridSection(getContext(), mBrowsePresenter, false, false);
                break;
        }

        mContentType = type;
        VideoCardAdapter.applyListPadding(mList, false); // the feeds set their own
        mContent.attach(mList);
        updateContentWidth();

        return mContent;
    }

    private int getCurrentType() {
        BrowseSection current = mBrowsePresenter.getCurrentSection();

        if (current == null) {
            current = findSection(mSelectedSectionId);
        }

        return current != null ? current.getType() : BrowseSection.TYPE_GRID;
    }

    private void showContent() {
        mErrorPanel.setVisibility(View.GONE);
        mRefresh.setVisibility(View.VISIBLE);
    }

    private void applyPendingSelection() {
        if (mContent == null) {
            return;
        }

        if (mPendingItemIndex != -1) {
            mContent.setPosition(mPendingItemIndex);
            mPendingItemIndex = -1;
        }

        if (mPendingItem != null) {
            mContent.selectItem(mPendingItem);
            mPendingItem = null;
        }
    }

    // endregion

    // region BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existing = indexOfSection(section.getId());

        if (existing != -1 && (index == -1 || existing == index)) {
            return;
        }

        if (existing != -1) {
            mSections.remove(existing);
        }

        if (index == -1 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }

        notifyNavigationChanged();
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        int index = indexOfSection(section.getId());

        if (index != -1) {
            mSections.remove(index);
            notifyNavigationChanged();
        }
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        notifyNavigationChanged();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || mSections.isEmpty()) {
            return;
        }

        // Fallback to the last section if index above size
        BrowseSection section = mSections.get(Math.min(index, mSections.size() - 1));

        mSelectedSectionId = section.getId();
        mIsYouShowing = false;
        // Home and its filters keep the app name, like the other ORIKAMI apps
        mTitle.setText(section.getId() == MediaGroup.TYPE_HOME || isTopic(section) ? getString(R.string.app_name) : section.getTitle());
        notifyNavigationChanged();
        showContent();

        if (section.getType() == BrowseSection.TYPE_ERROR) {
            showError((ErrorFragmentData) section.getData());
        } else {
            ensureContent(section.getType()).clear();
            mList.scrollToPosition(0);
        }

        // Wait till the current action finished. Upstream does the same after the fragment switch.
        mHandler.removeCallbacks(mSectionFocused);
        mHandler.post(mSectionFocused);
    }

    private final Runnable mSectionFocused = () -> {
        if (mSelectedSectionId != -1) {
            mBrowsePresenter.onSectionFocused(mSelectedSectionId);
        }
    };

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null || mIsYouShowing) {
            return;
        }

        showContent();
        ensureContent(getCurrentType()).update(group);
        applyPendingSelection();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null || mIsYouShowing) {
            return;
        }

        showContent();
        MobileSection content = ensureContent(BrowseSection.TYPE_SETTINGS_GRID);

        if (content instanceof SettingsSection) {
            ((SettingsSection) content).update(group);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (mContent != null && !mIsYouShowing) {
            mContent.clear();
        }
    }

    @Override
    public void selectSectionItem(int index) {
        if (index < 0) {
            return;
        }

        mPendingItemIndex = index;
        mPendingItem = null;
        applyPendingSelection();
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item == null) {
            return;
        }

        mPendingItem = item;
        mPendingItemIndex = -1;
        applyPendingSelection();
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (data == null || mErrorPanel == null || mIsYouShowing) {
            return;
        }

        mRefresh.setVisibility(View.GONE);
        mErrorPanel.setVisibility(View.VISIBLE);
        mErrorMessage.setText(data.getMessage());

        String action = data.getActionText();
        mErrorAction.setVisibility(action != null ? View.VISIBLE : View.GONE);
        mErrorAction.setText(action);
        mErrorAction.setOnClickListener(v -> data.onAction());
    }

    @Override
    public void showProgressBar(boolean show) {
        mIsProgressShowing = show;

        // Essential. Need to run on the main thread.
        mHandler.post(() -> {
            if (mProgress != null) {
                mProgress.setVisibility(mIsProgressShowing ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public boolean isProgressBarShowing() {
        return mIsProgressShowing;
    }

    @Override
    public void focusOnContent() {
        // No focus on a touch screen
    }

    @Override
    public boolean isEmpty() {
        return mContent == null || mContent.isEmpty();
    }

    @Override
    public void updateBadge() {
        // The top bar shows the section title instead of the TV badge
    }

    // endregion
}
