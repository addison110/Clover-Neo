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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports the database and settings of the original Clover app from an "adb backup".
 * <p>The import is staged to files and applied on the next start, before the database is opened
 * or any setting is read, then the app is restarted.
 */
public class CloverImport {
    private static final String TAG = "CloverImport";

    private static final String STAGED_DATABASE = DatabaseHelper.DATABASE_NAME + ".clover-import";
    private static final String STAGED_PREFERENCES = "clover-import-preferences.xml";

    // These belong to the original app and don't work in Clover Neo
    static final List<String> SKIPPED_PREFERENCES = Arrays.asList(
            "preference_image_save_tree_uri",
            "update_check_time"
    );

    /**
     * Reads the backup and stages it for the next start.
     */
    public static void stage(Context context, Uri backup) throws IOException {
        File database = context.getDatabasePath(STAGED_DATABASE);
        File preferences = new File(context.getFilesDir(), STAGED_PREFERENCES);
        //noinspection ResultOfMethodCallIgnored
        database.getParentFile().mkdirs();

        try (InputStream in = context.getContentResolver().openInputStream(backup)) {
            if (in == null) {
                throw new IOException("Could not open " + backup);
            }
            CloverBackupReader.Contents contents = CloverBackupReader.read(in, database);

            //noinspection ResultOfMethodCallIgnored
            preferences.delete();
            if (contents.preferences != null) {
                try (OutputStream out = new FileOutputStream(preferences)) {
                    out.write(contents.preferences);
                }
            }
        } catch (IOException e) {
            discardStaged(context);
            throw e;
        }
    }

    public static void discardStaged(Context context) {
        //noinspection ResultOfMethodCallIgnored
        context.getDatabasePath(STAGED_DATABASE).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), STAGED_PREFERENCES).delete();
    }

    /**
     * Applies a staged import. Must run before the database or the settings are used.
     */
    public static void applyStaged(Context context) {
        File database = context.getDatabasePath(STAGED_DATABASE);
        if (!database.exists()) {
            return;
        }

        try {
            // Removes the journal files too
            context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
            File target = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
            if (!database.renameTo(target)) {
                IOUtils.copyFile(database, target);
            }

            File preferences = new File(context.getFilesDir(), STAGED_PREFERENCES);
            if (preferences.exists()) {
                applyPreferences(context, SharedPreferencesXml.parse(readFile(preferences)));
            }
            Log.i(TAG, "Imported the Clover backup");
        } catch (IOException e) {
            Log.e(TAG, "Failed to apply the Clover import", e);
        } finally {
            discardStaged(context);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            IOUtils.copy(in, out);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void applyPreferences(Context context, Map<String, Object> values) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (SKIPPED_PREFERENCES.contains(key)) {
                continue;
            }
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set) {
                editor.putStringSet(key, (Set<String>) value);
            }
        }
        editor.commit();
    }

    /**
     * Restarts the app so the staged import is applied.
     */
    public static void restart(Activity activity) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }
}
