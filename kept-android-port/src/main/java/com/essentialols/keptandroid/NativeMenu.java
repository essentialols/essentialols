package com.essentialols.keptandroid;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.widget.Toast;

final class NativeMenu {
    private NativeMenu() {}

    static void show(KeptActivity activity) {
        String[] items={"Reload Kept","Sync reminders now","Open in browser","Copy page URL","Clear WebView cache","Change server","About"};
        new AlertDialog.Builder(activity).setTitle("Kept app").setItems(items,(d,w)->{
            String x=items[w]; WebView web=activity.webView();
            if(x.equals("Reload Kept")){if(web!=null)web.reload();}
            else if(x.equals("Sync reminders now")){ReminderWebSync.trigger(web);Toast.makeText(activity,"Reminder sync requested",Toast.LENGTH_SHORT).show();}
            else if(x.equals("Open in browser")){String u=web==null?activity.serverUrl():web.getUrl();if(u!=null)open(activity,u);}
            else if(x.equals("Copy page URL")){String u=web==null?activity.serverUrl():web.getUrl();ClipboardManager cm=(ClipboardManager)activity.getSystemService(KeptActivity.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Kept URL",u==null?activity.serverUrl():u));Toast.makeText(activity,"URL copied",Toast.LENGTH_SHORT).show();}
            else if(x.equals("Clear WebView cache")){if(web!=null)web.clearCache(true);Toast.makeText(activity,"Cache cleared",Toast.LENGTH_SHORT).show();}
            else if(x.equals("Change server"))confirmChange(activity); else if(x.equals("About"))about(activity);
        }).setNegativeButton("Cancel",null).show();
    }

    private static void open(KeptActivity a,String url){try{a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));}catch(Exception e){Toast.makeText(a,"No app can open this link.",Toast.LENGTH_SHORT).show();}}
    private static void confirmChange(KeptActivity a){new AlertDialog.Builder(a).setTitle("Change Kept server?").setMessage("Your data stays on the server. This only changes which instance the app opens.").setNegativeButton("Cancel",null).setPositiveButton("Change",(d,w)->a.showSetup(null)).show();}
    private static void about(KeptActivity a){new AlertDialog.Builder(a).setTitle("Kept Community 0.3").setMessage("v0.3 removes duplicate browser chrome, improves compact/medium/landscape layouts and touch targets, follows Kept's theme in Android system bars, and fixes authenticated native reminder sync. v0.2 sharing and time reminders remain included.\n\nThis is a community port based on public ericerkz/kept source, not the unpublished official Android client.").setPositiveButton("OK",null).show();}
}
