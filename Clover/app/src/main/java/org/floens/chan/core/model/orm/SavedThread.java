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
package org.floens.chan.core.model.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * A thread saved for offline viewing. The posts and media live on disk, see
 * {@link org.floens.chan.core.offline.OfflineStore}.
 */
@DatabaseTable(tableName = "savedthread")
public class SavedThread {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable;

    @DatabaseField
    public String thumbnailUrl;

    /**
     * Time of the last successful save.
     */
    @DatabaseField
    public long savedDate;

    @DatabaseField
    public int postCount;

    @DatabaseField
    public int fileCount;

    /**
     * Files that could not be downloaded on the last save.
     */
    @DatabaseField
    public int missingCount;

    @DatabaseField
    public long size;

    /**
     * Save the latest posts every time the thread loads, and keep the thread watched so it
     * loads in the background.
     */
    @DatabaseField
    public boolean autoUpdate;

    public SavedThread() {
    }
}
