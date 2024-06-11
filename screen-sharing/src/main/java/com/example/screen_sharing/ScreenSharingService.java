package com.example.screen_sharing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ScreenSharingService extends Service {
    private static final String CHANNEL_ID = "screen_capture";
    private static final String CHANNEL_NAME = "Screen_Capture";

    public static final String ACTION_START = "START";

    public static final String ACTION_STOP = "STOP";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
        chan.setImportance(NotificationManager.IMPORTANCE_MIN);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(chan);

        final int notificationId = (int) System.currentTimeMillis();
        Notification.Builder notificationBuilder = new Notification.Builder(this, CHANNEL_ID);
        Notification notification =
                notificationBuilder
                        .setSmallIcon(R.drawable.service_icon)
                        .setOngoing(true)
                        .setShowWhen(true)
                        .setContentTitle("ScreenSharingService is running in the foreground")
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .addAction(createStopAction())
                        .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(notificationId, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Context context = getApplicationContext();
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_STOP));
                stopSelf();
                return START_NOT_STICKY;
            } else {
                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_START));
            }
        }
        return START_STICKY;
    }

    private Notification.Action createStopAction() {
        Intent stopIntent = new Intent(this, ScreenSharingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            stopPendingIntent = PendingIntent.getForegroundService(this, 0, stopIntent, PendingIntent.FLAG_MUTABLE);
        } else {
            stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        }
        Icon stopIcon = Icon.createWithResource(this, R.drawable.abc_vector_test);
        String stopString = "stop";
        Notification.Action.Builder actionBuilder = new Notification.Action.Builder(stopIcon, stopString, stopPendingIntent);
        return actionBuilder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }
}
