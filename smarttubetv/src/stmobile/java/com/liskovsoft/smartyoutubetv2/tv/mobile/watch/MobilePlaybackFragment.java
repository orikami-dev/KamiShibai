package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;

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
         * The watch panel is on screen. While it's hidden the player keeps no engine.
         */
        boolean isPanelShown();

        /**
         * The touch event in player coordinates, or null when the touch is elsewhere (infos, suggestions, comments).
         */
        MotionEvent toPlayerEvent(MotionEvent event);
    }

    private Host host() {
        // The main screen forwards to its watch panel
        return getActivity() instanceof Host ? (Host) getActivity() : null;
    }

    /**
     * The main screen uses the touch theme; the upstream player controls (kept hidden) need the player theme.
     */
    @Override
    public android.view.LayoutInflater onGetLayoutInflater(android.os.Bundle savedInstanceState) {
        android.view.LayoutInflater inflater = super.onGetLayoutInflater(savedInstanceState);
        return inflater.cloneInContext(new android.view.ContextThemeWrapper(inflater.getContext(), com.liskovsoft.smartyoutubetv2.tv.R.style.Kamishibai_Theme_Player));
    }

    /**
     * The fragment is attached again each time a video is picked. The video comes from the presenter: an id kept
     * by an earlier session of the screen must not open an old video, empty, in the panel.
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Helpers.setField(this, "mSelectedVideoId", null);
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * The fragment stays attached under Home: coming back to the app must not start an engine (and the last
     * video with it) behind a hidden panel.
     */
    @Override
    public void onResume() {
        super.onResume();

        Host host = host();

        if (host != null && !host.isPanelShown()) {
            maybeReleasePlayer();
        }
    }

    /**
     * The panel opens: the engine released when it last closed is created again, it loads the presenter's video.
     */
    public void ensureEngine() {
        if (getView() != null && Helpers.getField(this, "mPlayer") == null) {
            restartEngine();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.remove("SelectedVideoId"); // see onActivityCreated
    }

    @Override
    public void onDispatchTouchEvent(MotionEvent event) {
        Host host = host();
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

        Host host = host();

        if (host != null) {
            host.onOverlayShown(true);
        }
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(runAnimation);

        Host host = host();

        if (host != null) {
            host.onOverlayShown(false);
        }
    }

    @Override
    public void setVideo(Video video) {
        super.setVideo(video);

        Host host = host();

        if (host != null) {
            host.onVideoChanged(video);
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        super.updateSuggestions(group);

        Host host = host();

        if (host != null && group != null) {
            host.onSuggestionsUpdated(group);
        }
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        super.removeSuggestions(group);

        Host host = host();

        if (host != null && group != null) {
            host.onSuggestionsRemoved(group);
        }
    }

    @Override
    public void clearSuggestions() {
        super.clearSuggestions();

        Host host = host();

        if (host != null) {
            host.onSuggestionsCleared();
        }
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        super.setButtonState(buttonId, buttonState);

        Host host = host();

        if (host != null) {
            host.onButtonStateChanged(buttonId, buttonState);
        }
    }
}
