package com.essentialols.keptandroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "kept_community";
    private static final String KEY_SERVER_URL = "server_url";
    private static final int REQUEST_FILES = 1001;
    private static final int REQUEST_LOCATION = 1002;

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView webView;
    private TextView toolbarTitle;
    private String serverUrl;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = new FrameLayout(this);
        setContentView(root);

        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            showSetup(null);
        } else {
            showBrowser(savedInstanceState);
        }
    }

    private void showSetup(String error) {
        destroyWebView();
        root.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(40), dp(28), dp(28));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Kept Community");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setPadding(0, 0, 0, dp(10));
        panel.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Connect this Android client to your self-hosted Kept server. Your notes stay on the server you choose.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(95, 99, 104));
        subtitle.setPadding(0, 0, 0, dp(24));
        panel.addView(subtitle);

        TextView label = new TextView(this);
        label.setText("Kept server URL");
        label.setTextSize(14);
        label.setTextColor(Color.rgb(60, 64, 67));
        panel.addView(label);

        final EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("https://kept.example.com");
        urlInput.setText(serverUrl == null ? "" : serverUrl);
        urlInput.setTextSize(16);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, dp(4), 0, dp(12));
        panel.addView(urlInput, inputParams);

        if (error != null && !error.isEmpty()) {
            TextView errorView = new TextView(this);
            errorView.setText(error);
            errorView.setTextColor(Color.rgb(176, 0, 32));
            errorView.setPadding(0, 0, 0, dp(10));
            panel.addView(errorView);
        }

        Button connect = new Button(this);
        connect.setText("Connect");
        connect.setAllCaps(false);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String normalized = normalizeServerUrl(urlInput.getText().toString());
                if (normalized == null) {
                    showSetup("Enter a valid http:// or https:// URL.");
                    return;
                }
                serverUrl = normalized;
                prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply();
                showBrowser(null);
            }
        });
        panel.addView(connect, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView privacy = new TextView(this);
        privacy.setText("This community port does not proxy your Kept traffic. The WebView connects directly to the URL above. Local-network HTTP is allowed for self-hosted instances; HTTPS is recommended outside your LAN.");
        privacy.setTextSize(13);
        privacy.setTextColor(Color.rgb(95, 99, 104));
        privacy.setPadding(0, dp(22), 0, 0);
        panel.addView(privacy);

        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showBrowser(Bundle savedInstanceState) {
        root.removeAllViews();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.rgb(250, 248, 242));
        root.addView(outer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), 0, dp(4), 0);
        toolbar.setBackgroundColor(Color.rgb(250, 248, 242));
        outer.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button back = toolbarButton("‹", "Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoBack()) webView.goBack();
            }
        });
        toolbar.addView(back);

        Button reload = toolbarButton("↻", "Reload");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (webView != null) webView.reload(); }
        });
        toolbar.addView(reload);

        toolbarTitle = new TextView(this);
        toolbarTitle.setText("Kept");
        toolbarTitle.setTextSize(16);
        toolbarTitle.setTextColor(Color.rgb(60, 64, 67));
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        titleParams.setMargins(dp(8), 0, dp(4), 0);
        toolbar.addView(toolbarTitle, titleParams);

        Button menu = toolbarButton("⋮", "More");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(v); }
        });
        toolbar.addView(menu);

        webView = new WebView(this);
        configureWebView(webView);
        outer.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        webView.loadUrl(serverUrl);
    }

    private Button toolbarButton(String text, String description) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(24);
        button.setAllCaps(false);
        button.setContentDescription(description);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(Color.rgb(60, 64, 67));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private void configureWebView(final WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " KeptAndroidCommunity/0.1");
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                if (toolbarTitle != null) {
                    String title = webView.getTitle();
                    toolbarTitle.setText(title == null || title.trim().isEmpty() ? "Kept" : title);
                }
            }

            @Override
            public void onReceivedError(WebView webView, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(webView, request, error);
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "Could not reach Kept. Check the server URL or connection.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReceivedSslError(WebView webView, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this,
                        "Kept's HTTPS certificate is not trusted by Android.",
                        Toast.LENGTH_LONG).show();
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params != null ? params.createIntent() : null;
                    if (intent == null) {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                    }
                    if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(intent, REQUEST_FILES);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                            GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                geolocationOrigin = origin;
                geolocationCallback = callback;
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, REQUEST_LOCATION);
                } else {
                    callback.invoke(origin, true, false);
                    clearGeolocationRequest();
                }
            }
        });

        view.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                enqueueDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.US);

        if (scheme.equals("http") || scheme.equals("https")) {
            Uri base = Uri.parse(serverUrl);
            if (sameAuthority(base, uri)) return false;
            openExternal(uri);
            return true;
        }
        if (scheme.equals("blob") || scheme.equals("data") || scheme.equals("about")) return false;
        openExternal(uri);
        return true;
    }

    private boolean sameAuthority(Uri a, Uri b) {
        if (a == null || b == null) return false;
        String ah = a.getHost();
        String bh = b.getHost();
        if (ah == null || bh == null || !ah.equalsIgnoreCase(bh)) return false;
        return effectivePort(a) == effectivePort(b);
    }

    private int effectivePort(Uri uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void enqueueDownload(String url, String userAgent,
                                 String contentDisposition, String mimetype) {
        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Downloading from Kept");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            if (mimetype != null) request.setMimeType(mimetype);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Downloading " + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    private void showMenu(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Open in browser");
        popup.getMenu().add("Copy page URL");
        popup.getMenu().add("Clear WebView cache");
        popup.getMenu().add("Change server");
        popup.getMenu().add("About");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Open in browser")) {
                String current = webView == null ? serverUrl : webView.getUrl();
                if (current != null) openExternal(Uri.parse(current));
            } else if (title.equals("Copy page URL")) {
                String current = webView == null ? serverUrl : webView.getUrl();
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Kept URL", current == null ? serverUrl : current));
                Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
            } else if (title.equals("Clear WebView cache")) {
                if (webView != null) webView.clearCache(true);
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
            } else if (title.equals("Change server")) {
                confirmChangeServer();
            } else if (title.equals("About")) {
                showAbout();
            }
            return true;
        });
        popup.show();
    }

    private void confirmChangeServer() {
        new AlertDialog.Builder(this)
                .setTitle("Change Kept server?")
                .setMessage("Your Kept data stays on the server. This only changes which instance the app opens.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Change", (dialog, which) -> showSetup(null))
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Kept Community 0.1")
                .setMessage("Community Android port based on the public ericerkz/kept web app. " +
                        "It is not the unpublished official Kept Android client.\n\n" +
                        "This first build focuses on the complete web feature surface plus a reliable Android shell. " +
                        "Native background geofencing and native Smart Capture are not yet implemented.")
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && geolocationCallback != null) {
            geolocationCallback.invoke(geolocationOrigin, hasLocationPermission(), false);
            clearGeolocationRequest();
        }
    }

    private void clearGeolocationRequest() {
        geolocationCallback = null;
        geolocationOrigin = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILES || fileCallback == null) return;

        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    result[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[] { data.getData() };
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
    }

    private void destroyWebView() {
        if (webView == null) return;
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
        webView.removeAllViews();
        webView.destroy();
        webView = null;
    }

    private String normalizeServerUrl(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        if (!raw.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) raw = "https://" + raw;
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) return null;
        String normalized = uri.toString();
        while (normalized.endsWith("/") && normalized.length() > scheme.length() + 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
