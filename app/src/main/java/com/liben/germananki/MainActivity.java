package com.liben.germananki;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String ENTRY_HTML = "index.html";
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildWebUi();
    }

    private void buildWebUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 9, 20));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NativeBridge(), "NativePlayer");

        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/" + ENTRY_HTML);
    }

    private float parseSpeed(String value) {
        try { return Math.max(0.40f, Math.min(1.60f, Float.parseFloat(value.trim()))); }
        catch (Exception e) { return 0.9f; }
    }

    private String cleanLang(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private void startFreeTextPlayback(String text, String lang, float speed, int repeat, int gapMs) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("freeText", text == null ? "" : text);
        intent.putExtra("freeLang", cleanLang(lang, "de"));
        intent.putExtra("freeRepeat", Math.max(1, Math.min(50, repeat)));
        intent.putExtra("gapMs", Math.max(0, gapMs));
        intent.putExtra("speed", speed);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "已启动朗读", Toast.LENGTH_SHORT).show();
    }

    private void startBackgroundSequence(String sequenceJson) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("sequenceJson", sequenceJson == null ? "[]" : sequenceJson);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "已启动息屏播放", Toast.LENGTH_SHORT).show();
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class NativeBridge {
        @JavascriptInterface public void speakText(String text, String lang, String speed, int repeat, int gapMs) {
            runOnUiThread(() -> startFreeTextPlayback(text, lang, parseSpeed(speed), repeat, gapMs));
        }
        @JavascriptInterface public void playBackgroundSequence(String sequenceJson) {
            runOnUiThread(() -> startBackgroundSequence(sequenceJson));
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(() -> sendCommand(PlaybackService.ACTION_STOP));
        }
    }
}
