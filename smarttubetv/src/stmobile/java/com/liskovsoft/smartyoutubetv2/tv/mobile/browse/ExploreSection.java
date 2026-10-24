package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Explore" page (compass at the start of the Home filters): the topic feeds,
 * then "My library" with every section that isn't a tab (playlists, queue, notifications, pinned channels...).<br/>
 * One column when folded, more when unfolded. Group titles take the full width.
 */
public class ExploreSection implements MobileSection {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context mContext;
    private final List<BrowseSection> mTopics;
    private final List<BrowseSection> mLibrary;
    private final NavAdapter.Listener mListener;
    // Group title (String) or section, in display order
    private final List<Object> mRows = new ArrayList<>();
    private final GridLayoutManager mLayoutManager;
    private final Adapter mAdapter = new Adapter();

    public ExploreSection(Context context, List<BrowseSection> topics, List<BrowseSection> library, NavAdapter.Listener listener) {
        mContext = context;
        mTopics = topics;
        mLibrary = library;
        mListener = listener;
        mLayoutManager = new GridLayoutManager(context, 1);
        mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter.getItemViewType(position) == TYPE_HEADER ? mLayoutManager.getSpanCount() : 1;
            }
        });
        rebuild();
    }

    public void notifyChanged() {
        rebuild();
        mAdapter.notifyDataSetChanged();
    }

    private void rebuild() {
        mRows.clear();
        addGroup(R.string.kami_topics, mTopics);
        addGroup(R.string.kami_library, mLibrary);
    }

    private void addGroup(int titleResId, List<BrowseSection> sections) {
        if (sections.isEmpty()) {
            return;
        }

        mRows.add(mContext.getString(titleResId));
        mRows.addAll(sections);
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
    public void onWidthChanged(float widthDp) {
        int columns = WindowSize.columns(widthDp, 300, 3);

        if (mLayoutManager.getSpanCount() != columns) {
            mLayoutManager.setSpanCount(columns);
        }
    }

    @Override
    public void update(VideoGroup group) {
        // Not a presenter section
    }

    @Override
    public void clear() {
        // NOP
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

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return mRows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (viewType == TYPE_HEADER) {
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.kami_item_block_title, parent, false)) {
                };
            }

            return new NavAdapter.Holder(inflater.inflate(R.layout.kami_item_nav_wide, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            Object row = mRows.get(position);

            if (row instanceof String) {
                ((TextView) viewHolder.itemView).setText((String) row);
                return;
            }

            BrowseSection section = (BrowseSection) row;
            NavAdapter.Holder holder = (NavAdapter.Holder) viewHolder;
            holder.label.setText(section.getTitle());
            NavAdapter.bindIcon(holder.icon, section);
            holder.itemView.setOnClickListener(v -> mListener.onSectionClicked(section));
            holder.itemView.setOnLongClickListener(v -> {
                mListener.onSectionLongClicked(section);
                return true;
            });
        }
    }
}
