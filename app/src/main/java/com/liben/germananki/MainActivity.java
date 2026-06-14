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

    @Override protected void onCreate(Bundle savedInstanceState) {
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
        webView.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView view, String url) { injectNativeControls(); } });
        webView.addJavascriptInterface(new NativeBridge(), "NativePlayer");
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/" + ORIGINAL_HTML);
    }

    private void injectNativeControls() {
        String js = "(function(){"
                + "if(document.getElementById('nativeGearOnly'))return;"
                + "var moved=false,sx=0,sy=0,ox=0,oy=0,playing=false;"
                + "var cfg={gap:500,sp:{wordSlow:.62,spell:.72,wordNormal:.90,phrase:.62,sentence:.62,zh:.78},ct:{wordSlow:3,spell:2,wordNormal:3,meaning:1,phrase:1,sentence:1,sentenceCn:1},txt:{text:'',lang:'de',speed:.90,repeat:1}};try{var s=JSON.parse(localStorage.nativeTrainConfig||'{}');if(s.sp)Object.assign(cfg.sp,s.sp);if(s.ct)Object.assign(cfg.ct,s.ct);if(s.txt)Object.assign(cfg.txt,s.txt);if(s.gap)cfg.gap=s.gap;}catch(e){}"
                + "function save(){localStorage.nativeTrainConfig=JSON.stringify(cfg);}"
                + "function currentCardObj(){try{if(typeof currentCard==='function'){var c=currentCard();if(c)return c;}if(typeof allCards!=='undefined'&&typeof index!=='undefined'&&allCards[index])return allCards[index];var names=['currentIndex','cardIndex','currentCardIndex'];for(var i=0;i<names.length;i++){var v=window[names[i]];if(typeof v==='number'&&allCards&&allCards[v])return allCards[v];}}catch(e){}return null;}"
                + "function currentId(){var c=currentCardObj();try{if(c&&c.id)return c.id;if(typeof index!=='undefined')return index+1;}catch(e){}return 1;}"
                + "function currentWord(){var c=currentCardObj();return c?(c.front||c.word||''):'';}"
                + "function opts(){var o={deLang:'de',zhLang:'zh-CN',speed:'0.9'};try{if(typeof getNativeOptions==='function')o=getNativeOptions();}catch(e){}return o;}"
                + "function flash(t){var x=document.getElementById('nativeApkToast');if(!x){x=document.createElement('div');x.id='nativeApkToast';x.style.cssText='position:fixed;left:50%;top:calc(12px + env(safe-area-inset-top));transform:translateX(-50%);z-index:1000000;max-width:76vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border-radius:999px;padding:7px 11px;background:rgba(18,14,10,.78);color:#fff4df;font-size:12px;font-weight:900;box-shadow:0 8px 20px rgba(0,0,0,.25);opacity:0;transition:.18s;pointer-events:none';document.body.appendChild(x);}x.textContent=t;x.style.opacity='.94';clearTimeout(x._tm);x._tm=setTimeout(function(){x.style.opacity='0'},1200);}"
                + "var gear=document.createElement('button');gear.id='nativeGearOnly';gear.textContent='⚙';gear.style.cssText='position:fixed;right:8px;top:48%;z-index:999999;width:38px;height:38px;border:0;border-radius:999px;background:rgba(255,231,180,.68);color:#251a11;font-size:20px;font-weight:950;box-shadow:0 8px 18px rgba(0,0,0,.26);backdrop-filter:blur(10px);opacity:.72;touch-action:none';document.body.appendChild(gear);"
                + "var cover=document.createElement('div');cover.id='nativeSettingsCover';cover.style.cssText='position:fixed;inset:0;z-index:1000001;background:rgba(0,0,0,.46);display:none;align-items:flex-end';"
                + "var sheet=document.createElement('div');sheet.style.cssText='width:100%;max-height:86vh;overflow:auto;border-radius:22px 22px 0 0;background:#17110d;color:#fff4df;padding:14px 14px calc(18px + env(safe-area-inset-bottom));box-shadow:0 -14px 36px rgba(0,0,0,.42);font-family:system-ui,-apple-system,BlinkMacSystemFont,sans-serif';cover.appendChild(sheet);document.body.appendChild(cover);"
                + "function btn(t){var b=document.createElement('button');b.textContent=t;b.style.cssText='height:38px;border:0;border-radius:14px;background:#ffe1a3;color:#251a11;font-size:14px;font-weight:950;padding:0 12px';return b;}"
                + "function row(title,key,type,min,max,step){var r=document.createElement('div');r.style.cssText='display:grid;grid-template-columns:1fr 42px 52px 42px;gap:7px;align-items:center;margin:8px 0';var lab=document.createElement('div');lab.textContent=title;lab.style.cssText='font-size:13px;font-weight:850;color:#fff0d0';var m=btn('-'),v=document.createElement('div'),p=btn('+');v.style.cssText='text-align:center;font-size:13px;font-weight:950;color:#ffe1a3';function draw(){v.textContent=(type==='sp'?cfg.sp[key].toFixed(2):cfg.ct[key]);}m.onclick=function(){if(type==='sp')cfg.sp[key]=Math.max(min,+(cfg.sp[key]-step).toFixed(2));else cfg.ct[key]=Math.max(min,cfg.ct[key]-step);save();draw();};p.onclick=function(){if(type==='sp')cfg.sp[key]=Math.min(max,+(cfg.sp[key]+step).toFixed(2));else cfg.ct[key]=Math.min(max,cfg.ct[key]+step);save();draw();};draw();r.appendChild(lab);r.appendChild(m);r.appendChild(v);r.appendChild(p);sheet.appendChild(r);}"
                + "function smallTitle(t){var x=document.createElement('div');x.textContent=t;x.style.cssText='font-size:15px;font-weight:1000;margin:16px 0 6px;color:#ffe1a3';sheet.appendChild(x);return x;}"
                + "function render(){sheet.innerHTML='';var head=document.createElement('div');head.style.cssText='display:flex;align-items:center;justify-content:space-between;margin-bottom:10px';var title=document.createElement('div');title.textContent='朗读设置';title.style.cssText='font-size:18px;font-weight:1000';var close=btn('关闭');close.onclick=function(){cover.style.display='none'};head.appendChild(title);head.appendChild(close);sheet.appendChild(head);var actions=document.createElement('div');actions.style.cssText='display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:10px 0 14px';var train=btn('完整训练');train.style.height='44px';train.onclick=function(){var o=opts();NativePlayer.startTrainingWithConfig(currentId(),cfg.gap,o.deLang,o.zhLang,''+cfg.sp.wordSlow,''+cfg.sp.spell,''+cfg.sp.wordNormal,''+cfg.sp.phrase,''+cfg.sp.sentence,''+cfg.sp.zh,cfg.ct.wordSlow,cfg.ct.spell,cfg.ct.wordNormal,cfg.ct.meaning,cfg.ct.phrase,cfg.ct.sentence,cfg.ct.sentenceCn);playing=true;flash('训练：'+currentWord());cover.style.display='none';};var spell=btn('只拼读');spell.style.height='44px';spell.onclick=function(){var o=opts();NativePlayer.startTrainingWithConfig(currentId(),cfg.gap,o.deLang,o.zhLang,''+cfg.sp.wordSlow,''+cfg.sp.spell,''+cfg.sp.wordNormal,''+cfg.sp.phrase,''+cfg.sp.sentence,''+cfg.sp.zh,0,Math.max(1,cfg.ct.spell),0,0,0,0,0);playing=true;flash('拼读：'+currentWord());cover.style.display='none';};var stop=btn('停止');stop.style.height='44px';stop.onclick=function(){NativePlayer.stop();playing=false;flash('已停止');cover.style.display='none';};actions.appendChild(train);actions.appendChild(spell);actions.appendChild(stop);sheet.appendChild(actions);var normal=btn('普通朗读当前卡片');normal.style.cssText+=';width:100%;height:42px;margin-bottom:10px';normal.onclick=function(){var o=opts();NativePlayer.startWithOptions(currentId(),2,1,cfg.gap,''+(o.speed||.9),o.deLang,o.zhLang);playing=true;flash('朗读：'+currentWord());cover.style.display='none';};sheet.appendChild(normal);smallTitle('输入文本朗读');var ta=document.createElement('textarea');ta.value=cfg.txt.text||'';ta.placeholder='在这里输入德语、中文或英文，点下面按钮朗读';ta.style.cssText='box-sizing:border-box;width:100%;height:96px;border:1px solid rgba(255,225,163,.25);border-radius:16px;background:#241a13;color:#fff4df;font-size:14px;line-height:1.45;padding:10px;outline:none;resize:vertical';ta.oninput=function(){cfg.txt.text=ta.value;save();};sheet.appendChild(ta);var textActions=document.createElement('div');textActions.style.cssText='display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:8px 0 12px';var readDe=btn('读德语');readDe.onclick=function(){var t=ta.value.trim();if(!t){flash('先输入文本');return;}cfg.txt.text=t;cfg.txt.lang='de';save();NativePlayer.speakText(t,'de',''+cfg.sp.wordNormal,Math.max(1,cfg.txt.repeat||1),cfg.gap);playing=true;flash('文本朗读');cover.style.display='none';};var readZh=btn('读中文');readZh.onclick=function(){var t=ta.value.trim();if(!t){flash('先输入文本');return;}cfg.txt.text=t;cfg.txt.lang='zh-CN';save();NativePlayer.speakText(t,'zh-CN',''+cfg.sp.zh,Math.max(1,cfg.txt.repeat||1),cfg.gap);playing=true;flash('中文朗读');cover.style.display='none';};var readEn=btn('读英文');readEn.onclick=function(){var t=ta.value.trim();if(!t){flash('先输入文本');return;}cfg.txt.text=t;cfg.txt.lang='en';save();NativePlayer.speakText(t,'en',''+cfg.sp.wordNormal,Math.max(1,cfg.txt.repeat||1),cfg.gap);playing=true;flash('英文朗读');cover.style.display='none';};textActions.appendChild(readDe);textActions.appendChild(readZh);textActions.appendChild(readEn);sheet.appendChild(textActions);smallTitle('速度');row('单词慢速','wordSlow','sp',.4,1.3,.05);row('拼读速度','spell','sp',.4,1.3,.05);row('单词正常','wordNormal','sp',.45,1.6,.05);row('词块速度','phrase','sp',.4,1.3,.05);row('句子速度','sentence','sp',.4,1.3,.05);row('中文速度','zh','sp',.45,1.5,.05);smallTitle('次数');row('单词慢速次数','wordSlow','ct',0,10,1);row('拼读次数','spell','ct',0,10,1);row('单词正常次数','wordNormal','ct',0,10,1);row('中文意思次数','meaning','ct',0,10,1);row('词块整体次数','phrase','ct',0,10,1);row('句子整体次数','sentence','ct',0,10,1);row('句子中文次数','sentenceCn','ct',0,10,1);}"
                + "gear.onclick=function(){if(moved){moved=false;return;}render();cover.style.display='flex';};"
                + "gear.ontouchstart=function(e){var t=e.touches[0],r=gear.getBoundingClientRect();sx=t.clientX;sy=t.clientY;ox=r.left;oy=r.top;moved=false;};gear.ontouchmove=function(e){var t=e.touches[0],dx=t.clientX-sx,dy=t.clientY-sy;if(Math.abs(dx)+Math.abs(dy)>5)moved=true;gear.style.left=Math.max(4,Math.min(innerWidth-42,ox+dx))+'px';gear.style.top=Math.max(40,Math.min(innerHeight-60,oy+dy))+'px';gear.style.right='auto';};"
                + "})();";
        if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null); else webView.loadUrl("javascript:" + js);
    }

    private void startNativePlayback(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, float speed, String deLang, String zhLang, boolean trainingMode, float wordSlowSpeed, float spellSpeed, float wordNormalSpeed, float phraseSpeed, float sentenceSpeed, float zhSpeed, int wordSlowCount, int spellCount, int wordNormalCount, int meaningCount, int phraseCount, int sentenceCount, int sentenceCnCount) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("deRepeat", deRepeat); intent.putExtra("zhRepeat", zhRepeat);
        intent.putExtra("startIndex", Math.max(0, startIndexOneBased - 1));
        intent.putExtra("gapMs", Math.max(0, gapMs)); intent.putExtra("speed", speed);
        intent.putExtra("deLang", cleanLang(deLang, "de")); intent.putExtra("zhLang", cleanLang(zhLang, "zh-CN"));
        intent.putExtra("trainingMode", trainingMode);
        intent.putExtra("wordSlowSpeed", wordSlowSpeed); intent.putExtra("spellSpeed", spellSpeed); intent.putExtra("wordNormalSpeed", wordNormalSpeed);
        intent.putExtra("phraseSpeed", phraseSpeed); intent.putExtra("sentenceSpeed", sentenceSpeed); intent.putExtra("zhSpeed", zhSpeed);
        intent.putExtra("wordSlowCount", wordSlowCount); intent.putExtra("spellCount", spellCount); intent.putExtra("wordNormalCount", wordNormalCount);
        intent.putExtra("meaningCount", meaningCount); intent.putExtra("phraseCount", phraseCount); intent.putExtra("sentenceCount", sentenceCount); intent.putExtra("sentenceCnCount", sentenceCnCount);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, trainingMode ? "已启动训练模式" : "已启动朗读", Toast.LENGTH_SHORT).show();
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

    private void sendCommand(String action) { Intent intent = new Intent(this, PlaybackService.class); intent.setAction(action); if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    private void openBatterySettings() { try { Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } }
    private float parseSpeed(String value) { try { return Math.max(0.40f, Math.min(1.60f, Float.parseFloat(value.trim()))); } catch (Exception e) { return 0.9f; } }
    private String cleanLang(String value, String fallback) { return value == null || value.trim().length() == 0 ? fallback : value.trim(); }

    public class NativeBridge {
        @JavascriptInterface public void startWithOptions(int startIndexOneBased, int deRepeat, int zhRepeat, int gapMs, String speed, String deLang, String zhLang) { float s=parseSpeed(speed); runOnUiThread(() -> startNativePlayback(startIndexOneBased, deRepeat, zhRepeat, gapMs, s, deLang, zhLang, false, .62f, .72f, s, .62f, .62f, .78f, 3, 2, 3, 1, 1, 1, 1)); }
        @JavascriptInterface public void startTrainingWithConfig(int startIndexOneBased, int gapMs, String deLang, String zhLang, String wordSlow, String spell, String wordNormal, String phrase, String sentence, String zh, int wordSlowCount, int spellCount, int wordNormalCount, int meaningCount, int phraseCount, int sentenceCount, int sentenceCnCount) { runOnUiThread(() -> startNativePlayback(startIndexOneBased, 2, 1, gapMs, parseSpeed(wordNormal), deLang, zhLang, true, parseSpeed(wordSlow), parseSpeed(spell), parseSpeed(wordNormal), parseSpeed(phrase), parseSpeed(sentence), parseSpeed(zh), wordSlowCount, spellCount, wordNormalCount, meaningCount, phraseCount, sentenceCount, sentenceCnCount)); }
        @JavascriptInterface public void speakText(String text, String lang, String speed, int repeat, int gapMs) { runOnUiThread(() -> startFreeTextPlayback(text, lang, parseSpeed(speed), repeat, gapMs)); }
        @JavascriptInterface public void stop() { runOnUiThread(() -> sendCommand(PlaybackService.ACTION_STOP)); }
        @JavascriptInterface public void battery() { runOnUiThread(() -> openBatterySettings()); }
    }
}
