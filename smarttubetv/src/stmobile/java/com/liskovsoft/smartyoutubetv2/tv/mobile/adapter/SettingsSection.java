package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
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
 * The Settings section: one list on a phone, tiles on wider screens.
 * The upstream settings dialogs are reused as they are (they show as bottom sheets),
 * with the entries that only make sense on a phone added on top and the TV only ones left out.
 */
public class SettingsSection implements MobileSection {
    private static final float MIN_TILE_DP = 320;
    private static final int MAX_COLUMNS = 3;

    private final Context mContext;
    private final List<SettingsItem> mItems = new ArrayList<>();
    private final GridLayoutManager mLayoutManager;
    private final SettingsAdapter mAdapter = new SettingsAdapter();

    public SettingsSection(Context context) {
        mContext = context;
        mLayoutManager = new GridLayoutManager(context, 1);
    }

    public void update(SettingsGroup group) {
        mItems.clear();

        if (group != null && group.getItems() != null) {
            mItems.addAll(phoneItems());

            for (SettingsItem item : group.getItems()) {
                if (item.imageResId != R.drawable.settings_afr) { // match the frame rate of a TV, nothing to do on a phone
                    mItems.add(item);
                }
            }
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void attach(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void detach() {
        // NOP
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
        return mItems.isEmpty();
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
        int columns = WindowSize.columns(widthDp, MIN_TILE_DP, MAX_COLUMNS);

        if (mLayoutManager.getSpanCount() != columns) {
            mLayoutManager.setSpanCount(columns);
        }
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

    private final class SettingsAdapter extends RecyclerView.Adapter<SettingsHolder> {
        @NonNull
        @Override
        public SettingsHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SettingsHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_item_settings, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SettingsHolder holder, int position) {
            SettingsItem item = mItems.get(position);
            holder.title.setText(item.title);

            holder.icon.setImageResource(iconOf(item, mContext));

            holder.itemView.setOnClickListener(v -> onItemClicked(item));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
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
