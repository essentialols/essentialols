package com.essentialols.keptandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class KeptReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "kept_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String signature = intent.getStringExtra("signature");
        String reminderId = safe(intent.getStringExtra("reminder_id"));
        String noteId = safe(intent.getStringExtra("note_id"));
        String title = safe(intent.getStringExtra("title"));
        String body = safe(intent.getStringExtra("body"));
        String serverUrl = safe(intent.getStringExtra("server_url"));
        if (title.isEmpty()) title = "Kept reminder";
        if (body.isEmpty()) body = "Open Kept to view this reminder.";

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);

        Intent open = new Intent(context, KeptActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("open_from_reminder", true);
        open.putExtra("reminder_id", reminderId);
        open.putExtra("note_id", noteId);
        open.putExtra("server_url", serverUrl);
        PendingIntent content = PendingIntent.getActivity(
                context,
                (signature == null ? reminderId : signature).hashCode() & 0x7fffffff,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(context, CHANNEL_ID);
        else builder = new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setDefaults(Notification.DEFAULT_ALL);

        nm.notify(((signature == null ? reminderId : signature).hashCode() & 0x7fffffff), builder.build());
        KeptReminderScheduler.markFired(context, signature);
    }

    static void ensureChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kept reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Time reminders synced from your Kept server");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
