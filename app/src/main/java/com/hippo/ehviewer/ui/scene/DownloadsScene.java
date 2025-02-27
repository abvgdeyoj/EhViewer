/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.scene;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.android.resource.AttrResources;
import com.hippo.app.CheckBoxDialogBuilder;
import com.hippo.beerbelly.SimpleDiskCache;
import com.hippo.conaco.DataContainer;
import com.hippo.conaco.ProgressNotifier;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.FastScroller;
import com.hippo.easyrecyclerview.HandlerDrawable;
import com.hippo.easyrecyclerview.MarginItemDecoration;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.io.UniFileInputStreamPipe;
import com.hippo.ripple.Ripple;
import com.hippo.scene.Announcer;
import com.hippo.streampipe.InputStreamPipe;
import com.hippo.unifile.UniFile;
import com.hippo.util.DrawableManager;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.view.ViewTransition;
import com.hippo.widget.FabLayout;
import com.hippo.widget.LoadImageView;
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.ObjectUtils;
import com.hippo.yorozuya.ViewUtils;
import com.hippo.yorozuya.collect.LongList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hippo.ehviewer.spider.SpiderQueen.SPIDER_INFO_FILENAME;

public class DownloadsScene extends ToolbarScene
        implements DownloadManager.DownloadInfoListener,
        EasyRecyclerView.OnItemClickListener,
        EasyRecyclerView.OnItemLongClickListener,
        FabLayout.OnClickFabListener, FastScroller.OnDragHandlerListener {

    private static final String TAG = DownloadsScene.class.getSimpleName();

    public static final String KEY_GID = "gid";

    public static final String KEY_ACTION = "action";
    public static final String KEY_LABEL = "label";

    public static final String ACTION_CLEAR_DOWNLOAD_SERVICE = "clear_download_service";
    private static final int REQUEST_GALLERY_CLOSE = 0;

    private static Pattern authorPattern = Pattern.compile("^(?:\\([^\\[\\]\\(\\)]*\\))?\\s*\\[([^\\[\\]\\(\\)]*)(?:\\(([^\\[\\]\\(\\)]*)\\))?\\]");

    private int lastPosition;
    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private DownloadManager mDownloadManager;
    @Nullable
    private String mLabel;
    @Nullable
    private List<DownloadInfo> mList;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private FabLayout mFabLayout;
    @Nullable
    private DownloadAdapter mAdapter;
    @Nullable
    private AutoStaggeredGridLayoutManager mLayoutManager;

    private ShowcaseView mShowcaseView;

    private int mInitPosition = -1;

    @Override
    public int getNavCheckedItem() {
        return R.id.nav_downloads;
    }

    private boolean handleArguments(Bundle args) {
        if (null == args) {
            return false;
        }

        if (ACTION_CLEAR_DOWNLOAD_SERVICE.equals(args.getString(KEY_ACTION))) {
            DownloadService.clear();
        }

        long gid;
        if (null != mDownloadManager && -1L != (gid = args.getLong(KEY_GID, -1L))) {
            DownloadInfo info = mDownloadManager.getDownloadInfo(gid);
            if (null != info) {
                mLabel = info.getLabel();
                updateForLabel();
                updateView();

                // Get position
                if (null != mList) {
                    int position = mList.indexOf(info);
                    if (position >= 0 && null != mRecyclerView) {
                        mRecyclerView.scrollToPosition(position);
                    } else {
                        mInitPosition = position;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onNewArguments(@NonNull Bundle args) {
        handleArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        mDownloadManager = EhApplication.getDownloadManager(context);
        mDownloadManager.addDownloadInfoListener(this);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mList = null;

        DownloadManager manager = mDownloadManager;
        if (null == manager) {
            Context context = getContext2();
            if (null != context) {
                manager = EhApplication.getDownloadManager(context);
            }
        } else {
            mDownloadManager = null;
        }

        if (null != manager) {
            manager.removeDownloadInfoListener(this);
        } else {
            Log.e(TAG, "Can't removeDownloadInfoListener");
        }
    }

    private void updateForLabel() {
        if (null == mDownloadManager) {
            return;
        }

        if (mLabel == null) {
            mList = mDownloadManager.getDefaultDownloadInfoList();
        } else {
            mList = mDownloadManager.getLabelDownloadInfoList(mLabel);
            if (mList == null) {
                mLabel = null;
                mList = mDownloadManager.getDefaultDownloadInfoList();
            }
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }

        updateTitle();
        Settings.putRecentDownloadLabel(mLabel);
    }

    private void updateTitle() {
        setTitle(getString(R.string.scene_download_title,
                mLabel != null ? mLabel : getString(R.string.default_download_label_name)));
    }

    private void onInit() {
        if (!handleArguments(getArguments())) {
            mLabel = Settings.getRecentDownloadLabel();
            updateForLabel();
        }
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mLabel = savedInstanceState.getString(KEY_LABEL);
        updateForLabel();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_LABEL, mLabel);
    }

    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_download, container, false);

        View content = ViewUtils.$$(view, R.id.content);
        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(content, R.id.recycler_view);
        FastScroller fastScroller = (FastScroller) ViewUtils.$$(content, R.id.fast_scroller);
        mFabLayout = (FabLayout) ViewUtils.$$(view, R.id.fab_layout);
        TextView tip = (TextView) ViewUtils.$$(view, R.id.tip);
        mViewTransition = new ViewTransition(content, tip);

        Context context = getContext2();
        AssertUtils.assertNotNull(content);
        Resources resources = context.getResources();

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_download);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);

        mAdapter = new DownloadAdapter();
        mAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(mAdapter);
        mLayoutManager = new AutoStaggeredGridLayoutManager(0, StaggeredGridLayoutManager.VERTICAL);
        mLayoutManager.setColumnSize(resources.getDimensionPixelOffset(Settings.getDetailSizeResId()));
        mLayoutManager.setStrategy(AutoStaggeredGridLayoutManager.STRATEGY_MIN_SIZE);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        mRecyclerView.setDrawSelectorOnTop(true);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setOnItemClickListener(this);
        mRecyclerView.setOnItemLongClickListener(this);
        mRecyclerView.setChoiceMode(EasyRecyclerView.CHOICE_MODE_MULTIPLE_CUSTOM);
        mRecyclerView.setCustomCheckedListener(new DownloadChoiceListener());
        // Cancel change animation
        RecyclerView.ItemAnimator itemAnimator = mRecyclerView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
        int interval = resources.getDimensionPixelOffset(R.dimen.gallery_list_interval);
        int paddingH = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_h);
        int paddingV = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_v);
        MarginItemDecoration decoration = new MarginItemDecoration(interval, paddingH, paddingV, paddingH, paddingV);
        mRecyclerView.addItemDecoration(decoration);
        decoration.applyPaddings(mRecyclerView);
        if (mInitPosition >= 0) {
            mRecyclerView.scrollToPosition(mInitPosition);
            mInitPosition = -1;
        }

        fastScroller.attachToRecyclerView(mRecyclerView);
        HandlerDrawable handlerDrawable = new HandlerDrawable();
        handlerDrawable.setColor(AttrResources.getAttrColor(context, R.attr.widgetColorThemeAccent));
        fastScroller.setHandlerDrawable(handlerDrawable);
        fastScroller.setOnDragHandlerListener(this);

        mFabLayout.setExpanded(false, false);
        mFabLayout.setHidePrimaryFab(true);
        mFabLayout.setAutoCancel(false);
        mFabLayout.setOnClickFabListener(this);
        addAboveSnackView(mFabLayout);

        updateView();

        guide();

        return view;
    }

    private void guide() {
        if (Settings.getGuideDownloadThumb() && null != mRecyclerView) {
            mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Settings.getGuideDownloadThumb()) {
                        guideDownloadThumb();
                    }
                    if (null != mRecyclerView) {
                        ViewUtils.removeOnGlobalLayoutListener(mRecyclerView.getViewTreeObserver(), this);
                    }
                }
            });
        } else {
            guideDownloadLabels();
        }
    }

    private void guideDownloadThumb() {
        MainActivity activity = getActivity2();
        if (null == activity || !Settings.getGuideDownloadThumb() || null == mLayoutManager || null == mRecyclerView) {
            guideDownloadLabels();
            return;
        }
        int position = mLayoutManager.findFirstCompletelyVisibleItemPositions(null)[0];
        if (position < 0) {
            guideDownloadLabels();
            return;
        }
        RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (null == holder) {
            guideDownloadLabels();
            return;
        }

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new ViewTarget(((DownloadHolder) holder).thumb))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_thumb_title)
                .setContentText(R.string.guide_download_thumb_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.putGuideDownloadThumb(false);
                        guideDownloadLabels();
                    }
                }).build();
    }

    private void guideDownloadLabels() {
        MainActivity activity = getActivity2();
        if (null == activity || !Settings.getGuideDownloadLabels()) {
            return;
        }

        Display display = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new PointTarget(point.x, point.y / 3))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_labels_title)
                .setContentText(R.string.guide_download_labels_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.puttGuideDownloadLabels(false);
                        openDrawer(Gravity.RIGHT);
                    }
                }).build();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateTitle();
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView);
            mShowcaseView = null;
        }
        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout);
            mFabLayout = null;
        }

        mRecyclerView = null;
        mViewTransition = null;
        mAdapter = null;
        mLayoutManager = null;
    }

    @Override
    public void onNavigationClick() {
        onBackPressed();
    }

    @Override
    public int getMenuResId() {
        return R.menu.scene_download;
    }

    private static String getAuthor(DownloadInfo downloadInfo) {
        return getAuthor(downloadInfo.title);
    }

    private static String getAuthor(String title) {
        Matcher matcher = authorPattern.matcher(title);
        if (!matcher.find())
            return "";
        if (matcher.group(2) != null) {
            return matcher.group(2);
        }
        if (matcher.group(1) != null) {
            return matcher.group(1);
        }
        return "";
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Skip when in choice mode
        Activity activity = getActivity2();
        int id = item.getItemId();
        if ((null == activity || null == mRecyclerView || mRecyclerView.isInCustomChoice())) {
            return false;
        }

        switch (id) {
            case R.id.action_start_all: {
                Intent intent = new Intent(activity, DownloadService.class);
                intent.setAction(DownloadService.ACTION_START_ALL);
                activity.startService(intent);
                return true;
            }
            case R.id.action_stop_all: {
                if (null != mDownloadManager) {
                    mDownloadManager.stopAllDownload();
                }
                return true;
            }
            case R.id.action_reset_reading_progress: {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.reset_reading_progress_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (mDownloadManager != null) {
                                mDownloadManager.resetAllReadingProgress();
                            }
                        }).show();
                return true;
            }
            case R.id.action_select_all:{
                EasyRecyclerView recyclerView = mRecyclerView;
                recyclerView.intoCustomChoiceMode();
                recyclerView.checkAll();
                return true;
            }
            case R.id.action_edit_mode: {
                mRecyclerView.intoCustomChoiceMode();
                return true;
            }
            case R.id.action_select_read: {
                mRecyclerView.intoCustomChoiceMode();
                for (int i = 0, n = mAdapter.getItemCount(); i < n; i++) {
                    SpiderInfo spiderInfo = mAdapter.readSpiderInfoFromLocalByInfo(mList.get(i));
                    if (spiderInfo != null && spiderInfo.startPage > 0) {
                        mRecyclerView.setItemChecked(i, true);
                    }
                }
                return true;
            }
            case R.id.action_select_done:{
                mRecyclerView.intoCustomChoiceMode();
                for (int i = 0, n = mAdapter.getItemCount(); i < n; i++) {
                    if (mList.get(i).getState() == DownloadInfo.STATE_FINISH) {
                        mRecyclerView.setItemChecked(i, true);
                    }
                }
                return true;
            }
            case R.id.action_start_all_reversed: {
                List<DownloadInfo> list = mList;
                if (list == null) {
                    return true;
                }
                LongList gidList = new LongList();
                for (int i = list.size() - 1; i > -1; i--) {
                    DownloadInfo info = list.get(i);
                    if(info.state!=DownloadInfo.STATE_FINISH){
                        gidList.add(info.gid);
                    }
                }
                Intent intent = new Intent(activity, DownloadService.class);
                intent.setAction(DownloadService.ACTION_START_RANGE);
                intent.putExtra(DownloadService.KEY_GID_LIST, gidList);
                activity.startService(intent);
                return true;
            }
            case R.id.action_scroll_to_top:{
                mRecyclerView.scrollToPosition(0);
                return true;
            }
            case R.id.action_scroll_to_bottom:{
                mRecyclerView.scrollToPosition(mList.size()-1);
                return true;
            }
            case R.id.action_shuffle:{
                Collections.shuffle(mList);
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }
            case R.id.action_sort_by_page:{
                //noinspection Convert2Lambda
                Collections.sort(mList, new Comparator<DownloadInfo>() {
                    @Override
                    public int compare(DownloadInfo o1, DownloadInfo o2) {
                        return o2.total - o1.total;
                    }
                });
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }
            case R.id.action_sort_by_title:{
                //noinspection Convert2Lambda
                Collections.sort(mList, new Comparator<DownloadInfo>() {
                    @Override
                    public int compare(DownloadInfo o1, DownloadInfo o2) {
                        return o1.title.compareTo(o2.title);
                    }
                });
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }
            case R.id.action_sort_by_author:{
                //noinspection Convert2Lambda
                Collections.sort(mList, new Comparator<DownloadInfo>() {
                    @Override
                    public int compare(DownloadInfo o1, DownloadInfo o2) {
                        String a1 = getAuthor(o1);
                        String a2 = getAuthor(o2);
                        int aCompare = a1.compareTo(a2);
                        if (aCompare == 0) {
                            return o1.title.compareTo(o2.title);
                        } else {
                            return aCompare;
                        }
                    }
                });
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }
            case R.id.action_sort_by_category:{
                //noinspection Convert2Lambda
                Collections.sort(mList, new Comparator<DownloadInfo>() {
                    @Override
                    public int compare(DownloadInfo o1, DownloadInfo o2) {
                        return o1.category - o2.category;
                    }
                });
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }
        }
        return false;
    }

    public void updateView() {
        if (mViewTransition != null) {
            if (mList == null || mList.size() == 0) {
                mViewTransition.showView(1);
            } else {
                mViewTransition.showView(0);
            }
        }
    }

    @Override
    public View onCreateDrawerView(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drawer_list, container, false);

        final Context context = getContext2();
        AssertUtils.assertNotNull(context);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.download_labels);
        toolbar.inflateMenu(R.menu.drawer_download);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                switch (id) {
                    case R.id.action_settings:
                        startScene(new Announcer(DownloadLabelsScene.class));
                        return true;
                    case R.id.action_default_download_label:
                        DownloadManager dm = mDownloadManager;
                        if (null == dm) {
                            return true;
                        }

                        List<DownloadLabel> list = dm.getLabelList();
                        final String[] items = new String[list.size() + 2];
                        items[0] = getString(R.string.let_me_select);
                        items[1] = getString(R.string.default_download_label_name);
                        for (int i = 0, n = list.size(); i < n; i++) {
                            items[i + 2] = list.get(i).getLabel();
                        }
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.default_download_label)
                                .setItems(items, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (which == 0) {
                                            Settings.putHasDefaultDownloadLabel(false);
                                        } else {
                                            Settings.putHasDefaultDownloadLabel(true);
                                            String label;
                                            if (which == 1) {
                                                label = null;
                                            } else {
                                                label = items[which];
                                            }
                                            Settings.putDefaultDownloadLabel(label);
                                        }
                                    }
                                }).show();
                        return true;
                }
                return false;
            }
        });

        List<DownloadLabel> list = EhApplication.getDownloadManager(context).getLabelList();
        final List<String> labels = new ArrayList<>(list.size() + 1);
        // Add default label name
        labels.add(getString(R.string.default_download_label_name));
        for (DownloadLabel raw: list) {
            labels.add(raw.getLabel());
        }

        // TODO handle download label items update
        ListView listView = (ListView) view.findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<>(context, R.layout.item_simple_list, labels));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String label;
                if (position == 0) {
                    label = null;
                } else {
                    label = labels.get(position);
                }
                if (!ObjectUtils.equal(label, mLabel)) {
                    mLabel = label;
                    updateForLabel();
                    updateView();
                    closeDrawer(Gravity.RIGHT);
                }
            }
        });

        return view;
    }

    @Override
    public void onBackPressed() {
        if (null != mShowcaseView) {
            return;
        }

        if (mRecyclerView != null && mRecyclerView.isInCustomChoice()) {
            mRecyclerView.outOfCustomChoiceMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
    }

    @Override
    public void onEndDragHandler() {
        // Restore right drawer
        if (null != mRecyclerView && !mRecyclerView.isInCustomChoice()) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        Activity activity = getActivity2();
        EasyRecyclerView recyclerView = mRecyclerView;
        if (null == activity || null == recyclerView) {
            return false;
        }

        if (recyclerView.isInCustomChoice()) {
            recyclerView.toggleItemChecked(position);
            return true;
        } else {
            List<DownloadInfo> list = mList;
            if (list == null) {
                return false;
            }
            if (position < 0 || position >= list.size()) {
                return false;
            }

            Intent intent = new Intent(activity, GalleryActivity.class);
            intent.setAction(GalleryActivity.ACTION_EH);
            intent.putExtra(GalleryActivity.KEY_GALLERY_POSITION, position);
            intent.putExtra(KEY_LABEL, mLabel);
            lastPosition = position;
            startActivityForResult(intent, REQUEST_GALLERY_CLOSE);
            return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_GALLERY_CLOSE == requestCode) {
            if (mAdapter != null) {
                mAdapter.notifyItemChanged(lastPosition);
            }
            if (data == null) {
                return;
            }
            int requestNextPosition = data.getIntExtra(GalleryActivity.KEY_REQUEST_NEXT_POSITION, -1);
            if (requestNextPosition >= 0 && requestNextPosition < mList.size()) {
                Intent intent = new Intent(getActivity2(), GalleryActivity.class);
                intent.setAction(GalleryActivity.ACTION_EH);
                intent.putExtra(GalleryActivity.KEY_GALLERY_POSITION, requestNextPosition);
                intent.putExtra(KEY_LABEL, mLabel);
                lastPosition = requestNextPosition;
                if (mRecyclerView != null) {
                    mRecyclerView.scrollToPosition(requestNextPosition);
                }
                startActivityForResult(intent, REQUEST_GALLERY_CLOSE);
            }
        }
    }

    @Override
    public boolean onItemLongClick(EasyRecyclerView parent, View view, int position, long id) {
        EasyRecyclerView recyclerView = mRecyclerView;
        if (recyclerView == null) {
            return false;
        }

        if (!recyclerView.isInCustomChoice()) {
            recyclerView.intoCustomChoiceMode();
        }
        recyclerView.toggleItemChecked(position);

        return true;
    }

    @Override
    public void onClickPrimaryFab(FabLayout view, FloatingActionButton fab) {
        if (mRecyclerView != null && mRecyclerView.isInCustomChoice()) {
            mRecyclerView.outOfCustomChoiceMode();
        }
    }

    @Override
    public void onClickSecondaryFab(FabLayout view, FloatingActionButton fab, int position) {
        Context context = getContext2();
        Activity activity = getActivity2();
        EasyRecyclerView recyclerView = mRecyclerView;
        if (null == context || null == activity || null == recyclerView) {
            return;
        }

        if (0 == position) {
            recyclerView.checkAll();
        } else {
            List<DownloadInfo> list = mList;
            if (list == null) {
                return;
            }

            LongList gidList = null;
            List<DownloadInfo> downloadInfoList = null;
            boolean collectGid = position == 1 || position == 2 || position == 3; // Start, Stop, Delete
            boolean collectDownloadInfo = position == 3 || position == 4; // Delete or Move
            if (collectGid) {
                gidList = new LongList();
            }
            if (collectDownloadInfo) {
                downloadInfoList = new LinkedList<>();
            }

            SparseBooleanArray stateArray = recyclerView.getCheckedItemPositions();
            for (int i = 0, n = stateArray.size(); i < n; i++) {
                if (stateArray.valueAt(i)) {
                    DownloadInfo info = list.get(stateArray.keyAt(i));
                    if (collectDownloadInfo) {
                        downloadInfoList.add(info);
                    }
                    if (collectGid) {
                        gidList.add(info.gid);
                    }
                }
            }

            switch (position) {
                case 1: { // Start
                    Intent intent = new Intent(activity, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_START_RANGE);
                    intent.putExtra(DownloadService.KEY_GID_LIST, gidList);
                    activity.startService(intent);
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode();
                    break;
                }
                case 2: { // Stop
                    if (null != mDownloadManager) {
                        mDownloadManager.stopRangeDownload(gidList);
                    }
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode();
                    break;
                }
                case 3: { // Delete
                    CheckBoxDialogBuilder builder = new CheckBoxDialogBuilder(context,
                            getString(R.string.download_remove_dialog_message_2, gidList.size()),
                            getString(R.string.download_remove_dialog_check_text),
                            Settings.getRemoveImageFiles());
                    DeleteRangeDialogHelper helper = new DeleteRangeDialogHelper(
                            downloadInfoList, gidList, builder);
                    builder.setTitle(R.string.download_remove_dialog_title)
                            .setPositiveButton(android.R.string.ok, helper)
                            .show();
                    break;
                }
                case 4: {// Move
                    List<DownloadLabel> labelRawList = EhApplication.getDownloadManager(context).getLabelList();
                    List<String> labelList = new ArrayList<>(labelRawList.size() + 1);
                    labelList.add(getString(R.string.default_download_label_name));
                    for (int i = 0, n = labelRawList.size(); i < n; i++) {
                        labelList.add(labelRawList.get(i).getLabel());
                    }
                    String[] labels = labelList.toArray(new String[labelList.size()]);

                    MoveDialogHelper helper = new MoveDialogHelper(labels, downloadInfoList);

                    new AlertDialog.Builder(context)
                            .setTitle(R.string.download_move_dialog_title)
                            .setItems(labels, helper)
                            .show();
                    break;
                }
            }
        }
    }

    @Override
    public void onAdd(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        if (mList != list) {
            return;
        }
        if (mAdapter != null) {
            mAdapter.notifyItemInserted(position);
        }
        updateView();
    }

    @Override
    public void onUpdate(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list) {
        if (mList != list) {
            return;
        }

        int index = list.indexOf(info);
        if (index >= 0 && mAdapter != null) {
            mAdapter.notifyItemChanged(index);
        }
    }

    @Override
    public void onUpdateAll() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onReload() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        updateView();
    }

    @Override
    public void onChange() {
        mLabel = null;
        updateForLabel();
        updateView();
    }

    @Override
    public void onRenameLabel(String from, String to) {
        if (!ObjectUtils.equal(mLabel, from)) {
            return;
        }

        mLabel = to;
        updateForLabel();
        updateView();
    }

    @Override
    public void onRemove(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        if (mList != list) {
            return;
        }
        if (mAdapter != null) {
            mAdapter.notifyItemRemoved(position);
        }
        updateView();
    }

    @Override
    public void onUpdateLabels() {
        // TODO
    }

    private void bindForState(DownloadHolder holder, DownloadInfo info) {
        Resources resources = getResources2();
        if (null == resources) {
            return;
        }

        switch (info.state) {
            case DownloadInfo.STATE_NONE:
                bindState(holder, info, resources.getString(R.string.download_state_none));
                break;
            case DownloadInfo.STATE_WAIT:
                bindState(holder, info, resources.getString(R.string.download_state_wait));
                break;
            case DownloadInfo.STATE_DOWNLOAD:
                bindProgress(holder, info);
                break;
            case DownloadInfo.STATE_FAILED:
                String text;
                if (info.legacy <= 0) {
                    text = resources.getString(R.string.download_state_failed);
                } else {
                    text = resources.getString(R.string.download_state_failed_2, info.legacy);
                }
                bindState(holder, info, text);
                break;
            case DownloadInfo.STATE_FINISH:
                bindState(holder, info, resources.getString(R.string.download_state_finish));
                break;
        }
    }

    private void bindState(DownloadHolder holder, DownloadInfo info, String state) {
        holder.uploader.setVisibility(View.VISIBLE);
        holder.rating.setVisibility(View.VISIBLE);
        holder.category.setVisibility(View.VISIBLE);
        holder.state.setVisibility(View.VISIBLE);
        holder.progressBar.setVisibility(View.GONE);
        holder.percent.setVisibility(View.GONE);
        holder.speed.setVisibility(View.GONE);
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE);
            holder.stop.setVisibility(View.VISIBLE);
        } else {
            holder.start.setVisibility(View.VISIBLE);
            holder.stop.setVisibility(View.GONE);
        }

        holder.state.setText(state);
    }

    @SuppressLint("SetTextI18n")
    private void bindProgress(DownloadHolder holder, DownloadInfo info) {
        holder.uploader.setVisibility(View.GONE);
        holder.rating.setVisibility(View.GONE);
        holder.category.setVisibility(View.GONE);
        holder.state.setVisibility(View.GONE);
        holder.progressBar.setVisibility(View.VISIBLE);
        holder.percent.setVisibility(View.VISIBLE);
        holder.speed.setVisibility(View.VISIBLE);
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE);
            holder.stop.setVisibility(View.VISIBLE);
        } else {
            holder.start.setVisibility(View.VISIBLE);
            holder.stop.setVisibility(View.GONE);
        }

        if (info.total <= 0 || info.finished < 0) {
            holder.percent.setText(null);
            holder.progressBar.setIndeterminate(true);
        } else {
            holder.percent.setText(info.finished + "/" + info.total);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setMax(info.total);
            holder.progressBar.setProgress(info.finished);
        }
        long speed = info.speed;
        if (speed < 0) {
            speed = 0;
        }
        holder.speed.setText(FileUtils.humanReadableByteCount(speed, false) + "/S");
    }

    private static void deleteFileAsync(UniFile... files) {
        new AsyncTask<UniFile, Void, Void>() {
            @Override
            protected Void doInBackground(UniFile... params) {
                for (UniFile file: params) {
                    if (file != null) {
                        file.delete();
                    }
                }
                return null;
            }
        }.executeOnExecutor(IoThreadPoolExecutor.getInstance(), files);
    }

    private class DeleteDialogHelper implements DialogInterface.OnClickListener {

        private final GalleryInfo mGalleryInfo;
        private final CheckBoxDialogBuilder mBuilder;

        public DeleteDialogHelper(GalleryInfo galleryInfo, CheckBoxDialogBuilder builder) {
            mGalleryInfo = galleryInfo;
            mBuilder = builder;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return;
            }

            // Delete
            if (null != mDownloadManager) {
                mDownloadManager.deleteDownload(mGalleryInfo.gid);
            }

            // Delete image files
            boolean checked = mBuilder.isChecked();
            Settings.putRemoveImageFiles(checked);
            if (checked) {
                // Remove download path
                EhDB.removeDownloadDirname(mGalleryInfo.gid);
                // Delete file
                UniFile file = SpiderDen.getGalleryDownloadDir(mGalleryInfo);
                deleteFileAsync(file);
            }
        }
    }

    private class DeleteRangeDialogHelper implements DialogInterface.OnClickListener {

        private final List<DownloadInfo> mDownloadInfoList;
        private final LongList mGidList;
        private final CheckBoxDialogBuilder mBuilder;

        public DeleteRangeDialogHelper(List<DownloadInfo> downloadInfoList,
                LongList gidList, CheckBoxDialogBuilder builder) {
            mDownloadInfoList = downloadInfoList;
            mGidList = gidList;
            mBuilder = builder;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return;
            }

            // Cancel check mode
            if (mRecyclerView != null) {
                mRecyclerView.outOfCustomChoiceMode();
            }

            // Delete
            if (null != mDownloadManager) {
                mDownloadManager.deleteRangeDownload(mGidList);
            }

            // Delete image files
            boolean checked = mBuilder.isChecked();
            Settings.putRemoveImageFiles(checked);
            if (checked) {
                UniFile[] files = new UniFile[mDownloadInfoList.size()];
                int i = 0;
                for (DownloadInfo info: mDownloadInfoList) {
                    // Remove download path
                    EhDB.removeDownloadDirname(info.gid);
                    // Put file
                    files[i] = SpiderDen.getGalleryDownloadDir(info);
                    i++;
                }
                // Delete file
                deleteFileAsync(files);
            }
        }
    }

    private class MoveDialogHelper implements DialogInterface.OnClickListener {

        private final String[] mLabels;
        private final List<DownloadInfo> mDownloadInfoList;

        public MoveDialogHelper(String[] labels, List<DownloadInfo> downloadInfoList) {
            mLabels = labels;
            mDownloadInfoList = downloadInfoList;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Cancel check mode
            Context context = getContext2();
            if (null == context) {
                return;
            }
            if (null != mRecyclerView) {
                mRecyclerView.outOfCustomChoiceMode();
            }

            String label;
            if (which == 0) {
                label = null;
            } else {
                label = mLabels[which];
            }
            EhApplication.getDownloadManager(context).changeLabel(mDownloadInfoList, label);
        }
    }

    private class DownloadHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public final LoadImageView thumb;
        public final TextView title;
        public final TextView uploader;
        public final SimpleRatingView rating;
        public final TextView category;
        public final View start;
        public final View stop;
        public final TextView state;
        public final ProgressBar progressBar;
        public final TextView percent;
        public final TextView speed;
        public final TextView readProgress;

        public DownloadHolder(View itemView) {
            super(itemView);

            thumb = (LoadImageView) itemView.findViewById(R.id.thumb);
            title = (TextView) itemView.findViewById(R.id.title);
            uploader = (TextView) itemView.findViewById(R.id.uploader);
            rating = (SimpleRatingView) itemView.findViewById(R.id.rating);
            category = (TextView) itemView.findViewById(R.id.category);
            start = itemView.findViewById(R.id.start);
            stop = itemView.findViewById(R.id.stop);
            state = (TextView) itemView.findViewById(R.id.state);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progress_bar);
            percent = (TextView) itemView.findViewById(R.id.percent);
            speed = (TextView) itemView.findViewById(R.id.speed);
            readProgress = itemView.findViewById(R.id.read_progress);

            // TODO cancel on click listener when select items
            thumb.setOnClickListener(this);
            start.setOnClickListener(this);
            stop.setOnClickListener(this);

            boolean isDarkTheme = !AttrResources.getAttrBoolean(getContext2(), R.attr.isLightTheme);
            Ripple.addRipple(start, isDarkTheme);
            Ripple.addRipple(stop, isDarkTheme);
        }

        @Override
        public void onClick(View v) {
            Context context = getContext2();
            Activity activity = getActivity2();
            EasyRecyclerView recyclerView = mRecyclerView;
            if (null == context || null == activity || null == recyclerView || recyclerView.isInCustomChoice()) {
                return;
            }
            List<DownloadInfo> list = mList;
            if (list == null) {
                return;
            }
            int size = list.size();
            int index = recyclerView.getChildAdapterPosition(itemView);
            if (index < 0 || index >= size) {
                return;
            }

            if (thumb == v) {
                Bundle args = new Bundle();
                args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
                args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, list.get(index));
                Announcer announcer = new Announcer(GalleryDetailScene.class).setArgs(args);
                announcer.setTranHelper(new EnterGalleryDetailTransaction(thumb));
                startScene(announcer);
            } else if (start == v) {
                Intent intent = new Intent(activity, DownloadService.class);
                intent.setAction(DownloadService.ACTION_START);
                intent.putExtra(DownloadService.KEY_GALLERY_INFO, list.get(index));
                activity.startService(intent);
            } else if (stop == v) {
                if (null != mDownloadManager) {
                    mDownloadManager.stopDownload(list.get(index).gid);
                }
            }
        }
    }

    private class DownloadAdapter extends RecyclerView.Adapter<DownloadHolder> {

        private final LayoutInflater mInflater;
        private final int mListThumbWidth;
        private final int mListThumbHeight;

        public DownloadAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);

            View calculator = mInflater.inflate(R.layout.item_gallery_list_thumb_height, null);
            ViewUtils.measureView(calculator, 1024, ViewGroup.LayoutParams.WRAP_CONTENT);
            mListThumbHeight = calculator.getMeasuredHeight();
            mListThumbWidth = mListThumbHeight * 2 / 3;
        }

        @Override
        public long getItemId(int position) {
            if (mList == null || position < 0 || position >= mList.size()) {
                return 0;
            }
            return mList.get(position).gid;
        }

        @Override
        public DownloadHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DownloadHolder holder = new DownloadHolder(mInflater.inflate(R.layout.item_download, parent, false));

            ViewGroup.LayoutParams lp = holder.thumb.getLayoutParams();
            lp.width = mListThumbWidth;
            lp.height = mListThumbHeight;
            holder.thumb.setLayoutParams(lp);

            return holder;
        }

        private SpiderInfo readSpiderInfoFromLocalByInfo(DownloadInfo info) {
            // Read from download dir
            SpiderDen spiderDen = new SpiderDen(info);
            UniFile downloadDir = spiderDen.getDownloadDir();
            if (downloadDir != null) {
                UniFile file = downloadDir.findFile(SPIDER_INFO_FILENAME);
                SpiderInfo spiderInfo = SpiderInfo.read(file);
                if (spiderInfo != null && spiderInfo.gid == info.gid &&
                        spiderInfo.token.equals(info.token)) {
                    return spiderInfo;
                }
            }

            // Read from cache
            Context context = getActivity2();
            EhApplication application = (EhApplication) context.getApplicationContext();
            SimpleDiskCache spiderInfoCache= EhApplication.getSpiderInfoCache(application);
            InputStreamPipe pipe = spiderInfoCache.getInputStreamPipe(Long.toString(info.gid));
            if (null != pipe) {
                try {
                    pipe.obtain();
                    SpiderInfo spiderInfo = SpiderInfo.read(pipe.open());
                    if (spiderInfo != null && spiderInfo.gid == info.gid &&
                            spiderInfo.token.equals(info.token)) {
                        return spiderInfo;
                    }
                } catch (IOException e) {
                    // Ignore
                } finally {
                    pipe.close();
                    pipe.release();
                }
            }

            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(DownloadHolder holder, int position) {
            if (mList == null) {
                return;
            }
            DownloadInfo info = mList.get(position);
            SpiderInfo spiderInfo = readSpiderInfoFromLocalByInfo(info);
            if (spiderInfo == null) {
                holder.readProgress.setText(1 + "/" + info.total);
                holder.readProgress.setTextColor(Color.rgb(255, 0, 0));
            } else {
                holder.readProgress.setText((spiderInfo.startPage + 1) + "/" + spiderInfo.pages);
                int read255th = spiderInfo.startPage * 255 / spiderInfo.pages;
                holder.readProgress.setTextColor(Color.rgb(255 - read255th, read255th, 0));
            }
            holder.thumb.load(EhCacheKeyFactory.getThumbKey(info.gid), info.thumb,
                    new ThumbDataContainer(info), true);
            holder.title.setText(EhUtils.getSuitableTitle(info));
            holder.uploader.setText(info.uploader);
            holder.rating.setRating(info.rating);
            TextView category = holder.category;
            String newCategoryText = EhUtils.getCategory(info.category);
            if (!newCategoryText.equals(category.getText())) {
                category.setText(newCategoryText);
                category.setBackgroundColor(EhUtils.getCategoryColor(info.category));
            }
            bindForState(holder, info);

            // Update transition name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ViewCompat.setTransitionName(holder.thumb, TransitionNameFactory.getThumbTransitionName(info.gid));
            }
        }

        @Override
        public int getItemCount() {
            return mList == null ? 0 : mList.size();
        }
    }

    private class DownloadChoiceListener implements  EasyRecyclerView.CustomChoiceListener {

        @Override
        public void onIntoCustomChoice(EasyRecyclerView view) {
            if (mRecyclerView != null) {
                mRecyclerView.setOnItemLongClickListener(null);
                mRecyclerView.setLongClickable(false);
            }
            if (mFabLayout != null) {
                mFabLayout.setExpanded(true);
            }
            // Lock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
        }

        @Override
        public void onOutOfCustomChoice(EasyRecyclerView view) {
            if (mRecyclerView != null) {
                mRecyclerView.setOnItemLongClickListener(DownloadsScene.this);
            }
            if (mFabLayout != null) {
                mFabLayout.setExpanded(false);
            }
            // Unlock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
        }

        @Override
        public void onItemCheckedStateChanged(EasyRecyclerView view, int position, long id, boolean checked) {
            if (view.getCheckedItemCount() == 0) {
                view.outOfCustomChoiceMode();
            }
        }
    }

    private class ThumbDataContainer implements DataContainer {

        private final DownloadInfo mInfo;
        @Nullable
        private UniFile mFile;

        public ThumbDataContainer(@NonNull DownloadInfo info) {
            mInfo = info;
        }

        private void ensureFile() {
            if (mFile == null) {
                UniFile dir = SpiderDen.getGalleryDownloadDir(mInfo);
                if (dir != null && dir.isDirectory()) {
                    mFile = dir.createFile(".thumb");
                }
            }
        }

        @Override
        public boolean isEnabled() {
            ensureFile();
            return mFile != null;
        }

        @Override
        public void onUrlMoved(String requestUrl, String responseUrl) {}

        @Override
        public boolean save(InputStream is, long length, String mediaType, ProgressNotifier notify) {
            ensureFile();
            if (mFile == null) {
                return false;
            }

            OutputStream os = null;
            try {
                os = mFile.openOutputStream();
                IOUtils.copy(is, os);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                IOUtils.closeQuietly(os);
            }
        }

        @Override
        public InputStreamPipe get() {
            ensureFile();
            if (mFile != null) {
                return new UniFileInputStreamPipe(mFile);
            } else {
                return null;
            }
        }

        @Override
        public void remove() {
            if (mFile != null) {
                mFile.delete();
            }
        }
    }
}
