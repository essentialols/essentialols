package com.essentialols.keptandroid;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

final class ReminderScheduler {
    static final String PREFS = "kept_native_reminders";
    static final String KEY_REMINDERS_JSON = "reminders_json";
    static final String KEY_SERVER_URL = "server_url";
    static final String KEY_SCHEDULED = "scheduled_signatures";
    static final String KEY_FIRED = "fired_signatures";

    private ReminderScheduler() {}

    static int sync(Context context, String serverUrl, JSONArray reminders) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_REMINDERS_JSON, reminders.toString())
                .putString(KEY_SERVER_URL, serverUrl == null ? "" : serverUrl)
                .apply();

        Set<String> oldScheduled = new HashSet<>(prefs.getStringSet(KEY_SCHEDULED, new HashSet<String>()));
        Set<String> fired = new HashSet<>(prefs.getStringSet(KEY_FIRED, new HashSet<String>()));
        Set<String> newScheduled = new HashSet<>();
        long now = System.currentTimeMillis();
        int count = 0;

        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder == null) continue;
            if (!"pending".equals(reminder.optString("status", "pending"))) continue;
            String dueAt = clean(reminder.optString("dueAtUtc", ""));
            if (dueAt.isEmpty()) continue;
            long dueMs = parseIsoMillis(dueAt);
            if (dueMs <= 0) continue;

            String id = clean(reminder.optString("id", ""));
            if (id.isEmpty()) id = clean(reminder.optString("syncId", ""));
            if (id.isEmpty()) continue;
            String signature = id + "|" + dueAt;
            if (fired.contains(signature)) continue;
            if (dueMs < now - 6L * 60L * 60L * 1000L) continue;
            long triggerAt = Math.max(dueMs, now + 1500L);
            scheduleOne(context, serverUrl, reminder, signature, triggerAt);
            newScheduled.add(signature);
            count++;
        }

        for (String signature : oldScheduled) {
            if (!newScheduled.contains(signature)) cancel(context, signature);
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, newScheduled).apply();
        return count;
    }

    static int reschedulePersisted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_REMINDERS_JSON, "[]");
        String serverUrl = prefs.getString(KEY_SERVER_URL, "");
        try {
            return sync(context, serverUrl, new JSONArray(json));
        } catch (Exception ignored) {
            return 0;
        }
    }

    static void markFired(Context context, String signature) {
        if (signature == null || signature.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> fired = new HashSet<>(prefs.getStringSet(KEY_FIRED, new HashSet<String>()));
        fired.add(signature);
        if (fired.size() > 200) {
            fired = new HashSet<>();
            fired.add(signature);
        }
        Set<String> scheduled = new HashSet<>(prefs.getStringSet(KEY_SCHEDULED, new HashSet<String>()));
        scheduled.remove(signature);
        prefs.edit().putStringSet(KEY_FIRED, fired).putStringSet(KEY_SCHEDULED, scheduled).apply();
    }

    private static void scheduleOne(Context context, String serverUrl, JSONObject reminder,
                                    String signature, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.essentialols.keptandroid.REMINDER");
        intent.putExtra("signature", signature);
        intent.putExtra("reminder_id", clean(reminder.optString("id", "")));
        intent.putExtra("note_id", clean(reminder.optString("noteId", "")));
        intent.putExtra("title", clean(reminder.optString("title", "")));
        intent.putExtra("body", clean(reminder.optString("body", "")));
        intent.putExtra("server_url", serverUrl == null ? "" : serverUrl);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode(signature),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= 31) {
            if (manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static void cancel(Context context, String signature) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.essentialols.keptandroid.REMINDER");
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode(signature),
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            manager.cancel(pi);
            pi.cancel();
        }
    }

    private static int requestCode(String signature) {
        return signature.hashCode() & 0x7fffffff;
    }

    private static long parseIsoMillis(String value) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(value).getTime();
            } catch (ParseException ignored) {}
        }
        return -1L;
    }

    private static String clean(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) return "";
        return value.trim();
    }
}
