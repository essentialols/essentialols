package com.essentialols.keptandroid;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AndroidUiIntegration {
    private AndroidUiIntegration() {}

    static void install(Activity activity, WebView webView) {
        try {
            String css = read(activity, "kept-android.css");
            String script = read(activity, "kept-android.js");
            String bootstrap = "(function(){var st=document.getElementById('kept-community-android-css');" +
                    "if(!st){st=document.createElement('style');st.id='kept-community-android-css';document.head.appendChild(st);}" +
                    "st.textContent=" + JSONObject.quote(css) + ";})();" + script;
            webView.evaluateJavascript(bootstrap, null);
        } catch (Exception ignored) {}
    }

    static void systemBars(Activity activity, boolean light) {
        int color = light ? Color.WHITE : Color.rgb(32, 33, 36);
        setBars(activity, color, light, light);
    }

    static void noteBars(Activity activity, String cssColor) {
        int color;
        try { color = Color.parseColor(cssColor); }
        catch (Exception ignored) { color = Color.rgb(32, 33, 36); }
        // The official Kept Android editor uses white status icons over the
        // note color, while the bottom gesture/navigation glyph remains dark
        // on the light Keep-style palette.
        setBars(activity, color, false, true);
    }

    private static void setBars(Activity activity, int color, boolean darkStatusIcons, boolean darkNavIcons) {
        activity.getWindow().setStatusBarColor(color);
        activity.getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= 23) {
            View decor = activity.getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (darkStatusIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (darkNavIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private static String read(Activity activity, String name) throws Exception {
        try (InputStream in = activity.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
