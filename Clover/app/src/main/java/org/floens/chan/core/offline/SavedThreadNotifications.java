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
package org.floens.chan.core.offline;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import org.floens.chan.R;
import org.floens.chan.core.model.orm.SavedThread;
import org.floens.chan.ui.service.SavingNotification;

/**
 * Progress notifications for saving threads for offline viewing.
 */
public class SavedThreadNotifications {
    // Away from the ids of the other notifications
    private static final int NOTIFICATION_ID_BASE = 10000;

    private final Context context;
    private final NotificationManager notificationManager;
    private boolean channelCreated;

    public SavedThreadNotifications(Context context) {
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void showProgress(SavedThread savedThread, int done, int total) {
        NotificationCompat.Builder builder = builder(savedThread)
                .setContentTitle(context.getString(R.string.saved_thread_notification_saving))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total, done, total == 0);
        if (total > 0) {
            builder.setSubText(context.getString(R.string.saved_thread_notification_progress, done, total));
        }
        notify(savedThread, builder);
    }

    public void showDone(SavedThread savedThread) {
        NotificationCompat.Builder builder = builder(savedThread)
                .setContentTitle(context.getString(R.string.saved_thread_notification_done))
                .setAutoCancel(true);
        if (savedThread.missingCount > 0) {
            builder.setSubText(context.getResources().getQuantityString(
                    R.plurals.saved_thread_missing, savedThread.missingCount, savedThread.missingCount));
        }
        notify(savedThread, builder);
    }

    public void cancel(SavedThread savedThread) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + savedThread.id);
    }

    private NotificationCompat.Builder builder(SavedThread savedThread) {
        String title = savedThread.loadable.title;
        if (TextUtils.isEmpty(title)) {
            title = "/" + savedThread.loadable.boardCode + "/" + savedThread.loadable.no;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SavingNotification.CHANNEL_ID_SAVING)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentText(title);

        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return builder;
    }

    private void notify(SavedThread savedThread, NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelCreated) {
            createChannel();
            channelCreated = true;
        }
        notificationManager.notify(NOTIFICATION_ID_BASE + savedThread.id, builder.build());
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createChannel() {
        // Same channel as the image saving notification
        NotificationChannel channel = new NotificationChannel(
                SavingNotification.CHANNEL_ID_SAVING, "Save notificatons",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Current save tasks");
        notificationManager.createNotificationChannel(channel);
    }
}
