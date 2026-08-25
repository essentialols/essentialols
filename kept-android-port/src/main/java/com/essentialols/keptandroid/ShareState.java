package com.essentialols.keptandroid;

import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

final class ShareState {
    private String text;
    private String mime;
    private final ArrayList<Uri> uris = new ArrayList<>();
    private boolean openFromReminder;

    void consumeIntent(Intent intent) {
        if (intent == null) return;
        openFromReminder |= intent.getBooleanExtra("open_from_reminder", false);
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            mime = intent.getType();
            CharSequence body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
            if (body != null || subject != null) text = combine(subject, body);
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) uris.add(stream);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            mime = intent.getType();
            CharSequence body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (body != null) text = body.toString();
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) uris.addAll(streams);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            mime = "text/plain";
            CharSequence body = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (body != null) text = body.toString();
        }
    }

    boolean hasPending() { return text != null || !uris.isEmpty(); }
    boolean consumeReminderOpen() { boolean result = openFromReminder; openFromReminder = false; return result; }
    boolean hasUris() { return !uris.isEmpty(); }

    Uri[] takeUris() {
        Uri[] result = uris.toArray(new Uri[0]);
        uris.clear();
        return result;
    }

    void consumeText() { text = null; }

    String dispatchScript() {
        if (!hasPending()) return null;
        String body = text == null ? "" : text;
        boolean files = !uris.isEmpty();
        boolean images = mime != null && mime.toLowerCase(Locale.US).startsWith("image/");
        String quoted = JSONObject.quote(body);
        return "(function(){var p=document.querySelector('.placeholder');if(!p)return 'no-placeholder';p.click();" +
                "setTimeout(function(){" +
                (body.isEmpty() ? "" : "var b=document.querySelector('.note-body[contenteditable=\\\"true\\\"]');if(b){b.focus();b.textContent=" + quoted + ";b.dispatchEvent(new Event('input',{bubbles:true}));}") +
                (files ? "setTimeout(function(){var f=document.querySelector('" + (images ? ".image-input" : ".attachment-input") + "');if(f)f.click();},180);" : "") +
                "if(window.KeptNative)KeptNative.shareTextConsumed();},220);return 'queued';})();";
    }

    private static String combine(CharSequence subject, CharSequence body) {
        String s = subject == null ? "" : subject.toString().trim();
        String b = body == null ? "" : body.toString().trim();
        if (s.isEmpty()) return b;
        if (b.isEmpty() || b.equals(s)) return s;
        return s + "\n\n" + b;
    }
}
