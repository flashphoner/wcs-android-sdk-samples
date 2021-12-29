package com.example.screen_sharing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ScreenSharingService extends Service {

    public static final int NOTIFICATION_ID = 2336;
    private static final String CHANNEL_ID = "screen_capture";
    private static final String CHANNEL_NAME = "Screen_Capture";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan =
                new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(chan);

        final int notificationId = (int) System.currentTimeMillis();
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID);
        Notification notification =
                notificationBuilder
                        .setSmallIcon(R.drawable.service_icon)
                        .setOngoing(true)
                        .setShowWhen(true)
                        .setContentTitle("ScreenSharingService is running in the foreground")
                        .setPriority(NotificationManager.IMPORTANCE_MIN)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .build();
        NotificationManager notificationManager
                = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);

        startForeground(notificationId, notification);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }
}
