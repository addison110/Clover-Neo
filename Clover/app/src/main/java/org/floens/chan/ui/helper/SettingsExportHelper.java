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
package org.floens.chan.ui.helper;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.floens.chan.R;
import org.floens.chan.core.cloverimport.CloverExport;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.ui.activity.ActivityResultHelper;
import org.floens.chan.utils.BackgroundUtils;
import org.floens.chan.utils.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

/**
 * Lets the user pick where to save a backup of the sites, boards, bookmarks, history, filters
 * and settings, which "Import backup" can restore.
 */
public class SettingsExportHelper {
    private static final String TAG = "SettingsExportHelper";

    @Inject
    ActivityResultHelper resultHelper;

    @Inject
    DatabaseManager databaseManager;

    private final Context context;

    public SettingsExportHelper(Context context) {
        this.context = context;
        inject(this);
    }

    public void start() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "clover-neo-backup-" + date + ".ab");

        resultHelper.getResultFromIntent(intent, (resultCode, result) -> {
            if (resultCode == Activity.RESULT_OK && result != null && result.getData() != null) {
                export(result.getData());
            }
        });
    }

    private void export(Uri target) {
        ProgressDialog progress = ProgressDialog.show(context, null,
                context.getString(R.string.export_settings_writing), true, false);

        BackgroundUtils.runWithExecutor(Executors.newSingleThreadExecutor(), () -> {
            try {
                CloverExport.export(context, databaseManager, target);
                return null;
            } catch (Exception e) {
                return e;
            }
        }, error -> {
            progress.dismiss();
            if (error == null) {
                Toast.makeText(context, R.string.export_settings_done, Toast.LENGTH_LONG).show();
            } else {
                Logger.e(TAG, "Failed to export the settings", error);
                new AlertDialog.Builder(context)
                        .setTitle(R.string.export_settings_failed)
                        .setMessage(error.getMessage())
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });
    }
}
