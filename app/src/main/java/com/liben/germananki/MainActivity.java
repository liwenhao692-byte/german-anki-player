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
                + "if(document.getElementById('nativeApkDot')) return;"
                + "var mode='',moved=false,sx=0,sy=0,ox=0,oy=0;"
                + "function word(){try{if(typeof allCards!=='undefined'&&typeof index!=='undefined'&&allCards[index])return allCards[index].front||allCards[index].word||'';}catch(e){}return '';}"
                + "function opts(){var o={deRepeat:2,zhRepeat:1,gapMs:500,speed:'0.9',deLang:'de',zhLang:'zh-CN'};try{if(typeof getNativeOptions==='function')o=getNativeOptions();}catch(e){}return o;}"
                + "function currentIndex(){try{if(typeof index!=='undefined')return index+1;}catch(e){}return 1;}"
                + "function flash(t){var x=document.getElementById('nativeApkToast');if(!x){x=document.createElement('div');x.id='nativeApkToast';x.style.cssText='position:fixed;left:50%;top:calc(12px + env(safe-area-inset-top));transform:translateX(-50%);z-index:1000000;max-width:72vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border-radius:999px;padding:7px 11px;background:rgba(18,14,10,.76);color:#fff4df;font-size:12px;font-weight:900;box-shadow:0 8px 20px rgba(0,0,0,.25);opacity:0;transition:.18s;pointer-events:none';document.body.appendChild(x);}x.textContent=t;x.style.opacity='.92';clearTimeout(x._tm);x._tm=setTimeout(function(){x.style.opacity='0'},1200);}"
                + "var box=document.createElement('div');box.id='nativeApkDot';box.style.cssText='position:fixed;right:6px;top:46%;z-index:999999;width:40px;display:grid;gap:4px;opacity:.72;touch-action:none';"
                + "function mk(t){var b=document.createElement('button');b.textContent=t;b.style.cssText='width:40px;height:32px;border:0;border-radius:999px;background:rgba(255,231,180,.90);color:#251a11;font-size:12px;font-weight:950;box-shadow:0 8px 18px rgba(0,0,0,.30);backdrop-filter:blur(10px);padding:0';return b;}"
                + "var read=mk('读'),spell=mk('拼');box.appendChild(read);box.appendChild(spell);"
                + "function paint(){read.textContent=mode==='read'?'停':'读';spell.textContent=mode==='spell'?'停':'拼';read.style.background=mode==='read'?'rgba(22,18,14,.78)':'rgba(255,231,180,.90)';read.style.color=mode==='read'?'#fff4df':'#251a11';spell.style.background=mode==='spell'?'rgba(22,18,14,.78)':'rgba(255,231,180,.90)';spell.style.color=mode==='spell'?'#fff4df':'#251a11';}"
                + "function start(kind){if(moved){moved=false;return;}if(mode===kind){NativePlayer.stop();mode='';paint();flash('已停止');return;}var o=opts();if(kind==='spell'){NativePlayer.startSpellWithOptions(currentIndex(),o.deRepeat,o.zhRepeat,o.gapMs,''+o.speed,o.deLang,o.zhLang);mode='spell';paint();flash('拼读：'+word());}else{NativePlayer.startWithOptions(currentIndex(),o.deRepeat,o.zhRepeat,o.gapMs,''+o.speed,o.deLang,o.zhLang);mode='read';paint();flash('朗读：'+word());}}"
                + "read.onclick=function(){start('read')};spell.onclick=function(){start('spell')};"
                + "box.ontouchstart=function(e){var t=e.touches[0];sx=t.clientX;sy=t.clientY;var r=box.getBoundingClientRect();ox=r.left;oy=r.top;moved=false;};"
                + "box.ontouchmove=function(e){var t=e.touches[0],dx=t.clientX-sx,dy=t.clientY-sy;if(Math.abs(dx)+Math.abs(dy)>5)moved=true;var x=Math.max(2,Math.min(innerWidth-42,ox+dx));var y=Math.max(40,Math.min(innerHeight-74,oy+dy));box.style.left=x+'px';box.style.top=y+'px';box.style.right='auto';};"
                + "box.ontouchend=function(){if(!moved)return;var r=box.getBoundingClientRect();if(r.left<innerWidth/2){box.style.left='6px';box.style.right='auto';}else{box.style.right='6px';box.style.left='auto';}};"
                + "document.body.appendChild(box);"
                + "})();";
        if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null); else webView.loadUrl("javascript:" + js);
    }

    private void startNativePlayback(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, float speed, String deLang, String zhLang, boolean spellMode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("deRepeat", deRepeat);
        intent.putExtra("zhRepeat", zhRepeat);
        intent.putExtra("startIndex", Math.max(0, startIndexOneBased - 1));
        intent.putExtra("gapMs", Math.max(0, gapMs));
        intent.putExtra("speed", speed);
        intent.putExtra("deLang", cleanLang(deLang, "de"));
        intent.putExtra("zhLang", cleanLang(zhLang, "zh-CN"));
        intent.putExtra("spellMode", spellMode);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, spellMode ? "已从当前单词开始拼读" : "已从当前单词开始朗读", Toast.LENGTH_SHORT).show();
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
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, 2, 1, 500, 0.9f, "de", "zh-CN", false));
        }
        @JavascriptInterface public void startWithOptions(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, String speed, String deLang, String zhLang) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, deRepeat, zhRepeat, gapMs, parseSpeed(speed), deLang, zhLang, false));
        }
        @JavascriptInterface public void startSpellWithOptions(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, String speed, String deLang, String zhLang) {
            runOnUiThread(() -> startNativePlayback(startIndexOneBased, deRepeat, zhRepeat, gapMs, parseSpeed(speed), deLang, zhLang, true));
        }
        @JavascriptInterface public void stop() { runOnUiThread(() -> sendCommand(PlaybackService.ACTION_STOP)); }
        @JavascriptInterface public void battery() { runOnUiThread(() -> openBatterySettings()); }
    }
}
