package com.liskovsoft.smartyoutubetv2.tv.mobile.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Several groups of videos on one vertical page.<br/>
 * {@link #MODE_ROWS}: each group is a titled horizontal row that you swipe (Home, Music, a channel page...).<br/>
 * {@link #MODE_BLOCKS}: each group is a titled block of the grid (search results), so the main list scrolls vertically.
 */
public class FeedSection implements MobileSection {
    public static final int MODE_ROWS = 0;
    public static final int MODE_BLOCKS = 1;

    private static final float MIN_VIDEO_CARD_DP = 280;
    private static final float EDGE_TO_EDGE_MAX_DP = 600;
    private static final float MIN_SHORTS_CARD_DP = 160;
    private static final float ROW_CARD_DP = 280;
    private static final float ROW_SHORTS_CARD_DP = 150;
    private static final int MAX_COLUMNS = 6;

    private static final int TYPE_ROW = 0;
    private static final int TYPE_TITLE = 1;
    private static final int TYPE_CARD = 2;
    private static final int TYPE_HEADER = 3;

    private final Context mContext;
    private final VideoGroupPresenter mPresenter;
    private final int mMode;
    private final List<Block> mBlocks = new ArrayList<>();
    private final FeedAdapter mAdapter = new FeedAdapter();
    private final RecyclerView.RecycledViewPool mCardPool = new RecyclerView.RecycledViewPool();
    private final GridLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private VideoCardAdapter.Header mHeader;
    private int mColumns = 1;
    private int mShortsColumns = 2;
    private int mRowCardWidthPx;
    private int mRowShortsWidthPx;
    private int mPendingPosition = -1;
    private boolean mEdgeToEdge;
    // Flattened positions for MODE_BLOCKS: block index and item index (-1 = title)
    private final List<int[]> mCells = new ArrayList<>();

    private static final class Block {
        final int id;
        String title;
        final VideoCardAdapter adapter;
        final boolean isShorts;

        Block(int id, String title, VideoCardAdapter adapter, boolean isShorts) {
            this.id = id;
            this.title = title;
            this.adapter = adapter;
            this.isShorts = isShorts;
        }
    }

    public FeedSection(Context context, VideoGroupPresenter presenter, int mode) {
        mContext = context;
        mPresenter = presenter;
        mMode = mode;
        mLayoutManager = new GridLayoutManager(context, 1);
        mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getCellSpan(position);
            }
        });
        mRowCardWidthPx = WindowSize.dpToPx(context, ROW_CARD_DP);
        mRowShortsWidthPx = WindowSize.dpToPx(context, ROW_SHORTS_CARD_DP);
    }

    /**
     * A full width view on top of the page (e.g. the channel bar).
     */
    public void setHeader(VideoCardAdapter.Header header) {
        mHeader = header;
        rebuild();
    }

    public void refreshHeader() {
        if (mHeader != null) {
            mAdapter.notifyItemChanged(0);
        }
    }

    @Override
    public void attach(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void detach() {
        for (Block block : mBlocks) {
            block.adapter.release();
        }
        mRecyclerView = null;
    }

    @Override
    public void onWidthChanged(float widthDp) {
        int columns = WindowSize.columns(widthDp, MIN_VIDEO_CARD_DP, MAX_COLUMNS);
        int shortsColumns = WindowSize.columns(widthDp, MIN_SHORTS_CARD_DP, MAX_COLUMNS * 2);
        // Leave a peek of the next card in the rows so it's obvious they scroll sideways
        float rowCardDp = Math.min(ROW_CARD_DP * (widthDp < 600 ? 1.15f : 1f), widthDp * 0.8f);
        int rowCardPx = WindowSize.dpToPx(mContext, rowCardDp);
        int rowShortsPx = WindowSize.dpToPx(mContext, Math.min(ROW_SHORTS_CARD_DP, widthDp * 0.42f));

        // Lowest common multiple keeps both card kinds aligned in MODE_BLOCKS
        int span = mMode == MODE_BLOCKS ? lcm(columns, shortsColumns) : 1;
        boolean changed = columns != mColumns || shortsColumns != mShortsColumns || mLayoutManager.getSpanCount() != span;

        mColumns = columns;
        mShortsColumns = shortsColumns;

        if (mLayoutManager.getSpanCount() != span) {
            mLayoutManager.setSpanCount(span);
        }

        if (rowCardPx != mRowCardWidthPx || rowShortsPx != mRowShortsWidthPx) {
            mRowCardWidthPx = rowCardPx;
            mRowShortsWidthPx = rowShortsPx;
            for (Block block : mBlocks) {
                block.adapter.setCardWidth(block.isShorts ? mRowShortsWidthPx : mRowCardWidthPx);
            }
        }

        // Folded phone: one big card per line, from edge to edge
        boolean edgeToEdge = mMode == MODE_BLOCKS && columns == 1 && widthDp < EDGE_TO_EDGE_MAX_DP;

        if (edgeToEdge != mEdgeToEdge) {
            mEdgeToEdge = edgeToEdge;
            for (Block block : mBlocks) {
                block.adapter.setEdgeToEdge(edgeToEdge);
            }
            changed = true;
        }

        VideoCardAdapter.applyListPadding(mRecyclerView, edgeToEdge);

        if (changed) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void update(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
            } else {
                removeBlock(group.getId());
            }
        } else if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_REMOVE_AUTHOR || action == VideoGroup.ACTION_SYNC) {
            Block block = findBlock(group.getId());
            if (block != null) {
                GridSection.applyAction(block.adapter, group);
            } else if (action != VideoGroup.ACTION_REMOVE_AUTHOR) {
                // Upstream adapters look for the group in other rows too
                for (Block other : mBlocks) {
                    GridSection.applyAction(other.adapter, group);
                }
            }
            rebuildIfBlocks();
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        Block block = findBlock(group.getId());

        if (block == null) {
            block = createBlock(group);
            int position = group.getPosition();
            if (position < 0 || position > mBlocks.size()) {
                mBlocks.add(block);
            } else {
                mBlocks.add(position, block);
            }
            rebuild();
        } else {
            block.adapter.getData().add(group); // continue the row
            rebuildIfBlocks();
        }

        restorePosition();
    }

    private Block createBlock(VideoGroup group) {
        BlockListener listener = new BlockListener();
        VideoCardAdapter adapter = new VideoCardAdapter(group, mMode == MODE_ROWS ? VideoCardAdapter.STYLE_ROW : VideoCardAdapter.STYLE_GRID, listener);
        Block block = new Block(group.getId(), group.getTitle(), adapter, group.isShorts());
        listener.mBlock = block;
        adapter.setCardWidth(block.isShorts ? mRowShortsWidthPx : mRowCardWidthPx);
        adapter.setEdgeToEdge(mEdgeToEdge);
        return block;
    }

    private Block findBlock(int id) {
        for (Block block : mBlocks) {
            if (block.id == id) {
                return block;
            }
        }

        return null;
    }

    private void removeBlock(int id) {
        Block block = findBlock(id);

        if (block != null) {
            block.adapter.release();
            mBlocks.remove(block);
            rebuild();
        }
    }

    @Override
    public void clear() {
        for (Block block : mBlocks) {
            block.adapter.release();
        }
        mBlocks.clear();
        rebuild();
    }

    @Override
    public boolean isEmpty() {
        for (Block block : mBlocks) {
            if (!block.adapter.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setPosition(int index) {
        mPendingPosition = index;
        restorePosition();
    }

    @Override
    public void selectItem(Video item) {
        // Rows keep their own scroll. Nothing to restore here.
    }

    private void restorePosition() {
        if (mPendingPosition < 0 || mPendingPosition >= mBlocks.size() || mMode != MODE_ROWS) {
            return;
        }

        mLayoutManager.scrollToPositionWithOffset(mPendingPosition + (mHeader != null ? 1 : 0), 0);
        mPendingPosition = -1;
    }

    private void rebuildIfBlocks() {
        if (mMode == MODE_BLOCKS) {
            rebuild();
        }
    }

    private void rebuild() {
        mCells.clear();

        if (mMode == MODE_BLOCKS) {
            boolean singleBlock = mBlocks.size() == 1;
            for (int i = 0; i < mBlocks.size(); i++) {
                Block block = mBlocks.get(i);
                int size = block.adapter.getData().size();
                if (size == 0) {
                    continue;
                }
                if (!singleBlock && !TextUtils.isEmpty(block.title)) {
                    mCells.add(new int[] {i, -1});
                }
                for (int j = 0; j < size; j++) {
                    mCells.add(new int[] {i, j});
                }
            }
        }

        mAdapter.notifyDataSetChanged();
    }

    private int headerCount() {
        return mHeader != null ? 1 : 0;
    }

    private int getCellSpan(int position) {
        int span = mLayoutManager.getSpanCount();

        if (mMode == MODE_ROWS || position < headerCount()) {
            return span;
        }

        int[] cell = mCells.get(position - headerCount());

        if (cell[1] == -1) {
            return span;
        }

        Block block = mBlocks.get(cell[0]);
        int columns = block.isShorts ? mShortsColumns : mColumns;
        return Math.max(1, span / columns);
    }

    private static int lcm(int a, int b) {
        int x = a, y = b;
        while (y != 0) {
            int t = y;
            y = x % y;
            x = t;
        }
        return a / x * b;
    }

    private final class BlockListener extends PresenterListener {
        Block mBlock;

        BlockListener() {
            super(mPresenter);
        }

        @Override
        public void onScrollEnd(Video lastVideo) {
            if (mMode == MODE_ROWS || isLastBlock()) {
                super.onScrollEnd(lastVideo);
            }
        }

        private boolean isLastBlock() {
            return !mBlocks.isEmpty() && mBlocks.get(mBlocks.size() - 1) == mBlock;
        }
    }

    private static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(View itemView) {
            super(itemView);
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView list;

        RowHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.kami_row_title);
            list = itemView.findViewById(R.id.kami_row_list);
        }
    }

    private final class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return headerCount() + (mMode == MODE_ROWS ? mBlocks.size() : mCells.size());
        }

        @Override
        public int getItemViewType(int position) {
            if (position < headerCount()) {
                return TYPE_HEADER;
            }

            if (mMode == MODE_ROWS) {
                return TYPE_ROW;
            }

            int[] cell = mCells.get(position - headerCount());
            return cell[1] == -1 ? TYPE_TITLE : TYPE_CARD + cardType(cell);
        }

        /**
         * Card view types are delegated to the block adapter and shifted to avoid collisions.
         */
        private int cardType(int[] cell) {
            VideoCardAdapter adapter = mBlocks.get(cell[0]).adapter;
            return 10 + adapter.getItemViewType(adapter.toAdapterPosition(cell[1]));
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (viewType == TYPE_HEADER) {
                return new SimpleHolder(mHeader.onCreateView(parent));
            }

            if (viewType == TYPE_ROW) {
                RowHolder holder = new RowHolder(inflater.inflate(R.layout.kami_item_row, parent, false));
                LinearLayoutManager lm = new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false);
                lm.setInitialPrefetchItemCount(4);
                holder.list.setLayoutManager(lm);
                holder.list.setRecycledViewPool(mCardPool);
                return holder;
            }

            if (viewType == TYPE_TITLE) {
                return new SimpleHolder(inflater.inflate(R.layout.kami_item_block_title, parent, false));
            }

            // Any block adapter can create the card, they share the layouts
            VideoCardAdapter anyAdapter = findAdapterForType(viewType - TYPE_CARD - 10);
            return anyAdapter.onCreateViewHolder(parent, viewType - TYPE_CARD - 10);
        }

        private VideoCardAdapter findAdapterForType(int cardType) {
            for (int[] cell : mCells) {
                if (cell[1] != -1 && cardType(cell) - 10 == cardType) {
                    return mBlocks.get(cell[0]).adapter;
                }
            }

            return mBlocks.get(0).adapter;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < headerCount()) {
                mHeader.onBind(holder.itemView);
                return;
            }

            if (holder instanceof RowHolder) {
                Block block = mBlocks.get(position - headerCount());
                RowHolder row = (RowHolder) holder;
                row.title.setText(block.title);
                row.title.setVisibility(TextUtils.isEmpty(block.title) ? View.GONE : View.VISIBLE);
                if (row.list.getAdapter() != block.adapter) {
                    row.list.swapAdapter(block.adapter, false);
                }
                return;
            }

            int[] cell = mCells.get(position - headerCount());
            Block block = mBlocks.get(cell[0]);

            if (cell[1] == -1) {
                ((TextView) holder.itemView.findViewById(R.id.kami_row_title)).setText(block.title);
                return;
            }

            block.adapter.onBindViewHolder(holder, block.adapter.toAdapterPosition(cell[1]));
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (!(holder instanceof RowHolder) && !(holder instanceof SimpleHolder) && !mBlocks.isEmpty()) {
                mBlocks.get(0).adapter.onViewRecycled(holder);
            }
        }
    }
}
