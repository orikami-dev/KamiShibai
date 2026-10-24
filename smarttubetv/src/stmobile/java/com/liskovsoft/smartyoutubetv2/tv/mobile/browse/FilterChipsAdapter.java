package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Horizontal strip of topic filters on top of Home: an "Explore" button, then All, Music, Gaming, News, Live...
 * Each filter is an upstream feed section, so it loads exactly like it does in SmartTube.
 */
public class FilterChipsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_EXPLORE = 0;
    private static final int TYPE_CHIP = 1;

    private final List<BrowseSection> mChips;
    private final NavAdapter.Listener mListener;
    private final View.OnClickListener mExploreListener;
    private int mSelectedId = Integer.MAX_VALUE;

    public FilterChipsAdapter(List<BrowseSection> chips, NavAdapter.Listener listener, View.OnClickListener exploreListener) {
        mChips = chips;
        mListener = listener;
        mExploreListener = exploreListener;
    }

    public void setSelectedId(int sectionId) {
        mSelectedId = sectionId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mChips.isEmpty() ? 0 : mChips.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_EXPLORE : TYPE_CHIP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        int layoutId = viewType == TYPE_EXPLORE ? R.layout.kami_item_filter_explore : R.layout.kami_item_filter_chip;
        return new Holder(inflater.inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position == 0) {
            holder.itemView.setOnClickListener(mExploreListener);
            return;
        }

        BrowseSection section = mChips.get(position - 1);
        TextView label = (TextView) holder.itemView;
        label.setText(section.getTitle());
        label.setSelected(section.getId() == mSelectedId);
        label.setOnClickListener(v -> mListener.onSectionClicked(section));
        label.setOnLongClickListener(v -> {
            mListener.onSectionLongClicked(section);
            return true;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
