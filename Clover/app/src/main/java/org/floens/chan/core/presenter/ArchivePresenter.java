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
package org.floens.chan.core.presenter;

import android.os.Handler;
import android.os.Looper;

import com.android.volley.RequestQueue;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.Archive;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.SiteActions;
import org.floens.chan.core.site.sites.chan4.Chan4ArchiveThreadInfoRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import javax.inject.Inject;

import static android.text.TextUtils.isEmpty;

public class ArchivePresenter implements SiteActions.ArchiveListener {
    /**
     * Pause between two thread info requests. 4chan asks API clients to keep requests slow,
     * and only the rows on screen are loaded, so this stays a trickle.
     */
    private static final long INFO_REQUEST_DELAY_MS = 300;

    private DatabaseManager databaseManager;
    private RequestQueue requestQueue;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Map<Integer, Archive.ThreadInfo> threadInfos = new HashMap<>();
    private final Set<Integer> failedThreadInfos = new HashSet<>();
    // Newest request first, so the rows the user just scrolled to load first.
    private final Deque<Integer> infoQueue = new ArrayDeque<>();
    private boolean infoRequestRunning = false;
    private boolean destroyed = false;

    private Callback callback;
    private Board board;

    private boolean inRequest = false;

    private String filter;
    private List<Archive.ArchiveItem> items = new ArrayList<>();
    private List<Archive.ArchiveItem> filteredItems = new ArrayList<>();

    @Inject
    public ArchivePresenter(DatabaseManager databaseManager, RequestQueue requestQueue) {
        this.databaseManager = databaseManager;
        this.requestQueue = requestQueue;
    }

    public void destroy() {
        destroyed = true;
        infoQueue.clear();
        handler.removeCallbacksAndMessages(null);
        requestQueue.cancelAll(this);
    }

    /**
     * Returns the loaded details of an archived thread, or null if they are not loaded yet. Asks
     * for them to be loaded when they're missing.
     */
    public Archive.ThreadInfo getThreadInfo(Archive.ArchiveItem item) {
        Archive.ThreadInfo info = threadInfos.get(item.id);
        if (info == null && !failedThreadInfos.contains(item.id)) {
            infoQueue.remove(item.id);
            infoQueue.addFirst(item.id);
            if (!infoRequestRunning) {
                loadNextThreadInfo();
            }
        }
        return info;
    }

    private void loadNextThreadInfo() {
        Integer next = null;
        while (!infoQueue.isEmpty()) {
            Integer candidate = infoQueue.pollFirst();
            // Skip rows that were scrolled past before their turn came.
            if (!threadInfos.containsKey(candidate) && callback.isItemVisible(candidate)) {
                next = candidate;
                break;
            }
        }
        if (next == null || destroyed) {
            infoRequestRunning = false;
            return;
        }

        infoRequestRunning = true;
        final int id = next;
        Chan4ArchiveThreadInfoRequest request = new Chan4ArchiveThreadInfoRequest(
                board.site, board, id,
                info -> {
                    threadInfos.put(id, info);
                    callback.onThreadInfoLoaded(id);
                    scheduleNextThreadInfo();
                },
                error -> {
                    // Usually a thread that was deleted after being archived; keep its excerpt.
                    failedThreadInfos.add(id);
                    scheduleNextThreadInfo();
                });
        request.setTag(this);
        requestQueue.add(request);
    }

    private void scheduleNextThreadInfo() {
        if (destroyed) {
            return;
        }
        handler.postDelayed(this::loadNextThreadInfo, INFO_REQUEST_DELAY_MS);
    }

    public void create(Callback callback, Board board) {
        this.callback = callback;
        this.board = board;

        loadArchive();
    }

    public void onRefresh() {
        if (!inRequest) {
            loadArchive();
        }
    }

    private void loadArchive() {
        inRequest = true;
        callback.showError(false);
        board.site.actions().archive(board, this);
    }

    public void onSearchEntered(String query) {
        filterArchive(query);
    }

    public void onSearchVisibility(boolean visible) {
        if (!visible) {
            filterArchive(null);
        }
    }

    public void onItemClicked(Archive.ArchiveItem item) {
        Loadable loadable = databaseManager.getDatabaseLoadableManager()
                .get(Loadable.forThread(board.site, board, item.id));

        callback.openThread(loadable);
    }

    @Override
    public void onArchive(Archive archive) {
        inRequest = false;
        callback.hideRefreshing();
        callback.showList();
        items = archive.items;
        updateWithFilter();
    }

    @Override
    public void onArchiveError() {
        inRequest = false;
        callback.hideRefreshing();
        callback.showError(true);
    }

    private void filterArchive(String query) {
        filter = query;
        updateWithFilter();
    }

    private void updateWithFilter() {
        filteredItems.clear();
        if (isEmpty(filter)) {
            filteredItems.addAll(items);
        } else {
            for (Archive.ArchiveItem item : items) {
                if (filterApplies(item, filter)) {
                    filteredItems.add(item);
                }
            }
        }

        callback.setArchiveItems(filteredItems);
    }

    private boolean filterApplies(Archive.ArchiveItem item, String filter) {
        String lowerFilter = filter.toLowerCase(Locale.ENGLISH);
        if (item.description.toLowerCase(Locale.ENGLISH).contains(lowerFilter)
                || String.valueOf(item.id).contains(lowerFilter)) {
            return true;
        }
        Archive.ThreadInfo info = threadInfos.get(item.id);
        return info != null && info.subject != null
                && info.subject.toLowerCase(Locale.ENGLISH).contains(lowerFilter);
    }

    public interface Callback {
        void setArchiveItems(List<Archive.ArchiveItem> items);

        void hideRefreshing();

        void showList();

        void showError(boolean show);

        void openThread(Loadable loadable);

        boolean isItemVisible(int id);

        void onThreadInfoLoaded(int id);
    }
}
