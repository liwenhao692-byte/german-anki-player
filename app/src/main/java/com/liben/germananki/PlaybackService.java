package com.liben.germananki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlaybackService extends Service {
    public static final String ACTION_START = "com.liben.germananki.START";
    public static final String ACTION_STOP = "com.liben.germananki.STOP";
    public static final String ACTION_PAUSE = "com.liben.germananki.PAUSE";
    public static final String ACTION_RESUME = "com.liben.germananki.RESUME";
    public static final String ACTION_NEXT = "com.liben.germananki.NEXT";
    public static final String ACTION_PREV = "com.liben.germananki.PREV";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "german_anki_media";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Card> cards = new ArrayList<>();

    private MediaSession mediaSession;
    private TextToSpeech tts;
    private PowerManager.WakeLock wakeLock;

    private boolean foregroundStarted = false;
    private boolean paused = false;
    private boolean playing = false;
    private boolean ttsReady = false;
    private boolean pendingPlay = false;

    private int cardIndex = 0;
    private int segmentInCard = 0;
    private int deRepeat = 2;
    private int zhRepeat = 1;
    private int gapMs = 500;
    private float speed = 0.9f;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        createMediaSession();
        loadCardsIfNeeded();
        createWakeLock();
        initTts();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (action == null) action = ACTION_START;
        ensureForeground("德语 Anki 播放器", "准备播放");

        if (ACTION_START.equals(action)) {
            deRepeat = Math.max(0, intent.getIntExtra("deRepeat", 2));
            zhRepeat = Math.max(0, intent.getIntExtra("zhRepeat", 1));
            gapMs = Math.max(0, intent.getIntExtra("gapMs", 500));
            speed = clamp(intent.getFloatExtra("speed", 0.9f), 0.55f, 1.6f);
            cardIndex = Math.max(0, Math.min(intent.getIntExtra("startIndex", 0), Math.max(0, cards.size() - 1)));
            segmentInCard = 0;
            paused = false;
            playing = true;
            acquireWakeLock();
            playCurrentSegment();
        } else if (ACTION_PAUSE.equals(action)) {
            pausePlayback();
        } else if (ACTION_RESUME.equals(action)) {
            resumePlayback();
        } else if (ACTION_NEXT.equals(action)) {
            nextCard();
        } else if (ACTION_PREV.equals(action)) {
            prevCard();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopTts();
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        releaseWakeLock();
        super.onDestroy();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                try {
                    if (Build.VERSION.SDK_INT >= 21) {
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build());
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {
                            handler.post(() -> {
                                if (playing && !paused) advanceSegment();
                            });
                        }
                        @Override public void onError(String utteranceId) {
                            handler.post(() -> {
                                if (playing && !paused) advanceSegment();
                            });
                        }
                    });
                } catch (Exception ignored) {}
                if (pendingPlay) {
                    pendingPlay = false;
                    handler.post(this::playCurrentSegment);
                }
            } else {
                updateNotification("系统 TTS 初始化失败", "请安装/启用系统文字转语音引擎");
            }
        });
    }

    private void loadCardsIfNeeded() {
        if (!cards.isEmpty()) return;
        try {
            InputStream is = getAssets().open("cards.tsv");
            String tsv = readAll(is);
            String[] lines = tsv.split("\\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\t", -1);
                if (p.length >= 4) {
                    int id = 0;
                    try { id = Integer.parseInt(p[0]); } catch (Exception ignored) {}
                    cards.add(new Card(id, p[1], p[2], p[3]));
                }
            }
        } catch (Exception e) {
            cards.clear();
        }
    }

    private String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) bos.write(buffer, 0, n);
        return bos.toString("UTF-8");
    }

    private void playCurrentSegment() {
        loadCardsIfNeeded();
        if (cards.isEmpty()) {
            updateNotification("没有卡片数据", "assets/cards.tsv 缺失");
            return;
        }
        if (!ttsReady || tts == null) {
            pendingPlay = true;
            updateNotification("系统 TTS 准备中", "稍等几秒");
            return;
        }
        if (!playing || paused) return;

        int total = Math.max(1, deRepeat + zhRepeat);
        if (segmentInCard >= total) {
            segmentInCard = 0;
            cardIndex = (cardIndex + 1) % cards.size();
        }

        Card card = cards.get(cardIndex);
        boolean german = segmentInCard < deRepeat;
        String text = german ? cleanGerman(card.front) : cleanChinese(card.meaning);
        String phase = german ? "德语" : "中文";
        if (text.length() == 0) {
            advanceSegment();
            return;
        }

        String title = (cardIndex + 1) + " / " + cards.size() + " · " + phase;
        String sub = card.front + " ｜ " + card.meaning;
        updateNotification(title, sub);
        updatePlaybackState(false);
        speakText(text, german);
    }

    private void speakText(String text, boolean german) {
        try {
            Locale locale = german ? Locale.GERMANY : Locale.SIMPLIFIED_CHINESE;
            int lang = tts.setLanguage(locale);
            tts.setSpeechRate(speed);
            tts.setPitch(1.0f);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                updateNotification("缺少语音包", german ? "请安装德语 TTS 语音包" : "请安装中文 TTS 语音包");
                handler.postDelayed(this::advanceSegment, 900);
                return;
            }
            String utteranceId = "u_" + System.currentTimeMillis() + "_" + cardIndex + "_" + segmentInCard;
            if (Build.VERSION.SDK_INT >= 21) {
                Bundle params = new Bundle();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                handler.postDelayed(this::advanceSegment, estimateDurationMs(text));
            }
        } catch (Exception e) {
            handler.postDelayed(this::advanceSegment, 700);
        }
    }

    private int estimateDurationMs(String text) {
        int len = Math.max(1, text.length());
        return Math.max(900, Math.min(6000, (int)(len * 180 / Math.max(0.55f, speed))));
    }

    private void advanceSegment() {
        if (!playing || paused) return;
        handler.removeCallbacksAndMessages(null);
        segmentInCard++;
        handler.postDelayed(this::playCurrentSegment, Math.max(0, gapMs));
    }

    private void nextCard() {
        if (cards.isEmpty()) loadCardsIfNeeded();
        if (!cards.isEmpty()) cardIndex = (cardIndex + 1) % cards.size();
        segmentInCard = 0;
        paused = false;
        playing = true;
        acquireWakeLock();
        stopTts();
        playCurrentSegment();
    }

    private void prevCard() {
        if (cards.isEmpty()) loadCardsIfNeeded();
        if (!cards.isEmpty()) cardIndex = (cardIndex - 1 + cards.size()) % cards.size();
        segmentInCard = 0;
        paused = false;
        playing = true;
        acquireWakeLock();
        stopTts();
        playCurrentSegment();
    }

    private void pausePlayback() {
        paused = true;
        playing = false;
        stopTts();
        updateNotification("已暂停", currentText());
        updatePlaybackState(true);
    }

    private void resumePlayback() {
        paused = false;
        playing = true;
        acquireWakeLock();
        updateNotification("继续播放", currentText());
        updatePlaybackState(false);
        playCurrentSegment();
    }

    private void stopPlayback() {
        playing = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
        stopTts();
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }

    private void stopTts() {
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
    }

    private void createWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GermanAnkiPlayer:SpeechWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception ignored) {}
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}
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

    private String cleanChinese(String s) {
        if (s == null) return "";
        return s.replace(';', '，').replace('；', '，').replace('/', '，').replace('|', '，').trim();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "德语 Anki 播放", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("德语 Anki 息屏播放服务");
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

    private void ensureForeground(String title, String text) {
        updateNotification(title, text);
        foregroundStarted = true;
    }

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
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        Notification notification = builder.build();
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
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
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP |
                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, isPaused ? 0f : speed)
                .build());
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class Card {
        final int id;
        final String front;
        final String meaning;
        final String deck;
        Card(int id, String front, String meaning, String deck) {
            this.id = id;
            this.front = front;
            this.meaning = meaning;
            this.deck = deck;
        }
    }
}
