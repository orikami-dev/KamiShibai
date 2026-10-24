package com.liskovsoft.smartyoutubetv2.tv.mobile.dialog;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileScreen;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;
import com.liskovsoft.smartyoutubetv2.tv.mobile.watch.WatchPanel;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Every menu and setting of the app.<br/>
 * The upstream presenters build the same lists of categories and options for the TV dialogs; this view shows them
 * the Android way. Settings and their sub-menus are full screen pages with a top app bar and a back arrow:
 * pick a category, its values open on the next page, back goes up one level.
 * Short menus of actions (the "more" menu of a video) and anything opened over the player
 * stay a bottom sheet, so the video and the page behind remain in sight.<br/>
 * A menu opened from a button of the player ({@link DialogAnchor}) grows out of that button and stays inside its
 * block (the video, the page under it), like the search field in the top bar. Its settings are tiles on about
 * three rows, as many columns as needed, and the values of a setting open in the same place.
 */
public class MobileAppDialogActivity extends MobileActivity implements AppDialogView {
    private static final float SHEET_MAX_WIDTH_DP = 520;
    private static final float SHEET_MAX_HEIGHT_RATIO = 0.82f;
    private static final float PAGE_MAX_WIDTH_DP = 720;
    private static final int MENU_INSET_DP = 6;
    private static final int MENU_ROWS = 3;
    private static final int TILE_MIN_WIDTH_DP = 104;

    /**
     * One screen of the sheet: the categories of a dialog, the options of one category, or a text.
     */
    private static final class Page {
        static final int CATEGORIES = 0;
        static final int OPTIONS = 1;
        static final int TEXT = 2;

        final int kind;
        final CharSequence title;
        final List<OptionCategory> categories;
        final OptionCategory category;
        final CharSequence text;
        boolean[] checked;
        int selectedIndex = -1;

        Page(int kind, CharSequence title, List<OptionCategory> categories, OptionCategory category, CharSequence text) {
            this.kind = kind;
            this.title = title;
            this.categories = categories;
            this.category = category;
            this.text = text;

            if (category != null && category.options != null) {
                checked = new boolean[category.options.size()];

                for (int i = 0; i < checked.length; i++) {
                    checked[i] = category.options.get(i).isSelected();

                    if (checked[i] && selectedIndex == -1) {
                        selectedIndex = i;
                    }
                }
            }
        }

        boolean isRadio() {
            return category != null && category.type == OptionCategory.TYPE_RADIO_LIST;
        }
    }

    private final List<Page> mStack = new ArrayList<>();
    private final Adapter mAdapter = new Adapter();
    private AppDialogPresenter mPresenter;
    private View mRoot;
    // Decided by the first list shown, kept for the sub-pages
    private boolean mIsSheet;
    private View mSheet;
    private TextView mTitle;
    private View mBack;
    private RecyclerView mList;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private int mId;
    // Menu grown out of a button, null for the usual sheet and page
    private DialogAnchor mAnchor;
    private final Rect mMenuArea = new Rect();
    private final Rect mMenuButton = new Rect();
    private int mMenuMaxList;
    private boolean mMenuShown;
    private boolean mClosing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAnchor = DialogAnchor.take();
        mPresenter = AppDialogPresenter.instance(this);
        mPresenter.setView(this);
        // The first show() picks the page or the sheet
        mPresenter.onViewInitialized();

        if (mRoot == null && !isFinishing()) {
            setupViews(true);
        }
    }

    private void setupViews(boolean isSheet) {
        mIsSheet = isSheet;

        if (!isSheet) {
            mAnchor = null; // settings stay pages
        }

        setContentView(mAnchor != null ? R.layout.kami_activity_dialog_menu : isSheet ? R.layout.kami_activity_dialog : R.layout.kami_activity_page);

        if (!isSheet) {
            // The window is see-through for the sheet: a page paints its own background, under the system bars too
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme.color(this, R.attr.kamiInk)));
            MobileScreen.edgeToEdge(this, true);
            overridePendingTransition(R.anim.kami_fade_in, R.anim.kami_stay);
        }

        mRoot = findViewById(R.id.kami_dialog_root);
        mSheet = findViewById(R.id.kami_dialog_sheet);
        mTitle = findViewById(R.id.kami_dialog_title);
        mBack = findViewById(R.id.kami_dialog_back);
        mList = findViewById(R.id.kami_dialog_list);
        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mAdapter);
        mBack.setOnClickListener(v -> goBack());

        if (isSheet) {
            mRoot.setOnClickListener(v -> {
                if (!mIsOverlay) {
                    finish();
                }
            });
            findViewById(R.id.kami_dialog_close).setOnClickListener(v -> finish());

            // The scrim covers the bars, the sheet stays above the navigation bar and the keyboard
            MobileScreen.padForBars(mRoot, true);

            if (WatchPanel.isVideoOnlyShown()) {
                // Over the fullscreen video the bars stay away, as in the player below
                MobileScreen.hideSystemBars(this);
            }

            if (mAnchor != null) {
                // No window animation: the menu itself grows out of its button once placed
                overridePendingTransition(0, 0);
                mSheet.setAlpha(0f);
                mSheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    if (r - l != or - ol || b - t != ob - ot) {
                        placeMenu();
                    }
                });
                mRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applySize(r - l, b - t));
                return;
            }

            // Rises from the bottom while the scrim fades in
            mRoot.setAlpha(0f);
            mRoot.animate().alpha(1f).setDuration(160).start();
            mSheet.setTranslationY(WindowSize.dpToPx(this, 48));
            mSheet.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        }

        mRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applySize(r - l, b - t));
    }

    /**
     * Settings are pages. A menu made only of actions, or anything shown over the player, is a sheet.
     */
    private boolean isSheet(List<OptionCategory> categories, boolean isTransparent, boolean isOverlay) {
        if (isTransparent || isOverlay || ViewManager.instance(this).getTopView() == PlaybackView.class) {
            return true;
        }

        for (OptionCategory category : categories) {
            if (category.type != OptionCategory.TYPE_SINGLE_BUTTON) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Dialogs may be destroyed with a delay: let the presenter know this one is on its way out
        mIsPaused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPresenter != null && mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Respect PIP mode of the player below
        if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof PlaybackActivity) {
            ((PlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        }

        finish();
    }

    @Override
    public void finish() {
        // Closing is asked twice when a value is picked: by the option (presenter.closeDialog) and by the list going
        // back. finishReally pops the screen stack each time, which also popped the player: Home came back instead.
        if (isFinishing()) {
            return;
        }

        if (mClosing) {
            return;
        }

        if (mPresenter != null && mPresenter.getView() == this) {
            mPresenter.onFinish();
        }

        if (mAnchor != null && mSheet != null && mMenuShown) {
            // Back into its button
            mClosing = true;
            mSheet.animate().cancel();
            mSheet.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .setDuration(150)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        if (!isFinishing()) {
                            finishReally();
                            overridePendingTransition(0, 0);
                        }
                    })
                    .start();
            return;
        }

        finishReally();
    }

    @Override
    protected boolean paintsBehindBars() {
        return false; // see-through window, the sheet pads its own root and the page paints the content frame
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (canGoBack()) {
            goBack();
            return;
        }

        finish();
    }

    // region Size

    private void applySize(int rootWidth, int rootHeight) {
        if (mRoot == null || rootWidth == 0) {
            return;
        }

        if (mAnchor != null) {
            applyMenuSize(rootWidth, rootHeight);
            return;
        }

        if (mIsSheet) {
            applySheetSize(rootWidth, rootHeight);
            return;
        }

        // Wide screens: the page keeps a readable width, centered
        int maxWidth = Math.round(PAGE_MAX_WIDTH_DP * WindowSize.density(this));
        int side = rootWidth > maxWidth ? (rootWidth - maxWidth) / 2 : 0;

        if (mList.getPaddingLeft() != side) {
            mList.setPadding(side, mList.getPaddingTop(), side, mList.getPaddingBottom());
        }
    }

    private void applySheetSize(int rootWidth, int rootHeight) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mSheet.getLayoutParams();
        int maxWidth = Math.round(SHEET_MAX_WIDTH_DP * WindowSize.density(this));
        int width = rootWidth > maxWidth ? maxWidth : ViewGroup.LayoutParams.MATCH_PARENT;
        int maxList = Math.round(rootHeight * SHEET_MAX_HEIGHT_RATIO) - WindowSize.dpToPx(this, 80);

        boolean changed = lp.width != width || lp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.width = width;
        mSheet.setLayoutParams(lp);

        ViewGroup.LayoutParams listParams = mList.getLayoutParams();

        if (listParams.height != ViewGroup.LayoutParams.WRAP_CONTENT && !changed) {
            return;
        }

        // RecyclerView doesn't wrap with a maximum: measure the content and cap it
        mList.post(() -> {
            int content = 0;

            for (int i = 0; i < mList.getChildCount(); i++) {
                content += mList.getChildAt(i).getHeight();
            }

            boolean tooTall = mList.computeVerticalScrollRange() > maxList;
            ViewGroup.LayoutParams params = mList.getLayoutParams();
            int wanted = tooTall ? maxList : ViewGroup.LayoutParams.WRAP_CONTENT;

            if (params.height != wanted) {
                params.height = wanted;
                mList.setLayoutParams(params);
            }
        });
    }

    /**
     * The menu takes the width of its block (up to the sheet width), and at most the block's height:
     * more than that scrolls inside.
     */
    private void applyMenuSize(int rootWidth, int rootHeight) {
        int[] rootScreen = new int[2];
        mRoot.getLocationOnScreen(rootScreen);
        int inset = WindowSize.dpToPx(this, MENU_INSET_DP);

        Rect area = new Rect(mRoot.getPaddingLeft(), mRoot.getPaddingTop(), rootWidth - mRoot.getPaddingRight(), rootHeight - mRoot.getPaddingBottom());
        Rect block = new Rect(mAnchor.block);
        block.offset(-rootScreen[0], -rootScreen[1]);

        if (!area.intersect(block)) {
            // The block went away (rotation...): the whole screen is the block
            area.set(mRoot.getPaddingLeft(), mRoot.getPaddingTop(), rootWidth - mRoot.getPaddingRight(), rootHeight - mRoot.getPaddingBottom());
        }

        area.inset(inset, inset);
        mMenuArea.set(area);
        mMenuButton.set(mAnchor.button);
        mMenuButton.offset(-rootScreen[0], -rootScreen[1]);

        int width = Math.max(WindowSize.dpToPx(this, 200), Math.min(area.width(), Math.round(SHEET_MAX_WIDTH_DP * WindowSize.density(this))));
        mMenuMaxList = Math.max(WindowSize.dpToPx(this, 96), area.height() - mRoot.findViewById(R.id.kami_dialog_header).getLayoutParams().height);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mSheet.getLayoutParams();

        if (lp.width != width) {
            lp.width = width;
            mSheet.setLayoutParams(lp);
        }

        applyListLayout(width);
        capMenuList();
        mSheet.post(this::placeMenu);
    }

    /**
     * RecyclerView doesn't wrap with a maximum: measure the content and cap it.
     */
    private void capMenuList() {
        mList.post(() -> {
            int range = mList.computeVerticalScrollRange();
            ViewGroup.LayoutParams params = mList.getLayoutParams();
            int wanted = range > mMenuMaxList ? mMenuMaxList : ViewGroup.LayoutParams.WRAP_CONTENT;

            if (params.height != wanted) {
                params.height = wanted;
                mList.setLayoutParams(params);
            }
        });
    }

    /**
     * Next to its button, on the side of the block where there is room, then it grows out of the button
     * the first time.
     */
    private void placeMenu() {
        int width = mSheet.getWidth();
        int height = mSheet.getHeight();

        if (mAnchor == null || width == 0 || mMenuArea.isEmpty()) {
            return;
        }

        Rect area = mMenuArea;
        float x = clamp(mMenuButton.centerX() - width / 2f, area.left, area.right - width);
        // A button low in its block opens the menu upwards, a button high opens it downwards
        float y = mMenuButton.centerY() > area.centerY() ? area.bottom - height : area.top;
        y = clamp(y, mRoot.getPaddingTop(), mRoot.getHeight() - mRoot.getPaddingBottom() - height);

        mSheet.setX(x);
        mSheet.setY(y);
        mSheet.setPivotX(clamp(mMenuButton.centerX() - x, 0, width));
        mSheet.setPivotY(clamp(mMenuButton.centerY() - y, 0, height));

        if (!mMenuShown) {
            mMenuShown = true;
            mSheet.setScaleX(0.3f);
            mSheet.setScaleY(0.3f);
            mSheet.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private static float clamp(float value, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    /**
     * Tiles for the settings and their values: about {@link #MENU_ROWS} rows, as many columns as that takes
     * and the width allows. A plain list for menus of actions and texts.
     */
    private void applyListLayout(int widthPx) {
        if (mStack.isEmpty()) {
            return;
        }

        RecyclerView.LayoutManager manager = mList.getLayoutManager();

        if (!isGrid(current())) {
            if (manager instanceof GridLayoutManager || manager == null) {
                mList.setLayoutManager(new LinearLayoutManager(this));
            }
            return;
        }

        int count = mAdapter.getItemCount();
        int widthDp = Math.round(widthPx / WindowSize.density(this));
        int span = Math.max(1, Math.min((count + MENU_ROWS - 1) / MENU_ROWS, widthDp / TILE_MIN_WIDTH_DP));

        if (manager instanceof GridLayoutManager) {
            if (((GridLayoutManager) manager).getSpanCount() != span) {
                ((GridLayoutManager) manager).setSpanCount(span);
            }
        } else {
            mList.setLayoutManager(new GridLayoutManager(this, span));
        }
    }

    private boolean isGrid(Page page) {
        if (mAnchor == null || page.kind == Page.TEXT) {
            return false;
        }

        if (page.kind == Page.OPTIONS) {
            return true;
        }

        for (OptionCategory category : page.categories) {
            if (category.type != OptionCategory.TYPE_SINGLE_BUTTON) {
                return true;
            }
        }

        return false; // a menu of actions
    }

    // endregion

    // region AppDialogView

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent, boolean isOverlay, int id) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        mIsTransparent = mStack.isEmpty() ? isTransparent : mIsTransparent;
        mIsOverlay = isOverlay;
        mId = id;

        if (categories == null || categories.isEmpty()) {
            if (mStack.isEmpty()) {
                finish();
            }
            return;
        }

        if (mRoot == null) {
            setupViews(isSheet(categories, isTransparent, isOverlay));
        }

        if (isExpandable && categories.size() == 1 && categories.get(0).options != null) {
            // A single list: go straight to its values, like the TV dialog does
            if (!openCategory(categories.get(0), title)) {
                if (mStack.isEmpty()) {
                    mStack.add(new Page(Page.CATEGORIES, title, categories, null, null));
                    bind();
                }
            }
            return;
        }

        mStack.add(new Page(Page.CATEGORIES, title, categories, null, null));
        bind();
    }

    @Override
    public void goBack() {
        if (isFinishing()) {
            return;
        }

        if (canGoBack()) {
            mStack.remove(mStack.size() - 1);
            bind();
        } else {
            finish();
        }
    }

    @Override
    public void clearBackstack() {
        while (mStack.size() > 1) {
            mStack.remove(0);
        }

        bind();
    }

    @Override
    public boolean canGoBack() {
        return mStack.size() > 1;
    }

    @Override
    public boolean isShown() {
        return !mIsPaused && !isFinishing() && !mClosing;
    }

    @Override
    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mIsOverlay;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    // endregion

    private Page current() {
        return mStack.get(mStack.size() - 1);
    }

    private void bind() {
        if (mStack.isEmpty() || mRoot == null) {
            return;
        }

        Page page = current();
        mTitle.setText(page.title);
        // A page always has its back arrow (it closes the first page), the sheet has a close button for that
        mBack.setVisibility(canGoBack() || !mIsSheet ? View.VISIBLE : View.GONE);
        if (mAnchor != null && mSheet.getLayoutParams().width > 0) {
            applyListLayout(mSheet.getLayoutParams().width);
        }

        mAdapter.notifyDataSetChanged();
        mList.scrollToPosition(0);
        applySize(mRoot.getWidth(), mRoot.getHeight());
    }

    /**
     * Opens the values of a category. Returns false when the category isn't a list (switch, button).
     */
    private boolean openCategory(OptionCategory category, CharSequence title) {
        CharSequence pageTitle = category.title != null ? category.title : title;

        switch (category.type) {
            case OptionCategory.TYPE_RADIO_LIST:
            case OptionCategory.TYPE_CHECKBOX_LIST:
            case OptionCategory.TYPE_STRING_LIST:
                mStack.add(new Page(Page.OPTIONS, pageTitle, null, category, null));
                bind();
                return true;
            case OptionCategory.TYPE_LONG_TEXT:
                CharSequence text = category.options != null && !category.options.isEmpty() ? category.options.get(0).getTitle() : "";
                mStack.add(new Page(Page.TEXT, pageTitle, null, category, text));
                bind();
                return true;
            case OptionCategory.TYPE_SINGLE_BUTTON:
                if (category.options != null && category.options.size() == 1) {
                    category.options.get(0).onSelect(true);
                    return true;
                }
                return false;
            case OptionCategory.TYPE_CHAT:
            case OptionCategory.TYPE_COMMENTS:
                // Live chat and comments have their own place on the watch page
                MessageHelpers.showMessage(this, R.string.kami_comments_live);
                return false;
            default:
                return false;
        }
    }

    private void onCategoryClicked(OptionCategory category) {
        if (category.type == OptionCategory.TYPE_SINGLE_BUTTON && category.options != null && !category.options.isEmpty()) {
            category.options.get(0).onSelect(true);
        } else if (category.type == OptionCategory.TYPE_SINGLE_SWITCH && category.options != null && !category.options.isEmpty()) {
            OptionItem item = category.options.get(0);
            boolean selected = !item.isSelected();
            item.onSelect(selected);
            mAdapter.notifyDataSetChanged();
        } else {
            openCategory(category, current().title);
        }
    }

    private void onOptionClicked(Page page, int position) {
        List<OptionItem> options = page.category.options;
        OptionItem item = options.get(position);

        if (page.isRadio()) {
            page.selectedIndex = position;

            for (int i = 0; i < page.checked.length; i++) {
                page.checked[i] = i == position;
            }

            item.onSelect(true);

            // The TV dialog closes once a value is picked
            if (current() == page) {
                goBack();
            }

            return;
        }

        boolean selected = !page.checked[position];

        if (selected) {
            OptionItem[] required = item.getRequired();

            if (required != null) {
                for (OptionItem requiredItem : required) {
                    if (!requiredItem.isSelected()) {
                        MessageHelpers.showMessage(this, getString(com.liskovsoft.smartyoutubetv2.common.R.string.require_checked, requiredItem.getTitle()));
                    }
                }
            }

            OptionItem[] radio = item.getRadio();

            if (radio != null) {
                for (OptionItem radioItem : radio) {
                    radioItem.onSelect(false);

                    int index = options.indexOf(radioItem);

                    if (index != -1) {
                        page.checked[index] = false;
                    }
                }
            }
        }

        page.checked[position] = selected;
        item.onSelect(selected);
        mAdapter.notifyDataSetChanged();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ROW = 0;
        private static final int TYPE_TEXT = 1;
        private static final int TYPE_TILE = 2;

        @Override
        public int getItemCount() {
            if (mStack.isEmpty()) {
                return 0;
            }

            Page page = current();

            switch (page.kind) {
                case Page.CATEGORIES:
                    return page.categories.size();
                case Page.OPTIONS:
                    return page.category.options.size();
                default:
                    return 1;
            }
        }

        @Override
        public int getItemViewType(int position) {
            Page page = current();
            return page.kind == Page.TEXT ? TYPE_TEXT : isGrid(page) ? TYPE_TILE : TYPE_ROW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (viewType == TYPE_TILE) {
                return new TileHolder(inflater.inflate(R.layout.kami_item_dialog_tile, parent, false));
            }

            int layout = viewType == TYPE_TEXT ? R.layout.kami_item_dialog_text : R.layout.kami_item_dialog_row;

            return new RowHolder(inflater.inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Page page = current();

            if (holder instanceof TileHolder) {
                bindTile((TileHolder) holder, page, position);
                return;
            }

            RowHolder row = (RowHolder) holder;

            switch (page.kind) {
                case Page.CATEGORIES:
                    bindCategory(row, page.categories.get(position));
                    break;
                case Page.OPTIONS:
                    bindOption(row, page, position);
                    break;
                default:
                    row.title.setText(page.text);
                    break;
            }
        }

        private void bindCategory(RowHolder row, OptionCategory category) {
            row.reset();
            OptionItem single = category.options != null && category.options.size() == 1 ? category.options.get(0) : null;
            boolean isSingle = category.type == OptionCategory.TYPE_SINGLE_BUTTON || category.type == OptionCategory.TYPE_SINGLE_SWITCH;
            CharSequence title = isSingle && single != null ? single.getTitle() : category.title;

            row.title.setText(title);
            row.itemView.setOnClickListener(v -> onCategoryClicked(category));

            if (category.type == OptionCategory.TYPE_SINGLE_SWITCH && single != null) {
                row.toggle.setVisibility(View.VISIBLE);
                row.toggle.setChecked(single.isSelected());
            } else if (category.type == OptionCategory.TYPE_SINGLE_BUTTON) {
                if (single != null && single.getDescription() != null) {
                    row.setSummary(single.getDescription());
                }
            } else {
                // Android settings show no arrow on a page, a menu in a sheet keeps it
                row.chevron.setVisibility(mIsSheet ? View.VISIBLE : View.GONE);

                // What is picked now, under the name of the category
                CharSequence summary = summarize(category);

                if (summary != null) {
                    row.setSummary(summary);
                }
            }
        }

        private void bindTile(TileHolder tile, Page page, int position) {
            if (page.kind == Page.OPTIONS) {
                OptionItem item = page.category.options.get(position);
                tile.set(item.getTitle(), item.getDescription(), page.isRadio() ? page.selectedIndex == position : page.checked[position]);
                tile.itemView.setOnClickListener(v -> onOptionClicked(page, tile.getAdapterPosition()));
                return;
            }

            OptionCategory category = page.categories.get(position);
            OptionItem single = category.options != null && category.options.size() == 1 ? category.options.get(0) : null;

            if (category.type == OptionCategory.TYPE_SINGLE_SWITCH && single != null) {
                boolean on = single.isSelected();
                tile.set(single.getTitle(), getString(on ? R.string.kami_on : R.string.kami_off), on);
            } else if (category.type == OptionCategory.TYPE_SINGLE_BUTTON && single != null) {
                tile.set(single.getTitle(), single.getDescription(), false);
            } else {
                tile.set(category.title, summarize(category), false);
            }

            tile.itemView.setOnClickListener(v -> onCategoryClicked(category));
        }

        private CharSequence summarize(OptionCategory category) {
            if (category.options == null || category.type != OptionCategory.TYPE_RADIO_LIST) {
                return null;
            }

            for (OptionItem option : category.options) {
                if (option.isSelected()) {
                    return option.getTitle();
                }
            }

            return null;
        }

        private void bindOption(RowHolder row, Page page, int position) {
            row.reset();
            OptionItem item = page.category.options.get(position);
            row.title.setText(item.getTitle());

            if (item.getDescription() != null) {
                row.setSummary(item.getDescription());
            }

            if (page.isRadio()) {
                row.radio.setVisibility(View.VISIBLE);
                row.radio.setChecked(page.selectedIndex == position);
            } else {
                row.toggle.setVisibility(View.VISIBLE);
                row.toggle.setChecked(page.checked[position]);
            }

            row.itemView.setOnClickListener(v -> onOptionClicked(page, row.getAdapterPosition()));
        }
    }

    private static final class TileHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView value;

        TileHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.kami_tile_title);
            value = itemView.findViewById(R.id.kami_tile_value);
        }

        void set(CharSequence name, CharSequence current, boolean picked) {
            title.setText(name);
            value.setText(current);
            value.setVisibility(current != null && current.length() > 0 ? View.VISIBLE : View.GONE);
            itemView.setActivated(picked);
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final RadioButton radio;
        final ImageView chevron;
        final Switch toggle;

        RowHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.kami_row_title);
            summary = itemView.findViewById(R.id.kami_row_summary);
            radio = itemView.findViewById(R.id.kami_row_radio);
            chevron = itemView.findViewById(R.id.kami_row_chevron);
            toggle = itemView.findViewById(R.id.kami_row_switch);
        }

        void reset() {
            if (summary != null) {
                summary.setVisibility(View.GONE);
                radio.setVisibility(View.GONE);
                chevron.setVisibility(View.GONE);
                toggle.setVisibility(View.GONE);
            }
        }

        void setSummary(CharSequence text) {
            summary.setText(text);
            summary.setVisibility(View.VISIBLE);
        }
    }
}
