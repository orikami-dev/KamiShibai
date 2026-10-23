package com.liskovsoft.smartyoutubetv2.tv.mobile.signin;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.List;

/**
 * Google account sign in, made for a phone.<br/>
 * Same presenter and same device code flow as the TV screen: the app gets a code, the user confirms it
 * on the activation page and the presenter, which keeps polling, closes this screen once it's done.
 * The TV screen only offered a QR code and a custom tab, which on a phone opens the YouTube app
 * (it claims youtube.com links) instead of the activation page. Here the page opens in the browser itself,
 * with the code in the address and in the clipboard.
 */
public class MobileSignInActivity extends MobileActivity implements SignInView {
    private static final float CONTENT_MAX_WIDTH_DP = 560;

    private SignInPresenter mPresenter;
    private TextView mCode;
    private TextView mStatus;
    private ProgressBar mProgress;
    private Button mOpen;
    private Button mCopy;
    private TextView mSteps;
    private TextView mOther;
    private ImageView mQr;
    private String mUserCode;
    private String mPageUrl;
    private boolean mIsError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kami_activity_signin);

        mCode = findViewById(R.id.kami_signin_code);
        mStatus = findViewById(R.id.kami_signin_status);
        mProgress = findViewById(R.id.kami_signin_progress);
        mOpen = findViewById(R.id.kami_signin_open);
        mCopy = findViewById(R.id.kami_signin_copy);
        mSteps = findViewById(R.id.kami_signin_steps);
        mOther = findViewById(R.id.kami_signin_other);
        mQr = findViewById(R.id.kami_signin_qr);

        findViewById(R.id.kami_back).setOnClickListener(v -> finish());
        mOpen.setOnClickListener(v -> onOpenClicked());
        mCopy.setOnClickListener(v -> copyCode(true));
        setAddress(null);

        View content = findViewById(R.id.kami_signin_content);
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int maxWidth = WindowSize.dpToPx(this, CONTENT_MAX_WIDTH_DP);
            int width = r - l;
            int side = width > maxWidth ? (width - maxWidth) / 2 : WindowSize.dpToPx(this, 24);

            if (v.getPaddingLeft() != side) {
                v.post(() -> v.setPadding(side, v.getPaddingTop(), side, v.getPaddingBottom()));
            }
        });

        mPresenter = SignInPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Same as the TV screen
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    // region SignInView

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        runOnUiThread(() -> bindCode(userCode, signInUrl, fullSignInUrl));
    }

    @Override
    public void close() {
        runOnUiThread(this::finish);
    }

    // endregion

    private void bindCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        // The presenters report an error as a "code" without an address
        mIsError = TextUtils.isEmpty(signInUrl);

        if (mIsError) {
            mUserCode = null;
            mPageUrl = null;
            mCode.setText(null);
            mCode.setVisibility(View.GONE);
            mProgress.setVisibility(View.GONE);
            mStatus.setText(getString(R.string.kami_signin_error, userCode != null ? userCode : ""));
            mOpen.setText(R.string.kami_signin_retry);
            mOpen.setEnabled(true);
            mCopy.setEnabled(false);
            mQr.setVisibility(View.GONE);
            return;
        }

        mUserCode = userCode;
        mPageUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        mCode.setText(userCode);
        mCode.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.kami_signin_waiting);
        mOpen.setText(R.string.kami_signin_open);
        mOpen.setEnabled(true);
        mCopy.setEnabled(true);
        setAddress(signInUrl);

        mQr.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(Utils.toQrCodeLink(mPageUrl))
                .into(mQr);
    }

    private void setAddress(String signInUrl) {
        String address = !TextUtils.isEmpty(signInUrl) ? signInUrl.replaceFirst("^https?://", "") : "yt.be/activate";
        mSteps.setText(getString(R.string.kami_signin_steps, address));
        mOther.setText(getString(R.string.kami_signin_other, address));
    }

    private void onOpenClicked() {
        if (mIsError) {
            // Ask for a new code
            mIsError = false;
            mStatus.setText(R.string.kami_signin_loading);
            mProgress.setVisibility(View.VISIBLE);
            mOpen.setEnabled(false);
            mPresenter.setView(this);
            mPresenter.onViewInitialized();
            return;
        }

        if (mPageUrl == null) {
            return;
        }

        copyCode(false);
        openInBrowser(this, mPageUrl);
    }

    private void copyCode(boolean notify) {
        if (mUserCode == null) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.kami_signin_title), mUserCode));
        }

        // Android 13 and above show their own confirmation
        if (notify && Build.VERSION.SDK_INT < 33) {
            MessageHelpers.showMessage(this, R.string.kami_signin_copied);
        }
    }

    /**
     * Opens the page in the browser app itself. A plain or custom tab intent for a youtube.com address
     * is taken over by the YouTube app, which has no activation page.
     */
    private static void openInBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        String browser = findBrowser(context);

        if (browser != null) {
            intent.setPackage(browser);
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                intent.setPackage(null);
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e2) {
                MessageHelpers.showMessage(context, R.string.kami_signin_no_browser);
            }
        }
    }

    /**
     * The default browser, else the first one that isn't a YouTube app.
     */
    private static String findBrowser(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);

        ResolveInfo preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);

        if (isBrowser(context, preferred)) {
            return preferred.activityInfo.packageName;
        }

        List<ResolveInfo> all = pm.queryIntentActivities(probe, 0);

        for (ResolveInfo info : all) {
            if (isBrowser(context, info)) {
                return info.activityInfo.packageName;
            }
        }

        return null;
    }

    private static boolean isBrowser(Context context, ResolveInfo info) {
        if (info == null || info.activityInfo == null) {
            return false;
        }

        String pkg = info.activityInfo.packageName;

        // "android" is the app chooser, shown when there's no default browser
        return pkg != null && !"android".equals(pkg) && !pkg.equals(context.getPackageName()) && !pkg.contains("youtube");
    }
}
