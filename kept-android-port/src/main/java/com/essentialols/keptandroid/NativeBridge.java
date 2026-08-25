package com.essentialols.keptandroid;

import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;

final class NativeBridge {
    private final KeptActivity activity;
    NativeBridge(KeptActivity activity) { this.activity = activity; }

    @JavascriptInterface public void syncReminders(String json) {
        try {
            int count = KeptReminderScheduler.sync(activity, activity.serverUrl(), new JSONArray(json));
            activity.runOnUiThread(() -> activity.maybeAskNotificationPermission(count));
        } catch (Exception ignored) {}
    }

    @JavascriptInterface public void shareTextConsumed() {
        activity.runOnUiThread(activity::consumeShareText);
    }

    @JavascriptInterface public void openMenu() {
        activity.runOnUiThread(activity::showNativeMenu);
    }


    @JavascriptInterface public void setNoteChrome(String color) {
        activity.runOnUiThread(() -> AndroidUiIntegration.noteBars(activity, color));
    }

    @JavascriptInterface public void smartCaptureUnavailable() {
        activity.runOnUiThread(() -> Toast.makeText(activity,
                "Smart Capture is not ported yet. The control is shown to match the official Kept Android layout.",
                Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface public void setTheme(String theme) {
        boolean light = "light".equalsIgnoreCase(theme);
        activity.runOnUiThread(() -> AndroidUiIntegration.systemBars(activity, light));
    }
}
