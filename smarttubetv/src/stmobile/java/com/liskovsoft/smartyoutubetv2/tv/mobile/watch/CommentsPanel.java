package com.liskovsoft.smartyoutubetv2.tv.mobile.watch;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.KamiTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.disposables.Disposable;

/**
 * Comments panel of the watch page: top level comments, then the replies of one comment.<br/>
 * Loads pages with the same upstream service as the TV comments dialog.
 */
public class CommentsPanel {
    private static final int LOAD_MORE_THRESHOLD = 4;

    public interface Listener {
        void onPanelClosed();
    }

    /**
     * One list of comments: the top level one, or the replies of a comment.
     */
    private static final class Page {
        final String title;
        final List<CommentItem> items = new ArrayList<>();
        String nextKey;
        boolean loading;
        int scrollPosition;

        Page(String title, String firstKey) {
            this.title = title;
            this.nextKey = firstKey;
        }
    }

    private final View mRoot;
    private final ImageButton mBack;
    private final TextView mTitle;
    private final RecyclerView mList;
    private final View mProgress;
    private final TextView mEmpty;
    private final Adapter mAdapter = new Adapter();
    private final List<Page> mStack = new ArrayList<>();
    // Likes toggled here: the service doesn't send back updated items
    private final Map<String, Integer> mMyVotes = new HashMap<>();
    private final Listener mListener;
    private WatchController mController;
    private Disposable mLoadAction;
    private String mCommentsKey;

    public CommentsPanel(View root, Listener listener) {
        mRoot = root;
        mListener = listener;
        mBack = root.findViewById(R.id.kami_c_back);
        mTitle = root.findViewById(R.id.kami_c_title);
        mList = root.findViewById(R.id.kami_c_list);
        mProgress = root.findViewById(R.id.kami_c_progress);
        mEmpty = root.findViewById(R.id.kami_c_empty);

        mList.setLayoutManager(new LinearLayoutManager(root.getContext()));
        mList.setAdapter(mAdapter);
        mBack.setOnClickListener(v -> goBack());
        root.findViewById(R.id.kami_c_close).setOnClickListener(v -> hide());
    }

    public void setController(WatchController controller) {
        mController = controller;
    }

    /**
     * New video: forget the previous comments. Null key when comments are off.
     */
    public void reset(String commentsKey) {
        RxHelper.disposeActions(mLoadAction);
        mCommentsKey = commentsKey;
        mStack.clear();
        mMyVotes.clear();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * First page, loaded early to show the top comment on the comments card.
     */
    public void preload(Runnable onLoaded) {
        if (mCommentsKey == null || !mStack.isEmpty()) {
            return;
        }

        mStack.add(new Page(mRoot.getContext().getString(R.string.kami_comments), mCommentsKey));
        loadMore(onLoaded);
    }

    public CommentItem getTopComment() {
        if (mStack.isEmpty() || mStack.get(0).items.isEmpty()) {
            return null;
        }

        return mStack.get(0).items.get(0);
    }

    public boolean isShown() {
        return mRoot.getVisibility() == View.VISIBLE;
    }

    public void show(String emptyMessage) {
        mRoot.setVisibility(View.VISIBLE);
        mRoot.setTranslationY(mRoot.getHeight() > 0 ? mRoot.getHeight() / 3f : 200);
        mRoot.setAlpha(0f);
        mRoot.animate().translationY(0).alpha(1f).setDuration(200).start();

        if (emptyMessage != null) {
            mStack.clear();
            mAdapter.notifyDataSetChanged();
            showState(false, emptyMessage);
            mTitle.setText(R.string.kami_comments);
            mBack.setVisibility(View.GONE);
            return;
        }

        if (mStack.isEmpty()) {
            preload(null);
        }

        while (mStack.size() > 1) {
            mStack.remove(mStack.size() - 1);
        }

        bindPage();
    }

    public void hide() {
        if (!isShown()) {
            return;
        }

        mRoot.animate().translationY(mRoot.getHeight() / 3f).alpha(0f).setDuration(160)
                .withEndAction(() -> mRoot.setVisibility(View.GONE)).start();

        if (mListener != null) {
            mListener.onPanelClosed();
        }
    }

    /**
     * Back key: replies → comments → closed.
     */
    public boolean goBack() {
        if (!isShown()) {
            return false;
        }

        if (mStack.size() > 1) {
            mStack.remove(mStack.size() - 1);
            bindPage();
            mList.scrollToPosition(currentPage().scrollPosition);
        } else {
            hide();
        }

        return true;
    }

    public void release() {
        RxHelper.disposeActions(mLoadAction);
    }

    private Page currentPage() {
        return mStack.get(mStack.size() - 1);
    }

    private void bindPage() {
        Page page = currentPage();
        mTitle.setText(page.title);
        mBack.setVisibility(mStack.size() > 1 ? View.VISIBLE : View.GONE);
        mAdapter.notifyDataSetChanged();
        updateState();
    }

    private void updateState() {
        Page page = currentPage();
        boolean empty = page.items.isEmpty();
        showState(empty && page.loading, empty && !page.loading ? mRoot.getContext().getString(R.string.kami_no_comments) : null);
    }

    private void showState(boolean loading, String message) {
        mProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        mEmpty.setVisibility(message != null ? View.VISIBLE : View.GONE);
        mEmpty.setText(message);
    }

    private void openReplies(CommentItem item) {
        currentPage().scrollPosition = ((LinearLayoutManager) mList.getLayoutManager()).findFirstVisibleItemPosition();
        Page replies = new Page(mRoot.getContext().getString(R.string.kami_replies_title), item.getNestedCommentsKey());
        replies.items.add(item); // the replied comment stays on top
        mStack.add(replies);
        bindPage();
        mList.scrollToPosition(0);
        loadMore(null);
    }

    private void loadMore(Runnable onLoaded) {
        if (mController == null || mStack.isEmpty()) {
            return;
        }

        Page page = currentPage();

        if (page.loading || page.nextKey == null) {
            return;
        }

        page.loading = true;
        String key = page.nextKey;
        page.nextKey = null;

        if (isShown()) {
            updateState();
        }

        mLoadAction = RxHelper.execute(mController.loadComments(key), group -> onPageLoaded(page, group, onLoaded), error -> {
            page.loading = false;
            if (isShown() && mStack.contains(page)) {
                updateState();
            }
        });
    }

    private void onPageLoaded(Page page, CommentGroup group, Runnable onLoaded) {
        page.loading = false;

        if (group != null) {
            page.nextKey = group.getNextCommentsKey();

            if (group.getComments() != null) {
                for (CommentItem item : group.getComments()) {
                    if (item != null && !item.isEmpty()) {
                        page.items.add(item);
                    }
                }
            }
        }

        if (!mStack.isEmpty() && currentPage() == page) {
            mAdapter.notifyDataSetChanged();

            if (isShown()) {
                updateState();
            }
        }

        if (onLoaded != null) {
            onLoaded.run();
        }
    }

    private void vote(CommentItem item, int vote) {
        if (mController == null || item.getNestedCommentsKey() == null) {
            return;
        }

        Integer current = mMyVotes.get(item.getId());
        boolean undo = current != null && current == vote;
        mMyVotes.put(item.getId(), undo ? 0 : vote);
        mAdapter.notifyDataSetChanged();

        // Same keys as the upstream comments dialog
        RxHelper.execute(vote > 0 ? mController.toggleCommentLike(item.getNestedCommentsKey()) : mController.toggleCommentDislike(item.getNestedCommentsKey()),
                error -> MessageHelpers.showMessage(mRoot.getContext(), error.getMessage()));
    }

    private int getVote(CommentItem item) {
        Integer vote = mMyVotes.get(item.getId());

        if (vote != null) {
            return vote;
        }

        return item.isLiked() ? 1 : 0;
    }

    private String getLikeCount(CommentItem item) {
        String count = item.getLikeCount();
        int base = item.isLiked() ? 1 : 0;
        int delta = (getVote(item) == 1 ? 1 : 0) - base;

        if (delta != 0 && Helpers.isInteger(count != null ? count : "0")) {
            int value = Helpers.parseInt(count != null ? count : "0") + delta;
            return value > 0 ? String.valueOf(value) : null;
        }

        return count;
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mStack.isEmpty() ? 0 : currentPage().items.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_item_comment, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Page page = currentPage();
            CommentItem item = page.items.get(position);
            boolean isRepliedComment = mStack.size() > 1 && position == 0;
            holder.bind(item, isRepliedComment);

            if (position >= page.items.size() - LOAD_MORE_THRESHOLD) {
                mList.post(() -> loadMore(null));
            }
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        private final ImageView mAvatar;
        private final TextView mAuthor;
        private final TextView mText;
        private final ImageButton mLike;
        private final TextView mLikeCount;
        private final ImageButton mDislike;
        private final TextView mReplies;

        Holder(View itemView) {
            super(itemView);
            mAvatar = itemView.findViewById(R.id.kami_cm_avatar);
            mAuthor = itemView.findViewById(R.id.kami_cm_author);
            mText = itemView.findViewById(R.id.kami_cm_text);
            mLike = itemView.findViewById(R.id.kami_cm_like);
            mLikeCount = itemView.findViewById(R.id.kami_cm_like_count);
            mDislike = itemView.findViewById(R.id.kami_cm_dislike);
            mReplies = itemView.findViewById(R.id.kami_cm_replies);
        }

        void bind(CommentItem item, boolean isRepliedComment) {
            Context context = itemView.getContext();
            String author = item.getAuthorName() != null ? item.getAuthorName() : "";
            String date = item.getPublishedDate();
            mAuthor.setText(date != null && !date.isEmpty() ? author + " · " + date : author);
            mText.setText(item.getMessage());
            loadAvatar(mAvatar, item.getAuthorPhoto());

            int vote = getVote(item);
            int accent = KamiTheme.color(context, R.attr.kamiAccent);
            int secondary = KamiTheme.color(context, R.attr.kamiTextSecondary);
            mLike.setColorFilter(vote == 1 ? accent : secondary);
            mDislike.setColorFilter(vote == -1 ? accent : secondary);
            mLikeCount.setText(getLikeCount(item));
            mLike.setOnClickListener(v -> vote(item, 1));
            mDislike.setOnClickListener(v -> vote(item, -1));

            String replyCount = item.getReplyCount();
            boolean hasReplies = !isRepliedComment && item.getNestedCommentsKey() != null && replyCount != null && !replyCount.isEmpty() && !"0".equals(replyCount);
            mReplies.setVisibility(hasReplies ? View.VISIBLE : View.GONE);

            if (hasReplies) {
                mReplies.setText("1".equals(replyCount) ? context.getString(R.string.kami_reply_one) : context.getString(R.string.kami_replies, replyCount));
                mReplies.setOnClickListener(v -> openReplies(item));
            }

            itemView.setBackgroundColor(isRepliedComment ? KamiTheme.color(context, R.attr.kamiSurfaceHigh) : 0);
        }
    }

    static void loadAvatar(ImageView view, String url) {
        if (url == null || url.isEmpty()) {
            Glide.with(view.getContext()).clear(view);
            view.setImageDrawable(null);
            return;
        }

        Glide.with(view.getContext()).load(url).into(view);
    }
}
