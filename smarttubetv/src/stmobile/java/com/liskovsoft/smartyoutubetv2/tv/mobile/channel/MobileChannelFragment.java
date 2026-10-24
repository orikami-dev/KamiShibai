package com.liskovsoft.smartyoutubetv2.tv.mobile.channel;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.FeedSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.TopBarFragment;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

/**
 * Channel page: rows (videos, shorts, live, playlists...) under a bar with the channel name and a search button.
 */
public class MobileChannelFragment extends TopBarFragment implements ChannelView {
    private ChannelPresenter mChannelPresenter;
    private FeedSection mRows;
    private CrashRestorer mCrashRestorer;
    private boolean mIsFragmentCreated;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mChannelPresenter = ChannelPresenter.instance(getContext());
        mChannelPresenter.setView(this);
        mRows = new FeedSection(getContext(), mChannelPresenter, FeedSection.MODE_ROWS);
    }

    @Override
    protected MobileSection getSection() {
        return mRows;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View search = getTopBarAction();

        if (search != null && MainUIData.instance(getContext()).isChannelSearchBarEnabled()) {
            search.setVisibility(View.VISIBLE);
            search.setOnClickListener(v -> SimpleEditDialog.show(
                    getContext(),
                    getString(R.string.kami_search_channel),
                    null,
                    query -> mChannelPresenter.onSearchSubmit(query)));
            search.setOnLongClickListener(v -> {
                mChannelPresenter.onSearchSettingsClicked();
                return true;
            });
        }

        updateTitle();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mChannelPresenter.onViewInitialized();

        // Restore state after crash
        mCrashRestorer.restoreHeader((idx, video) -> setPosition(idx));
        mCrashRestorer.restorePlayback();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCrashRestorer.persistHeaderIndex(outState, 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mChannelPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelPresenter.onFinish();
    }

    private void updateTitle() {
        Video channel = mChannelPresenter.getChannel();

        if (channel == null) {
            String channelId = mChannelPresenter.getChannelId();
            setTitle(Helpers.startsWith(channelId, "@") ? channelId : null);
            return;
        }

        String author = channel.getAuthor();
        String title = channel.getTitle();
        String subs = channel.subscriberCount;

        setTitle(Helpers.toString(YouTubeHelper.createInfo(Helpers.firstNonNull(author, title), subs)));
    }

    @Override
    public void update(VideoGroup videoGroup) {
        mRows.update(videoGroup);
        updateTitle();
    }

    @Override
    public void setPosition(int index) {
        mRows.setPosition(index);
    }

    @Override
    public void clear() {
        mRows.clear();
    }
}
