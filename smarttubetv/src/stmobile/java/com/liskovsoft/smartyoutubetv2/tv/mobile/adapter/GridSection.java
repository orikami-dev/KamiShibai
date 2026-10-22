package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

/**
 * One endless grid of cards (Subscriptions, History, Shorts, channel uploads...).<br/>
 * With {@code withChannels}, it also shows the upstream "multi grid" layout of the Channels section:
 * a strip of channel avatars on top (group position 0) and the uploads of the picked channel below (group position 1).
 */
public class GridSection implements MobileSection {
    private static final float MIN_VIDEO_CARD_DP = 300;
    private static final float MIN_SHORTS_CARD_DP = 160;
    private static final int MAX_COLUMNS = 6;

    private final Context mContext;
    private final VideoGroupPresenter mPresenter;
    private final boolean mIsShorts;
    private final boolean mWithChannels;
    private final VideoCardAdapter mAdapter;
    private final VideoCardAdapter mChannelsAdapter;
    private final GridLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private RecyclerView mChannelsView;
    private int mPendingPosition = -1;
    private Video mPendingItem;

    public GridSection(Context context, VideoGroupPresenter presenter, boolean isShorts, boolean withChannels) {
        mContext = context;
        mPresenter = presenter;
        mIsShorts = isShorts;
        mWithChannels = withChannels;
        mAdapter = new VideoCardAdapter(VideoCardAdapter.STYLE_GRID, new PresenterListener(presenter));
        mLayoutManager = new GridLayoutManager(context, 1);
        mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter.isHeader(position) ? mLayoutManager.getSpanCount() : 1;
            }
        });

        if (withChannels) {
            mChannelsAdapter = new VideoCardAdapter(VideoCardAdapter.STYLE_CHANNELS, new ChannelsListener());
            mAdapter.setHeader(new ChannelsHeader());
        } else {
            mChannelsAdapter = null;
        }
    }

    @Override
    public void attach(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void detach() {
        mAdapter.release();
        if (mChannelsAdapter != null) {
            mChannelsAdapter.release();
        }
        mRecyclerView = null;
        mChannelsView = null;
    }

    @Override
    public void onWidthChanged(float widthDp) {
        int columns = WindowSize.columns(widthDp, mIsShorts ? MIN_SHORTS_CARD_DP : MIN_VIDEO_CARD_DP, MAX_COLUMNS);

        if (mLayoutManager.getSpanCount() != columns) {
            mLayoutManager.setSpanCount(columns);
        }
    }

    @Override
    public void update(VideoGroup group) {
        if (mWithChannels && group.getPosition() == 0) {
            applyAction(mChannelsAdapter, group);
            return;
        }

        if (mWithChannels && group.getPosition() != 1) {
            return;
        }

        applyAction(mAdapter, group);
        restorePosition();
    }

    /**
     * Same actions as the upstream grid fragments.
     */
    static void applyAction(VideoCardAdapter adapter, VideoGroup group) {
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                adapter.clear();
                break;
            case VideoGroup.ACTION_REMOVE:
                adapter.getData().remove(group);
                return;
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                adapter.getData().removeAuthor(group);
                return;
            case VideoGroup.ACTION_SYNC:
                adapter.getData().sync(group);
                return;
        }

        if (group.isEmpty()) {
            return;
        }

        adapter.getData().add(group);
    }

    @Override
    public void clear() {
        mAdapter.clear();
        if (mChannelsAdapter != null) {
            mChannelsAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        return mAdapter.isEmpty() && (mChannelsAdapter == null || mChannelsAdapter.isEmpty());
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (mWithChannels) {
            // Upstream position points to the channel list
            if (mChannelsView != null && index < mChannelsAdapter.getItemCount()) {
                mChannelsView.scrollToPosition(index);
            }
            return;
        }

        mPendingPosition = index;
        mPendingItem = null;
        restorePosition();
    }

    @Override
    public void selectItem(Video item) {
        if (item == null) {
            return;
        }

        mPendingItem = item;
        mPendingPosition = -1;
        restorePosition();
    }

    private void restorePosition() {
        if (mRecyclerView == null) {
            return;
        }

        VideoCardAdapter target = mWithChannels ? mChannelsAdapter : mAdapter;

        if (mPendingItem != null) {
            int index = target.getData().indexOfAlt(mPendingItem);
            if (index != -1) {
                scrollTo(target, index);
                mPendingItem = null;
            }
        } else if (mPendingPosition != -1 && mPendingPosition < target.getData().size()) {
            scrollTo(target, mPendingPosition);
            mPendingPosition = -1;
        }
    }

    private void scrollTo(VideoCardAdapter target, int index) {
        if (target == mChannelsAdapter) {
            if (mChannelsView != null) {
                mChannelsView.scrollToPosition(index);
            }
        } else {
            mLayoutManager.scrollToPositionWithOffset(mAdapter.toAdapterPosition(index), 0);
        }
    }

    private final class ChannelsListener extends PresenterListener {
        ChannelsListener() {
            super(mPresenter);
        }

        @Override
        public void onVideoClicked(Video video) {
            mChannelsAdapter.setSelectedIndex(mChannelsAdapter.getData().indexOf(video));
            // Upstream loads the uploads on focus (auto load) or on click. A tap does both jobs here.
            mPresenter.onVideoItemSelected(video);
            if (!MainUIData.instance(mContext).isUploadsAutoLoadEnabled()) {
                mPresenter.onVideoItemClicked(video);
            }
            if (mRecyclerView != null) {
                mRecyclerView.scrollToPosition(0);
            }
        }
    }

    private final class ChannelsHeader implements VideoCardAdapter.Header {
        @Override
        public View onCreateView(ViewGroup parent) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_header_channels, parent, false);
            RecyclerView list = view.findViewById(R.id.kami_channels_list);
            list.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
            list.setAdapter(mChannelsAdapter);
            mChannelsView = list;
            return view;
        }

        @Override
        public void onBind(View view) {
            // The strip keeps its own adapter
        }
    }
}
