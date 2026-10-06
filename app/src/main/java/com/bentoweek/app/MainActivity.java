package com.bentoweek.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Shows the Bento Week page. The page hands its schedule to the app through the BentoApp bridge. */
public class MainActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    /** Reload the page when the app comes back after this long, so the day and "now" are right. */
    private static final long STALE_MS = 2L * 60L * 1000L;

    private WebView web;
    private long loadedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Scheduler.ensureChannels(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(getApplicationContext()), "BentoApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    // No browser to open the link with; stay on the page.
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl(PAGE);
        loadedAt = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (web != null && now - loadedAt > STALE_MS) {
            web.reload();
            loadedAt = now;
        }
        Scheduler.scheduleNext(this);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Methods the page can call as window.BentoApp. */
    public static final class Bridge {
        private final Context context;

        Bridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void setSchedule(String json) {
            Scheduler.save(context, json);
        }

        @JavascriptInterface
        public String nextAlert() {
            return Scheduler.describeNext(context);
        }
    }
}
