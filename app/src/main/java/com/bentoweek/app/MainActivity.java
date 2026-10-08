package com.bentoweek.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Shows the Bento Week page. The page hands its schedule to the app through the BentoApp bridge. */
public class MainActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    /** Reload the page when the app comes back after this long, so the day and "now" are right. */
    private static final long STALE_MS = 2L * 60L * 1000L;

    private static final int PICK_PHOTO = 42;

    private WebView web;
    private long loadedAt;
    /** The page's pending photo request, answered when the gallery returns. */
    private ValueCallback<Uri[]> photoCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Scheduler.ensureChannels(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
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
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (photoCallback != null) photoCallback.onReceiveValue(null);
                photoCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_PHOTO);
                } catch (Exception e) {
                    photoCallback = null;
                    return false;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && photoCallback != null) {
            photoCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            photoCallback = null;
        }
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
