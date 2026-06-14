package com.liben.germananki;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String ORIGINAL_HTML = "anki_german_b1_high_3000_speech_mode_fixed.html";
    private WebView webView;
    private BroadcastReceiver cardReceiver;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        registerCardReceiver();
        buildWebUi();
    }

    @Override protected void onDestroy() {
        try { if (cardReceiver != null) unregisterReceiver(cardReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void registerCardReceiver() {
        cardReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int id = intent.getIntExtra("cardId", 0);
                if (id > 0 && webView != null) runOnUiThread(() -> jumpToCard(id));
            }
        };
        IntentFilter filter = new IntentFilter(PlaybackService.ACTION_CARD_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(cardReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(cardReceiver, filter);
    }

    private void jumpToCard(int id) {
        String js = "try{window.nativeJumpToCardId&&window.nativeJumpToCardId(" + id + ");}catch(e){}";
        if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null); else webView.loadUrl("javascript:" + js);
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
        webView.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView view, String url) { injectNativeControls(); } });
        webView.addJavascriptInterface(new NativeBridge(), "NativePlayer");
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/" + ORIGINAL_HTML);
    }

    private void injectNativeControls() {
        try {
            String js = readAsset("native_controls.js");
            if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null); else webView.loadUrl("javascript:" + js);
        } catch (Exception e) {
            Toast.makeText(this, "设置面板加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String readAsset(String name) throws Exception {
        InputStream is = getAssets().open(name);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) bos.write(buffer, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }

    private void startNativePlayback(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, float speed, String deLang, String zhLang, boolean trainingMode, boolean randomReadMode, float wordSlowSpeed, float spellSpeed, float wordNormalSpeed, float phraseSpeed, float sentenceSpeed, float zhSpeed, int wordSlowCount, int spellCount, int wordNormalCount, int meaningCount, int phraseCount, int sentenceCount, int sentenceCnCount) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("deRepeat", deRepeat);
        intent.putExtra("zhRepeat", zhRepeat);
        intent.putExtra("startIndex", Math.max(0, startIndexOneBased - 1));
        intent.putExtra("gapMs", Math.max(0, gapMs));
        intent.putExtra("speed", speed);
        intent.putExtra("deLang", cleanLang(deLang, "de"));
        intent.putExtra("zhLang", cleanLang(zhLang, "zh-CN"));
        intent.putExtra("trainingMode", trainingMode);
        intent.putExtra("randomReadMode", randomReadMode);
        intent.putExtra("wordSlowSpeed", wordSlowSpeed);
        intent.putExtra("spellSpeed", spellSpeed);
        intent.putExtra("wordNormalSpeed", wordNormalSpeed);
        intent.putExtra("phraseSpeed", phraseSpeed);
        intent.putExtra("sentenceSpeed", sentenceSpeed);
        intent.putExtra("zhSpeed", zhSpeed);
        intent.putExtra("wordSlowCount", wordSlowCount);
        intent.putExtra("spellCount", spellCount);
        intent.putExtra("wordNormalCount", wordNormalCount);
        intent.putExtra("meaningCount", meaningCount);
        intent.putExtra("phraseCount", phraseCount);
        intent.putExtra("sentenceCount", sentenceCount);
        intent.putExtra("sentenceCnCount", sentenceCnCount);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, randomReadMode ? "已启动随机组合" : (trainingMode ? "已启动训练模式" : "已启动朗读"), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "已启动文本朗读", Toast.LENGTH_SHORT).show();
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
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private float parseSpeed(String value) {
        try { return Math.max(0.40f, Math.min(1.60f, Float.parseFloat(value.trim()))); }
        catch (Exception e) { return 0.9f; }
    }

    private String cleanLang(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    public class NativeBridge {
        @JavascriptInterface public void startWithOptions(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, String speed, String deLang, String zhLang) {
            float s = parseSpeed(speed);
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, deRepeat, zhRepeat, gapMs, s, deLang, zhLang, false, false, .62f, .72f, s, .62f, .62f, .78f, 3, 2, 3, 1, 1, 1, 1));
        }

        @JavascriptInterface public void startTrainingWithConfig(int startIndexOneBased, int gapMs, String deLang, String zhLang, String wordSlow, String spell, String wordNormal, String phrase, String sentence, String zh, int wordSlowCount, int spellCount, int wordNormalCount, int meaningCount, int phraseCount, int sentenceCount, int sentenceCnCount) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, 2, 1, gapMs, parseSpeed(wordNormal), deLang, zhLang, true, false, parseSpeed(wordSlow), parseSpeed(spell), parseSpeed(wordNormal), parseSpeed(phrase), parseSpeed(sentence), parseSpeed(zh), wordSlowCount, spellCount, wordNormalCount, meaningCount, phraseCount, sentenceCount, sentenceCnCount));
        }

        @JavascriptInterface public void startRandomTrainingWithConfig(int startIndexOneBased, int gapMs, String deLang, String zhLang, String wordSlow, String spell, String wordNormal, String phrase, String sentence, String zh, int wordSlowCount, int spellCount, int wordNormalCount, int meaningCount, int phraseCount, int sentenceCount, int sentenceCnCount) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, 2, 1, gapMs, parseSpeed(wordNormal), deLang, zhLang, true, true, parseSpeed(wordSlow), parseSpeed(spell), parseSpeed(wordNormal), parseSpeed(phrase), parseSpeed(sentence), parseSpeed(zh), wordSlowCount, spellCount, wordNormalCount, meaningCount, phraseCount, sentenceCount, sentenceCnCount));
        }

        @JavascriptInterface public void speakText(String text, String lang, String speed, int repeat, int gapMs) {
            runOnUiThread(() -> startFreeTextPlayback(text, lang, parseSpeed(speed), repeat, gapMs));
        }

        @JavascriptInterface public void stop() { runOnUiThread(() -> sendCommand(PlaybackService.ACTION_STOP)); }
        @JavascriptInterface public void battery() { runOnUiThread(() -> openBatterySettings()); }
    }
}
