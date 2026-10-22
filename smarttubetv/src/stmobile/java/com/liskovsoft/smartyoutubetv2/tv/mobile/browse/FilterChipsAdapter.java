package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Horizontal strip of topic filters on top of Home (All, Music, Gaming, News, Live...).
 * Each filter is an upstream feed section, so it loads exactly like it does in SmartTube.
 */
public class FilterChipsAdapter extends RecyclerView.Adapter<FilterChipsAdapter.Holder> {
    private final List<BrowseSection> mChips;
    private final NavAdapter.Listener mListener;
    private int mSelectedId = Integer.MAX_VALUE;

    public FilterChipsAdapter(List<BrowseSection> chips, NavAdapter.Listener listener) {
        mChips = chips;
        mListener = listener;
    }

    public void setSelectedId(int sectionId) {
        mSelectedId = sectionId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mChips.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder((TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_item_filter_chip, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BrowseSection section = mChips.get(position);
        holder.label.setText(section.getTitle());
        holder.label.setSelected(section.getId() == mSelectedId);
        holder.label.setOnClickListener(v -> mListener.onSectionClicked(section));
        holder.label.setOnLongClickListener(v -> {
            mListener.onSectionLongClicked(section);
            return true;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;

        Holder(TextView itemView) {
            super(itemView);
            label = itemView;
        }
    }
}
