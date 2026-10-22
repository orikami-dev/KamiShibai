package com.liskovsoft.smartyoutubetv2.tv.mobile.browse;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.mobile.common.WindowSize;

import java.util.List;

/**
 * Bottom sheet with every section, opened from the "More" entry of the bottom bar.
 * Long press on an entry opens the upstream section menu (move, rename, hide...).
 */
public class SectionsSheet extends Dialog {
    public SectionsSheet(Context context, List<BrowseSection> sections, int selectedId, NavAdapter.Listener listener) {
        super(context, R.style.Kamishibai_Sheet);

        setContentView(R.layout.kami_sheet_sections);
        setCanceledOnTouchOutside(true);

        RecyclerView list = findViewById(R.id.kami_sheet_list);
        float widthDp = context.getResources().getDisplayMetrics().widthPixels / WindowSize.density(context);
        list.setLayoutManager(new GridLayoutManager(context, WindowSize.columns(widthDp, 96, 6)));
        NavAdapter adapter = new NavAdapter(context, sections, NavAdapter.LAYOUT_RAIL, 0, listener);
        adapter.setSelectedId(selectedId);
        list.setAdapter(adapter);

        Window window = getWindow();

        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(R.style.Kamishibai_SheetAnimation);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }
}
