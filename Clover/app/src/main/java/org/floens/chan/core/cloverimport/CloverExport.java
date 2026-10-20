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
package org.floens.chan.core.cloverimport;

import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Exports the database and settings as a backup that {@link CloverImport} can import again.
 */
public class CloverExport {
    private static final String TEMP_DATABASE = DatabaseHelper.DATABASE_NAME + ".export";

    public static void export(Context context, DatabaseManager databaseManager, Uri target) throws IOException {
        File database = new File(context.getCacheDir(), TEMP_DATABASE);
        try {
            databaseManager.copyDatabase(database);

            Map<String, Object> preferences =
                    new HashMap<>(PreferenceManager.getDefaultSharedPreferences(context).getAll());
            preferences.keySet().removeAll(CloverImport.SKIPPED_PREFERENCES);

            try (OutputStream out = context.getContentResolver().openOutputStream(target)) {
                if (out == null) {
                    throw new IOException("Could not open " + target);
                }
                CloverBackupWriter.write(out, database, SharedPreferencesXml.write(preferences));
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            database.delete();
        }
    }
}
