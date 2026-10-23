package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.UiScale;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;
import com.liskovsoft.smartyoutubetv2.tv.mobile.download.DownloadAction;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings tab: one list grouped under titles, like the Android settings, centered on wide screens.
 * The upstream settings dialogs are reused as they are (they open as full screen pages),
 * with the entries that only make sense on a phone added on top and the TV only ones left out.
 */
public class SettingsSection implements MobileSection {
    private static final float MAX_WIDTH_DP = 720;

    private final Context mContext;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private static final int GROUP_APP = 0;
    private static final int GROUP_PLAYER = 1;
    private static final int GROUP_CONTENT = 2;
    private static final int GROUP_ACCOUNT = 3;
    private static final int GROUP_ABOUT = 4;

    /** A section title or an entry, in display order */
    private static final class Row {
        final int groupTitle; // string id, 0 for an entry
        final SettingsItem item;

        Row(int groupTitle, SettingsItem item) {
            this.groupTitle = groupTitle;
            this.item = item;
        }
    }

    private final List<Row> mRows = new ArrayList<>();
    private final LinearLayoutManager mLayoutManager;
    private final SettingsAdapter mAdapter = new SettingsAdapter();
    private RecyclerView mRecyclerView;

    public SettingsSection(Context context) {
        mContext = context;
        mLayoutManager = new LinearLayoutManager(context);
    }

    public void update(SettingsGroup group) {
        mRows.clear();

        if (group != null && group.getItems() != null) {
            List<SettingsItem> items = phoneItems();

            for (SettingsItem item : group.getItems()) {
                if (item.imageResId != R.drawable.settings_afr) { // match the frame rate of a TV, nothing to do on a phone
                    items.add(item);
                }
            }

            int[] titles = {R.string.kami_group_app, R.string.kami_group_player, R.string.kami_group_content, R.string.kami_group_account, R.string.kami_group_about};

            for (int g = 0; g < titles.length; g++) {
                boolean titled = false;

                for (SettingsItem item : items) {
                    if (groupOf(item, mContext) == g) {
                        if (!titled) {
                            mRows.add(new Row(titles[g], null));
                            titled = true;
                        }

                        mRows.add(new Row(0, item));
                    }
                }
            }
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void attach(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void detach() {
        mRecyclerView = null;
    }

    @Override
    public void update(VideoGroup group) {
        // Settings only receive SettingsGroup
    }

    @Override
    public void clear() {
        update((SettingsGroup) null);
    }

    @Override
    public boolean isEmpty() {
        return mRows.isEmpty();
    }

    @Override
    public void setPosition(int index) {
        // NOP
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }

    @Override
    public void onWidthChanged(float widthDp) {
        if (mRecyclerView == null) {
            return;
        }

        int side = widthDp > MAX_WIDTH_DP ? WindowSize.dpToPx(mContext, (widthDp - MAX_WIDTH_DP) / 2) : 0;
        mRecyclerView.setPadding(side, mRecyclerView.getPaddingTop(), side, mRecyclerView.getPaddingBottom());
    }

    private List<SettingsItem> phoneItems() {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(mContext.getString(R.string.kami_set_interface), this::showScaleDialog));
        items.add(new SettingsItem(mContext.getString(R.string.kami_set_pip), this::showBackgroundDialog));
        items.add(new SettingsItem(mContext.getString(R.string.kami_set_downloads), () -> DownloadAction.showSettings(mContext)));
        return items;
    }

    private void showScaleDialog() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);
        int current = UiScale.getChoice(mContext);
        List<OptionItem> options = new ArrayList<>();

        for (int choice : UiScale.CHOICES) {
            String label = choice == UiScale.DYNAMIC ? mContext.getString(R.string.kami_scale_dynamic) : mContext.getString(R.string.kami_scale_percent, choice);
            options.add(UiOptionItem.from(label, item -> UiScale.setChoice(mContext, choice), choice == current));
        }

        String title = mContext.getString(R.string.kami_set_interface);
        dialog.appendRadioCategory(title, options);
        dialog.showDialog(title);
    }

    private void showBackgroundDialog() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(mContext, PlayerData.instance(mContext), GeneralData.instance(mContext));
        dialog.appendCategory(category);
        dialog.showDialog(mContext.getString(R.string.kami_set_pip));
    }

    /**
     * Icon of our own for each upstream entry.
     */
    private static int iconFor(int upstreamIcon) {
        if (upstreamIcon == R.drawable.settings_account) return R.drawable.kami_ic_person;
        if (upstreamIcon == R.drawable.settings_cast) return R.drawable.kami_ic_cast;
        if (upstreamIcon == R.drawable.settings_language) return R.drawable.kami_ic_language;
        if (upstreamIcon == R.drawable.settings_main_ui) return R.drawable.kami_ic_tune;
        if (upstreamIcon == R.drawable.settings_player) return R.drawable.kami_ic_player;
        if (upstreamIcon == R.drawable.settings_subtitles) return R.drawable.kami_ic_subtitles;
        if (upstreamIcon == R.drawable.settings_search) return R.drawable.kami_ic_search;
        if (upstreamIcon == R.drawable.settings_block) return R.drawable.kami_ic_shield;
        if (upstreamIcon == R.drawable.settings_backup) return R.drawable.kami_ic_backup;
        if (upstreamIcon == R.drawable.settings_about) return R.drawable.kami_ic_info;
        return R.drawable.kami_ic_settings;
    }

    /**
     * Where an entry lives: app look and language, player, content, account and devices, about.
     */
    private static int groupOf(SettingsItem item, Context context) {
        String title = item.title;

        if (title.equals(context.getString(R.string.kami_set_interface))) return GROUP_APP;
        if (title.equals(context.getString(R.string.kami_set_pip)) || title.equals(context.getString(R.string.kami_set_downloads))) return GROUP_PLAYER;

        int icon = item.imageResId;

        if (icon == R.drawable.settings_player || icon == R.drawable.settings_subtitles) return GROUP_PLAYER;
        if (icon == R.drawable.settings_search || icon == R.drawable.settings_block || icon == R.drawable.settings_dearrow) return GROUP_CONTENT;
        if (icon == R.drawable.settings_account || icon == R.drawable.settings_cast || icon == R.drawable.settings_backup) return GROUP_ACCOUNT;
        if (icon == R.drawable.settings_about) return GROUP_ABOUT;

        return GROUP_APP; // language, general, main UI, and anything upstream adds later
    }

    private static int iconOf(SettingsItem item, Context context) {
        String title = item.title;

        if (title.equals(context.getString(R.string.kami_set_interface))) return R.drawable.kami_ic_text_size;
        if (title.equals(context.getString(R.string.kami_set_pip))) return R.drawable.kami_ic_pip;
        if (title.equals(context.getString(R.string.kami_set_downloads))) return R.drawable.kami_ic_download;

        return iconFor(item.imageResId);
    }

    private void onItemClicked(SettingsItem item) {
        String password = GeneralData.instance(mContext).getSettingsPassword();

        if (password == null) {
            item.onClick.run();
            return;
        }

        SimpleEditDialog.showPassword(
                mContext,
                mContext.getString(R.string.enter_settings_password),
                null,
                newValue -> {
                    if (Utils.passwordMatch(password, newValue)) {
                        item.onClick.run();
                        return true;
                    }
                    return false;
                });
    }

    private final class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return mRows.get(position).item == null ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (viewType == TYPE_HEADER) {
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.kami_item_settings_header, parent, false)) {
                };
            }

            return new SettingsHolder(inflater.inflate(R.layout.kami_item_settings, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            Row row = mRows.get(position);

            if (row.item == null) {
                ((TextView) viewHolder.itemView).setText(row.groupTitle);
                return;
            }

            SettingsHolder holder = (SettingsHolder) viewHolder;
            SettingsItem item = row.item;
            holder.title.setText(item.title);

            holder.icon.setImageResource(iconOf(item, mContext));

            holder.itemView.setOnClickListener(v -> onItemClicked(item));
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }
    }

    private static final class SettingsHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        SettingsHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.kami_settings_icon);
            title = itemView.findViewById(R.id.kami_settings_title);
        }
    }
}
