package com.liskovsoft.smartyoutubetv2.tv.mobile.logs;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * "Logs to send": the filtered report as it will leave the phone, a field to describe the problem,
 * then e-mail (report attached) or copy.
 */
public class KamiLogsActivity extends MobileActivity {
    private static final String MAILBOX = "orikami.logs@gmail.com";
    private static final String FILE_NAME = "kamishibai-journaux.txt";
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private TextView mReport;
    private EditText mDescription;
    private ProgressBar mProgress;
    private Button mSend;
    private Button mCopy;
    private String mText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kami_activity_logs);

        mReport = findViewById(R.id.kami_logs_report);
        mDescription = findViewById(R.id.kami_logs_description);
        mProgress = findViewById(R.id.kami_logs_progress);
        mSend = findViewById(R.id.kami_logs_send);
        mCopy = findViewById(R.id.kami_logs_copy);

        findViewById(R.id.kami_back).setOnClickListener(v -> finish());
        findViewById(R.id.kami_logs_cancel).setOnClickListener(v -> finish());
        mSend.setOnClickListener(v -> send());
        mCopy.setOnClickListener(v -> copy());
        mSend.setEnabled(false);
        mCopy.setEnabled(false);

        Context app = getApplicationContext();
        String player = KamiLogs.describePlayer(this);
        String title = KamiLogs.currentTitle(this);
        String channel = KamiLogs.currentChannel(this);

        new Thread(() -> {
            String text = KamiLogs.build(app, player, title, channel);
            mMain.post(() -> show(text));
        }).start();
    }

    private void show(String text) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        mText = text;
        mReport.setText(text);
        mProgress.setVisibility(View.GONE);
        mSend.setEnabled(true);
        mCopy.setEnabled(true);
    }

    private String fullText() {
        String description = mDescription.getText().toString().trim();
        return (TextUtils.isEmpty(description) ? "" : getString(R.string.kami_logs_description) + " :\n" + description + "\n\n") + mText;
    }

    private void send() {
        File file = new File(getCacheDir(), FILE_NAME);

        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write(fullText());
        } catch (Exception e) {
            MessageHelpers.showMessage(this, R.string.kami_logs_failed);
            return;
        }

        Uri uri = FileHelpers.getFileUri(this, file.getAbsolutePath());
        String description = mDescription.getText().toString().trim();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {MAILBOX});
        intent.putExtra(Intent.EXTRA_SUBJECT, "[" + getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + "] Journaux");
        intent.putExtra(Intent.EXTRA_TEXT, TextUtils.isEmpty(description) ? getString(R.string.kami_logs_mail_body) : description);

        if (uri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri(FILE_NAME, uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.kami_logs_send)));
        } catch (ActivityNotFoundException e) {
            MessageHelpers.showMessage(this, R.string.kami_logs_no_mail);
        }
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.kami_logs_title), fullText()));
        }

        // Android 13 and above show their own confirmation
        if (Build.VERSION.SDK_INT < 33) {
            MessageHelpers.showMessage(this, R.string.kami_logs_copied);
        }
    }
}
