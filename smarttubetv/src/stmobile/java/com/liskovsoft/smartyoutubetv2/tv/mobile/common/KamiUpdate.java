package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Updates from the Kamishibai releases on GitHub, same scheme as Ongaku: the "update" branch holds check.json
 * ({"version", "url", "arm64url", "announcementurl"}), the notes are the body of the latest release.
 * Checked when the app starts (at most twice a day) and from Settings > About. A newer version is offered in a menu,
 * downloaded into the cache and handed to the Android installer.
 */
public final class KamiUpdate {
    private static final String TAG = "KamiUpdate";
    private static final String CHECK_URL = "https://raw.githubusercontent.com/orikami-dev/kamishibai/update/check.json";
    private static final String RELEASE_URL = "https://api.github.com/repos/orikami-dev/kamishibai/releases/latest";
    private static final String PREFS = "kami_update";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final long CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 15_000;
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static boolean sBusy;

    private KamiUpdate() {
    }

    public static void checkOnStart(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) > CHECK_INTERVAL_MS) {
            check(context, false);
        }
    }

    /**
     * @param manual asked by the user: says so when there's nothing new or the check failed
     */
    public static void check(Context context, boolean manual) {
        Context app = context.getApplicationContext();

        if (sBusy) {
            return;
        }

        sBusy = true;

        new Thread(() -> {
            try {
                JSONObject check = new JSONObject(get(CHECK_URL));
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                String version = check.optString("version");
                String url = pickUrl(check);

                if (url == null || !isNewer(version, BuildConfig.VERSION_NAME)) {
                    if (manual) {
                        sMain.post(() -> MessageHelpers.showMessage(app, app.getString(R.string.kami_update_none, BuildConfig.VERSION_NAME)));
                    }
                    return;
                }

                String notes = null;

                try {
                    JSONObject release = new JSONObject(get(RELEASE_URL));
                    notes = release.isNull("body") ? null : release.optString("body");
                } catch (Exception e) {
                    Log.w(TAG, "No release notes: " + e);
                }

                String releaseNotes = notes;
                sMain.post(() -> offer(context, version, url, releaseNotes));
            } catch (Exception e) {
                Log.w(TAG, "Update check failed: " + e);

                if (manual) {
                    sMain.post(() -> MessageHelpers.showMessage(app, R.string.kami_update_failed));
                }
            } finally {
                sBusy = false;
            }
        }).start();
    }

    private static void offer(Context context, String version, String url, String notes) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        if (!TextUtils.isEmpty(notes)) {
            dialog.appendLongTextCategory(context.getString(R.string.kami_update_notes), UiOptionItem.from(notes.trim()));
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_update_install), option -> {
            dialog.closeDialog();
            download(context.getApplicationContext(), version, url);
        }));
        dialog.showDialog(context.getString(R.string.kami_update_title, version));
    }

    private static void download(Context app, String version, String url) {
        MessageHelpers.showMessage(app, app.getString(R.string.kami_update_downloading, version));

        new Thread(() -> {
            File dir = app.getExternalCacheDir() != null ? app.getExternalCacheDir() : app.getCacheDir();
            File apk = new File(dir, "Kamishibai-update.apk");

            try (InputStream in = open(url).getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                out.flush();
                sMain.post(() -> Helpers.installPackage(app, apk.getAbsolutePath()));
            } catch (IOException e) {
                Log.w(TAG, "Download failed: " + e);
                apk.delete();
                sMain.post(() -> MessageHelpers.showMessage(app, R.string.kami_update_failed));
            }
        }).start();
    }

    /**
     * The arm64 build when the phone runs it and one is published, the main one otherwise.
     */
    private static String pickUrl(JSONObject check) {
        String arm64 = check.isNull("arm64url") ? null : check.optString("arm64url", null);
        String url = check.isNull("url") ? null : check.optString("url", null);
        boolean isArm64 = Build.SUPPORTED_64_BIT_ABIS.length > 0 && "arm64-v8a".equals(Build.SUPPORTED_64_BIT_ABIS[0]);

        return isArm64 && !TextUtils.isEmpty(arm64) ? arm64 : TextUtils.isEmpty(url) ? null : url;
    }

    /**
     * Compares dotted numbers: 1.10 is newer than 1.9, 1.0.1 newer than 1.0.
     */
    static boolean isNewer(String candidate, String current) {
        if (TextUtils.isEmpty(candidate) || TextUtils.isEmpty(current)) {
            return false;
        }

        String[] a = candidate.trim().split("\\.");
        String[] b = current.trim().split("\\.");

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;

            if (x != y) {
                return x > y;
            }
        }

        return false;
    }

    private static int number(String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Kamishibai/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*");

        int code = connection.getResponseCode();

        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }

        return connection;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = open(url);

        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
