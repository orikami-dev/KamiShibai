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

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Sections navigation, drawn as a bottom bar (folded phone), a rail (unfolded foldable, landscape)
 * or a wide drawer (tablets, and the list of the "You" page).
 */
public class NavAdapter extends RecyclerView.Adapter<NavAdapter.Holder> {
    public static final int LAYOUT_BAR = 0;
    public static final int LAYOUT_RAIL = 1;
    public static final int LAYOUT_DRAWER = 2;

    public interface Listener {
        void onSectionClicked(BrowseSection section);
        void onSectionLongClicked(BrowseSection section);
    }

    private final List<BrowseSection> mSections;
    private final Listener mListener;
    private final int mLayout;
    private int mSelectedId = Integer.MAX_VALUE;

    public NavAdapter(List<BrowseSection> sections, int layout, Listener listener) {
        mSections = sections;
        mLayout = layout;
        mListener = listener;
    }

    public void setSelectedId(int sectionId) {
        mSelectedId = sectionId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mSections.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = mLayout == LAYOUT_DRAWER ? R.layout.kami_item_nav_wide : R.layout.kami_item_nav;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BrowseSection section = mSections.get(position);
        boolean selected = section.getId() == mSelectedId;

        holder.label.setText(section.getTitle());
        holder.itemView.setSelected(selected);
        holder.indicator.setSelected(selected);
        bindIcon(holder.icon, section);

        holder.itemView.setOnClickListener(v -> mListener.onSectionClicked(section));
        holder.itemView.setOnLongClickListener(v -> {
            mListener.onSectionLongClicked(section);
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
            icon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(context, R.color.kami_nav_content));
            icon.setImageResource(iconFor(section.getId()));
        }
    }

    /**
     * One icon family for every section, instead of the upstream TV bitmaps.
     */
    static int iconFor(int sectionId) {
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
            case MediaGroup.TYPE_USER_PLAYLISTS:
            case MediaGroup.TYPE_MY_VIDEOS:
                return R.drawable.kami_ic_library;
            case MediaGroup.TYPE_NOTIFICATIONS:
                return R.drawable.kami_ic_bell;
            case MediaGroup.TYPE_PLAYBACK_QUEUE:
                return R.drawable.kami_ic_queue;
            case MediaGroup.TYPE_BLOCKED_CHANNELS:
                return R.drawable.kami_ic_block;
            case MediaGroup.TYPE_TRENDING:
                return R.drawable.kami_ic_trending;
            case MediaGroup.TYPE_MUSIC:
                return R.drawable.kami_ic_music;
            case MediaGroup.TYPE_GAMING:
                return R.drawable.kami_ic_gaming;
            case MediaGroup.TYPE_NEWS:
                return R.drawable.kami_ic_news;
            case MediaGroup.TYPE_SPORTS:
                return R.drawable.kami_ic_sports;
            case MediaGroup.TYPE_LIVE:
                return R.drawable.kami_ic_live;
            case MediaGroup.TYPE_SETTINGS:
                return R.drawable.kami_ic_settings;
            default:
                return R.drawable.kami_ic_play;
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
