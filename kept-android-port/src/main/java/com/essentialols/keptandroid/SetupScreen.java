package com.essentialols.keptandroid;

import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class SetupScreen {
    interface OnConnect { void connect(String serverUrl); }
    private SetupScreen() {}

    static void show(KeptActivity activity, FrameLayout root, String current, String error, OnConnect callback) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int side = dp(activity, 28);
        panel.setPadding(side, dp(activity, 40), side, side);
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        TextView title = text(activity, "Kept Community", 30, Color.rgb(32,33,36));
        title.setPadding(0,0,0,dp(activity,10)); panel.addView(title);
        TextView subtitle = text(activity, "Connect to your self-hosted Kept server. Your notes stay on the server you choose.", 16, Color.rgb(95,99,104));
        subtitle.setPadding(0,0,0,dp(activity,24)); panel.addView(subtitle);
        panel.addView(text(activity, "Kept server URL", 14, Color.rgb(60,64,67)));

        EditText input = new EditText(activity);
        input.setSingleLine(true); input.setHint("https://kept.example.com"); input.setText(current == null ? "" : current);
        input.setTextSize(16); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1,-2); ip.setMargins(0,dp(activity,4),0,dp(activity,12)); panel.addView(input, ip);
        if (error != null && !error.trim().isEmpty()) { TextView e = text(activity,error,14,Color.rgb(176,0,32)); e.setPadding(0,0,0,dp(activity,10)); panel.addView(e); }

        Button connect = new Button(activity); connect.setText("Connect"); connect.setAllCaps(false);
        connect.setOnClickListener(v -> { String n = ServerUrl.normalize(input.getText().toString()); if (n == null) activity.showSetup("Enter a valid http:// or https:// URL."); else callback.connect(n); });
        panel.addView(connect, new LinearLayout.LayoutParams(-1,dp(activity,50)));
        TextView note = text(activity, "The app connects directly to this server. Local HTTP is allowed for private networks; use HTTPS for remote access.", 13, Color.rgb(95,99,104));
        note.setPadding(0,dp(activity,22),0,0); panel.addView(note);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static TextView text(KeptActivity a, String value, int sp, int color) { TextView v=new TextView(a); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v; }
    private static int dp(KeptActivity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
}
