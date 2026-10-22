package com.liskovsoft.smartyoutubetv2.tv.mobile.channel;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;

public class MobileChannelActivity extends MobileActivity {
    private MobileChannelFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kami_activity_channel);
        mFragment = (MobileChannelFragment) getSupportFragmentManager().findFragmentById(R.id.kami_channel_fragment);
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
