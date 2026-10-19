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

import androidx.appcompat.app.AlertDialog;

import org.floens.chan.R;
import org.floens.chan.core.cloverimport.CloverBackupReader;
import org.floens.chan.core.cloverimport.CloverImport;
import org.floens.chan.ui.activity.ActivityResultHelper;
import org.floens.chan.utils.BackgroundUtils;
import org.floens.chan.utils.Logger;

import java.util.concurrent.Executors;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

/**
 * Lets the user pick an "adb backup" of the original Clover app and imports it.
 */
public class CloverImportHelper {
    private static final String TAG = "CloverImportHelper";

    @Inject
    ActivityResultHelper resultHelper;

    private final Context context;

    public CloverImportHelper(Context context) {
        this.context = context;
        inject(this);
    }

    public void start() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // adb backups have no MIME type of their own
        intent.setType("*/*");

        resultHelper.getResultFromIntent(intent, (resultCode, result) -> {
            if (resultCode == Activity.RESULT_OK && result != null && result.getData() != null) {
                read(result.getData());
            }
        });
    }

    private void read(Uri backup) {
        ProgressDialog progress = ProgressDialog.show(context, null,
                context.getString(R.string.import_clover_reading), true, false);

        BackgroundUtils.runWithExecutor(Executors.newSingleThreadExecutor(), () -> {
            try {
                CloverImport.stage(context, backup);
                return null;
            } catch (Exception e) {
                return e;
            }
        }, error -> {
            progress.dismiss();
            if (error == null) {
                confirm();
            } else {
                Logger.e(TAG, "Failed to read the Clover backup", error);
                showError(error);
            }
        });
    }

    private void confirm() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.import_clover_confirm_title)
                .setMessage(R.string.import_clover_confirm)
                .setNegativeButton(R.string.cancel, (dialog, which) -> CloverImport.discardStaged(context))
                .setPositiveButton(R.string.import_clover_import,
                        (dialog, which) -> CloverImport.restart((Activity) context))
                .setOnCancelListener(dialog -> CloverImport.discardStaged(context))
                .show();
    }

    private void showError(Exception error) {
        int message = R.string.import_clover_failed;
        if (error instanceof CloverBackupReader.BackupException) {
            switch (((CloverBackupReader.BackupException) error).problem) {
                case NOT_A_BACKUP:
                    message = R.string.import_clover_not_a_backup;
                    break;
                case ENCRYPTED:
                    message = R.string.import_clover_encrypted;
                    break;
                case NO_CLOVER_DATA:
                    message = R.string.import_clover_no_data;
                    break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.import_clover_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
