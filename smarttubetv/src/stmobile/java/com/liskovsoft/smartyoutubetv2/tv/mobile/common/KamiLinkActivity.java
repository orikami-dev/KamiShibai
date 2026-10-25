package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Stands for the TV web page screen ({@link WebBrowserView}): the link opens in a browser tab of the phone
 * and this invisible screen closes at once.<br/>
 * On a TV, links are shown as a QR code page to scan with a phone. Here the phone is the device: the address inside is opened.
 */
public class KamiLinkActivity extends Activity implements WebBrowserView {
    private static final String QR_PAGE = "api.qrserver.com";
    private WebBrowserPresenter mPresenter;
    private boolean mOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = WebBrowserPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        if (!mOpened) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void loadUrl(String url) {
        if (mOpened || TextUtils.isEmpty(url)) {
            return;
        }

        mOpened = true;
        Utils.openLinkExt(this, unwrap(url));
        finish();
        overridePendingTransition(0, 0);
    }

    static String unwrap(String url) {
        Uri uri = Uri.parse(url);

        if (QR_PAGE.equals(uri.getHost())) {
            String data = uri.getQueryParameter("data");

            if (data != null && (data.startsWith("http://") || data.startsWith("https://"))) {
                return data;
            }
        }

        return url;
    }
}
