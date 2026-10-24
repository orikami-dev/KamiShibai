package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

import java.util.List;

import io.reactivex.Observable;

/**
 * Listens to the upstream player like any other controller, to feed the watch page
 * (title, channel, counts, comments key) without touching the upstream controllers.
 */
public class WatchController extends BasePlayerController {
    public interface Callback {
        void onMetadata(MediaItemMetadata metadata);
        void onNewVideo(Video video);
    }

    private static WatchController sInstance;
    private Callback mCallback;
    private MediaItemMetadata mMetadata;

    /**
     * Appends this controller to the presenter's own list, so it gets the same events as the upstream ones.
     */
    @SuppressWarnings("unchecked")
    public static WatchController install(PlaybackPresenter presenter) {
        if (sInstance == null) {
            sInstance = new WatchController();
            Object listeners = Helpers.getField(presenter, "mEventListeners");

            if (listeners instanceof List) {
                ((List<PlayerEventListener>) listeners).add(sInstance);
            }
        }

        return sInstance;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;

        if (callback != null && mMetadata != null) {
            callback.onMetadata(mMetadata);
        }
    }

    public MediaItemMetadata getMetadata() {
        return mMetadata;
    }

    @Override
    public void onNewVideo(Video item) {
        mMetadata = null;

        if (mCallback != null) {
            mCallback.onNewVideo(item);
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mMetadata = metadata;

        if (mCallback != null) {
            mCallback.onMetadata(metadata);
        }
    }

    public Observable<CommentGroup> loadComments(String commentsKey) {
        return getCommentsService().getCommentsObserve(commentsKey);
    }

    public Observable<Void> toggleCommentLike(String commentKey) {
        return getCommentsService().toggleLikeObserve(commentKey);
    }

    public Observable<Void> toggleCommentDislike(String commentKey) {
        return getCommentsService().toggleDislikeObserve(commentKey);
    }
}
