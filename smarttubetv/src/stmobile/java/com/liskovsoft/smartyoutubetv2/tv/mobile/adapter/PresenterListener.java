package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;

/**
 * Forwards touch events to an upstream presenter.<br/>
 * There's no focus on a touch screen, so the "selected" event is sent right before the click,
 * the way the TV views do when the card under the cursor is pressed.
 */
public class PresenterListener implements VideoCardAdapter.Listener {
    private final VideoGroupPresenter mPresenter;

    public PresenterListener(VideoGroupPresenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void onVideoClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemLongClicked(video);
    }

    @Override
    public void onScrollEnd(Video lastVideo) {
        mPresenter.onScrollEnd(lastVideo);
    }
}
