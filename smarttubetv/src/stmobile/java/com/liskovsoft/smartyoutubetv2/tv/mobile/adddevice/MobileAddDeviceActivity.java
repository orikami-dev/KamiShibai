package com.liskovsoft.smartyoutubetv2.tv.mobile.adddevice;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;

/**
 * Settings > Remote control > Add a device, made for a phone: the pairing code to type in the YouTube app
 * of the other device. Same presenter as the TV screen.
 */
public class MobileAddDeviceActivity extends MobileActivity implements AddDeviceView {
    private AddDevicePresenter mPresenter;
    private TextView mCode;
    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kami_activity_add_device);

        mCode = findViewById(R.id.kami_adddevice_code);
        mProgress = findViewById(R.id.kami_adddevice_progress);
        findViewById(R.id.kami_back).setOnClickListener(v -> finish());
        findViewById(R.id.kami_adddevice_done).setOnClickListener(v -> mPresenter.onActionClicked());

        mPresenter = AddDevicePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void showCode(String userCode) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            mCode.setText(userCode);
            mProgress.setVisibility(View.GONE);
        });
    }

    @Override
    public void close() {
        runOnUiThread(this::finish);
    }
}
