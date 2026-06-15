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

import org.json.JSONArray;
import org.json.JSONObject;

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
    private final List<Segment> segmentPlan = new ArrayList<>();
    private final Map<String, String> nounArticles = new HashMap<>();
    private final Random random = new Random();
    private final Set<String> downloading = Collections.synchronizedSet(new HashSet<>());

    private MediaPlayer player;
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private File audioCacheDir;
    private boolean foregroundStarted = false;
    private boolean paused = false;
    private boolean playing = false;
    private boolean trainingMode = false;
    private boolean randomReadMode = false;
    private boolean freeTextMode = false;
    private String freeTextTitle = "输入文本";

    private int cardIndex = 0;
    private int preparedNextIndex = -1;
    private int segmentInCard = 0;
    private int builtCardIndex = -1;
    private boolean builtTrainingMode = false;
    private int playbackToken = 0;
    private int deRepeat = 2, zhRepeat = 1, gapMs = 500, retryCount = 0;
    private int wordSlowCount = 3, spellCount = 2, wordNormalCount = 3, meaningCount = 1, phraseCount = 1, sentenceCount = 1, sentenceCnCount = 1;
    private float speed = 0.9f, activeSpeed = 0.9f;
    private float wordSlowSpeed = 0.62f, spellSpeed = 0.72f, wordNormalSpeed = 0.9f, phraseSpeed = 0.62f, sentenceSpeed = 0.62f, zhSpeed = 0.78f;
    private String deLang = "de", zhLang = "zh-CN";

    @Override public void onCreate() {
        super.onCreate();
        audioCacheDir = new File(getCacheDir(), "online_tts_cache");
        if (!audioCacheDir.exists()) audioCacheDir.mkdirs();
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
        if (ACTION_START.equals(action)) {
            gapMs = Math.max(0, intent.getIntExtra("gapMs", 500));
            speed = clamp(intent.getFloatExtra("speed", 0.9f), 0.45f, 1.6f);
            activeSpeed = speed;
            String freeText = intent.getStringExtra("freeText");
            if (freeText != null && freeText.trim().length() > 0) {
                startFreeTextPlan(freeText.trim(), cleanLang(intent.getStringExtra("freeLang"), "de"), clampInt(intent.getIntExtra("freeRepeat", 1), 1, 50));
                return START_STICKY;
            }
            freeTextMode = false;
            randomReadMode = intent.getBooleanExtra("randomReadMode", false);
            deRepeat = Math.max(0, intent.getIntExtra("deRepeat", 2));
            zhRepeat = Math.max(0, intent.getIntExtra("zhRepeat", 1));
            wordSlowSpeed = clamp(intent.getFloatExtra("wordSlowSpeed", 0.62f), 0.40f, 1.3f);
            spellSpeed = clamp(intent.getFloatExtra("spellSpeed", 0.72f), 0.40f, 1.3f);
            wordNormalSpeed = clamp(intent.getFloatExtra("wordNormalSpeed", speed), 0.45f, 1.6f);
            phraseSpeed = clamp(intent.getFloatExtra("phraseSpeed", 0.62f), 0.40f, 1.3f);
            sentenceSpeed = clamp(intent.getFloatExtra("sentenceSpeed", 0.62f), 0.40f, 1.3f);
            zhSpeed = clamp(intent.getFloatExtra("zhSpeed", 0.78f), 0.45f, 1.5f);
            wordSlowCount = clampInt(intent.getIntExtra("wordSlowCount", 3), 0, 20);
            spellCount = clampInt(intent.getIntExtra("spellCount", 2), 0, 20);
            wordNormalCount = clampInt(intent.getIntExtra("wordNormalCount", 3), 0, 20);
            meaningCount = clampInt(intent.getIntExtra("meaningCount", 1), 0, 20);
            phraseCount = clampInt(intent.getIntExtra("phraseCount", 1), 0, 20);
            sentenceCount = clampInt(intent.getIntExtra("sentenceCount", 1), 0, 20);
            sentenceCnCount = clampInt(intent.getIntExtra("sentenceCnCount", 1), 0, 20);
            deLang = cleanLang(intent.getStringExtra("deLang"), "de");
            zhLang = cleanLang(intent.getStringExtra("zhLang"), "zh-CN");
            trainingMode = intent.getBooleanExtra("trainingMode", intent.getBooleanExtra("spellMode", false));
            loadCardsIfNeeded();
            cardIndex = Math.max(0, Math.min(intent.getIntExtra("startIndex", 0), Math.max(0, cards.size() - 1)));
            preparedNextIndex = -1;
            segmentInCard = 0;
            clearPlan();
            retryCount = 0;
            paused = false;
            playing = true;
            playbackToken++;
            acquireLocks();
            playCurrentSegment();
        } else if (ACTION_PAUSE.equals(action)) pausePlayback();
        else if (ACTION_RESUME.equals(action)) resumePlayback();
        else if (ACTION_NEXT.equals(action)) nextCard();
        else if (ACTION_PREV.equals(action)) prevCard();
        else if (ACTION_STOP.equals(action)) stopPlayback();
        return START_STICKY;
    }

    private void startFreeTextPlan(String text, String lang, int repeat) {
        freeTextMode = true;
        randomReadMode = false;
        trainingMode = false;
        preparedNextIndex = -1;
        deLang = lang;
        retryCount = 0;
        paused = false;
        playing = true;
        playbackToken++;
        segmentInCard = 0;
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        releaseLocks();
        super.onDestroy();
    }

    private void loadCardsIfNeeded() {
        if (!cards.isEmpty()) return;
        loadCardsFromOriginalHtml();
        if (cards.isEmpty()) loadCardsFromTsvFallback();
        buildNounArticles();
    }

    private void loadCardsFromOriginalHtml() {
        try {
            String html = readAll(getAssets().open(ORIGINAL_HTML));
            int marker = html.indexOf("const allCards");
            if (marker < 0) marker = html.indexOf("allCards");
            if (marker < 0) return;
            int start = html.indexOf('[', marker);
            int end = html.indexOf("];", start);
            if (start < 0 || end < 0 || end <= start) return;
            JSONArray array = new JSONArray(html.substring(start, end + 1));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                int id = obj.optInt("id", i + 1);
                String front = obj.optString("front", "");
                String meaning = obj.optString("meaning", "");
                String deck = obj.optString("deck", "");
                String pos = obj.optString("pos", "");
                String example = obj.optString("example", "");
                String exampleCn = obj.optString("exampleCn", "");
                if (front.trim().length() == 0 && meaning.trim().length() == 0) continue;
                cards.add(new Card(id, front, meaning, deck, pos, example, exampleCn));
            }
        } catch (Exception e) { cards.clear(); }
    }

    private void loadCardsFromTsvFallback() {
        try {
            String[] lines = readAll(getAssets().open("cards.tsv")).split("\\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\t", -1);
                if (p.length >= 4) {
                    int id = 0;
                    try { id = Integer.parseInt(p[0]); } catch (Exception ignored) {}
                    cards.add(new Card(id, p[1], p[2], p[3], "", "", ""));
                }
            }
        } catch (Exception e) { cards.clear(); }
    }

    private void buildNounArticles() {
        nounArticles.clear();
        for (Card c : cards) {
            List<String> words = wordsFrom(c.front);
            if (words.size() >= 2) {
                String article = words.get(0).toLowerCase();
                if (article.equals("der") || article.equals("die") || article.equals("das")) nounArticles.put(words.get(1).toLowerCase(), article);
            }
        }
    }

    private String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) bos.write(buffer, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }

    private void playCurrentSegment() {
        if (!playing || paused) return;
        if (freeTextMode) {
            Segment seg = nextFreeTextSegment();
            if (seg == null) {
                updateNotification("文本朗读结束", freeTextTitle);
                playing = false;
                releaseLocks();
                updatePlaybackState(true);
                return;
            }
            activeSpeed = seg.speed;
            updateNotification(seg.phase, freeTextTitle);
            updatePlaybackState(false);
            prefetchAround();
            playSegment(seg, playbackToken);
            return;
        }

        loadCardsIfNeeded();
        if (cards.isEmpty()) {
            updateNotification("没有卡片数据", "原版 HTML 未解析到 allCards");
            return;
        }
        Segment seg = nextPlayableSegment();
        if (seg == null || seg.text.length() == 0) {
            updateNotification("没有可播放内容", "当前卡片缺少文本");
            return;
        }
        activeSpeed = seg.speed;
        Card card = cards.get(cardIndex);
        notifyCardChanged(card.id);
        updateNotification((cardIndex + 1) + " / " + cards.size() + " · " + (randomReadMode ? "随机 · " : "") + seg.phase, card.front + " ｜ " + card.meaning);
        updatePlaybackState(false);
        prefetchAround();
        playSegment(seg, playbackToken);
    }

    private void playSegment(Segment seg, int token) {
        String url = ttsUrl(seg.text, seg.lang);
        File cached = cachedFileFor(url);
        if (cached.exists() && cached.length() > 0) {
            playFile(cached, token);
            return;
        }
        new Thread(() -> {
            File f = downloadToCache(url, true);
            handler.post(() -> {
                if (token != playbackToken || !playing || paused) return;
                if (f != null && f.exists() && f.length() > 0) playFile(f, token);
                else playUrlDirect(url, token);
            });
        }, "tts-current-download").start();
    }

    private void playFile(File file, int token) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            try { player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            player.setOnPreparedListener(mp -> {
                if (token != playbackToken || !playing || paused) return;
                applySpeed(mp);
                paused = false;
                retryCount = 0;
                mp.start();
                updatePlaybackState(false);
            });
            player.setOnCompletionListener(mp -> advanceSegment());
            player.setOnErrorListener((mp, what, extra) -> { advanceSegment(); return true; });
            player.setDataSource(file.getAbsolutePath());
            player.prepareAsync();
        } catch (Exception e) {
            advanceSegment();
        }
    }

    private void playUrlDirect(String url, int token) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            try { player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            player.setOnPreparedListener(mp -> {
                if (token != playbackToken || !playing || paused) return;
                applySpeed(mp);
                paused = false;
                retryCount = 0;
                mp.start();
                updatePlaybackState(false);
            });
            player.setOnCompletionListener(mp -> advanceSegment());
            player.setOnErrorListener((mp, what, extra) -> {
                if (retryCount < 2 && playing && !paused && token == playbackToken) {
                    retryCount++;
                    handler.postDelayed(this::playCurrentSegment, 500);
                } else {
                    retryCount = 0;
                    advanceSegment();
                }
                return true;
            });
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 Android GermanAnkiPlayer");
            headers.put("Referer", "https://translate.google.com/");
            player.setDataSource(this, Uri.parse(url), headers);
            player.prepareAsync();
        } catch (Exception e) {
            advanceSegment();
        }
    }

    private void applySpeed(MediaPlayer mp) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PlaybackParams params = mp.getPlaybackParams();
                params.setSpeed(activeSpeed);
                mp.setPlaybackParams(params);
            }
        } catch (Exception ignored) {}
    }

    private void prefetchAround() {
        final int token = playbackToken;
        final List<Segment> list = new ArrayList<>();
        int start = Math.max(0, segmentInCard + 1);
        int end = Math.min(segmentPlan.size(), start + 10);
        for (int i = start; i < end; i++) list.add(segmentPlan.get(i));
        if (!freeTextMode && !cards.isEmpty()) {
            int next = ensurePreparedNextIndex();
            if (next >= 0 && next < cards.size()) list.addAll(makePlan(cards.get(next), trainingMode));
        }
        if (list.isEmpty()) return;
        new Thread(() -> {
            for (Segment s : list) {
                if (token != playbackToken || !playing) return;
                downloadToCache(ttsUrl(s.text, s.lang), false);
            }
        }, "tts-prefetch").start();
    }

    private int ensurePreparedNextIndex() {
        if (preparedNextIndex >= 0 && preparedNextIndex < cards.size() && preparedNextIndex != cardIndex) return preparedNextIndex;
        if (cards.size() <= 1) return -1;
        if (!randomReadMode) preparedNextIndex = (cardIndex + 1) % cards.size();
        else {
            int next = random.nextInt(cards.size());
            if (next == cardIndex) next = (next + 1) % cards.size();
            preparedNextIndex = next;
        }
        return preparedNextIndex;
    }

    private File cachedFileFor(String url) {
        return new File(audioCacheDir, sha1(url) + ".mp3");
    }

    private File downloadToCache(String url, boolean waitIfBusy) {
        File target = cachedFileFor(url);
        if (target.exists() && target.length() > 0) return target;
        String key = target.getName();
        if (!downloading.add(key)) {
            if (waitIfBusy) {
                long end = System.currentTimeMillis() + 3500;
                while (System.currentTimeMillis() < end) {
                    if (target.exists() && target.length() > 0) return target;
                    try { Thread.sleep(80); } catch (Exception ignored) {}
                }
            }
            return target.exists() && target.length() > 0 ? target : null;
        }
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 Android GermanAnkiPlayer");
            conn.setRequestProperty("Referer", "https://translate.google.com/");
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            out.close();
            in.close();
            if (tmp.length() > 0) {
                if (target.exists()) target.delete();
                tmp.renameTo(target);
            }
            return target.exists() && target.length() > 0 ? target : null;
        } catch (Exception e) {
            try { tmp.delete(); } catch (Exception ignored) {}
            return null;
        } finally {
            downloading.remove(key);
        }
    }

    private String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode()).replace('-', 'x');
        }
    }

    private Segment nextFreeTextSegment() {
        if (segmentInCard >= segmentPlan.size()) return null;
        Segment seg = segmentPlan.get(segmentInCard);
        return seg.text != null && seg.text.trim().length() > 0 ? seg : null;
    }

    private Segment nextPlayableSegment() {
        int guard = 0;
        while (guard++ < 80) {
            if (cardIndex < 0 || cardIndex >= cards.size()) cardIndex = 0;
            Card card = cards.get(cardIndex);
            ensurePlan(card);
            if (segmentInCard >= segmentPlan.size()) {
                segmentInCard = 0;
                clearPlan();
                cardIndex = nextAutomaticCardIndex();
                continue;
            }
            Segment seg = segmentPlan.get(segmentInCard);
            if (seg.text != null && seg.text.trim().length() > 0) return seg;
            segmentInCard++;
        }
        return null;
    }

    private int nextAutomaticCardIndex() {
        if (cards.size() <= 1) return 0;
        if (preparedNextIndex >= 0 && preparedNextIndex < cards.size() && preparedNextIndex != cardIndex) {
            int next = preparedNextIndex;
            preparedNextIndex = -1;
            return next;
        }
        if (!randomReadMode) return (cardIndex + 1) % cards.size();
        int next = random.nextInt(cards.size());
        if (next == cardIndex) next = (next + 1) % cards.size();
        return next;
    }

    private void ensurePlan(Card card) {
        if (builtCardIndex == cardIndex && builtTrainingMode == trainingMode && !segmentPlan.isEmpty()) return;
        segmentPlan.clear();
        segmentPlan.addAll(makePlan(card, trainingMode));
        builtCardIndex = cardIndex;
        builtTrainingMode = trainingMode;
        if (segmentPlan.isEmpty()) addSeg(spokenWord(card.front), deLang, "德语原声", speed);
    }

    private List<Segment> makePlan(Card card, boolean useTrainingMode) {
        List<Segment> out = new ArrayList<>();
        String front = cleanGerman(card.front), word = spokenWord(front), cn = cleanChinese(card.meaning);
        if (!useTrainingMode) {
            for (int i = 0; i < Math.max(0, deRepeat); i++) addSegTo(out, word, deLang, "德语朗读", speed);
            for (int i = 0; i < Math.max(1, spellCount); i++) addSegTo(out, spellGerman(spellBase(front)), deLang, "拼读", spellSpeed);
            for (int i = 0; i < Math.max(0, zhRepeat); i++) addSegTo(out, cn, zhLang, "中文意思", zhSpeed);
            return out;
        }
        for (int i = 0; i < wordSlowCount; i++) addSegTo(out, word, deLang, "慢速单词", wordSlowSpeed);
        for (int i = 0; i < spellCount; i++) addSegTo(out, spellGerman(spellBase(front)), deLang, "单词拼读", spellSpeed);
        for (int i = 0; i < wordNormalCount; i++) addSegTo(out, word, deLang, "正常单词", wordNormalSpeed);
        for (int i = 0; i < meaningCount; i++) addSegTo(out, cn, zhLang, "中文意思", zhSpeed);
        Related rel = randomRelatedFor(card);
        String phrase = cleanGerman(rel.phrase), sentence = cleanGerman(rel.example), sentenceCn = cleanChinese(rel.exampleCn);
        if (phrase.length() > 0) {
            addWordSpellPiecesTo(out, phrase, "词块拆词", "词块拼读");
            for (int i = 0; i < phraseCount; i++) addSegTo(out, phrase, deLang, "慢速词块", phraseSpeed);
        }
        if (sentence.length() > 0) {
            addWordSpellPiecesTo(out, sentence, "句子拆词", "句子拼读");
            for (int i = 0; i < sentenceCount; i++) addSegTo(out, sentence, deLang, "慢速句子", sentenceSpeed);
        }
        for (int i = 0; i < sentenceCnCount; i++) addSegTo(out, sentenceCn, zhLang, "句子中文", zhSpeed);
        return out;
    }

    private void addWordSpellPiecesTo(List<Segment> out, String text, String wordPhase, String spellPhase) {
        for (String w : wordsFrom(text)) {
            if (w.length() < 2 || isStopWord(w.toLowerCase())) continue;
            addSegTo(out, spokenWord(w), deLang, wordPhase, wordSlowSpeed);
            addSegTo(out, spellGerman(spellBase(w)), deLang, spellPhase, spellSpeed);
        }
    }

    private void addSegTo(List<Segment> out, String text, String lang, String phase, float spd) {
        if (text != null && text.trim().length() > 0) out.add(new Segment(text.trim(), lang, phase, spd));
    }

    private void addSeg(String text, String lang, String phase, float spd) {
        if (text != null && text.trim().length() > 0) segmentPlan.add(new Segment(text.trim(), lang, phase, spd));
    }

    private void notifyCardChanged(int cardId) {
        try {
            Intent i = new Intent(ACTION_CARD_CHANGED);
            i.setPackage(getPackageName());
            i.putExtra("cardId", cardId);
            sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    private Related randomRelatedFor(Card card) {
        String front = cleanGerman(card.front);
        if (front.length() == 0) return new Related("", card.example, card.exampleCn);
        if (!isSingleWordCard(card, front)) return new Related(front, card.example, card.exampleCn);
        String token = keyToken(front);
        if (token.length() == 0) return new Related("", card.example, card.exampleCn);
        List<Card> sameDeck = relatedCandidates(card, token, true);
        List<Card> pool = sameDeck.isEmpty() ? relatedCandidates(card, token, false) : sameDeck;
        if (!pool.isEmpty()) {
            Card picked = pool.get(random.nextInt(pool.size()));
            return new Related(cleanGerman(picked.front), picked.example, picked.exampleCn);
        }
        return new Related("", card.example, card.exampleCn);
    }

    private List<Card> relatedCandidates(Card card, String token, boolean sameDeckOnly) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            if (c == card) continue;
            if (sameDeckOnly && !safeEq(c.deck, card.deck)) continue;
            if (!isPhraseLike(c)) continue;
            if (containsToken(c.front, token) || containsToken(c.example, token)) out.add(c);
        }
        return out;
    }

    private boolean isSingleWordCard(Card c, String front) { return front.indexOf(' ') < 0 && (c.pos == null || c.pos.length() == 0 || "单词".equals(c.pos)); }
    private boolean isPhraseLike(Card c) { String front = cleanGerman(c.front); return front.length() > 0 && ("词块".equals(c.pos) || "句型".equals(c.pos) || front.indexOf(' ') >= 0); }
    private boolean containsToken(String text, String token) { for (String w : wordsFrom(text)) if (w.equalsIgnoreCase(token)) return true; return false; }

    private List<String> wordsFrom(String text) {
        List<String> out = new ArrayList<>();
        String[] parts = cleanGerman(text).split("[^A-Za-zÄÖÜäöüß]+");
        for (String p : parts) if (p != null && p.trim().length() > 0) out.add(p.trim());
        return out;
    }

    private String keyToken(String front) {
        String best = "";
        for (String p : wordsFrom(front.toLowerCase())) {
            if (p.length() < 3 || isStopWord(p)) continue;
            if (p.length() > best.length()) best = p;
        }
        return best;
    }

    private String spokenWord(String input) {
        String clean = cleanGerman(input);
        if (clean.length() == 0) return "";
        List<String> words = wordsFrom(clean);
        if (words.size() >= 2) {
            String first = words.get(0).toLowerCase();
            if (first.equals("der") || first.equals("die") || first.equals("das")) return clean;
        }
        if (words.size() == 1) {
            String article = nounArticles.get(words.get(0).toLowerCase());
            if (article != null) return article + " " + words.get(0);
        }
        return clean;
    }

    private String spellBase(String input) {
        List<String> words = wordsFrom(input);
        if (words.size() >= 2) {
            String first = words.get(0).toLowerCase();
            if (first.equals("der") || first.equals("die") || first.equals("das")) return words.get(1);
        }
        return cleanGerman(input);
    }

    private boolean isStopWord(String s) {
        return s.equals("sich") || s.equals("mit") || s.equals("auf") || s.equals("für") || s.equals("aus") || s.equals("von") || s.equals("bei") || s.equals("nach") || s.equals("über") || s.equals("unter") || s.equals("durch") || s.equals("ohne") || s.equals("gegen") || s.equals("jemand") || s.equals("jemanden") || s.equals("jemandem") || s.equals("etwas") || s.equals("eine") || s.equals("einen") || s.equals("einem") || s.equals("einer") || s.equals("der") || s.equals("die") || s.equals("das") || s.equals("den") || s.equals("dem") || s.equals("des") || s.equals("und") || s.equals("oder") || s.equals("ich") || s.equals("du") || s.equals("er") || s.equals("sie") || s.equals("wir") || s.equals("ihr");
    }

    private boolean safeEq(String a, String b) { return a != null && a.equals(b); }
    private void clearPlan() { segmentPlan.clear(); builtCardIndex = -1; }

    private void advanceSegment() {
        if (!playing || paused) return;
        handler.removeCallbacksAndMessages(null);
        segmentInCard++;
        retryCount = 0;
        handler.postDelayed(this::playCurrentSegment, Math.max(0, gapMs));
    }

    private void nextCard() {
        freeTextMode = false;
        randomReadMode = false;
        preparedNextIndex = -1;
        if (cards.isEmpty()) loadCardsIfNeeded();
        if (!cards.isEmpty()) cardIndex = (cardIndex + 1) % cards.size();
        segmentInCard = 0;
        clearPlan();
        paused = false;
        playing = true;
        playbackToken++;
        retryCount = 0;
        acquireLocks();
        playCurrentSegment();
    }

    private void prevCard() {
        freeTextMode = false;
        randomReadMode = false;
        preparedNextIndex = -1;
        if (cards.isEmpty()) loadCardsIfNeeded();
        if (!cards.isEmpty()) cardIndex = (cardIndex - 1 + cards.size()) % cards.size();
        segmentInCard = 0;
        clearPlan();
        paused = false;
        playing = true;
        playbackToken++;
        retryCount = 0;
        acquireLocks();
        playCurrentSegment();
    }

    private void pausePlayback() {
        try { if (player != null && player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        paused = true;
        playing = false;
        playbackToken++;
        updateNotification("已暂停", freeTextMode ? freeTextTitle : currentText());
        updatePlaybackState(true);
    }

    private void resumePlayback() {
        paused = false;
        playing = true;
        playbackToken++;
        acquireLocks();
        try {
            if (player != null) {
                player.start();
                updateNotification("继续播放", freeTextMode ? freeTextTitle : currentText());
                updatePlaybackState(false);
            } else playCurrentSegment();
        } catch (Exception e) {
            playCurrentSegment();
        }
    }

    private void stopPlayback() {
        playbackToken++;
        playing = false;
        paused = false;
        freeTextMode = false;
        randomReadMode = false;
        preparedNextIndex = -1;
        releasePlayer();
        handler.removeCallbacksAndMessages(null);
        releaseLocks();
        stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.setOnPreparedListener(null);
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
                try { player.stop(); } catch (Exception ignored) {}
                player.release();
            }
        } catch (Exception ignored) {}
        player = null;
    }

    private String currentText() {
        if (cards.isEmpty()) return "";
        Card c = cards.get(Math.max(0, Math.min(cardIndex, cards.size() - 1)));
        return c.front + " ｜ " + c.meaning;
    }

    private String cleanGerman(String s) {
        if (s == null) return "";
        return s.replaceAll("\\([^)]*\\)", " ")
                .replace("…", "")
                .replace("...", "")
                .replaceAll("\\s*\\+\\s*(Akk|Dat|Gen|Nom)\\.?", " ")
                .replace("jmdn.", "jemanden")
                .replace("jdn.", "jemanden")
                .replace("jmdm.", "jemandem")
                .replace("jmd.", "jemand")
                .replace("etw.", "etwas")
                .replace("z.B.", "zum Beispiel")
                .replace("CAD", "C A D")
                .replace("WG", "W G")
                .replaceAll("\\s*[|/]\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String spellGerman(String s) {
        String base = cleanGerman(s);
        if (base.length() == 0) return "";
        StringBuilder spelled = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if (!Character.isLetterOrDigit(ch)) continue;
            String part = ch == 'ß' ? "Eszett" : String.valueOf(ch).toLowerCase();
            if (spelled.length() > 0) spelled.append(", ");
            spelled.append(part);
        }
        return spelled.toString();
    }

    private String cleanChinese(String s) { return s == null ? "" : s.replace(';', '，').replace('；', '，').replace('/', '，').replace('|', '，').trim(); }

    private String ttsUrl(String text, String lang) {
        return Uri.parse("https://translate.google.com/translate_tts")
                .buildUpon()
                .appendQueryParameter("ie", "UTF-8")
                .appendQueryParameter("client", "tw-ob")
                .appendQueryParameter("tl", lang)
                .appendQueryParameter("q", text)
                .build()
                .toString();
    }

    private String cleanLang(String value, String fallback) { if (value == null) return fallback; value = value.trim(); return value.length() == 0 ? fallback : value; }

    private void createWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GermanAnkiPlayer:AudioWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception ignored) {}
    }

    private void createWifiLock() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GermanAnkiPlayer:WifiLock");
                wifiLock.setReferenceCounted(false);
            }
        } catch (Exception ignored) {}
    }

    private void acquireLocks() {
        try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L); } catch (Exception ignored) {}
        try { if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire(); } catch (Exception ignored) {}
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "德语 Anki 原声播放", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("德语 Anki 原声息屏播放服务");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void createMediaSession() {
        if (mediaSession != null) return;
        mediaSession = new MediaSession(this, "GermanAnkiPlayer");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSkipToNext() { nextCard(); }
            @Override public void onSkipToPrevious() { prevCard(); }
        });
        mediaSession.setActive(true);
    }

    private void ensureForeground(String title, String text) { updateNotification(title, text); foregroundStarted = true; }

    private void updateNotification(String title, String text) {
        createMediaSession();
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous, "上一张", serviceIntent(ACTION_PREV))
                .addAction(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause, paused ? "继续" : "暂停", serviceIntent(paused ? ACTION_RESUME : ACTION_PAUSE))
                .addAction(android.R.drawable.ic_media_next, "下一张", serviceIntent(ACTION_NEXT))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", serviceIntent(ACTION_STOP));
        if (Build.VERSION.SDK_INT >= 21) builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        Notification notification = builder.build();
        if (!foregroundStarted) startForeground(NOTIFICATION_ID, notification);
        else {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent serviceIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, pendingFlags());
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void updatePlaybackState(boolean isPaused) {
        if (mediaSession == null) return;
        int state = isPaused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, isPaused ? 0f : activeSpeed).build());
    }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private int clampInt(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static class Segment {
        final String text, lang, phase;
        final float speed;
        Segment(String text, String lang, String phase, float speed) {
            this.text = text == null ? "" : text;
            this.lang = lang;
            this.phase = phase;
            this.speed = speed;
        }
    }

    private static class Related {
        final String phrase, example, exampleCn;
        Related(String phrase, String example, String exampleCn) {
            this.phrase = phrase == null ? "" : phrase;
            this.example = example == null ? "" : example;
            this.exampleCn = exampleCn == null ? "" : exampleCn;
        }
    }

    private static class Card {
        final int id;
        final String front, meaning, deck, pos, example, exampleCn;
        Card(int id, String front, String meaning, String deck, String pos, String example, String exampleCn) {
            this.id = id;
            this.front = front;
            this.meaning = meaning;
            this.deck = deck;
            this.pos = pos;
            this.example = example;
            this.exampleCn = exampleCn;
        }
    }
}
