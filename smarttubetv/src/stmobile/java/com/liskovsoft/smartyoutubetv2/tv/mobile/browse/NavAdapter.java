package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Sections navigation, drawn as a bottom bar (phones), a rail (foldables, landscape) or a wide drawer (tablets).
 */
public class NavAdapter extends RecyclerView.Adapter<NavAdapter.Holder> {
    public static final int LAYOUT_BAR = 0;
    public static final int LAYOUT_RAIL = 1;
    public static final int LAYOUT_DRAWER = 2;

    /**
     * Pseudo section that opens the full list (bottom bar only).
     */
    public static final int ID_MORE = Integer.MIN_VALUE;

    public interface Listener {
        void onSectionClicked(BrowseSection section);
        void onSectionLongClicked(BrowseSection section);
        void onMoreClicked();
    }

    private final List<BrowseSection> mAllSections;
    private final Listener mListener;
    private final int mLayout;
    private final int mMaxItems;
    private final BrowseSection mMore;
    private int mSelectedId = -1;

    /**
     * @param maxItems items shown before the "More" entry, or 0 to show all of them
     */
    public NavAdapter(Context context, List<BrowseSection> sections, int layout, int maxItems, Listener listener) {
        mAllSections = sections;
        mLayout = layout;
        mMaxItems = maxItems;
        mListener = listener;
        mMore = new BrowseSection(ID_MORE, context.getString(R.string.kami_more), BrowseSection.TYPE_GRID, R.drawable.kami_ic_more_horiz);
    }

    public void setSelectedId(int sectionId) {
        mSelectedId = sectionId;
        notifyDataSetChanged();
    }

    private boolean hasMore() {
        return mMaxItems > 0 && mAllSections.size() > mMaxItems;
    }

    private int visibleCount() {
        return hasMore() ? mMaxItems - 1 : mAllSections.size();
    }

    private BrowseSection getItem(int position) {
        return position < visibleCount() ? mAllSections.get(position) : mMore;
    }

    private boolean isSelectedInsideMore() {
        if (!hasMore()) {
            return false;
        }

        for (int i = visibleCount(); i < mAllSections.size(); i++) {
            if (mAllSections.get(i).getId() == mSelectedId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getItemCount() {
        return visibleCount() + (hasMore() ? 1 : 0);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = mLayout == LAYOUT_DRAWER ? R.layout.kami_item_nav_wide : R.layout.kami_item_nav;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BrowseSection section = getItem(position);
        boolean selected = section == mMore ? isSelectedInsideMore() : section.getId() == mSelectedId;

        holder.label.setText(section.getTitle());
        holder.itemView.setSelected(selected);
        holder.indicator.setSelected(selected);
        bindIcon(holder.icon, section);

        holder.itemView.setOnClickListener(v -> {
            if (section == mMore) {
                mListener.onMoreClicked();
            } else {
                mListener.onSectionClicked(section);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (section != mMore) {
                mListener.onSectionLongClicked(section);
            }
            return true;
        });
    }

    static void bindIcon(ImageView icon, BrowseSection section) {
        Context context = icon.getContext();

        if (section.getIconUrl() != null) {
            // Pinned channel or playlist
            icon.setImageTintList(null);
            if (!(context instanceof Activity && ((Activity) context).isDestroyed())) {
                Glide.with(context)
                        .load(section.getIconUrl())
                        .apply(RequestOptions.circleCropTransform())
                        .into(icon);
            }
        } else {
            icon.setImageTintList(context.getResources().getColorStateList(R.color.kami_nav_content));
            icon.setImageResource(section.getResId() > 0 ? section.getResId() : R.drawable.kami_ic_more_horiz);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final View indicator;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.kami_nav_icon);
            label = itemView.findViewById(R.id.kami_nav_label);
            indicator = itemView.findViewById(R.id.kami_nav_indicator);
        }
    }
}
