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
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings section: one list on a phone, tiles on wider screens.
 * Every entry opens the upstream settings dialogs, untouched.
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
            mItems.addAll(group.getItems());
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

            if (item.imageResId > 0) {
                holder.icon.setImageResource(item.imageResId);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.INVISIBLE);
            }

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
