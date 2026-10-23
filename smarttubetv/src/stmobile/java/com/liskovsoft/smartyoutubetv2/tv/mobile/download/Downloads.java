package com.liskovsoft.smartyoutubetv2.tv.mobile.download;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Download preferences and the list of what was downloaded.
 */
public final class Downloads {
    public static final int ASK = -2;
    public static final int AUDIO = -1;
    public static final int BEST = 0;
    /** Maximum video heights, other values are heights in pixels */
    public static final int[] CHOICES = {ASK, 360, 480, 720, 1080, BEST, AUDIO};

    private static final String PREFS = "kamishibai_downloads";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_HISTORY = "history";
    private static final int HISTORY_SIZE = 50;

    public static final class Entry {
        public final String title;
        public final String uri;
        public final boolean audio;

        Entry(String title, String uri, boolean audio) {
            this.title = title;
            this.uri = uri;
            this.audio = audio;
        }
    }

    private Downloads() {
    }

    public static int getQuality(Context context) {
        return prefs(context).getInt(KEY_QUALITY, ASK);
    }

    public static void setQuality(Context context, int quality) {
        prefs(context).edit().putInt(KEY_QUALITY, quality).apply();
    }

    public static synchronized void addToHistory(Context context, String title, String uri, boolean audio) {
        try {
            JSONArray old = new JSONArray(prefs(context).getString(KEY_HISTORY, "[]"));
            JSONArray updated = new JSONArray();
            updated.put(new JSONObject().put("title", title).put("uri", uri).put("audio", audio));

            for (int i = 0; i < old.length() && updated.length() < HISTORY_SIZE; i++) {
                updated.put(old.get(i));
            }

            prefs(context).edit().putString(KEY_HISTORY, updated.toString()).apply();
        } catch (Exception e) {
            // the file is saved anyway, only the list misses it
        }
    }

    public static synchronized List<Entry> getHistory(Context context) {
        List<Entry> result = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_HISTORY, "[]"));

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Entry(item.getString("title"), item.getString("uri"), item.optBoolean("audio")));
            }
        } catch (Exception e) {
            // empty list
        }

        return result;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
