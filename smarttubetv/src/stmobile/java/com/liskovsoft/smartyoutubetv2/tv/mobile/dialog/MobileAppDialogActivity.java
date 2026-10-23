package com.liskovsoft.smartyoutubetv2.tv.mobile.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Every menu and setting of the app.<br/>
 * The upstream presenters build the same lists of categories and options for the TV dialogs; this view shows them
 * the Android way. Settings and their sub-menus are full screen pages with a top app bar and a back arrow:
 * pick a category, its values open on the next page, back goes up one level.
 * Short menus of actions (the "more" menu of a video) and anything opened over the player
 * stay a bottom sheet, so the video and the page behind remain in sight.
 */
public class MobileAppDialogActivity extends MobileActivity implements AppDialogView {
    private static final float SHEET_MAX_WIDTH_DP = 520;
    private static final float SHEET_MAX_HEIGHT_RATIO = 0.82f;
    private static final float PAGE_MAX_WIDTH_DP = 720;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        setContentView(isSheet ? R.layout.kami_activity_dialog : R.layout.kami_activity_page);

        if (!isSheet) {
            // The window is see-through for the sheet: a page paints its own background, under the system bars too
            getWindow().setBackgroundDrawableResource(R.color.kami_ink);
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
        if (mPresenter != null && mPresenter.getView() == this && !isFinishing()) {
            mPresenter.onFinish();
        }

        finishReally();
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
        return !mIsPaused && !isFinishing();
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
            return current().kind == Page.TEXT ? TYPE_TEXT : TYPE_ROW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layout = viewType == TYPE_TEXT ? R.layout.kami_item_dialog_text : R.layout.kami_item_dialog_row;

            return new RowHolder(inflater.inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowHolder row = (RowHolder) holder;
            Page page = current();

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
