package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.MobileActivity;

public class MobileBrowseActivity extends MobileActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.kami_activity_browse);
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.kami_browse_fragment);

        // Back from another tab, a filter or "Explore" returns to Home; back from Home follows the upstream exit rule
        if (fragment instanceof MobileBrowseFragment && ((MobileBrowseFragment) fragment).goBack()) {
            return;
        }

        super.onBackPressed();
    }
}
