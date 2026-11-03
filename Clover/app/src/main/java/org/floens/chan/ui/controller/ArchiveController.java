/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Archive;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.presenter.ArchivePresenter;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.ui.view.DividerItemDecoration;
import org.floens.chan.ui.view.FastScrollerHelper;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.dp;

public class ArchiveController extends Controller implements ArchivePresenter.Callback,
        ToolbarNavigationController.ToolbarSearchCallback,
        SwipeRefreshLayout.OnRefreshListener {
    private static final int SEARCH_ID = 1;

    private CrossfadeView crossfadeView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView archiveRecyclerview;
    private View progress;
    private View errorView;

    @Inject
    private ArchivePresenter presenter;

    private ArchiveAdapter adapter;

    private Board board;

    public ArchiveController(Context context) {
        super(context);
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        // Inflate
        view = inflateRes(R.layout.controller_archive);

        // Navigation
        navigation.title = context.getString(R.string.archive_title, BoardHelper.getName(board));
        navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .build();

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        archiveRecyclerview = view.findViewById(R.id.recycler_view);
        progress = view.findViewById(R.id.progress);
        errorView = view.findViewById(R.id.error_text);

        // Adapters
        adapter = new ArchiveAdapter();

        // View setup
        archiveRecyclerview.setLayoutManager(new LinearLayoutManager(context));
        archiveRecyclerview.setAdapter(adapter);
        archiveRecyclerview.addItemDecoration(
                new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        FastScrollerHelper.create(archiveRecyclerview);
//        archiveRecyclerview.setVerticalScrollBarEnabled(false);
        crossfadeView.toggle(false, false);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Presenter
        presenter.create(this, board);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroy();
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    @Override
    public void onSearchEntered(String entered) {
        presenter.onSearchEntered(entered);
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        presenter.onSearchVisibility(visible);
    }

    @Override
    public void onRefresh() {
        presenter.onRefresh();
    }

    @Override
    public void setArchiveItems(List<Archive.ArchiveItem> items) {
        adapter.setArchiveItems(items);
    }

    @Override
    public void hideRefreshing() {
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void showList() {
        crossfadeView.toggle(true, true);
    }

    @Override
    public void showError(boolean show) {
        progress.setVisibility(show ? View.GONE : View.VISIBLE);
        errorView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openThread(Loadable loadable) {
        ViewThreadController threadController = new ViewThreadController(context);
        threadController.setLoadable(loadable);
        navigationController.pushController(threadController);
    }

    @Override
    public boolean isItemVisible(int id) {
        return adapter.boundIds.contains(id);
    }

    @Override
    public void onThreadInfoLoaded(int id) {
        adapter.notifyItemChangedById(id);
    }

    private void onItemClicked(Archive.ArchiveItem item) {
        presenter.onItemClicked(item);
    }

    private class ArchiveAdapter extends RecyclerView.Adapter<ArchiveCell> {
        private List<Archive.ArchiveItem> archiveItems = new ArrayList<>();
        // Ids of the rows that currently have a view, so off-screen rows aren't fetched.
        private final Set<Integer> boundIds = new HashSet<>();

        @Override
        public int getItemCount() {
            return archiveItems.size();
        }

        @Override
        public ArchiveCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ArchiveCell(LayoutInflater.from(context)
                    .inflate(R.layout.cell_archive, parent, false));
        }

        @Override
        public void onBindViewHolder(ArchiveCell holder, int position) {
            Archive.ArchiveItem archiveItem = archiveItems.get(position);

            if (holder.item != null) {
                boundIds.remove(holder.item.id);
            }
            holder.item = archiveItem;
            boundIds.add(archiveItem.id);
            holder.bind(presenter.getThreadInfo(archiveItem));
        }

        @Override
        public void onViewRecycled(ArchiveCell holder) {
            if (holder.item != null) {
                boundIds.remove(holder.item.id);
            }
            holder.thumbnail.setUrl(null, 0, 0);
        }

        void notifyItemChangedById(int id) {
            for (int i = 0; i < archiveItems.size(); i++) {
                if (archiveItems.get(i).id == id) {
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        public void setArchiveItems(List<Archive.ArchiveItem> archiveItems) {
            this.archiveItems = archiveItems;
            notifyDataSetChanged();
        }
    }

    private class ArchiveCell extends RecyclerView.ViewHolder {
        private final ThumbnailView thumbnail;
        private final TextView title;
        private final TextView comment;
        private final TextView info;
        private Archive.ArchiveItem item;

        public ArchiveCell(View itemView) {
            super(itemView);

            itemView.setOnClickListener(v -> onItemClicked(item));

            thumbnail = itemView.findViewById(R.id.thumbnail);
            thumbnail.setRounding(dp(2));
            title = itemView.findViewById(R.id.title);
            comment = itemView.findViewById(R.id.comment);
            info = itemView.findViewById(R.id.info);
        }

        void bind(Archive.ThreadInfo threadInfo) {
            if (threadInfo == null) {
                // Not loaded (yet): show what the archive page gave us.
                thumbnail.setVisibility(View.VISIBLE);
                thumbnail.setUrl(null, 0, 0);
                title.setVisibility(View.GONE);
                comment.setText(item.description);
                info.setText(context.getString(R.string.archive_item_no, item.id));
                return;
            }

            if (threadInfo.thumbnailUrl != null) {
                thumbnail.setVisibility(View.VISIBLE);
                thumbnail.setUrl(threadInfo.thumbnailUrl, dp(64), dp(64));
            } else {
                thumbnail.setUrl(null, 0, 0);
                thumbnail.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(threadInfo.subject)) {
                title.setVisibility(View.VISIBLE);
                title.setText(threadInfo.subject);
            } else {
                title.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(threadInfo.comment)) {
                comment.setVisibility(View.VISIBLE);
                comment.setText(threadInfo.comment);
            } else {
                comment.setVisibility(TextUtils.isEmpty(threadInfo.subject)
                        ? View.VISIBLE : View.GONE);
                comment.setText(item.description);
            }

            StringBuilder text = new StringBuilder();
            text.append(context.getResources().getQuantityString(
                    R.plurals.reply, threadInfo.replies, threadInfo.replies));
            text.append(" · ");
            text.append(context.getResources().getQuantityString(
                    R.plurals.image, threadInfo.images, threadInfo.images));
            if (threadInfo.archivedOn > 0) {
                text.append(" · ");
                text.append(context.getString(R.string.archive_item_expired,
                        DateUtils.getRelativeTimeSpanString(threadInfo.archivedOn * 1000L,
                                Time.get(), DateUtils.MINUTE_IN_MILLIS, 0)));
            }
            info.setText(text);
        }
    }
}
