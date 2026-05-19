package com.example.caloriecounter.view;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class WaterReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "WATER_NOTIF";
    private static final String CHANNEL_ID = "water_reminder_channel";

    @Override public void onReceive(Context context, Intent intent) {
        Log.d(TAG, " Triggered");
        createChannel(context);

        boolean isOneTime = intent.getBooleanExtra("is_one_time", false);
        int mins = intent.getIntExtra("interval_minutes", 120);

        String title = "💧 Пора попить воды!";
        String text = isOneTime ? "Выпей стакан воды 💙" : "Следующее напоминание через " + ((mins < 60) ? mins + " мин" : (mins / 60) + " ч") + " 💙";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setShowWhen(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());
            Log.d(TAG, " Posted");
        }
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;

            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Напоминания воды", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Всплывающее напоминание");
            ch.enableLights(true); ch.enableVibration(true);
            ch.setShowBadge(true);
            ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(ch);
        }
    }
}