package com.liskovsoft.smartyoutubetv2.tv.mobile.channeluploads;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.GridSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.TopBarFragment;

/**
 * Uploads of a channel or content of a playlist, as an endless grid.
 */
public class MobileChannelUploadsFragment extends TopBarFragment implements ChannelUploadsView {
    private static final String SELECTED_ITEM_INDEX = "SelectedItemIndex";
    private ChannelUploadsPresenter mChannelUploadsPresenter;
    private GridSection mGrid;
    private boolean mIsFragmentCreated;
    private int mRestoredItemIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        mRestoredItemIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_ITEM_INDEX, -1) : -1;
        mIsFragmentCreated = true;
        mChannelUploadsPresenter = ChannelUploadsPresenter.instance(getContext());
        mChannelUploadsPresenter.setView(this);
        mGrid = new GridSection(getContext(), mChannelUploadsPresenter, false, false);
    }

    @Override
    protected MobileSection getSection() {
        return mGrid;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mChannelUploadsPresenter.onViewInitialized();

        // Restore state after crash
        mGrid.setPosition(mRestoredItemIndex);
        mRestoredItemIndex = -1;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_INDEX, -1);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mChannelUploadsPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelUploadsPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelUploadsPresenter.onFinish();
    }

    @Override
    public void update(VideoGroup videoGroup) {
        mGrid.update(videoGroup);
        updateTitle(videoGroup);
    }

    private void updateTitle(VideoGroup group) {
        Video channel = mChannelUploadsPresenter.getChannel();

        if (channel != null && channel.getTitle() != null) {
            setTitle(channel.getTitle());
        } else if (group != null && group.getTitle() != null) {
            setTitle(group.getTitle());
        }
    }

    @Override
    public void clear() {
        mGrid.clear();
    }
}
