package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
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
import com.liskovsoft.smartyoutubetv2.tv.mobile.search.MobileSearchFragment;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.FeedSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.GridSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.SettingsSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.Fonts;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch version of the upstream BrowseFragment: same {@link BrowseView} contract, same {@link BrowsePresenter}.<br/>
 * Same layout on the cover screen and the inner screen, portrait or landscape:
 * the name of the app on top with the notifications and search buttons, topic filters under it on Home,
 * and a bottom bar with Home, Shorts, Subscriptions, History and Settings.<br/>
 * Topic feeds and the user's library (playlists, queue, notifications, pinned channels...) are on the "Explore" page.<br/>
 * One column of edge to edge cards on the narrow cover screen, a grid elsewhere.
 * Only real tablets get a drawer with every section. The layout follows every size change live.
 */
public class MobileBrowseFragment extends Fragment implements BrowseView, MobileSearchFragment.Host {
    /**
     * Pseudo section: the "Explore" page (compass at the start of the filters).
     */
    private static final int ID_EXPLORE = Integer.MIN_VALUE + 1;
    private static final int TYPE_EXPLORE = Integer.MIN_VALUE + 1;
    private static final float DRAWER_MIN_DP = 1200;
    private static final float BAR_MAX_DP = 720;
    private static final long REFRESH_TIMEOUT_MS = 8_000;
    /** The screen is rebuilt for a new theme: the Settings tab opens again as soon as it is there */
    private static boolean sReopenSettings;

    private final List<BrowseSection> mSections = new ArrayList<>();
    // Derived from mSections each time it changes
    private final List<BrowseSection> mTabs = new ArrayList<>();
    private final List<BrowseSection> mChips = new ArrayList<>();
    private final List<BrowseSection> mExploreSections = new ArrayList<>();
    private final List<BrowseSection> mLibrarySections = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private BrowsePresenter mBrowsePresenter;
    private CrashRestorer mCrashRestorer;
    private boolean mIsFragmentCreated;

    private View mRoot;
    // Search inside Home: the field takes the top bar, the results take the content
    private View mTopBar;
    private View mSearchBar;
    private View mSearchHost;
    private MobileSearchFragment mSearch;
    private int mKeyboardHeight;
    private TextView mPageTitle;
    private View mNotificationsButton;
    private RecyclerView mDrawer;
    private LinearLayout mNavBar;
    private View mBottom;
    private boolean mDarkTabs; // a short is playing over Home: the tabs are drawn on black
    private RecyclerView mChipsView;
    private FilterChipsAdapter mChipsAdapter;
    private SwipeRefreshLayout mRefresh;
    private RecyclerView mList;
    private View mErrorPanel;
    private TextView mErrorMessage;
    private Button mErrorAction;
    private ProgressBar mProgress;

    private NavAdapter mDrawerAdapter;
    private boolean mIsDrawer;
    private boolean mIsNavReady;
    private WindowSize mWindowSize;

    private MobileSection mContent;
    private int mContentType = -1;
    private int mSelectedSectionId = -1;
    private int mPendingItemIndex = -1;
    private Video mPendingItem;
    private boolean mIsProgressShowing;
    // ID_EXPLORE while that page is shown, 0 otherwise
    private int mHubId;

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

        Fonts.applyBrand(mRoot.findViewById(R.id.kami_brand));
        mPageTitle = mRoot.findViewById(R.id.kami_page_title);
        mNotificationsButton = mRoot.findViewById(R.id.kami_action_notifications);
        mDrawer = mRoot.findViewById(R.id.kami_nav_rail);
        mNavBar = mRoot.findViewById(R.id.kami_nav_bar);
        mBottom = mRoot.findViewById(R.id.kami_bottom);
        mChipsView = mRoot.findViewById(R.id.kami_chips);
        mRefresh = mRoot.findViewById(R.id.kami_refresh);
        mList = mRoot.findViewById(R.id.kami_list);
        mErrorPanel = mRoot.findViewById(R.id.kami_error);
        mErrorMessage = mRoot.findViewById(R.id.kami_error_message);
        mErrorAction = mRoot.findViewById(R.id.kami_error_action);
        mProgress = mRoot.findViewById(R.id.kami_progress);

        mTopBar = mRoot.findViewById(R.id.kami_top_bar);
        mSearchBar = mRoot.findViewById(R.id.kami_search_bar);
        mSearchHost = mRoot.findViewById(R.id.kami_search_host);
        mRoot.findViewById(R.id.kami_action_search).setOnClickListener(v -> openSearch());
        mNotificationsButton.setOnClickListener(v -> openSection(MediaGroup.TYPE_NOTIFICATIONS));
        mRoot.findViewById(R.id.kami_brand_group).setOnClickListener(v -> openSection(MediaGroup.TYPE_HOME));

        mChipsAdapter = new FilterChipsAdapter(mChips, mNavListener, v -> showHub(ID_EXPLORE));
        mChipsView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mChipsView.setAdapter(mChipsAdapter);

        mRefresh.setColorSchemeColors(KamiTheme.color(requireContext(), R.attr.kamiAccent));
        mRefresh.setProgressBackgroundColorSchemeColor(KamiTheme.color(requireContext(), R.attr.kamiSurfaceHigh));
        mRefresh.setOnRefreshListener(() -> {
            // The spinner stays while the page reloads, see showProgressBar. Safety stop if nothing comes back.
            mHandler.removeCallbacks(mStopRefreshing);
            mHandler.postDelayed(mStopRefreshing, REFRESH_TIMEOUT_MS);
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
        mSearch = null; // goes away with this view

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

        // Cover screen and inner screen, portrait or landscape: the bottom bar. Real tablets: a drawer.
        boolean isDrawer = size.physicalWidthDp >= DRAWER_MIN_DP && !size.isShort();

        if (isDrawer != mIsDrawer || !mIsNavReady) {
            mIsDrawer = isDrawer;
            mIsNavReady = true;
            setupNavigation();
        }

        // On a wide screen the bar keeps phone proportions, centered
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mNavBar.getLayoutParams();
        lp.width = size.physicalWidthDp > BAR_MAX_DP + 40 ? Math.round(BAR_MAX_DP * WindowSize.systemDensity()) : ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mNavBar.setLayoutParams(lp);

        updateContentWidth();
    }

    private final NavAdapter.Listener mNavListener = new NavAdapter.Listener() {
        @Override
        public void onSectionClicked(BrowseSection section) {
            onNavSectionClicked(section);
        }

        @Override
        public void onSectionLongClicked(BrowseSection section) {
            if (section.getId() != ID_EXPLORE) {
                mBrowsePresenter.onSectionLongPressed(section.getId());
            }
        }
    };

    private void setupNavigation() {
        mBottom.setVisibility(mIsDrawer || mKeyboardHeight > 0 ? View.GONE : View.VISIBLE);
        mDrawer.setVisibility(mIsDrawer ? View.VISIBLE : View.GONE);

        if (mIsDrawer) {
            mDrawerAdapter = new NavAdapter(mSections, NavAdapter.LAYOUT_DRAWER, mNavListener);
            mDrawer.setLayoutManager(new LinearLayoutManager(getContext()));
            mDrawer.setAdapter(mDrawerAdapter);
        } else {
            mDrawerAdapter = null;
            mDrawer.setAdapter(null);
        }

        notifyNavigationChanged();
    }

    /**
     * Splits the upstream sections into tabs, Home filters and the two groups of the "Explore" page.
     */
    private void rebuildGroups() {
        mTabs.clear();
        mChips.clear();
        mExploreSections.clear();
        mLibrarySections.clear();

        BrowseSection home = findSection(MediaGroup.TYPE_HOME);
        BrowseSection shorts = findSection(MediaGroup.TYPE_SHORTS);
        BrowseSection subscriptions = findSection(MediaGroup.TYPE_SUBSCRIPTIONS);
        BrowseSection history = findSection(MediaGroup.TYPE_HISTORY);

        if (subscriptions == null) {
            subscriptions = findSection(MediaGroup.TYPE_CHANNEL_UPLOADS);
        }

        // Settings are the fifth icon of the bar
        BrowseSection settings = findSettingsSection();

        for (BrowseSection section : new BrowseSection[] {home, shorts, subscriptions, history, settings}) {
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
                mExploreSections.add(section);
            } else {
                mLibrarySections.add(section);
            }
        }

        if (mChips.size() < 2) {
            mChips.clear();
        }
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
        return indexOfChip(sectionId) != -1;
    }

    private int indexOfChip(int sectionId) {
        for (int i = 0; i < mChips.size(); i++) {
            if (mChips.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    private boolean isTab(int sectionId) {
        for (BrowseSection tab : mTabs) {
            if (tab.getId() == sectionId) {
                return true;
            }
        }

        return false;
    }

    /**
     * Which tab lights up: the tab itself, or Home for its filters, "Explore" and the library sections opened from it.
     */
    private int getSelectedTabId() {
        // "Explore" first: the selected section is still the previous one while it's shown
        if (mHubId == 0 && isTab(mSelectedSectionId)) {
            return mSelectedSectionId;
        }

        return MediaGroup.TYPE_HOME;
    }

    private void notifyNavigationChanged() {
        rebuildGroups();

        if (mRoot == null) {
            return;
        }

        if (!mIsDrawer) {
            rebuildBar();
        }

        if (mDrawerAdapter != null) {
            mDrawerAdapter.notifyDataSetChanged();
            mDrawerAdapter.setSelectedId(mSelectedSectionId);
        }

        boolean showChips = !mIsDrawer && mHubId == 0 && !mChips.isEmpty() && isChip(mSelectedSectionId);
        mChipsView.setVisibility(showChips ? View.VISIBLE : View.GONE);
        mChipsAdapter.setSelectedId(mSelectedSectionId);

        if (showChips) {
            // The picked filter may be off screen (e.g. restored at start, or picked from "Explore")
            int chipIndex = indexOfChip(mSelectedSectionId);

            if (chipIndex != -1) {
                mChipsView.post(() -> mChipsView.smoothScrollToPosition(chipIndex + 1)); // +1: the "Explore" button
            }
        }

        // Pages that aren't Home or one of its filters get their name on top
        CharSequence pageTitle = null;

        if (mHubId == ID_EXPLORE) {
            pageTitle = getString(R.string.kami_explore);
        } else if (!showChips) {
            BrowseSection section = findSection(mSelectedSectionId);
            pageTitle = section != null ? section.getTitle() : null;
        }

        mPageTitle.setText(pageTitle);
        mPageTitle.setVisibility(pageTitle != null && pageTitle.length() > 0 ? View.VISIBLE : View.GONE);

        mNotificationsButton.setVisibility(findSection(MediaGroup.TYPE_NOTIFICATIONS) != null ? View.VISIBLE : View.GONE);

        if (mContent instanceof ExploreSection) {
            ((ExploreSection) mContent).notifyChanged();
        }
    }

    /**
     * Home, Shorts, Subscriptions, History, Settings.
     */
    private void rebuildBar() {
        mNavBar.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mNavBar.getContext());
        int selectedId = getSelectedTabId();

        for (int i = 0; i < mTabs.size(); i++) {
            BrowseSection tab = mTabs.get(i);
            View item = inflater.inflate(R.layout.kami_item_tab, mNavBar, false);
            ImageView icon = item.findViewById(R.id.kami_nav_icon);
            TextView label = item.findViewById(R.id.kami_nav_label);
            label.setText(tab.getTitle());
            int ownIcon = tab.getType() == BrowseSection.TYPE_SETTINGS_GRID ? R.drawable.kami_ic_settings : getTabIcon(tab.getId());

            if (ownIcon != 0) {
                icon.setImageResource(ownIcon);
            } else {
                NavAdapter.bindIcon(icon, tab);
            }
            item.setSelected(tab.getId() == selectedId); // reaches the icon, its pill and the label
            paintTab(item);
            item.setContentDescription(tab.getTitle());
            item.setOnClickListener(v -> onNavSectionClicked(tab));
            item.setOnLongClickListener(v -> {
                mNavListener.onSectionLongClicked(tab);
                return true;
            });
            mNavBar.addView(item);
        }
    }

    private void paintTab(View item) {
        if (getContext() == null) {
            return;
        }

        android.content.res.ColorStateList content = ContextCompat.getColorStateList(getContext(), mDarkTabs ? R.color.kami_nav_content_dark : R.color.kami_nav_content);
        ImageViewCompat.setImageTintList(item.findViewById(R.id.kami_nav_icon), content);
        ((TextView) item.findViewById(R.id.kami_nav_label)).setTextColor(content);
        item.findViewById(R.id.kami_nav_pill).setBackgroundResource(mDarkTabs ? R.drawable.kami_tab_indicator_dark : R.drawable.kami_tab_indicator);
    }

    /**
     * Under a short the tabs stay, dark like the YouTube app; back to the page colors when it ends.
     */
    public void setDarkTabs(boolean dark) {
        if (mBottom == null || mDarkTabs == dark) {
            return;
        }

        mDarkTabs = dark;

        if (dark) {
            mBottom.setBackgroundColor(0xFF0F0F0F);
        } else {
            mBottom.setBackgroundResource(R.drawable.kami_bottom_bar_bg);
        }

        for (int i = 0; i < mNavBar.getChildCount(); i++) {
            paintTab(mNavBar.getChildAt(i));
        }
    }

    /**
     * Height of the bottom tabs, 0 when they're hidden (side rail, keyboard).
     */
    public int getTabBarHeight() {
        return mBottom != null && mBottom.getVisibility() == View.VISIBLE ? mBottom.getHeight() : 0;
    }

    /**
     * Search asked from outside Home (the Shorts player).
     */
    public void showSearch() {
        openSearch();
    }

    private static int getTabIcon(int sectionId) {
        switch (sectionId) {
            case MediaGroup.TYPE_HOME:
                return R.drawable.kami_ic_home;
            case MediaGroup.TYPE_SHORTS:
                return R.drawable.kami_ic_shorts;
            case MediaGroup.TYPE_SUBSCRIPTIONS:
            case MediaGroup.TYPE_CHANNEL_UPLOADS:
                return R.drawable.kami_ic_subscriptions;
            case MediaGroup.TYPE_HISTORY:
                return R.drawable.kami_ic_history;
            default:
                return 0;
        }
    }

    private void updateContentWidth() {
        if (mContent == null || mWindowSize == null) {
            return;
        }

        mContent.onWidthChanged(mWindowSize.widthDp - (mIsDrawer ? 240 : 0));
    }

    // endregion

    // region Navigation

    private void onNavSectionClicked(BrowseSection section) {
        if (getActivity() instanceof MobileBrowseActivity) {
            ((MobileBrowseActivity) getActivity()).onTabTapped(); // a short under the tabs ends, the mini player goes on
        }

        closeSearch();
        int index = indexOfSection(section.getId());

        if (section.getId() == mSelectedSectionId && mHubId == 0) {
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

    /**
     * Back key. Any page that isn't Home (a tab, a filter, "Explore", a library section) goes back to Home first.
     * Returns false on Home: the activity then applies the upstream exit rule (double back).
     */
    public boolean goBack() {
        if (mSearch != null) {
            // First the suggestions give way to the results, then the search closes
            if (!mSearch.goBack()) {
                closeSearch();
            }

            return true;
        }

        BrowseSection home = findSection(MediaGroup.TYPE_HOME);

        if (home == null || mSections.isEmpty()) {
            return false;
        }

        if (mHubId == 0 && mSelectedSectionId == home.getId()) {
            return false;
        }

        selectSection(indexOfSection(home.getId()), true);
        return true;
    }

    private void openSection(int sectionId) {
        int index = indexOfSection(sectionId);

        if (index != -1) {
            onNavSectionClicked(mSections.get(index));
        }
    }

    /**
     * "Explore": a list of sections, not a presenter page.
     */
    // region Search

    /**
     * The field grows from the search button over the logo, the keyboard opens, and the results
     * take the place of the feed. The tabs stay: a tap on one ends the search.
     */
    private void openSearch() {
        if (mSearch != null || getContext() == null || mSearchHost == null) {
            return;
        }

        mSearch = new MobileSearchFragment();
        mSearch.setBar(mSearchBar);

        mSearchHost.setVisibility(View.VISIBLE);
        mSearchBar.setVisibility(View.VISIBLE);
        getChildFragmentManager().beginTransaction().add(R.id.kami_search_host, mSearch, "search").commitNowAllowingStateLoss();

        animateSearch(true, null);
    }

    private void closeSearch() {
        if (mSearch == null) {
            return;
        }

        MobileSearchFragment search = mSearch;
        mSearch = null;
        search.hideKeyboard();
        search.onFinish(); // same as leaving the search page: the presenter forgets the query and the filters

        animateSearch(false, () -> {
            if (isAdded() && search.isAdded()) {
                getChildFragmentManager().beginTransaction().remove(search).commitNowAllowingStateLoss();
            }

            mSearchHost.setVisibility(View.GONE);
            mSearchBar.setVisibility(View.GONE);
        });
    }

    @Override
    public void onSearchClosed() {
        closeSearch();
    }

    /**
     * Keyboard height over the content, 0 when it's closed. The keyboard covers the tabs (hidden meanwhile)
     * and the lists end right above it, so the suggestions and results get all the room left.
     */
    public void onKeyboardChanged(int height) {
        if (mRoot == null || mKeyboardHeight == height) {
            return;
        }

        mKeyboardHeight = height;
        mBottom.setVisibility(mIsDrawer || height > 0 ? View.GONE : View.VISIBLE);
        int padding = height > 0 ? Math.max(0, height) : 0;
        mRoot.setPadding(mRoot.getPaddingLeft(), mRoot.getPaddingTop(), mRoot.getPaddingRight(), padding);
    }

    /**
     * The watch panel goes over Home (true) or leaves it (false). Home keeps its place and its search.
     */
    public void onPlayerShown(boolean shown) {
        if (shown && mSearch != null) {
            mSearch.hideKeyboard();
        }
    }

    private void animateSearch(boolean open, Runnable onEnd) {
        int duration = 220;
        // The field grows from the right, where the search button is, and shrinks back there
        mSearchBar.setPivotX(mSearchBar.getWidth() > 0 ? mSearchBar.getWidth() - WindowSize.dpToPx(getContext(), 28) : 0);
        mSearchBar.setPivotY(mSearchBar.getHeight() / 2f);
        mSearchBar.animate().cancel();
        mSearchHost.animate().cancel();
        mTopBar.animate().cancel();

        if (open) {
            mSearchBar.setAlpha(0f);
            mSearchBar.setScaleX(0.4f);
            mSearchHost.setAlpha(0f);
        }

        mSearchBar.animate()
                .alpha(open ? 1f : 0f)
                .scaleX(open ? 1f : 0.4f)
                .setDuration(duration)
                .setInterpolator(open ? new DecelerateInterpolator() : new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (!open) {
                        mSearchBar.setScaleX(1f);
                        mSearchBar.setAlpha(1f);
                    }
                })
                .start();
        mTopBar.animate().alpha(open ? 0f : 1f).setDuration(open ? 120 : duration).start();
        mSearchHost.animate()
                .alpha(open ? 1f : 0f)
                .setDuration(open ? 160 : duration)
                .withEndAction(() -> {
                    if (onEnd != null) {
                        onEnd.run();
                    }

                    mSearchHost.setAlpha(1f);
                })
                .start();
    }

    // endregion

    private void showHub(int hubId) {
        if (mHubId == hubId) {
            mList.smoothScrollToPosition(0);
            return;
        }

        mHubId = hubId;
        mHandler.removeCallbacks(mSectionFocused);
        showContent();
        ensureContent(TYPE_EXPLORE);
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
            case TYPE_EXPLORE:
                mContent = new ExploreSection(getContext(), mExploreSections, mLibrarySections, mNavListener);
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

        if (sReopenSettings && section.getType() == BrowseSection.TYPE_SETTINGS_GRID && mRoot != null) {
            sReopenSettings = false;
            mRoot.post(() -> openSection(section.getId()));
        }
    }

    public static void reopenSettings() {
        sReopenSettings = true;
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
        mHubId = 0;
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

    private final Runnable mStopRefreshing = () -> {
        if (mRefresh != null) {
            mRefresh.setRefreshing(false);
        }
    };

    private final Runnable mSectionFocused = () -> {
        if (mSelectedSectionId != -1) {
            mBrowsePresenter.onSectionFocused(mSelectedSectionId);
        }
    };

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null || mHubId != 0) {
            return;
        }

        showContent();
        ensureContent(getCurrentType()).update(group);
        applyPendingSelection();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null || mHubId != 0) {
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
        if (mContent != null && mHubId == 0) {
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
        if (data == null || mErrorPanel == null || mHubId != 0) {
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
                // The pull to refresh spinner is the progress indicator while it's shown
                mProgress.setVisibility(mIsProgressShowing && !(mRefresh != null && mRefresh.isRefreshing()) ? View.VISIBLE : View.GONE);
            }

            if (!mIsProgressShowing && mRefresh != null && mRefresh.isRefreshing()) {
                mHandler.removeCallbacks(mStopRefreshing);
                mRefresh.setRefreshing(false);
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
        // The page title replaces the TV badge
    }

    // endregion
}
