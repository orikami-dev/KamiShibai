package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.logs.KamiLogs;

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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Updates from the KamiShibai releases on GitHub, same scheme as Ongaku: the "update" branch holds check.json
 * ({"version", "url", "arm64url", "sha256", "arm64sha256", "announcementurl"}), the notes are the body of the latest release.<br/>
 * Automatic updates (on by default, Settings > About): when the app starts (at most twice a day) a newer version is
 * downloaded in the background, checked, then offered to install. Off: nothing happens at start, the manual check stays.<br/>
 * Before the Android installer gets the file: its SHA-256 must match the one of check.json (when published)
 * and it must be signed with the same key as the installed app.
 */
public final class KamiUpdate {
    private static final String TAG = "KamiUpdate";
    private static final String CHECK_URL = "https://raw.githubusercontent.com/orikami-dev/kamishibai/update/check.json";
    private static final String RELEASE_URL = "https://api.github.com/repos/orikami-dev/kamishibai/releases/latest";
    private static final String PREFS = "kami_update";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_AUTO = "auto";
    private static final long CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 15_000;
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static boolean sBusy;

    private KamiUpdate() {
    }

    public static boolean isAutoEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true);
    }

    public static void setAutoEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, enabled).apply();
    }

    /**
     * Settings > About > Automatic updates.
     */
    public static void showSettings(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(context.getString(R.string.kami_update_auto_on), option -> setAutoEnabled(context, true), isAutoEnabled(context)));
        options.add(UiOptionItem.from(context.getString(R.string.kami_update_auto_off), option -> setAutoEnabled(context, false), !isAutoEnabled(context)));
        dialog.appendRadioCategory(context.getString(R.string.kami_set_auto_update), options);
        dialog.showDialog(context.getString(R.string.kami_set_auto_update));
    }

    public static void checkOnStart(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (isAutoEnabled(context) && System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) > CHECK_INTERVAL_MS) {
            check(context, false);
        }
    }

    /**
     * @param manual asked by the user: offers the version before downloading it, says so when there's nothing new or the check failed.
     *               Otherwise (automatic) the version is downloaded first, then offered.
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
                String sha256 = pickSha256(check, url);

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

                if (manual) {
                    sMain.post(() -> offer(context, version, url, sha256, releaseNotes));
                } else {
                    File apk = fetch(app, url, sha256);
                    sMain.post(() -> offerReady(context, version, apk, releaseNotes));
                }
            } catch (Exception e) {
                Log.w(TAG, "Update check failed: " + e);
                KamiLogs.note("Update failed: " + e.getMessage());

                if (manual) {
                    sMain.post(() -> MessageHelpers.showMessage(app, R.string.kami_update_failed));
                }
            } finally {
                sBusy = false;
            }
        }).start();
    }

    private static void offer(Context context, String version, String url, String sha256, String notes) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        appendNotes(context, dialog, notes);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_update_install), option -> {
            dialog.closeDialog();
            download(context.getApplicationContext(), version, url, sha256);
        }));
        dialog.showDialog(context.getString(R.string.kami_update_title, version));
    }

    /**
     * Automatic update: the file is already there and checked, one tap opens the Android installer.
     */
    private static void offerReady(Context context, String version, File apk, String notes) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        appendNotes(context, dialog, notes);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_update_install_now), option -> {
            dialog.closeDialog();
            Helpers.installPackage(context.getApplicationContext(), apk.getAbsolutePath());
        }));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_update_later), option -> dialog.closeDialog()));
        dialog.showDialog(context.getString(R.string.kami_update_ready, version));
    }

    private static void appendNotes(Context context, AppDialogPresenter dialog, String notes) {
        if (!TextUtils.isEmpty(notes)) {
            dialog.appendLongTextCategory(context.getString(R.string.kami_update_notes), UiOptionItem.from(notes.trim()));
        }
    }

    private static void download(Context app, String version, String url, String sha256) {
        MessageHelpers.showMessage(app, app.getString(R.string.kami_update_downloading, version));

        new Thread(() -> {
            try {
                File apk = fetch(app, url, sha256);
                sMain.post(() -> Helpers.installPackage(app, apk.getAbsolutePath()));
            } catch (IOException e) {
                Log.w(TAG, "Download failed: " + e);
                KamiLogs.note("Update download failed: " + e.getMessage());
                boolean refused = e instanceof RefusedException;
                sMain.post(() -> MessageHelpers.showMessage(app, refused ? R.string.kami_update_refused : R.string.kami_update_failed));
            }
        }).start();
    }

    /**
     * Downloads into the cache and checks the file. Background thread.
     */
    private static File fetch(Context app, String url, String sha256) throws IOException {
        File dir = app.getExternalCacheDir() != null ? app.getExternalCacheDir() : app.getCacheDir();
        File apk = new File(dir, "KamiShibai-update.apk");
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }

        try (InputStream in = open(url).getInputStream(); OutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }

            out.flush();
        } catch (IOException e) {
            apk.delete();
            throw e;
        }

        String actual = toHex(digest.digest());

        if (!TextUtils.isEmpty(sha256) && !sha256.trim().equalsIgnoreCase(actual)) {
            apk.delete();
            throw new RefusedException("SHA-256 mismatch: " + actual);
        }

        if (!isSameSigner(app, apk)) {
            apk.delete();
            throw new RefusedException("Not signed with the key of the installed app");
        }

        return apk;
    }

    private static final class RefusedException extends IOException {
        RefusedException(String message) {
            super(message);
        }
    }

    /**
     * Same certificates as the installed app: Android would refuse the update anyway, here it never reaches the installer.
     */
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    private static boolean isSameSigner(Context app, File apk) {
        try {
            PackageManager manager = app.getPackageManager();
            PackageInfo installed = manager.getPackageInfo(app.getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);

            if (archive == null || archive.signatures == null || installed.signatures == null || !app.getPackageName().equals(archive.packageName)) {
                return false;
            }

            return fingerprints(installed.signatures).equals(fingerprints(archive.signatures));
        } catch (Exception e) {
            Log.w(TAG, "Can't compare the signatures: " + e);
            return false;
        }
    }

    private static Set<String> fingerprints(Signature[] signatures) throws Exception {
        Set<String> result = new HashSet<>();

        for (Signature signature : signatures) {
            result.add(toHex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        }

        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();

        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }

        return hex.toString();
    }

    /**
     * The SHA-256 published for the file picked by {@link #pickUrl}.
     */
    private static String pickSha256(JSONObject check, String url) {
        String arm64Url = check.isNull("arm64url") ? null : check.optString("arm64url", null);
        String key = url != null && url.equals(arm64Url) && !check.isNull("arm64sha256") ? "arm64sha256" : "sha256";
        return check.isNull(key) ? null : check.optString(key, null);
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
        connection.setRequestProperty("User-Agent", "KamiShibai/" + BuildConfig.VERSION_NAME);
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
