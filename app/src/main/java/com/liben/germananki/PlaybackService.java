package com.liben.germananki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class PlaybackService extends Service {
    public static final String ACTION_START = "com.liben.germananki.START";
    public static final String ACTION_STOP = "com.liben.germananki.STOP";
    public static final String ACTION_PAUSE = "com.liben.germananki.PAUSE";
    public static final String ACTION_RESUME = "com.liben.germananki.RESUME";
    public static final String ACTION_NEXT = "com.liben.germananki.NEXT";
    public static final String ACTION_PREV = "com.liben.germananki.PREV";
    public static final String ACTION_CARD_CHANGED = "com.liben.germananki.CARD_CHANGED";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "german_anki_media";
    private static final String ORIGINAL_HTML = "anki_german_b1_high_3000_speech_mode_fixed.html";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Card> cards = new ArrayList<>();
    private final List<Segment> plan = new ArrayList<>();
    private final Map<String, String> nounArticles = new HashMap<>();
    private final Set<String> downloading = Collections.synchronizedSet(new HashSet<>());
    private final Random random = new Random();

    private MediaPlayer player;
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private File cacheDir;
    private boolean foregroundStarted, paused, playing, trainingMode, randomReadMode, freeTextMode;
    private String freeTextTitle = "输入文本";
    private int cardIndex, preparedNextIndex = -1, segmentIndex, builtCardIndex = -1, playbackToken;
    private boolean builtTrainingMode;
    private int deRepeat = 2, zhRepeat = 1, gapMs = 500, retryCount;
    private int wordSlowCount = 3, spellCount = 2, wordNormalCount = 3, meaningCount = 1, phraseCount = 1, sentenceCount = 1, sentenceCnCount = 1;
    private float speed = .9f, activeSpeed = .9f, wordSlowSpeed = .62f, spellSpeed = .72f, wordNormalSpeed = .9f, phraseSpeed = .62f, sentenceSpeed = .62f, zhSpeed = .78f;
    private String deLang = "de", zhLang = "zh-CN";

    @Override public void onCreate() {
        super.onCreate();
        cacheDir = new File(getCacheDir(), "online_tts_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        createChannel();
        createMediaSession();
        createWakeLock();
        createWifiLock();
        loadCardsIfNeeded();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (action == null) action = ACTION_START;
        ensureForeground("德语 Anki 原声播放器", "准备播放");
        if (ACTION_START.equals(action)) startFromIntent(intent);
        else if (ACTION_PAUSE.equals(action)) pausePlayback();
        else if (ACTION_RESUME.equals(action)) resumePlayback();
        else if (ACTION_NEXT.equals(action)) nextCard();
        else if (ACTION_PREV.equals(action)) prevCard();
        else if (ACTION_STOP.equals(action)) stopPlayback();
        return START_STICKY;
    }

    private void startFromIntent(Intent intent) {
        gapMs = Math.max(0, intent.getIntExtra("gapMs", 500));
        speed = clamp(intent.getFloatExtra("speed", .9f), .45f, 1.6f);
        activeSpeed = speed;
        String freeText = intent.getStringExtra("freeText");
        if (freeText != null && freeText.trim().length() > 0) {
            startFreeTextPlan(freeText.trim(), cleanLang(intent.getStringExtra("freeLang"), "de"), clampInt(intent.getIntExtra("freeRepeat", 1), 1, 50));
            return;
        }
        freeTextMode = false;
        randomReadMode = intent.getBooleanExtra("randomReadMode", false);
        deRepeat = Math.max(0, intent.getIntExtra("deRepeat", 2));
        zhRepeat = Math.max(0, intent.getIntExtra("zhRepeat", 1));
        wordSlowSpeed = clamp(intent.getFloatExtra("wordSlowSpeed", .62f), .4f, 1.3f);
        spellSpeed = clamp(intent.getFloatExtra("spellSpeed", .72f), .4f, 1.3f);
        wordNormalSpeed = clamp(intent.getFloatExtra("wordNormalSpeed", speed), .45f, 1.6f);
        phraseSpeed = clamp(intent.getFloatExtra("phraseSpeed", .62f), .4f, 1.3f);
        sentenceSpeed = clamp(intent.getFloatExtra("sentenceSpeed", .62f), .4f, 1.3f);
        zhSpeed = clamp(intent.getFloatExtra("zhSpeed", .78f), .45f, 1.5f);
        wordSlowCount = clampInt(intent.getIntExtra("wordSlowCount", 3), 0, 20);
        spellCount = clampInt(intent.getIntExtra("spellCount", 2), 0, 20);
        wordNormalCount = clampInt(intent.getIntExtra("wordNormalCount", 3), 0, 20);
        meaningCount = clampInt(intent.getIntExtra("meaningCount", 1), 0, 20);
        phraseCount = clampInt(intent.getIntExtra("phraseCount", 1), 0, 20);
        sentenceCount = clampInt(intent.getIntExtra("sentenceCount", 1), 0, 20);
        sentenceCnCount = clampInt(intent.getIntExtra("sentenceCnCount", 1), 0, 20);
        deLang = cleanLang(intent.getStringExtra("deLang"), "de");
        zhLang = cleanLang(intent.getStringExtra("zhLang"), "zh-CN");
        trainingMode = intent.getBooleanExtra("trainingMode", false);
        loadCardsIfNeeded();
        cardIndex = Math.max(0, Math.min(intent.getIntExtra("startIndex", 0), Math.max(0, cards.size() - 1)));
        preparedNextIndex = -1;
        segmentIndex = 0;
        clearPlan();
        retryCount = 0;
        paused = false;
        playing = true;
        playbackToken++;
        acquireLocks();
        playCurrentSegment();
    }

    private void startFreeTextPlan(String text, String lang, int repeat) {
        freeTextMode = true;
        randomReadMode = false;
        trainingMode = false;
        preparedNextIndex = -1;
        retryCount = 0;
        paused = false;
        playing = true;
        playbackToken++;
        segmentIndex = 0;
        clearPlan();
        freeTextTitle = text.length() > 28 ? text.substring(0, 28) + "…" : text;
        for (int i = 0; i < repeat; i++) addSeg(text, lang, "输入文本朗读", speed);
        acquireLocks();
        prefetchAround();
        playCurrentSegment();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        releasePlayer();
        handler.removeCallbacksAndMessages(null);
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        releaseLocks();
        super.onDestroy();
    }

    private void loadCardsIfNeeded() {
        if (!cards.isEmpty()) return;
        loadCardsFromOriginalHtml();
        applyManualPatch();
        buildNounArticles();
    }

    private void loadCardsFromOriginalHtml() {
        try {
            String html = readAsset(ORIGINAL_HTML);
            int marker = html.indexOf("const allCards");
            if (marker < 0) marker = html.indexOf("allCards");
            int start = html.indexOf('[', marker), end = html.indexOf("];", start);
            JSONArray arr = new JSONArray(html.substring(start, end + 1));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                cards.add(new Card(o.optInt("id", i + 1), o.optString("front", ""), o.optString("meaning", ""), o.optString("deck", ""), o.optString("pos", ""), o.optString("example", ""), o.optString("exampleCn", ""), o.optString("notes", "")));
            }
        } catch (Exception ignored) { cards.clear(); }
    }

    private void applyManualPatch() {
        try {
            String js = readPatchJs();
            int start = js.indexOf("var p="); if (start < 0) return;
            start += 6;
            int end = js.indexOf("];var m=", start); if (end < 0) return;
            JSONArray arr = new JSONArray(js.substring(start, end + 1));
            Map<Integer, Card> map = new HashMap<>();
            for (Card c : cards) map.put(c.id, c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                Card c = map.get(o.optInt("id")); if (c == null) continue;
                if (o.has("front")) c.front = o.optString("front", c.front);
                if (o.has("meaning")) c.meaning = o.optString("meaning", c.meaning);
                if (o.has("example")) c.example = o.optString("example", c.example);
                if (o.has("exampleCn")) c.exampleCn = o.optString("exampleCn", c.exampleCn);
                if (o.has("notes")) c.notes = o.optString("notes", c.notes);
            }
        } catch (Exception ignored) {}
    }

    private String readPatchJs() throws Exception {
        StringBuilder b64 = new StringBuilder();
        for (int i = 0; i <= 8; i++) b64.append(readAsset(String.format("manual_patch_%02d.b64", i)));
        byte[] gz = Base64.decode(b64.toString(), Base64.DEFAULT);
        return readStream(new GZIPInputStream(new ByteArrayInputStream(gz)));
    }

    private String readAsset(String name) throws Exception { return readStream(getAssets().open(name)); }
    private String readStream(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n); is.close(); return bos.toString("UTF-8");
    }

    private void buildNounArticles() {
        nounArticles.clear();
        for (Card c : cards) {
            List<String> w = wordsFrom(c.front);
            if (w.size() >= 2) {
                String a = w.get(0).toLowerCase();
                if (a.equals("der") || a.equals("die") || a.equals("das")) nounArticles.put(w.get(1).toLowerCase(), a);
            }
        }
    }

    private void playCurrentSegment() {
        if (!playing || paused) return;
        Segment s = freeTextMode ? nextFreeTextSegment() : nextPlayableSegment();
        if (s == null) { playing = false; releaseLocks(); updateNotification("播放结束", freeTextMode ? freeTextTitle : currentText()); return; }
        activeSpeed = s.speed;
        if (!freeTextMode && !cards.isEmpty()) notifyCardChanged(cards.get(cardIndex).id);
        updateNotification(s.phase, freeTextMode ? freeTextTitle : currentText());
        updatePlaybackState(false);
        prefetchAround();
        playSegment(s, playbackToken);
    }

    private Segment nextFreeTextSegment() { return segmentIndex >= plan.size() ? null : plan.get(segmentIndex); }

    private Segment nextPlayableSegment() {
        int guard = 0;
        while (guard++ < 80) {
            if (cardIndex < 0 || cardIndex >= cards.size()) cardIndex = 0;
            ensurePlan(cards.get(cardIndex));
            if (segmentIndex >= plan.size()) { segmentIndex = 0; clearPlan(); cardIndex = nextAutomaticCardIndex(); continue; }
            Segment s = plan.get(segmentIndex); if (s.text.trim().length() > 0) return s; segmentIndex++;
        }
        return null;
    }

    private int nextAutomaticCardIndex() {
        if (cards.size() <= 1) return 0;
        if (preparedNextIndex >= 0 && preparedNextIndex < cards.size() && preparedNextIndex != cardIndex) { int n = preparedNextIndex; preparedNextIndex = -1; return n; }
        if (!randomReadMode) return (cardIndex + 1) % cards.size();
        int n = random.nextInt(cards.size()); return n == cardIndex ? (n + 1) % cards.size() : n;
    }

    private void ensurePlan(Card c) {
        if (builtCardIndex == cardIndex && builtTrainingMode == trainingMode && !plan.isEmpty()) return;
        plan.clear(); plan.addAll(makePlan(c, trainingMode)); builtCardIndex = cardIndex; builtTrainingMode = trainingMode;
        if (plan.isEmpty()) addSeg(spokenWord(c.front), deLang, "德语原声", speed);
    }

    private List<Segment> makePlan(Card c, boolean full) {
        List<Segment> out = new ArrayList<>();
        String front = cleanGerman(c.front), word = spokenWord(front), cn = cleanChinese(c.meaning);
        if (!full) {
            for (int i = 0; i < deRepeat; i++) add(out, word, deLang, "德语朗读", speed);
            for (int i = 0; i < Math.max(1, spellCount); i++) add(out, spellGerman(spellBase(front)), deLang, "拼读", spellSpeed);
            for (int i = 0; i < zhRepeat; i++) add(out, cn, zhLang, "中文意思", zhSpeed);
            return out;
        }
        for (int i = 0; i < wordSlowCount; i++) add(out, word, deLang, "慢速单词", wordSlowSpeed);
        for (int i = 0; i < spellCount; i++) add(out, spellGerman(spellBase(front)), deLang, "单词拼读", spellSpeed);
        for (int i = 0; i < wordNormalCount; i++) add(out, word, deLang, "正常单词", wordNormalSpeed);
        for (int i = 0; i < meaningCount; i++) add(out, cn, zhLang, "中文意思", zhSpeed);
        Related r = randomRelatedFor(c); String phrase = cleanGerman(r.phrase), sentence = cleanGerman(r.example), sentenceCn = cleanChinese(r.exampleCn);
        if (phrase.length() > 0) { addWordSpell(out, phrase); for (int i = 0; i < phraseCount; i++) add(out, phrase, deLang, "慢速词块", phraseSpeed); }
        if (sentence.length() > 0) { addWordSpell(out, sentence); for (int i = 0; i < sentenceCount; i++) add(out, sentence, deLang, "慢速句子", sentenceSpeed); }
        for (int i = 0; i < sentenceCnCount; i++) add(out, sentenceCn, zhLang, "句子中文", zhSpeed);
        return out;
    }

    private void addWordSpell(List<Segment> out, String text) {
        for (String w : wordsFrom(text)) if (w.length() >= 2 && !isStopWord(w.toLowerCase())) { add(out, spokenWord(w), deLang, "拆词", wordSlowSpeed); add(out, spellGerman(spellBase(w)), deLang, "拼读", spellSpeed); }
    }
    private void add(List<Segment> out, String t, String l, String p, float s) { if (t != null && t.trim().length() > 0) out.add(new Segment(t.trim(), l, p, s)); }
    private void addSeg(String t, String l, String p, float s) { if (t != null && t.trim().length() > 0) plan.add(new Segment(t.trim(), l, p, s)); }

    private Related randomRelatedFor(Card card) {
        String front = cleanGerman(card.front);
        if (front.length() == 0) return new Related("", card.example, card.exampleCn);
        if (!isSingleWordCard(card, front)) return new Related(front, card.example, card.exampleCn);
        String token = keyToken(front); if (token.length() == 0) return new Related("", card.example, card.exampleCn);
        List<Card> pool = relatedCandidates(card, token, true); if (pool.isEmpty()) pool = relatedCandidates(card, token, false);
        if (!pool.isEmpty()) { Card p = pool.get(random.nextInt(pool.size())); return new Related(cleanGerman(p.front), p.example, p.exampleCn); }
        return new Related("", card.example, card.exampleCn);
    }

    private List<Card> relatedCandidates(Card card, String token, boolean sameDeckOnly) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) if (c != card && (!sameDeckOnly || safeEq(c.deck, card.deck)) && isPhraseLike(c) && (containsToken(c.front, token) || containsToken(c.example, token))) out.add(c);
        return out;
    }

    private void playSegment(Segment s, int token) {
        String url = ttsUrl(s.text, s.lang); File f = cachedFileFor(url);
        if (f.exists() && f.length() > 0) { playFile(f, token); return; }
        new Thread(() -> { File d = downloadToCache(url, true); handler.post(() -> { if (token != playbackToken || !playing || paused) return; if (d != null && d.exists() && d.length() > 0) playFile(d, token); else playUrlDirect(url, token); }); }, "tts-current-download").start();
    }

    private void playFile(File file, int token) { releasePlayer(); try { player = newPlayer(token); player.setDataSource(file.getAbsolutePath()); player.prepareAsync(); } catch (Exception e) { advanceSegment(); } }
    private void playUrlDirect(String url, int token) { releasePlayer(); try { player = newPlayer(token); Map<String,String> h = new HashMap<>(); h.put("User-Agent","Mozilla/5.0 Android GermanAnkiPlayer"); h.put("Referer","https://translate.google.com/"); player.setDataSource(this, Uri.parse(url), h); player.prepareAsync(); } catch (Exception e) { advanceSegment(); } }

    private MediaPlayer newPlayer(int token) {
        MediaPlayer mp = new MediaPlayer();
        try { mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 21) mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        mp.setOnPreparedListener(p -> { if (token != playbackToken || !playing || paused) return; applySpeed(p); retryCount = 0; p.start(); });
        mp.setOnCompletionListener(p -> advanceSegment());
        mp.setOnErrorListener((p,w,e) -> { if (retryCount < 2 && playing && !paused && token == playbackToken) { retryCount++; handler.postDelayed(this::playCurrentSegment, 500); } else advanceSegment(); return true; });
        return mp;
    }

    private void applySpeed(MediaPlayer mp) { try { if (Build.VERSION.SDK_INT >= 23) { PlaybackParams p = mp.getPlaybackParams(); p.setSpeed(activeSpeed); mp.setPlaybackParams(p); } } catch (Exception ignored) {} }

    private void prefetchAround() {
        final int token = playbackToken; List<Segment> list = new ArrayList<>();
        for (int i = segmentIndex + 1; i < Math.min(plan.size(), segmentIndex + 11); i++) list.add(plan.get(i));
        if (!freeTextMode && !cards.isEmpty()) { int n = ensurePreparedNextIndex(); if (n >= 0 && n < cards.size()) list.addAll(makePlan(cards.get(n), trainingMode)); }
        if (list.isEmpty()) return;
        new Thread(() -> { for (Segment s : list) { if (token != playbackToken || !playing) return; downloadToCache(ttsUrl(s.text, s.lang), false); } }, "tts-prefetch").start();
    }

    private int ensurePreparedNextIndex() { if (preparedNextIndex >= 0 && preparedNextIndex < cards.size() && preparedNextIndex != cardIndex) return preparedNextIndex; if (cards.size() <= 1) return -1; if (!randomReadMode) preparedNextIndex = (cardIndex + 1) % cards.size(); else { int n = random.nextInt(cards.size()); preparedNextIndex = n == cardIndex ? (n + 1) % cards.size() : n; } return preparedNextIndex; }
    private File cachedFileFor(String url) { return new File(cacheDir, sha1(url) + ".mp3"); }

    private File downloadToCache(String url, boolean wait) {
        File target = cachedFileFor(url); if (target.exists() && target.length() > 0) return target;
        String key = target.getName(); if (!downloading.add(key)) { if (wait) { long end = System.currentTimeMillis()+3500; while (System.currentTimeMillis()<end) { if (target.exists() && target.length()>0) return target; try { Thread.sleep(80); } catch(Exception ignored){} } } return target.exists()&&target.length()>0 ? target : null; }
        File tmp = new File(target.getAbsolutePath()+".tmp");
        try { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(5000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent","Mozilla/5.0 Android GermanAnkiPlayer"); c.setRequestProperty("Referer","https://translate.google.com/"); InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(tmp); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); out.close(); in.close(); if(tmp.length()>0){ if(target.exists()) target.delete(); tmp.renameTo(target);} return target.exists()&&target.length()>0 ? target:null; } catch(Exception e){ try{tmp.delete();}catch(Exception ignored){} return null; } finally { downloading.remove(key); }
    }

    private void advanceSegment(){ if(!playing||paused)return; handler.removeCallbacksAndMessages(null); segmentIndex++; retryCount=0; handler.postDelayed(this::playCurrentSegment, Math.max(0,gapMs)); }
    private void nextCard(){ freeTextMode=false; randomReadMode=false; preparedNextIndex=-1; if(cards.isEmpty())loadCardsIfNeeded(); if(!cards.isEmpty())cardIndex=(cardIndex+1)%cards.size(); resetAndPlay(); }
    private void prevCard(){ freeTextMode=false; randomReadMode=false; preparedNextIndex=-1; if(cards.isEmpty())loadCardsIfNeeded(); if(!cards.isEmpty())cardIndex=(cardIndex-1+cards.size())%cards.size(); resetAndPlay(); }
    private void resetAndPlay(){ segmentIndex=0; clearPlan(); paused=false; playing=true; playbackToken++; retryCount=0; acquireLocks(); playCurrentSegment(); }
    private void pausePlayback(){ try{ if(player!=null&&player.isPlaying())player.pause(); }catch(Exception ignored){} paused=true; playing=false; playbackToken++; updateNotification("已暂停", currentText()); updatePlaybackState(true); }
    private void resumePlayback(){ paused=false; playing=true; playbackToken++; acquireLocks(); try{ if(player!=null) player.start(); else playCurrentSegment(); }catch(Exception e){ playCurrentSegment(); } }
    private void stopPlayback(){ playbackToken++; playing=false; paused=false; releasePlayer(); handler.removeCallbacksAndMessages(null); releaseLocks(); stopForeground(true); stopSelf(); }
    private void releasePlayer(){ try{ if(player!=null){ player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null); try{player.stop();}catch(Exception ignored){} player.release(); }}catch(Exception ignored){} player=null; }

    private String currentText(){ if(cards.isEmpty())return ""; Card c=cards.get(Math.max(0,Math.min(cardIndex,cards.size()-1))); return c.front+" ｜ "+c.meaning; }
    private void notifyCardChanged(int id){ try{ Intent i=new Intent(ACTION_CARD_CHANGED); i.setPackage(getPackageName()); i.putExtra("cardId",id); sendBroadcast(i);}catch(Exception ignored){} }
    private boolean isSingleWordCard(Card c,String f){return f.indexOf(' ')<0&&(c.pos==null||c.pos.length()==0||"单词".equals(c.pos));}
    private boolean isPhraseLike(Card c){String f=cleanGerman(c.front);return f.length()>0&&("词块".equals(c.pos)||"句型".equals(c.pos)||f.indexOf(' ')>=0);}
    private boolean containsToken(String text,String token){for(String w:wordsFrom(text))if(w.equalsIgnoreCase(token))return true;return false;}
    private boolean safeEq(String a,String b){return a!=null&&a.equals(b);} private void clearPlan(){plan.clear();builtCardIndex=-1;}

    private List<String> wordsFrom(String text){List<String> out=new ArrayList<>();String[] parts=cleanGerman(text).split("[^A-Za-zÄÖÜäöüß]+");for(String p:parts)if(p!=null&&p.trim().length()>0)out.add(p.trim());return out;}
    private String keyToken(String f){String best="";for(String p:wordsFrom(f.toLowerCase()))if(p.length()>=3&&!isStopWord(p)&&p.length()>best.length())best=p;return best;}
    private String spokenWord(String input){String clean=cleanGerman(input);List<String>w=wordsFrom(clean);if(w.size()>=2){String a=w.get(0).toLowerCase();if(a.equals("der")||a.equals("die")||a.equals("das"))return clean;}if(w.size()==1){String a=nounArticles.get(w.get(0).toLowerCase());if(a!=null)return a+" "+w.get(0);}return clean;}
    private String spellBase(String input){List<String>w=wordsFrom(input);if(w.size()>=2){String a=w.get(0).toLowerCase();if(a.equals("der")||a.equals("die")||a.equals("das"))return w.get(1);}return cleanGerman(input);}
    private String spellGerman(String s){String base=cleanGerman(s);StringBuilder b=new StringBuilder();for(int i=0;i<base.length();i++){char ch=base.charAt(i);if(!Character.isLetterOrDigit(ch))continue;String part=ch=='ß'?"Eszett":String.valueOf(ch).toLowerCase();if(b.length()>0)b.append(", ");b.append(part);}return b.toString();}
    private String cleanGerman(String s){return s==null?"":s.replaceAll("\\([^)]*\\)"," ").replace("…","").replace("...","").replace("jmdn.","jemanden").replace("jdn.","jemanden").replace("jmdm.","jemandem").replace("jmd.","jemand").replace("etw.","etwas").replace("z.B.","zum Beispiel").replace("CAD","C A D").replace("WG","W G").replaceAll("\\s*[|/]\\s*"," ").replaceAll("\\s+"," ").trim();}
    private String cleanChinese(String s){return s==null?"":s.replace(';','，').replace('；','，').replace('/','，').replace('|','，').trim();}
    private boolean isStopWord(String s){return s.equals("sich")||s.equals("mit")||s.equals("auf")||s.equals("für")||s.equals("aus")||s.equals("von")||s.equals("bei")||s.equals("nach")||s.equals("über")||s.equals("unter")||s.equals("durch")||s.equals("ohne")||s.equals("gegen")||s.equals("jemand")||s.equals("jemanden")||s.equals("jemandem")||s.equals("etwas")||s.equals("eine")||s.equals("einen")||s.equals("einem")||s.equals("einer")||s.equals("der")||s.equals("die")||s.equals("das")||s.equals("den")||s.equals("dem")||s.equals("des")||s.equals("und")||s.equals("oder")||s.equals("ich")||s.equals("du")||s.equals("er")||s.equals("sie")||s.equals("wir")||s.equals("ihr");}
    private String ttsUrl(String text,String lang){return Uri.parse("https://translate.google.com/translate_tts").buildUpon().appendQueryParameter("ie","UTF-8").appendQueryParameter("client","tw-ob").appendQueryParameter("tl",lang).appendQueryParameter("q",text).build().toString();}
    private String cleanLang(String v,String f){return v==null||v.trim().length()==0?f:v.trim();}
    private String sha1(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-1");byte[] bs=md.digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte x:bs)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){return String.valueOf(s.hashCode()).replace('-','x');}}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));} private int clampInt(int v,int min,int max){return Math.max(min,Math.min(max,v));}

    private void createWakeLock(){try{PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);if(pm!=null){wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GermanAnkiPlayer:AudioWakeLock");wakeLock.setReferenceCounted(false);}}catch(Exception ignored){}}
    private void createWifiLock(){try{WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wm!=null){wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"GermanAnkiPlayer:WifiLock");wifiLock.setReferenceCounted(false);}}catch(Exception ignored){}}
    private void acquireLocks(){try{if(wakeLock!=null&&!wakeLock.isHeld())wakeLock.acquire(6*60*60*1000L);}catch(Exception ignored){}try{if(wifiLock!=null&&!wifiLock.isHeld())wifiLock.acquire();}catch(Exception ignored){}}
    private void releaseLocks(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}}

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"德语 Anki 原声播放",NotificationManager.IMPORTANCE_LOW);NotificationManager m=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.createNotificationChannel(c);}}
    private void createMediaSession(){if(mediaSession!=null)return;mediaSession=new MediaSession(this,"GermanAnkiPlayer");mediaSession.setCallback(new MediaSession.Callback(){@Override public void onPlay(){resumePlayback();}@Override public void onPause(){pausePlayback();}@Override public void onStop(){stopPlayback();}@Override public void onSkipToNext(){nextCard();}@Override public void onSkipToPrevious(){prevCard();}});mediaSession.setActive(true);}
    private void ensureForeground(String title,String text){updateNotification(title,text);foregroundStarted=true;}
    private void updateNotification(String title,String text){createMediaSession();PendingIntent ci=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),pendingFlags());Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(text).setContentIntent(ci).setOngoing(!paused).setOnlyAlertOnce(true).setShowWhen(false).addAction(android.R.drawable.ic_media_previous,"上一张",serviceIntent(ACTION_PREV)).addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"继续":"暂停",serviceIntent(paused?ACTION_RESUME:ACTION_PAUSE)).addAction(android.R.drawable.ic_media_next,"下一张",serviceIntent(ACTION_NEXT)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",serviceIntent(ACTION_STOP));if(Build.VERSION.SDK_INT>=21)b.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0,1,2));Notification n=b.build();if(!foregroundStarted)startForeground(NOTIFICATION_ID,n);else{NotificationManager m=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.notify(NOTIFICATION_ID,n);}}
    private PendingIntent serviceIntent(String a){Intent i=new Intent(this,PlaybackService.class);i.setAction(a);return PendingIntent.getService(this,a.hashCode(),i,pendingFlags());}
    private int pendingFlags(){int f=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)f|=PendingIntent.FLAG_IMMUTABLE;return f;}
    private void updatePlaybackState(boolean p){if(mediaSession==null)return;long a=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(a).setState(p?PlaybackState.STATE_PAUSED:PlaybackState.STATE_PLAYING,PlaybackState.PLAYBACK_POSITION_UNKNOWN,p?0f:activeSpeed).build());}

    private static class Segment{final String text,lang,phase;final float speed;Segment(String t,String l,String p,float s){text=t==null?"":t;lang=l;phase=p;speed=s;}}
    private static class Related{final String phrase,example,exampleCn;Related(String p,String e,String c){phrase=p==null?"":p;example=e==null?"":e;exampleCn=c==null?"":c;}}
    private static class Card{final int id;String front,meaning,deck,pos,example,exampleCn,notes;Card(int id,String f,String m,String d,String p,String e,String ec,String n){this.id=id;front=f;meaning=m;deck=d;pos=p;example=e;exampleCn=ec;notes=n;}}
}
