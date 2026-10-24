package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

/**
 * Touch counterpart of the upstream VideoSection: receives the groups sent by a presenter
 * and lays them out in a RecyclerView that adapts to the window width.
 */
public interface MobileSection {
    void attach(RecyclerView recyclerView);
    void detach();
    void update(VideoGroup group);
    void clear();
    boolean isEmpty();
    void setPosition(int index);
    void selectItem(Video item);
    /**
     * Called each time the available width changes (rotation, fold/unfold, split screen).
     */
    void onWidthChanged(float widthDp);
}
