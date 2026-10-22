package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;

/**
 * Touch cards for a stream of videos.<br/>
 * The data side is the upstream {@link VideoGroupObjectAdapter}, so paging, removals, sync of the watched state
 * and the cross-row updates behave exactly like in SmartTube. Only the drawing is new.
 */
public class VideoCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int STYLE_GRID = 0; // card fills its grid cell
    public static final int STYLE_ROW = 1; // fixed width card inside a horizontal row
    public static final int STYLE_CHANNELS = 2; // round avatars strip (channels section)

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_SHORTS = 2;
    private static final int TYPE_CHANNEL = 3;
    private static final int TYPE_CHANNEL_CHIP = 4;

    private static final int SCROLL_END_THRESHOLD = 6;

    public interface Listener {
        void onVideoClicked(Video video);
        void onVideoLongClicked(Video video);
        void onScrollEnd(Video lastVideo);
    }

    /**
     * Optional view on top of the list, spanning the whole width (channels strip, channel search bar...).
     */
    public interface Header {
        View onCreateView(ViewGroup parent);
        void onBind(View view);
    }

    private final VideoGroupObjectAdapter mData;
    private final int mStyle;
    private final Listener mListener;
    private Header mHeader;
    private int mCardWidthPx = ViewGroup.LayoutParams.MATCH_PARENT;
    private int mScrollEndRequestedSize = -1;
    private int mSelectedIndex = -1;
    private int mThumbQuality = -1;

    private final ObjectAdapter.DataObserver mObserver = new ObjectAdapter.DataObserver() {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            notifyItemRangeChanged(positionStart + offset(), itemCount);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            notifyItemRangeChanged(positionStart + offset(), itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            notifyItemRangeInserted(positionStart + offset(), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            notifyItemRangeRemoved(positionStart + offset(), itemCount);
        }

        @Override
        public void onItemMoved(int fromPosition, int toPosition) {
            notifyItemMoved(fromPosition + offset(), toPosition + offset());
        }
    };

    public VideoCardAdapter(int style, Listener listener) {
        this(null, style, listener);
    }

    public VideoCardAdapter(VideoGroup group, int style, Listener listener) {
        mData = new VideoGroupObjectAdapter(group, (Presenter) null);
        mStyle = style;
        mListener = listener;
        mData.registerObserver(mObserver);
    }

    public VideoGroupObjectAdapter getData() {
        return mData;
    }

    public void setHeader(Header header) {
        boolean hadHeader = mHeader != null;
        mHeader = header;

        if (hadHeader && header == null) {
            notifyItemRemoved(0);
        } else if (!hadHeader && header != null) {
            notifyItemInserted(0);
        } else if (header != null) {
            notifyItemChanged(0);
        }
    }

    public boolean hasHeader() {
        return mHeader != null;
    }

    /**
     * Width of the cards inside a horizontal row. Grid cards always fill their cell.
     */
    public void setCardWidth(int widthPx) {
        if (mCardWidthPx != widthPx) {
            mCardWidthPx = widthPx;
            notifyDataSetChanged();
        }
    }

    /**
     * Highlights the current item (e.g. the channel whose uploads are shown).
     */
    public void setSelectedIndex(int index) {
        int old = mSelectedIndex;
        mSelectedIndex = index;

        if (old != -1) {
            notifyItemChanged(old + offset());
        }

        if (index != -1) {
            notifyItemChanged(index + offset());
        }
    }

    /**
     * Adapter position of the video at the given data index.
     */
    public int toAdapterPosition(int dataIndex) {
        return dataIndex + offset();
    }

    public boolean isHeader(int adapterPosition) {
        return mHeader != null && adapterPosition == 0;
    }

    public boolean isEmpty() {
        return mData.isEmpty();
    }

    public void clear() {
        mScrollEndRequestedSize = -1;
        mSelectedIndex = -1;
        mData.clear();
    }

    /**
     * Unregisters the cross-row sync. Call when the adapter is thrown away.
     */
    public void release() {
        mData.unregisterObserver(mObserver);
    }

    private int offset() {
        return mHeader != null ? 1 : 0;
    }

    @Override
    public int getItemCount() {
        return mData.size() + offset();
    }

    @Override
    public int getItemViewType(int position) {
        if (isHeader(position)) {
            return TYPE_HEADER;
        }

        if (mStyle == STYLE_CHANNELS) {
            return TYPE_CHANNEL_CHIP;
        }

        Video video = (Video) mData.get(position - offset());

        if (video != null && video.isChannel()) {
            return TYPE_CHANNEL;
        }

        if (video != null && (video.isShorts || (video.getGroup() != null && video.getGroup().isShorts()))) {
            return TYPE_SHORTS;
        }

        return TYPE_VIDEO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(mHeader.onCreateView(parent));
            case TYPE_CHANNEL_CHIP:
                return new CardHolder(inflater.inflate(R.layout.kami_item_channel_chip, parent, false));
            case TYPE_CHANNEL:
                return new CardHolder(inflater.inflate(R.layout.kami_item_channel_card, parent, false));
            case TYPE_SHORTS:
                return new CardHolder(inflater.inflate(R.layout.kami_item_shorts_card, parent, false));
            default:
                return new CardHolder(inflater.inflate(R.layout.kami_item_video_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            if (mHeader != null) {
                mHeader.onBind(holder.itemView);
            }
            return;
        }

        int index = position - offset();
        Video video = (Video) mData.get(index);

        if (video == null) {
            return;
        }

        // Holders may come from a pool shared between rows: always pass this adapter's own params
        ((CardHolder) holder).bind(video, index == mSelectedIndex, mStyle, mCardWidthPx, mListener, getThumbQuality(holder.itemView.getContext()));

        checkScrollEnd(index);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof CardHolder) {
            ((CardHolder) holder).unbind();
        }
    }

    private void checkScrollEnd(int index) {
        int size = mData.size();

        if (mListener == null || size == 0 || index < size - SCROLL_END_THRESHOLD) {
            return;
        }

        // One request per page: the presenter appends the next page and the size grows
        if (mScrollEndRequestedSize == size) {
            return;
        }

        mScrollEndRequestedSize = size;
        mListener.onScrollEnd((Video) mData.get(size - 1));
    }

    private int getThumbQuality(Context context) {
        if (mThumbQuality == -1) {
            mThumbQuality = MainUIData.instance(context).getThumbQuality();
        }

        return mThumbQuality;
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(View itemView) {
            super(itemView);
        }
    }

    private static final class CardHolder extends RecyclerView.ViewHolder {
        private final ImageView mThumb;
        private final TextView mTitle;
        private final TextView mSubtitle;
        private final TextView mBadge;
        private final ProgressBar mProgress;
        private final View mMore;
        private Video mVideo;
        private Listener mListener;
        private int mThumbQuality;

        CardHolder(View itemView) {
            super(itemView);
            mThumb = itemView.findViewById(R.id.kami_thumb);
            mTitle = itemView.findViewById(R.id.kami_title);
            mSubtitle = itemView.findViewById(R.id.kami_subtitle);
            mBadge = itemView.findViewById(R.id.kami_badge);
            mProgress = itemView.findViewById(R.id.kami_progress);
            mMore = itemView.findViewById(R.id.kami_more);

            itemView.setOnClickListener(v -> {
                if (mVideo != null && mListener != null) {
                    mListener.onVideoClicked(mVideo);
                }
            });
            View.OnLongClickListener longClick = v -> {
                if (mVideo != null && mListener != null) {
                    mListener.onVideoLongClicked(mVideo);
                    return true;
                }
                return false;
            };
            itemView.setOnLongClickListener(longClick);

            if (mMore != null) {
                mMore.setOnClickListener(longClick::onLongClick);
            }
        }

        void bind(Video video, boolean selected, int style, int cardWidthPx, Listener listener, int thumbQuality) {
            mVideo = video;
            mListener = listener;
            mThumbQuality = thumbQuality;
            Context context = itemView.getContext();

            if (style == STYLE_ROW || style == STYLE_CHANNELS) {
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                int width = style == STYLE_CHANNELS ? ViewGroup.LayoutParams.WRAP_CONTENT : cardWidthPx;
                if (lp != null && lp.width != width) {
                    lp.width = width;
                    itemView.setLayoutParams(lp);
                }
            }

            itemView.setActivated(selected);

            setText(mTitle, video.getTitle());
            setText(mSubtitle, video.getSecondTitle());
            setText(mBadge, getBadge(context, video));

            if (mBadge != null) {
                boolean red = video.hasNewContent || video.isLive || video.isUpcoming;
                mBadge.setBackgroundResource(red ? R.drawable.kami_badge_red : R.drawable.kami_badge);
            }

            if (mProgress != null) {
                // Count progress that very close to zero. E.g. when user closed video immediately.
                int percent = video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched);
                mProgress.setVisibility(percent > 0 ? View.VISIBLE : View.GONE);
                mProgress.setProgress(percent);
            }

            loadImage(context, video);
        }

        void unbind() {
            mVideo = null;
            mListener = null;

            if (mThumb != null) {
                Glide.with(mThumb.getContext().getApplicationContext()).clear(mThumb);
                mThumb.setImageDrawable(null);
            }
        }

        private void loadImage(Context context, Video video) {
            if (mThumb == null) {
                return;
            }

            if (context instanceof Activity && ((Activity) context).isDestroyed()) {
                // Glide.with(context): IllegalArgumentException: You cannot start a load for a destroyed activity
                return;
            }

            boolean round = getItemViewType() == TYPE_CHANNEL || getItemViewType() == TYPE_CHANNEL_CHIP;
            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.kami_thumb_placeholder)
                    .error(R.drawable.kami_thumb_placeholder);

            if (round) {
                options = options.circleCrop();
            }

            String url = round ? video.cardImageUrl : ClickbaitRemover.updateThumbnail(video, mThumbQuality);

            Glide.with(context)
                    .load(url)
                    .apply(options)
                    .error(
                            // Updated thumbnail url not found
                            Glide.with(context)
                                    .load(video.cardImageUrl) // always working
                                    .apply(options)
                    )
                    .into(mThumb);
        }
    }

    private static String getBadge(Context context, Video video) {
        if (video.hasNewContent) {
            return context.getString(R.string.badge_new_content);
        }

        if (video.isLive) {
            return context.getString(R.string.badge_live);
        }

        return video.badge;
    }

    private static void setText(TextView view, CharSequence text) {
        if (view == null) {
            return;
        }

        view.setText(text);
        view.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }
}
