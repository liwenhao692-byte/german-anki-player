package com.liben.germananki;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String ORIGINAL_HTML = "anki_german_b1_high_3000_speech_mode_fixed.html";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildWebUi();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void buildWebUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 13, 10));

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
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { injectNativeControls(); }
        });
        webView.addJavascriptInterface(new NativeBridge(), "NativePlayer");

        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/" + ORIGINAL_HTML);
    }

    private void injectNativeControls() {
        String js = "(function(){"
                + "if(document.getElementById('nativeApkBar')) return;"
                + "var bar=document.createElement('div');bar.id='nativeApkBar';"
                + "bar.style.cssText='position:fixed;right:8px;top:calc(92px + env(safe-area-inset-top));z-index:999999;display:flex;flex-direction:column;gap:6px;pointer-events:none';"
                + "function btn(t,bg,color){var x=document.createElement(\"button\");x.textContent=t;x.style.cssText='width:58px;height:34px;border:0;border-radius:999px;padding:0;font-weight:900;font-size:12px;letter-spacing:-.05em;box-shadow:0 8px 18px rgba(0,0,0,.28);opacity:.88;backdrop-filter:blur(10px);pointer-events:auto;background:'+bg+';color:'+color;return x;}"
                + "var start=btn('息屏','linear-gradient(135deg,#fff2d0,#ffc76f)','#24170e');var stop=btn('停止','rgba(20,16,12,.70)','#fff4df');"
                + "start.onclick=function(){var i=1,o={deRepeat:2,zhRepeat:1,gapMs:500,speed:'0.9',deLang:'de',zhLang:'zh-CN'};try{if(typeof index!==\"undefined\")i=index+1;if(typeof getNativeOptions===\"function\")o=getNativeOptions();}catch(e){} NativePlayer.startWithOptions(i,o.deRepeat,o.zhRepeat,o.gapMs,''+o.speed,o.deLang,o.zhLang);};"
                + "stop.onclick=function(){NativePlayer.stop();};bar.appendChild(start);bar.appendChild(stop);document.body.appendChild(bar);"
                + "})();";
        if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null); else webView.loadUrl("javascript:" + js);
    }

    private void startNativePlayback(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, float speed, String deLang, String zhLang) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("deRepeat", deRepeat);
        intent.putExtra("zhRepeat", zhRepeat);
        intent.putExtra("startIndex", Math.max(0, startIndexOneBased - 1));
        intent.putExtra("gapMs", Math.max(0, gapMs));
        intent.putExtra("speed", speed);
        intent.putExtra("deLang", cleanLang(deLang, "de"));
        intent.putExtra("zhLang", cleanLang(zhLang, "zh-CN"));
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "已启动原声息屏播放", Toast.LENGTH_SHORT).show();
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private float parseSpeed(String value) {
        try { return Math.max(0.55f, Math.min(1.6f, Float.parseFloat(value.trim()))); } catch (Exception e) { return 0.9f; }
    }

    private String cleanLang(String value, String fallback) {
        if (value == null || value.trim().length() == 0) return fallback;
        return value.trim();
    }

    public class NativeBridge {
        @JavascriptInterface public void start(int startIndexOneBased) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, 2, 1, 500, 0.9f, "de", "zh-CN"));
        }
        @JavascriptInterface public void startWithOptions(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, String speed, String deLang, String zhLang) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, deRepeat, zhRepeat, gapMs, parseSpeed(speed), deLang, zhLang));
        }
        @JavascriptInterface public void stop() { runOnUiThread(() -> sendCommand(PlaybackService.ACTION_STOP)); }
        @JavascriptInterface public void battery() { runOnUiThread(() -> openBatterySettings()); }
    }
}
