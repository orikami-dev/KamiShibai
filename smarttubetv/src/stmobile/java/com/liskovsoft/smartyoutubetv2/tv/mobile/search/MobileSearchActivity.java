package com.liskovsoft.smartyoutubetv2.tv.mobile.search;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;

public class MobileSearchActivity extends MobileActivity {
    private MobileSearchFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kami_activity_search);
        mFragment = (MobileSearchFragment) getSupportFragmentManager().findFragmentById(R.id.kami_search_fragment);
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
