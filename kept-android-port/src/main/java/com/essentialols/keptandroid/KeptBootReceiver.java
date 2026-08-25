package com.essentialols.keptandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class KeptBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            KeptReminderScheduler.reschedulePersisted(context);
        }
    }
}
