package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.List;

/**
 * The "You" page: every section that isn't a main tab or a Home filter
 * (History, Playlists, Notifications, pinned channels, Settings...).<br/>
 * One column when folded, two when unfolded.
 */
public class YouSection implements MobileSection {
    private final NavAdapter mAdapter;
    private final GridLayoutManager mLayoutManager;

    public YouSection(Context context, List<BrowseSection> sections, NavAdapter.Listener listener) {
        mAdapter = new NavAdapter(sections, NavAdapter.LAYOUT_DRAWER, listener);
        mLayoutManager = new GridLayoutManager(context, 1);
    }

    public void notifyChanged() {
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
        return mAdapter.getItemCount() == 0;
    }

    @Override
    public void setPosition(int index) {
        // NOP
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }
}
