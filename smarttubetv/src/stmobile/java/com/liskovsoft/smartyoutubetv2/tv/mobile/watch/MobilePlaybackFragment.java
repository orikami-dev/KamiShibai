package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import android.view.MotionEvent;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

/**
 * The upstream player, unchanged: engine, controllers, double tap seek, subtitles, PIP...<br/>
 * Its TV controls stay hidden (see the flavor's lb_playback_fragment layout). Every UI call
 * is mirrored to the watch page, which draws its own touch controls, infos and suggestions.
 */
public class MobilePlaybackFragment extends PlaybackFragment {
    public interface Host {
        void onOverlayShown(boolean shown);
        void onVideoChanged(Video video);
        void onSuggestionsUpdated(VideoGroup group);
        void onSuggestionsRemoved(VideoGroup group);
        void onSuggestionsCleared();
        void onButtonStateChanged(int buttonId, int buttonState);

        /**
         * The touch event in player coordinates, or null when the touch is elsewhere (infos, suggestions, comments).
         */
        MotionEvent toPlayerEvent(MotionEvent event);
    }

    private Host getHost() {
        return getActivity() instanceof Host ? (Host) getActivity() : null;
    }

    @Override
    public void onDispatchTouchEvent(MotionEvent event) {
        Host host = getHost();
        MotionEvent playerEvent = host != null ? host.toPlayerEvent(event) : null;

        if (playerEvent == null) {
            return; // scrolling the infos must not wake the player controls
        }

        super.onDispatchTouchEvent(playerEvent);

        if (playerEvent != event) {
            playerEvent.recycle();
        }
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        super.showControlsOverlay(runAnimation);

        Host host = getHost();

        if (host != null) {
            host.onOverlayShown(true);
        }
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(runAnimation);

        Host host = getHost();

        if (host != null) {
            host.onOverlayShown(false);
        }
    }

    @Override
    public void setVideo(Video video) {
        super.setVideo(video);

        Host host = getHost();

        if (host != null) {
            host.onVideoChanged(video);
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        super.updateSuggestions(group);

        Host host = getHost();

        if (host != null && group != null) {
            host.onSuggestionsUpdated(group);
        }
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        super.removeSuggestions(group);

        Host host = getHost();

        if (host != null && group != null) {
            host.onSuggestionsRemoved(group);
        }
    }

    @Override
    public void clearSuggestions() {
        super.clearSuggestions();

        Host host = getHost();

        if (host != null) {
            host.onSuggestionsCleared();
        }
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        super.setButtonState(buttonId, buttonState);

        Host host = getHost();

        if (host != null) {
            host.onButtonStateChanged(buttonId, buttonState);
        }
    }
}
