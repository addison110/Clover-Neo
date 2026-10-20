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
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.SavedThread;
import org.floens.chan.core.offline.SavedThreadManager;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;

/**
 * Lists the threads saved for offline viewing.
 */
public class SavedThreadsController extends Controller implements
        ToolbarNavigationController.ToolbarSearchCallback {
    @Inject
    SavedThreadManager savedThreadManager;

    private CrossfadeView crossfade;
    private SavedThreadAdapter adapter;

    public SavedThreadsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        navigation.setTitle(R.string.saved_threads_screen);
        navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .build();

        view = inflateRes(R.layout.controller_saved_threads);
        crossfade = view.findViewById(R.id.crossfade);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        adapter = new SavedThreadAdapter();
        recyclerView.setAdapter(adapter);
        adapter.load();

        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public void onEvent(SavedThreadManager.SavedThreadChangedMessage message) {
        adapter.load();
    }

    public void onEvent(SavedThreadManager.SavedThreadRemovedMessage message) {
        adapter.load();
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void openThread(SavedThread savedThread) {
        ViewThreadController viewThreadController = new ViewThreadController(context);
        viewThreadController.setLoadable(savedThread.loadable);
        navigationController.pushController(viewThreadController);
    }

    private void deleteSavedThread(SavedThread savedThread) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.saved_thread_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.saved_thread_delete_button, (dialog, which) ->
                        savedThreadManager.delete(savedThread))
                .show();
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            adapter.search(null);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.search(entered);
    }

    private String getInfo(SavedThread savedThread) {
        if (savedThreadManager.isSaving(savedThread)) {
            return context.getString(R.string.saved_thread_saving_now);
        }
        if (!savedThreadManager.hasSavedCopy(savedThread.loadable)) {
            return context.getString(R.string.saved_thread_not_downloaded);
        }

        List<String> parts = new ArrayList<>();
        Board board = savedThread.loadable.board;
        if (board != null) {
            parts.add("/" + board.code + "/");
        }
        parts.add(context.getResources().getQuantityString(R.plurals.posts,
                savedThread.postCount, savedThread.postCount));
        parts.add(context.getResources().getQuantityString(R.plurals.saved_thread_files,
                savedThread.fileCount, savedThread.fileCount));
        parts.add(Formatter.formatShortFileSize(context, savedThread.size));
        parts.add(DateUtils.getRelativeTimeSpanString(savedThread.savedDate, Time.get(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString());
        return TextUtils.join(" · ", parts);
    }

    private class SavedThreadAdapter extends RecyclerView.Adapter<SavedThreadCell> {
        private List<SavedThread> displayList = new ArrayList<>();
        private String searchQuery;

        public SavedThreadAdapter() {
            setHasStableIds(true);
        }

        @Override
        public SavedThreadCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SavedThreadCell(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cell_saved_thread, parent, false));
        }

        @Override
        public void onBindViewHolder(SavedThreadCell holder, int position) {
            SavedThread savedThread = displayList.get(position);
            holder.thumbnail.setUrl(savedThread.thumbnailUrl, dp(48), dp(48));
            holder.text.setText(savedThread.loadable.title);
            holder.subtext.setText(getInfo(savedThread));
            holder.autoUpdate.setOnCheckedChangeListener(null);
            holder.autoUpdate.setChecked(savedThread.autoUpdate);
            holder.autoUpdate.setOnCheckedChangeListener(holder);
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).id;
        }

        public void search(String query) {
            this.searchQuery = query;
            load();
        }

        private void load() {
            displayList.clear();
            String query = TextUtils.isEmpty(searchQuery) ? null : searchQuery.toLowerCase(Locale.ENGLISH);
            for (SavedThread savedThread : savedThreadManager.getSavedThreads()) {
                String title = savedThread.loadable.title;
                if (query == null || (title != null && title.toLowerCase(Locale.ENGLISH).contains(query))) {
                    displayList.add(savedThread);
                }
            }
            crossfade.toggle(!savedThreadManager.getSavedThreads().isEmpty(), true);
            notifyDataSetChanged();
        }
    }

    private class SavedThreadCell extends RecyclerView.ViewHolder implements
            View.OnClickListener, CompoundButton.OnCheckedChangeListener {
        private ThumbnailView thumbnail;
        private TextView text;
        private TextView subtext;
        private SwitchCompat autoUpdate;
        private ImageView delete;

        public SavedThreadCell(View itemView) {
            super(itemView);

            thumbnail = itemView.findViewById(R.id.thumbnail);
            thumbnail.setCircular(true);
            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);
            autoUpdate = itemView.findViewById(R.id.auto_update);
            delete = itemView.findViewById(R.id.delete);

            theme().clearDrawable.apply(delete);

            delete.setOnClickListener(this);
            itemView.setOnClickListener(this);
        }

        private SavedThread getSavedThread() {
            int position = getAdapterPosition();
            if (position >= 0 && position < adapter.getItemCount()) {
                return adapter.displayList.get(position);
            }
            return null;
        }

        @Override
        public void onClick(View v) {
            SavedThread savedThread = getSavedThread();
            if (savedThread != null) {
                if (v == itemView) {
                    openThread(savedThread);
                } else if (v == delete) {
                    deleteSavedThread(savedThread);
                }
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            SavedThread savedThread = getSavedThread();
            if (savedThread != null) {
                savedThreadManager.setAutoUpdate(savedThread, isChecked);
            }
        }
    }
}
