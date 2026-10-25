package com.liskovsoft.smartyoutubetv2.tv.mobile.logs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Export the logs": a text report the user reads, then sends by e-mail or copies.<br/>
 * Header (app, phone, screen), state of the player, the notes of the app, the last crash and the last lines of the log.
 * Everything personal is replaced before it is shown: e-mails, paths, titles, channels, video ids, tokens, addresses.
 * Nothing leaves the phone without the user: the report is only handed to the mail app they pick.
 */
public final class KamiLogs {
    private static final String TAG = "KamiLogs";
    private static final String PREFS = "kami_logs";
    private static final String KEY_CRASH_UNSEEN = "crash_unseen";
    private static final String CRASH_FILE = "last-crash.txt";
    private static final int LOG_LINES = 500;
    private static final int MAX_NOTES = 50;
    private static final ArrayDeque<String> sNotes = new ArrayDeque<>();

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PATH = Pattern.compile("(/storage/|/sdcard/|/mnt/|/data/user/|/data/data/|content://)[^\\s'\",;)\\]]*");
    private static final Pattern URL_SECRET = Pattern.compile(
            "(?i)([?&](?:access_token|refresh_token|token|key|sig|lsig|signature|auth|pot|cpn|code|password|session|visitor_data|cookie)=)[^&\\s'\"]+");
    private static final Pattern HEADER_SECRET = Pattern.compile("(?i)((?:authorization|cookie|x-goog-[a-z-]+)\\s*[:=]\\s*(?:bearer\\s+)?|bearer\\s+)[^\\s,;]+");
    private static final Pattern MAC = Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    // Channel (UC + 22), playlist (PL, UU, LL, RD, OL, FL...) and video ids (11 signs mixing case and digits or - _)
    private static final Pattern CHANNEL_ID = Pattern.compile("(?<![A-Za-z0-9_-])UC[A-Za-z0-9_-]{22}(?![A-Za-z0-9_-])");
    private static final Pattern PLAYLIST_ID = Pattern.compile("(?<![A-Za-z0-9_-])(?:PL|UU|LL|RD|OL|FL|OLAK5uy_)[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])");
    private static final Pattern VIDEO_ID_CONTEXT = Pattern.compile("(?i)(v=|video_?id[\"']?\\s*[:=]\\s*[\"']?|youtu\\.be/|/shorts/|/vi(?:_webp)?/|/embed/|/watch/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])");
    private static final Pattern VIDEO_ID = Pattern.compile("(?<![A-Za-z0-9_./-])(?=[A-Za-z0-9_-]*[A-Z])(?=[A-Za-z0-9_-]*[a-z])(?=[A-Za-z0-9_-]*[0-9_-])[A-Za-z0-9_-]{11}(?![A-Za-z0-9_-])");
    private static final Pattern TITLE_FIELD = Pattern.compile("(?i)\\b(title|titre|author|channelName|channel_name)(\\s*[:=]\\s*)([^,;}\\]\\n]+)");

    private KamiLogs() {
    }

    // region Crash

    /**
     * Keeps the trace of a crash for the next start, then lets Android close the app as before.
     */
    public static void installCrashHandler(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                String text = now() + " · thread " + thread.getName() + "\n" + trace;

                try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(app.getFilesDir(), CRASH_FILE)), StandardCharsets.UTF_8)) {
                    out.write(text);
                }

                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CRASH_UNSEEN, true).commit();
            } catch (Throwable ignored) {
                // Never stand in the way of the crash itself
            }

            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /**
     * After a crash, the next start offers to export the logs, once.
     * @return true when the offer is shown (other start dialogs wait for the next time)
     */
    public static boolean offerAfterCrash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_CRASH_UNSEEN, false)) {
            return false;
        }

        prefs.edit().putBoolean(KEY_CRASH_UNSEEN, false).apply();
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_logs_export), option -> {
            dialog.closeDialog();
            open(context);
        }));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.kami_logs_later), option -> dialog.closeDialog()));
        dialog.showDialog(context.getString(R.string.kami_logs_crash_title));
        return true;
    }

    // endregion

    /**
     * A line of the app itself, kept in memory for the report (a blocked screen, a failed update...).
     */
    public static void note(String line) {
        synchronized (sNotes) {
            if (sNotes.size() >= MAX_NOTES) {
                sNotes.pollFirst();
            }

            sNotes.addLast(now() + "  " + line);
        }
    }

    public static void open(Context context) {
        Intent intent = new Intent(context, KamiLogsActivity.class);

        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(intent);
    }

    // region Report

    /**
     * The report, already filtered. Reads the log: call it off the main thread, the player state is read before.
     */
    static String build(Context context, String playerState, String title, String channel) {
        StringBuilder report = new StringBuilder();
        report.append(context.getString(R.string.app_name)).append(' ').append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        report.append("Palette : ").append(KamiTheme.paletteName(context, KamiTheme.getPalette(context))).append('\n');
        report.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("Appareil : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');

        Configuration config = context.getResources().getConfiguration();
        report.append("Écran : ").append(config.screenWidthDp).append('×').append(config.screenHeightDp)
                .append(" dp, sw ").append(config.smallestScreenWidthDp).append(" dp, ")
                .append(config.smallestScreenWidthDp >= 600 ? "grand écran (déplié)" : "écran compact (plié ou téléphone)")
                .append(", ").append(config.orientation == Configuration.ORIENTATION_LANDSCAPE ? "paysage" : "portrait").append('\n');
        report.append("Heure : ").append(now()).append('\n');

        if (Build.VERSION.SDK_INT >= 24) {
            long uptime = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
            report.append("App ouverte depuis : ").append(uptime / 60_000).append(" min\n");
        }

        report.append("\n== Lecteur ==\n").append(playerState).append('\n');

        synchronized (sNotes) {
            if (!sNotes.isEmpty()) {
                report.append("\n== Notes de l'app ==\n");

                for (String note : sNotes) {
                    report.append(note).append('\n');
                }
            }
        }

        String crash = readCrash(context);
        report.append("\n== Dernier plantage ==\n").append(crash != null ? crash : "aucun\n");
        report.append("\n== Journal (").append(LOG_LINES).append(" dernières lignes) ==\n").append(readLog());

        return filter(report.toString(), title, channel, deviceName(context));
    }

    /**
     * What the player shows now. Main thread.
     */
    static String describePlayer(Context context) {
        StringBuilder state = new StringBuilder();
        ViewManager.ViewHost host = ViewManager.instance(context).getViewHost();
        state.append("Affichage : ").append(host instanceof MobileBrowseActivity ? ((MobileBrowseActivity) host).describePlayer() : "aucun").append('\n');

        try {
            PlaybackView player = PlaybackPresenter.instance(context).getPlayer();

            if (player != null) {
                FormatItem video = player.getVideoFormat();
                FormatItem audio = player.getAudioFormat();
                state.append("Vidéo : ").append(describe(video)).append('\n');
                state.append("Audio : ").append(describe(audio)).append('\n');
                state.append("Lecture : ").append(player.isPlaying() ? "en cours" : "en pause").append('\n');
            }
        } catch (Exception e) {
            state.append("État indisponible : ").append(e.getClass().getSimpleName()).append('\n');
        }

        return state.toString();
    }

    static String currentTitle(Context context) {
        Video video = PlaybackPresenter.instance(context).getVideo();
        return video != null ? video.getTitle() : null;
    }

    static String currentChannel(Context context) {
        Video video = PlaybackPresenter.instance(context).getVideo();
        return video != null ? video.getAuthor() : null;
    }

    private static String describe(FormatItem format) {
        if (format == null) {
            return "aucun";
        }

        String size = format.getWidth() > 0 ? format.getWidth() + "×" + format.getHeight() + " " : "";
        return size + (format.getTitle() != null ? format.getTitle() : "") + (format.getFrameRate() > 0 ? " " + Math.round(format.getFrameRate()) + " i/s" : "");
    }

    private static String readCrash(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);

        if (!file.exists()) {
            return null;
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null && text.length() < 32_000) {
                text.append(line).append('\n');
            }

            return text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The app can only read its own lines of the system log.
     */
    private static String readLog() {
        String[] command = Build.VERSION.SDK_INT >= 24
                ? new String[] {"logcat", "-d", "-v", "time", "-t", String.valueOf(LOG_LINES), "--pid", String.valueOf(Process.myPid())}
                : new String[] {"logcat", "-d", "-v", "time", "-t", String.valueOf(LOG_LINES)};

        try {
            java.lang.Process logcat = Runtime.getRuntime().exec(command);
            StringBuilder text = new StringBuilder();

            try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream(), StandardCharsets.UTF_8))) {
                String line;

                while ((line = in.readLine()) != null) {
                    text.append(line).append('\n');
                }
            }

            logcat.destroy();
            return text.length() > 0 ? text.toString() : "vide\n";
        } catch (Exception e) {
            Log.w(TAG, "Can't read the log: " + e);
            return "illisible (" + e.getClass().getSimpleName() + ")\n";
        }
    }

    private static String deviceName(Context context) {
        try {
            return Build.VERSION.SDK_INT >= 25 ? Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // endregion

    // region Filter

    /**
     * Replaces what could tell who the user is or what they watch. Ids are numbered, so the same video stays recognizable
     * along the report (<id-1>, <id-2>...) without being named.
     */
    static String filter(String text, String title, String channel, String device) {
        text = replaceExact(text, title, "<titre>");
        text = replaceExact(text, channel, "<chaîne>");
        text = replaceExact(text, device, "<appareil>");

        text = EMAIL.matcher(text).replaceAll("<email>");
        text = URL_SECRET.matcher(text).replaceAll("$1<masqué>");
        text = HEADER_SECRET.matcher(text).replaceAll("$1<masqué>");
        text = PATH.matcher(text).replaceAll("<chemin>");
        text = MAC.matcher(text).replaceAll("<mac>");
        text = IPV4.matcher(text).replaceAll("<ip>");
        text = TITLE_FIELD.matcher(text).replaceAll("$1$2<masqué>");

        Map<String, String> ids = new LinkedHashMap<>();
        text = number(CHANNEL_ID.matcher(text), ids, 0);
        text = number(PLAYLIST_ID.matcher(text), ids, 0);
        text = number(VIDEO_ID_CONTEXT.matcher(text), ids, 2);
        text = number(VIDEO_ID.matcher(text), ids, 0);

        return text;
    }

    private static String replaceExact(String text, String value, String mark) {
        if (TextUtils.isEmpty(value) || value.trim().length() < 3) {
            return text;
        }

        return text.replace(value.trim(), mark);
    }

    private static String number(Matcher matcher, Map<String, String> ids, int group) {
        StringBuffer out = new StringBuffer();

        while (matcher.find()) {
            String id = matcher.group(group);
            String mark = ids.get(id);

            if (mark == null) {
                mark = "<id-" + (ids.size() + 1) + ">";
                ids.put(id, mark);
            }

            String replaced = group == 0 ? mark : matcher.group().substring(0, matcher.start(group) - matcher.start()) + mark;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replaced));
        }

        matcher.appendTail(out);
        return out.toString();
    }

    // endregion

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(new Date());
    }
}
