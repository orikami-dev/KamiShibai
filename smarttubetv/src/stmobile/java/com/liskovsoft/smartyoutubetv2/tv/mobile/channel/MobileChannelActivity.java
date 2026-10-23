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
        // A channel slides in over the screen that opened it
        overridePendingTransition(R.anim.kami_slide_in_right, R.anim.kami_stay);
        mFragment = (MobileChannelFragment) getSupportFragmentManager().findFragmentById(R.id.kami_channel_fragment);
    }

    @Override
    public void finishReally() {
        super.finishReally();
        overridePendingTransition(R.anim.kami_stay, R.anim.kami_slide_out_right);

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
