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
package org.floens.chan.core.database;

import org.floens.chan.core.model.orm.SavedThread;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseSavedThreadManager {
    private static final String TAG = "DatabaseSavedThreadManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;
    private DatabaseLoadableManager databaseLoadableManager;

    public DatabaseSavedThreadManager(DatabaseManager databaseManager, DatabaseHelper helper, DatabaseLoadableManager databaseLoadableManager) {
        this.databaseManager = databaseManager;
        this.helper = helper;
        this.databaseLoadableManager = databaseLoadableManager;
    }

    public Callable<List<SavedThread>> getSavedThreads() {
        return () -> {
            List<SavedThread> savedThreads = helper.savedThreadDao.queryBuilder()
                    .orderBy("savedDate", false).query();
            for (int i = 0; i < savedThreads.size(); i++) {
                SavedThread savedThread = savedThreads.get(i);
                savedThread.loadable = databaseLoadableManager.refreshForeign(savedThread.loadable);
            }
            return savedThreads;
        };
    }

    public Callable<SavedThread> createSavedThread(final SavedThread savedThread) {
        if (savedThread.loadable.id == 0) {
            throw new IllegalArgumentException("Saved thread loadable is not yet in the db");
        }

        return () -> {
            helper.savedThreadDao.create(savedThread);
            return savedThread;
        };
    }

    public Callable<SavedThread> updateSavedThread(final SavedThread savedThread) {
        return () -> {
            helper.savedThreadDao.update(savedThread);
            return savedThread;
        };
    }

    public Callable<Void> deleteSavedThread(final SavedThread savedThread) {
        return () -> {
            helper.savedThreadDao.delete(savedThread);
            return null;
        };
    }
}
