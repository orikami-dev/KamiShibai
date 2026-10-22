package com.liskovsoft.smartyoutubetv2.tv.mobile.common;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.MobileSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * A page with a back button, a title and one list (channel, channel uploads...).
 */
public abstract class TopBarFragment extends Fragment {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private View mRoot;
    private TextView mTitle;
    private ProgressBar mProgress;
    private boolean mIsProgressShowing;
    private WindowSize mWindowSize;
    protected RecyclerView mList;

    protected abstract MobileSection getSection();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRoot = inflater.inflate(R.layout.kami_fragment_page, container, false);
        mTitle = mRoot.findViewById(R.id.kami_title);
        mProgress = mRoot.findViewById(R.id.kami_progress);
        mList = mRoot.findViewById(R.id.kami_list);

        mRoot.findViewById(R.id.kami_back).setOnClickListener(v -> {
            if (getActivity() instanceof LeanbackActivity) {
                getActivity().onBackPressed();
            }
        });

        getSection().attach(mList);

        mRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                mHandler.post(this::applyWindowSize);
            }
        });

        return mRoot;
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacksAndMessages(null);
        getSection().detach();
        super.onDestroyView();
    }

    protected void setTitle(CharSequence title) {
        if (mTitle != null && title != null) {
            mTitle.setText(title);
        }
    }

    protected View getTopBarAction() {
        return mRoot != null ? mRoot.findViewById(R.id.kami_action) : null;
    }

    private void applyWindowSize() {
        if (mRoot == null || mRoot.getWidth() == 0) {
            return;
        }

        WindowSize size = WindowSize.of(mRoot);

        if (!size.sameAs(mWindowSize)) {
            mWindowSize = size;
            getSection().onWidthChanged(size.widthDp);
        }
    }

    public void showProgressBar(boolean show) {
        mIsProgressShowing = show;
        mHandler.post(() -> {
            if (mProgress != null) {
                mProgress.setVisibility(mIsProgressShowing ? View.VISIBLE : View.GONE);
            }
        });
    }
}
