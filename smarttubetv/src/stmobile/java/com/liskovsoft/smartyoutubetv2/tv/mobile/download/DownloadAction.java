package com.liskovsoft.smartyoutubetv2.tv.mobile.download;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry points of the download feature: the button of the watch page, and the settings.
 */
public final class DownloadAction {
    private static final int ID_QUALITY = 4301;
    private static final int ID_HISTORY = 4302;

    private DownloadAction() {
    }

    /**
     * Starts the download with the default quality, or asks which one to use.
     */
    public static void start(Context context, Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }

        if (!ensurePermissions(context)) {
            return; // asked, the user taps Download again once granted
        }

        int quality = Downloads.getQuality(context);

        if (quality != Downloads.ASK) {
            DownloadService.start(context, video.videoId, video.title, quality);
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int choice : Downloads.CHOICES) {
            if (choice == Downloads.ASK) {
                continue;
            }

            options.add(UiOptionItem.from(label(context, choice), item -> {
                DownloadService.start(context, video.videoId, video.title, choice);
                dialog.closeDialog();
            }, false));
        }

        String title = context.getString(R.string.kami_dl_title);
        dialog.appendStringsCategory(title, options);
        dialog.showDialog(title);
    }

    /**
     * The "Downloads" entry of the settings: default quality and what was downloaded.
     */
    public static void showSettings(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        int current = Downloads.getQuality(context);
        List<OptionItem> qualities = new ArrayList<>();

        for (int choice : Downloads.CHOICES) {
            qualities.add(UiOptionItem.from(label(context, choice), item -> Downloads.setQuality(context, choice), choice == current));
        }

        dialog.appendCategory(OptionCategory.from(ID_QUALITY, OptionCategory.TYPE_RADIO_LIST, context.getString(R.string.kami_dl_default), qualities));

        List<OptionItem> history = new ArrayList<>();

        for (Downloads.Entry entry : Downloads.getHistory(context)) {
            history.add(UiOptionItem.from(entry.title, item -> open(context, entry)));
        }

        if (history.isEmpty()) {
            history.add(UiOptionItem.from(context.getString(R.string.kami_dl_history_empty)));
        }

        dialog.appendCategory(OptionCategory.from(ID_HISTORY, OptionCategory.TYPE_STRING_LIST, context.getString(R.string.kami_dl_history), history));

        dialog.showDialog(context.getString(R.string.kami_set_downloads));
    }

    private static void open(Context context, Downloads.Entry entry) {
        android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(entry.uri), entry.audio ? "audio/mp4" : "video/mp4")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(view);
        } catch (Exception e) {
            // no player, or the file was removed
        }
    }

    private static String label(Context context, int choice) {
        switch (choice) {
            case Downloads.ASK:
                return context.getString(R.string.kami_dl_ask);
            case Downloads.AUDIO:
                return context.getString(R.string.kami_dl_audio);
            case Downloads.BEST:
                return context.getString(R.string.kami_dl_best);
            default:
                return context.getString(R.string.kami_dl_height, choice);
        }
    }

    /**
     * Android 13+: permission to show the progress notification (the download works without it).
     * Android 6 to 9: permission to write in the Movies and Music folders (the download fails without it).
     */
    private static boolean ensurePermissions(Context context) {
        if (!(context instanceof Activity)) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            return false;
        }

        return true;
    }
}
