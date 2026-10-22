package com.liskovsoft.smartyoutubetv2.tv.mobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Keeps a fixed width/height ratio (16:9 thumbnails, 9:16 shorts), whatever the card width is.
 */
public class AspectFrameLayout extends FrameLayout {
    private float mRatio = 16f / 9f;

    public AspectFrameLayout(Context context) {
        this(context, null);
    }

    public AspectFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AspectFrameLayout);
            mRatio = a.getFloat(R.styleable.AspectFrameLayout_kamiRatio, mRatio);
            a.recycle();
        }
    }

    public void setRatio(float ratio) {
        if (ratio > 0 && ratio != mRatio) {
            mRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width / mRatio);

        super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    }
}
