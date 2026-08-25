package com.essentialols.keptandroid;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

public class KeptActivity extends Activity {
    static final String PREFS = "kept_community";
    static final String KEY_SERVER_URL = "server_url";
    static final String KEY_NOTIFICATION_PROMPTED = "notification_prompted";
    static final int REQUEST_FILES = 1001;
    static final int REQUEST_LOCATION = 1002;
    static final int REQUEST_NOTIFICATIONS = 1003;

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView webView;
    private WebShell shell;
    private String serverUrl;
    private final ShareState share = new ShareState();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = new FrameLayout(this);
        setContentView(root);
        AndroidUiIntegration.systemBars(this, true);
        ensureReminderChannel();
        share.consumeIntent(getIntent());
        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        String fromIntent = getIntent() == null ? null : getIntent().getStringExtra("server_url");
        if (empty(serverUrl) && fromIntent != null) {
            String normalized = ServerUrl.normalize(fromIntent);
            if (normalized != null) saveServer(normalized);
        }
        if (empty(serverUrl)) showSetup(null); else showBrowser(state);
    }

    String serverUrl() { return serverUrl; }
    SharedPreferences prefs() { return prefs; }
    WebView webView() { return webView; }
    ShareState share() { return share; }
    void consumeShareText() { share.consumeText(); }

    void saveServer(String value) {
        serverUrl = value;
        prefs.edit().putString(KEY_SERVER_URL, value).apply();
    }

    void showSetup(String error) {
        destroyWebView();
        root.removeAllViews();
        AndroidUiIntegration.systemBars(this, true);
        SetupScreen.show(this, root, serverUrl, error, value -> {
            saveServer(value);
            showBrowser(null);
        });
    }

    private void showBrowser(Bundle state) {
        root.removeAllViews();
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(250, 248, 242));
        shell = new WebShell(this, webView, share);
        shell.configure();
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        if (state != null && webView.restoreState(state) != null) {
            webView.postDelayed(() -> AndroidUiIntegration.install(this, webView), 250);
            return;
        }
        webView.loadUrl(serverUrl);
    }

    void showNativeMenu() { NativeMenu.show(this); }

    void maybeAskNotificationPermission(int count) {
        if (count <= 0 || Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)) return;
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPTED, true).apply();
        new android.app.AlertDialog.Builder(this)
                .setTitle("Allow Kept reminders?")
                .setMessage("Kept can mirror pending time reminders as Android notifications, even after the app is closed.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Allow", (d, w) -> requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS))
                .show();
    }

    private void ensureReminderChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) KeptReminderReceiver.ensureChannel(nm);
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (shell != null && shell.onPermissionResult(req)) return;
        if (req == REQUEST_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notifications are off. Enable them in Android settings for Kept reminders.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (shell != null) shell.onActivityResult(req, result, data);
    }

    @Override protected void onSaveInstanceState(Bundle b) {
        if (webView != null) webView.saveState(b);
        super.onSaveInstanceState(b);
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) { webView.onResume(); ReminderWebSync.trigger(webView); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        share.consumeIntent(intent);
        if (webView != null && shell != null) webView.postDelayed(shell::dispatchShare, 250);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() { destroyWebView(); super.onDestroy(); }

    private void destroyWebView() {
        if (webView == null) return;
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView.stopLoading(); webView.loadUrl("about:blank"); webView.clearHistory(); webView.removeAllViews(); webView.destroy();
        webView = null; shell = null;
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}
