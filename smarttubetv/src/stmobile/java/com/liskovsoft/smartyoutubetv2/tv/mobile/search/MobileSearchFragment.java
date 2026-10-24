package com.liskovsoft.smartyoutubetv2.tv.mobile.search;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.adapter.FeedSection;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch search: a real text field with the phone keyboard, live suggestions, voice input,
 * and results as a vertical feed. Same {@link SearchView} contract and {@link SearchPresenter} as upstream.<br/>
 * Lives in two places: on its own page (a search asked from the player or a channel) and inside Home,
 * where the field opens in the top bar and the results replace the feed. In that case the host gives the bar
 * with {@link #setBar} before adding the fragment, and implements {@link Host} to be told when the search closes.
 */
public class MobileSearchFragment extends Fragment implements SearchView {
    private static final int VOICE_REQUEST_CODE = 0x5EA;

    /**
     * The screen that shows the search inside itself.
     */
    public interface Host {
        void onSearchClosed();
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<Tag> mTags = new ArrayList<>();
    private SearchPresenter mSearchPresenter;
    private CrashRestorer mCrashRestorer;
    private boolean mIsFragmentCreated;
    private MediaServiceSearchTagProvider mTagsProvider;
    private FeedSection mResults;
    private String mSearchQuery;
    private boolean mIsProgressShowing;
    private boolean mIgnoreTextChange;

    private View mRoot;
    // Field and buttons given by the host (search inside Home), null on the standalone page
    private View mBar;
    private boolean mEmbedded;
    private EditText mField;
    private View mClear;
    private RecyclerView mSuggestions;
    private RecyclerView mList;
    private ProgressBar mProgress;
    private TagsAdapter mTagsAdapter;
    private WindowSize mWindowSize;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        if (getContext() == null) {
            throw new IllegalStateException("Can't create MobileSearchFragment: the context is null");
        }

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRoot = inflater.inflate(R.layout.kami_fragment_search, container, false);

        mEmbedded = mBar != null;
        View bar = mEmbedded ? mBar : mRoot.findViewById(R.id.kami_search_bar);

        if (mEmbedded) {
            // The host shows its own copy of the bar, in its top bar
            mRoot.findViewById(R.id.kami_search_bar).setVisibility(View.GONE);
        }

        mField = bar.findViewById(R.id.kami_search_field);
        mClear = bar.findViewById(R.id.kami_search_clear);
        mSuggestions = mRoot.findViewById(R.id.kami_suggestions);
        mList = mRoot.findViewById(R.id.kami_list);
        mProgress = mRoot.findViewById(R.id.kami_progress);

        bar.findViewById(R.id.kami_back).setOnClickListener(v -> finishReally());
        bar.findViewById(R.id.kami_search_voice).setOnClickListener(v -> startVoiceRecognition());
        bar.findViewById(R.id.kami_search_filters).setOnClickListener(v -> mSearchPresenter.onSearchSettingsClicked());
        mClear.setOnClickListener(v -> {
            mField.setText(null);
            showKeyboard();
        });

        mField.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) {
                submit(mField.getText().toString());
                return true;
            }
            return false;
        });
        mField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                if (!mIgnoreTextChange) {
                    loadSearchTags(s.toString());
                    showSuggestions(true);
                }
            }
        });
        mField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showSuggestions(true);
            }
        });

        mTagsAdapter = new TagsAdapter();
        mSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        mSuggestions.setAdapter(mTagsAdapter);

        mResults = new FeedSection(getContext(), mSearchPresenter, FeedSection.MODE_BLOCKS);
        mResults.attach(mList);
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });

        mRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                mHandler.post(this::applyWindowSize);
            }
        });

        return mRoot;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchPresenter.onViewInitialized();

        // Restore state after crash (inside Home, the Home fragment does it)
        if (!mEmbedded) {
            mCrashRestorer.restorePlayback();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        if (!mEmbedded) {
            mCrashRestorer.persistVideo(outState, mSearchPresenter.getCurrentVideo());
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Back from the player: this search is the one on screen again
        mSearchPresenter.setView(this);

        if (!mIsFragmentCreated) {
            mSearchPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    /**
     * Search inside another screen: the field and its buttons are the host's, the results come here.
     * To call before the fragment is added.
     */
    public void setBar(View bar) {
        mBar = bar;
    }

    public void hideKeyboard() {
        if (mField == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) mField.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mField.getWindowToken(), 0);
        }
        mField.clearFocus();
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacksAndMessages(null);

        if (mResults != null) {
            mResults.detach();
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSearchPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mSearchPresenter.onFinish();
    }

    private void applyWindowSize() {
        if (mRoot == null || mRoot.getWidth() == 0) {
            return;
        }

        WindowSize size = WindowSize.of(mRoot);

        if (!size.sameAs(mWindowSize)) {
            mWindowSize = size;
            mResults.onWidthChanged(size.widthDp);
        }
    }

    /**
     * Back key while the suggestions cover the results: show the results again. False when there's nothing to close.
     */
    public boolean goBack() {
        if (mSuggestions != null && mSuggestions.getVisibility() == View.VISIBLE && mResults != null && !mResults.isEmpty()) {
            hideKeyboard();
            showSuggestions(false);
            return true;
        }

        return false;
    }

    // region Search logic

    private void submit(String query) {
        hideKeyboard();
        showSuggestions(false);
        loadSearchResult(query);
    }

    private void loadSearchResult(String query) {
        // Don't show suggested videos (empty query). Same rule as upstream.
        if (!TextUtils.isEmpty(query) && !query.equals(mSearchQuery)) {
            mSearchQuery = query;
            mSearchPresenter.onSearch(query);
        }
    }

    private void loadSearchTags(String query) {
        if (mTagsProvider == null) {
            return;
        }

        mTagsProvider.search(query, results -> mHandler.post(() -> {
            mTags.clear();
            if (results != null) {
                mTags.addAll(results);
            }
            mTagsAdapter.notifyDataSetChanged();
        }));
    }

    private void setQueryText(String text) {
        mIgnoreTextChange = true;
        mField.setText(text);
        if (text != null) {
            mField.setSelection(text.length());
        }
        mIgnoreTextChange = false;
    }

    private void showSuggestions(boolean show) {
        // Suggestions over the results while typing, results once the query is sent
        boolean visible = show && (mField.hasFocus() || mResults.isEmpty());
        mSuggestions.setVisibility(visible ? View.VISIBLE : View.GONE);
        mList.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void showKeyboard() {
        mField.requestFocus();
        InputMethodManager imm = (InputMethodManager) mField.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            mField.post(() -> imm.showSoftInput(mField, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    // endregion

    // region SearchView

    @Override
    public void updateSearch(VideoGroup group) {
        mResults.update(group);

        if (!mField.hasFocus()) {
            showSuggestions(false);
        }
    }

    @Override
    public void clearSearch() {
        mSearchQuery = null;
        mResults.clear();
    }

    @Override
    public void clearSearchTags() {
        mTags.clear();
        mTagsAdapter.notifyDataSetChanged();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        int index = mTags.indexOf(tag);

        if (index != -1) {
            mTags.remove(index);
            mTagsAdapter.notifyItemRemoved(index);
        }
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
    }

    @Override
    public void showProgressBar(boolean show) {
        mIsProgressShowing = show;
        mHandler.post(() -> {
            if (mProgress != null) {
                mProgress.setVisibility(mIsProgressShowing ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void startSearch(String searchText) {
        if (mField == null) {
            return;
        }

        if (searchText != null) {
            setQueryText(searchText);
            mSearchQuery = null; // force reload
            submit(searchText);
        } else {
            mField.selectAll();
            loadSearchTags(mField.getText().toString());
            showSuggestions(true);
            showKeyboard();
        }
    }

    @Override
    public String getSearchText() {
        return mField != null ? mField.getText().toString() : null;
    }

    @Override
    public void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.kami_search_hint));

        Activity activity = getActivity();

        if (!(activity instanceof MotherActivity)) {
            return;
        }

        ((MotherActivity) activity).addOnResult((requestCode, resultCode, data) -> {
            if (requestCode != VOICE_REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null) {
                return;
            }

            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {
                startSearch(results.get(0));
            }
        });

        try {
            activity.startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            // No voice recognizer on this device: fallback to the keyboard
            showKeyboard();
        }
    }

    @Override
    public void finishReally() {
        if (mEmbedded) {
            // Closing the search shows the feed again, the screen stays
            hideKeyboard();
            Fragment parent = getParentFragment();

            if (parent instanceof Host) {
                ((Host) parent).onSearchClosed();
            }

            return;
        }

        LeanbackActivity activity = (LeanbackActivity) getActivity();

        if (activity != null) {
            activity.finishReally();
        }
    }

    // endregion

    private final class TagsAdapter extends RecyclerView.Adapter<TagHolder> {
        @NonNull
        @Override
        public TagHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TagHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.kami_item_suggestion, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TagHolder holder, int position) {
            Tag tag = mTags.get(position);
            holder.text.setText(tag.tag);
            holder.icon.setImageResource(TextUtils.isEmpty(mField.getText()) ? R.drawable.kami_ic_history : R.drawable.kami_ic_search);
            holder.itemView.setOnClickListener(v -> startSearch(tag.tag));
            holder.itemView.setOnLongClickListener(v -> {
                mSearchPresenter.onTagLongClicked(tag);
                return true;
            });
            holder.fill.setOnClickListener(v -> {
                // Put the suggestion in the field to keep typing
                mField.setText(tag.tag + " ");
                mField.setSelection(mField.getText().length());
            });
        }

        @Override
        public int getItemCount() {
            return mTags.size();
        }
    }

    private static final class TagHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;
        final View fill;

        TagHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.kami_suggestion_icon);
            text = itemView.findViewById(R.id.kami_suggestion_text);
            fill = itemView.findViewById(R.id.kami_suggestion_fill);
        }
    }
}
