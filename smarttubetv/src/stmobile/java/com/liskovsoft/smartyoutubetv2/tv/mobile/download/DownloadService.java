package com.liskovsoft.smartyoutubetv2.tv.mobile.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads a video (or only its sound) into the gallery, one at a time, with a progress notification.<br/>
 * The best video and audio streams the video offers are fetched in chunks, then joined without re-encoding.
 */
public class DownloadService extends Service {
    private static final String EXTRA_VIDEO_ID = "video_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_QUALITY = "quality";
    private static final String ACTION_CANCEL = "com.liskovsoft.smartyoutubetv2.tv.mobile.download.CANCEL";
    private static final String CHANNEL_ID = "kamishibai_downloads";
    private static final int FOREGROUND_ID = 4201;
    private static final int CHUNK_BYTES = 4 * 1024 * 1024;
    private static final int RETRIES = 3;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final class Cancelled extends IOException {
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mPending = new AtomicInteger();
    private final AtomicInteger mJobIds = new AtomicInteger(1);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mCancelled;
    private NotificationManager mNotifications;

    public static void start(Context context, String videoId, String title, int quality) {
        Intent intent = new Intent(context, DownloadService.class)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_QUALITY, quality);

        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.kami_dl_channel), NotificationManager.IMPORTANCE_LOW);
            mNotifications.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_CANCEL.equals(intent.getAction())) {
            mCancelled = true; // running service, already in the foreground
            return START_NOT_STICKY;
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        enterForeground(buildProgress(title != null ? title : "", getString(R.string.kami_dl_preparing), -1));

        String videoId = intent.getStringExtra(EXTRA_VIDEO_ID);
        int quality = intent.getIntExtra(EXTRA_QUALITY, Downloads.BEST);
        int jobId = mJobIds.getAndIncrement();

        if (mPending.getAndIncrement() == 0) {
            mCancelled = false;
        }

        toast(R.string.kami_dl_started);
        mExecutor.execute(() -> {
            try {
                run(jobId, videoId, title, quality);
            } finally {
                if (mPending.decrementAndGet() == 0) {
                    stopForeground(true);
                    stopSelf();
                }
            }
        });

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    // region Job

    private void run(int jobId, String videoId, String title, int quality) {
        File cache = new File(getCacheDir(), "download-" + jobId);
        cache.mkdirs();
        String shownTitle = title != null ? title : videoId;

        try {
            notifyProgress(shownTitle, -1);
            MediaItemFormatInfo info = YouTubeServiceManager.instance().getMediaItemService().getFormatInfo(videoId);

            if (info == null || info.isUnplayable() || info.isLive()) {
                throw new IOException("unavailable");
            }

            Streams streams = Streams.pick(info, quality);

            if (streams == null) {
                throw new IOException("no format");
            }

            File video = streams.video != null ? new File(cache, "video.mp4") : null;
            File audio = streams.audio != null ? new File(cache, "audio.m4a") : null;
            long videoSize = video != null ? Streams.length(streams.video) : 0;
            long audioSize = audio != null ? Streams.length(streams.audio) : 0;
            long[] done = {0};
            long total = Math.max(1, videoSize + audioSize);
            Progress progress = bytes -> {
                done[0] += bytes;
                notifyProgress(shownTitle, (int) Math.min(99, done[0] * 100 / total));
            };

            if (video != null) {
                fetch(streams.video.getUrl(), video, videoSize, progress);
            }

            if (audio != null) {
                fetch(streams.audio.getUrl(), audio, audioSize, progress);
            }

            File result = new File(cache, "result");

            if (video != null && audio != null) {
                notifyProgress(shownTitle, 99);
                mux(video, audio, result);
            } else {
                File single = video != null ? video : audio;

                if (!single.renameTo(result)) {
                    copy(single, result);
                }
            }

            boolean soundOnly = video == null;
            Uri uri = save(result, fileName(shownTitle, soundOnly), soundOnly);
            Downloads.addToHistory(this, shownTitle, uri.toString(), soundOnly);
            notifyDone(jobId, shownTitle, uri, soundOnly);
        } catch (Cancelled e) {
            // the user stopped it, nothing to report
        } catch (Exception e) {
            notifyFailed(jobId, shownTitle, e.getMessage() != null && e.getMessage().equals("unavailable") ? R.string.kami_dl_unavailable : R.string.kami_dl_failed);
        } finally {
            deleteRecursive(cache);
        }
    }

    private interface Progress {
        void onBytes(long bytes);
    }

    private void fetch(String url, File target, long size, Progress progress) throws IOException {
        long offset = 0;

        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];

            while (size <= 0 || offset < size) {
                if (mCancelled) {
                    throw new Cancelled();
                }

                long end = offset + CHUNK_BYTES - 1;
                HttpURLConnection connection = null;
                int attempt = 0;

                while (true) {
                    try {
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(20000);
                        connection.setRequestProperty("User-Agent", USER_AGENT);
                        connection.setRequestProperty("Range", "bytes=" + offset + "-" + end);
                        int code = connection.getResponseCode();

                        if (code != 200 && code != 206) {
                            throw new IOException("HTTP " + code);
                        }

                        if (size <= 0) {
                            size = totalFromContentRange(connection.getHeaderField("Content-Range"), connection.getContentLengthLong());
                        }

                        long received = 0;

                        try (InputStream in = connection.getInputStream()) {
                            int read;

                            while ((read = in.read(buffer)) != -1) {
                                if (mCancelled) {
                                    throw new Cancelled();
                                }

                                out.write(buffer, 0, read);
                                received += read;
                                progress.onBytes(read);
                            }
                        }

                        offset += received;

                        if (received == 0 || code == 200) {
                            return; // server sent everything at once, or nothing is left
                        }

                        break;
                    } catch (Cancelled e) {
                        throw e;
                    } catch (IOException e) {
                        if (++attempt >= RETRIES) {
                            throw e;
                        }
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }
        }
    }

    private static long totalFromContentRange(String contentRange, long fallback) {
        // "bytes 0-4194303/12345678"
        if (contentRange != null) {
            int slash = contentRange.lastIndexOf('/');

            if (slash > 0) {
                try {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
        }

        return fallback;
    }

    // endregion

    // region Mux and save

    private void mux(File video, File audio, File target) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;

        try {
            videoExtractor.setDataSource(video.getPath());
            audioExtractor.setDataSource(audio.getPath());
            int videoTrack = selectTrack(videoExtractor, "video/");
            int audioTrack = selectTrack(audioExtractor, "audio/");

            muxer = new MediaMuxer(target.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack));
            int outAudio = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack));
            muxer.start();
            started = true;

            copySamples(videoExtractor, muxer, outVideo);
            copySamples(audioExtractor, muxer, outAudio);
        } finally {
            videoExtractor.release();
            audioExtractor.release();

            if (muxer != null) {
                try {
                    if (started) {
                        muxer.stop();
                    }
                } finally {
                    muxer.release();
                }
            }
        }
    }

    private int selectTrack(MediaExtractor extractor, String mimePrefix) throws IOException {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime != null && mime.startsWith(mimePrefix)) {
                extractor.selectTrack(i);
                return i;
            }
        }

        throw new IOException("no " + mimePrefix + " track");
    }

    private void copySamples(MediaExtractor extractor, MediaMuxer muxer, int track) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (true) {
            if (mCancelled) {
                throw new Cancelled();
            }

            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);

            if (size < 0) {
                return;
            }

            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = extractor.getSampleTime();
            info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            muxer.writeSampleData(track, buffer, info);
            extractor.advance();
        }
    }

    private Uri save(File source, String name, boolean soundOnly) throws IOException {
        String mime = soundOnly ? "audio/mp4" : "video/mp4";
        String directory = soundOnly ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES;

        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = getContentResolver();
            Uri collection = soundOnly ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory + "/Kamishibai");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(collection, values);

            if (uri == null) {
                throw new IOException("MediaStore");
            }

            try (InputStream in = new FileInputStream(source); OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("MediaStore");
                }

                pipe(in, out);
            }

            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        }

        File folder = new File(Environment.getExternalStoragePublicDirectory(directory), "Kamishibai");

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("folder");
        }

        File target = new File(folder, name);
        copy(source, target);

        AtomicReference<Uri> scanned = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        MediaScannerConnection.scanFile(this, new String[]{target.getPath()}, new String[]{mime}, (path, uri) -> {
            scanned.set(uri);
            latch.countDown();
        });

        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return scanned.get() != null ? scanned.get() : Uri.fromFile(target);
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            pipe(in, out);
        }
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String fileName(String title, boolean soundOnly) {
        String clean = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();

        if (clean.length() > 80) {
            clean = clean.substring(0, 80).trim();
        }

        if (clean.isEmpty()) {
            clean = "video";
        }

        return clean + (soundOnly ? ".m4a" : ".mp4");
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();

        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }

        file.delete();
    }

    // endregion

    // region Notifications

    private void enterForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private Notification buildProgress(String title, String text, int percent) {
        Intent cancel = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelIntent = PendingIntent.getService(this, 0, cancel, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.kami_ic_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, percent), percent < 0)
                .addAction(0, getString(R.string.kami_dl_cancel), cancelIntent)
                .build();
    }

    private void notifyProgress(String title, int percent) {
        String text = percent < 0 ? getString(R.string.kami_dl_preparing) : getString(R.string.kami_dl_progress) + " " + percent + "%";
        mNotifications.notify(FOREGROUND_ID, buildProgress(title, text, percent));
    }

    private void notifyDone(int jobId, String title, Uri uri, boolean soundOnly) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, soundOnly ? "audio/mp4" : "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, jobId, view, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        mNotifications.notify(FOREGROUND_ID + jobId, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.kami_ic_download)
                .setContentTitle(title)
                .setContentText(getString(R.string.kami_dl_done))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
        toast(R.string.kami_dl_done);
    }

    private void notifyFailed(int jobId, String title, int messageId) {
        mNotifications.notify(FOREGROUND_ID + jobId, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.kami_ic_download)
                .setContentTitle(title)
                .setContentText(getString(messageId))
                .setAutoCancel(true)
                .build());
        toast(messageId);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void toast(int messageId) {
        mMainHandler.post(() -> Toast.makeText(getApplicationContext(), messageId, Toast.LENGTH_SHORT).show());
    }

    // endregion

    /**
     * What to fetch: separate video and audio streams (joined afterwards), or one regular file.
     */
    private static final class Streams {
        final com.liskovsoft.mediaserviceinterfaces.data.MediaFormat video;
        final com.liskovsoft.mediaserviceinterfaces.data.MediaFormat audio;

        private Streams(com.liskovsoft.mediaserviceinterfaces.data.MediaFormat video, com.liskovsoft.mediaserviceinterfaces.data.MediaFormat audio) {
            this.video = video;
            this.audio = audio;
        }

        static Streams pick(MediaItemFormatInfo info, int quality) {
            List<com.liskovsoft.mediaserviceinterfaces.data.MediaFormat> videos = new ArrayList<>();
            com.liskovsoft.mediaserviceinterfaces.data.MediaFormat bestAudio = null;
            int maxHeight = quality > 0 ? quality : Integer.MAX_VALUE;

            if (info.getAdaptiveFormats() != null) {
                for (com.liskovsoft.mediaserviceinterfaces.data.MediaFormat format : info.getAdaptiveFormats()) {
                    String mime = format.getMimeType();

                    if (format.getUrl() == null || mime == null || format.isDrc() || format.isOtf()) {
                        continue;
                    }

                    if (mime.startsWith("audio/mp4")) {
                        if (bestAudio == null || number(format.getBitrate()) > number(bestAudio.getBitrate())) {
                            bestAudio = format;
                        }
                    } else if (mime.startsWith("video/mp4") && mime.contains("avc1") && format.getHeight() <= maxHeight) {
                        videos.add(format);
                    }
                }
            }

            if (quality == Downloads.AUDIO) {
                return bestAudio != null ? new Streams(null, bestAudio) : null;
            }

            com.liskovsoft.mediaserviceinterfaces.data.MediaFormat bestVideo = best(videos);

            if (bestVideo != null && bestAudio != null) {
                return new Streams(bestVideo, bestAudio);
            }

            // Regular file with the sound inside, usually small
            List<com.liskovsoft.mediaserviceinterfaces.data.MediaFormat> regular = new ArrayList<>();

            if (info.getUrlFormats() != null) {
                for (com.liskovsoft.mediaserviceinterfaces.data.MediaFormat format : info.getUrlFormats()) {
                    if (format.getUrl() != null && format.getMimeType() != null && format.getMimeType().startsWith("video/mp4") && format.getHeight() <= maxHeight) {
                        regular.add(format);
                    }
                }
            }

            com.liskovsoft.mediaserviceinterfaces.data.MediaFormat file = best(regular);

            return file != null ? new Streams(file, null) : null;
        }

        private static com.liskovsoft.mediaserviceinterfaces.data.MediaFormat best(List<com.liskovsoft.mediaserviceinterfaces.data.MediaFormat> formats) {
            com.liskovsoft.mediaserviceinterfaces.data.MediaFormat best = null;

            for (com.liskovsoft.mediaserviceinterfaces.data.MediaFormat format : formats) {
                if (best == null
                        || format.getHeight() > best.getHeight()
                        || (format.getHeight() == best.getHeight() && number(format.getFps()) > number(best.getFps()))
                        || (format.getHeight() == best.getHeight() && number(format.getFps()) == number(best.getFps())
                        && number(format.getBitrate()) > number(best.getBitrate()))) {
                    best = format;
                }
            }

            return best;
        }

        static long length(com.liskovsoft.mediaserviceinterfaces.data.MediaFormat format) {
            return number(format.getClen());
        }

        private static long number(String value) {
            try {
                return value != null ? Long.parseLong(value.trim()) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
