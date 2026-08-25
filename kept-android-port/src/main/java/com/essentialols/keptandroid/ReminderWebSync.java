package com.essentialols.keptandroid;

import android.webkit.WebView;

final class ReminderWebSync {
    private ReminderWebSync() {}

    static void install(WebView view) {
        String js = "(function(){if(window.__keptNativeReminderSync)return;window.__keptNativeReminderSync=true;" +
                "function token(){try{var s=JSON.parse(localStorage.getItem('gk_session')||'{}');return s.token||'';}catch(e){return '';}}" +
                "window.__keptSyncReminders=async function(){try{var t=token();if(!t)return;var r=await fetch('/api/reminders',{headers:{'Accept':'application/json','Authorization':'Bearer '+t}});" +
                "if(!r.ok)return;var j=await r.json();if(window.KeptNative)KeptNative.syncReminders(JSON.stringify(j));}catch(e){}};" +
                "window.__keptSyncReminders();setInterval(window.__keptSyncReminders,60000);" +
                "document.addEventListener('visibilitychange',function(){if(!document.hidden)window.__keptSyncReminders();});})();";
        view.evaluateJavascript(js, null);
    }

    static void trigger(WebView view) {
        if (view != null) view.evaluateJavascript("window.__keptSyncReminders&&window.__keptSyncReminders();", null);
    }
}
