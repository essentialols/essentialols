package com.essentialols.keptandroid;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

final class WebShell {
    private final KeptActivity activity;
    private final WebView view;
    private final ShareState share;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    WebShell(KeptActivity activity, WebView view, ShareState share) {
        this.activity = activity; this.view = view; this.share = share;
    }

    void configure() {
        WebSettings s=view.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); s.setUserAgentString(s.getUserAgentString()+" KeptAndroidCommunity/0.4");
        if(Build.VERSION.SDK_INT>=21)s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);
        CookieManager cm=CookieManager.getInstance(); cm.setAcceptCookie(true); if(Build.VERSION.SDK_INT>=21)cm.setAcceptThirdPartyCookies(view,true);
        view.addJavascriptInterface(new NativeBridge(activity),"KeptNative");

        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){return navigate(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return navigate(Uri.parse(u));}
            @Override public void onPageFinished(WebView v,String u){
                super.onPageFinished(v,u); AndroidUiIntegration.install(activity,v); ReminderWebSync.install(v);
                v.postDelayed(WebShell.this::dispatchShare,450);
                if(share.consumeReminderOpen())Toast.makeText(activity,"Opened from a Kept reminder",Toast.LENGTH_SHORT).show();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e){
                super.onReceivedError(v,r,e);
                if(Build.VERSION.SDK_INT>=23&&r.isForMainFrame())Toast.makeText(activity,"Could not reach Kept. Check the server URL or connection.",Toast.LENGTH_LONG).show();
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e){h.cancel();Toast.makeText(activity,"Kept's HTTPS certificate is not trusted by Android.",Toast.LENGTH_LONG).show();}
        });

        view.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){
                if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=cb;
                if(share.hasUris()){ fileCallback.onReceiveValue(share.takeUris()); fileCallback=null; return true; }
                try{
                    Intent i=p!=null?p.createIntent():null;
                    if(i==null){i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");}
                    if(p!=null&&p.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE)i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                    activity.startActivityForResult(i,KeptActivity.REQUEST_FILES); return true;
                }catch(Exception e){fileCallback=null;Toast.makeText(activity,"No file picker is available.",Toast.LENGTH_SHORT).show();return false;}
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb){
                if(hasLocation()){cb.invoke(origin,true,false);return;}
                geoOrigin=origin; geoCallback=cb;
                if(Build.VERSION.SDK_INT>=23)activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},KeptActivity.REQUEST_LOCATION);
                else{cb.invoke(origin,true,false);clearGeo();}
            }
        });
        view.setDownloadListener((url,ua,cd,mime,len)->download(url,ua,cd,mime));
    }

    void dispatchShare(){String js=share.dispatchScript();if(js!=null)view.evaluateJavascript(js,null);}

    boolean onPermissionResult(int req){
        if(req!=KeptActivity.REQUEST_LOCATION||geoCallback==null)return false;
        geoCallback.invoke(geoOrigin,hasLocation(),false); clearGeo(); return true;
    }

    void onActivityResult(int req,int result,Intent data){
        if(req!=KeptActivity.REQUEST_FILES||fileCallback==null)return;
        Uri[] out=null;
        if(result==KeptActivity.RESULT_OK&&data!=null){ClipData c=data.getClipData();if(c!=null&&c.getItemCount()>0){out=new Uri[c.getItemCount()];for(int i=0;i<c.getItemCount();i++)out[i]=c.getItemAt(i).getUri();}else if(data.getData()!=null)out=new Uri[]{data.getData()};}
        fileCallback.onReceiveValue(out); fileCallback=null;
    }

    void openExternal(Uri uri){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){Toast.makeText(activity,"No app can open this link.",Toast.LENGTH_SHORT).show();}}

    private boolean navigate(Uri uri){
        if(uri==null||uri.getScheme()==null)return false;String scheme=uri.getScheme().toLowerCase(Locale.US);
        if(scheme.equals("http")||scheme.equals("https")){if(sameAuthority(Uri.parse(activity.serverUrl()),uri))return false;openExternal(uri);return true;}
        if(scheme.equals("blob")||scheme.equals("data")||scheme.equals("about"))return false;openExternal(uri);return true;
    }
    private boolean sameAuthority(Uri a,Uri b){return a!=null&&b!=null&&a.getHost()!=null&&b.getHost()!=null&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);}
    private int port(Uri u){return u.getPort()>=0?u.getPort():("https".equalsIgnoreCase(u.getScheme())?443:80);}
    private boolean hasLocation(){return Build.VERSION.SDK_INT<23||activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void clearGeo(){geoCallback=null;geoOrigin=null;}

    private void download(String url,String ua,String cd,String mime){
        try{
            String name=URLUtil.guessFileName(url,cd,mime);DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
            r.setTitle(name);r.setDescription("Downloading from Kept");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);if(mime!=null)r.setMimeType(mime);if(ua!=null)r.addRequestHeader("User-Agent",ua);
            String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)r.addRequestHeader("Cookie",cookie);
            ((DownloadManager)activity.getSystemService(KeptActivity.DOWNLOAD_SERVICE)).enqueue(r);Toast.makeText(activity,"Downloading "+name,Toast.LENGTH_SHORT).show();
        }catch(Exception e){openExternal(Uri.parse(url));}
    }
}
