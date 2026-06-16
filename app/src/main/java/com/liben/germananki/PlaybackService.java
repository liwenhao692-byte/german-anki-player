package com.liben.germananki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String CHANNEL_ID = "lexora_background_audio";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Seg> plan = new ArrayList<>();
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean playing = false;
    private int index = 0;
    private int token = 0;
    private int gapMs = 450;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) { handler.postDelayed(() -> nextSegment(), Math.max(0, gapMs)); }
                    @Override public void onError(String utteranceId) { handler.postDelayed(() -> nextSegment(), Math.max(0, gapMs)); }
                });
                if (playing) playCurrent();
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) { stopPlayback(); return START_NOT_STICKY; }
        ensureForeground("Lexora 后台发音", "准备播放");
        if (ACTION_PAUSE.equals(action)) { pausePlayback(); return START_STICKY; }
        if (ACTION_RESUME.equals(action)) { resumePlayback(); return START_STICKY; }
        if (ACTION_NEXT.equals(action)) { nextSegment(); return START_STICKY; }
        if (ACTION_PREV.equals(action)) { previousSegment(); return START_STICKY; }
        startFromIntent(intent);
        return START_STICKY;
    }

    private void startFromIntent(Intent intent) {
        plan.clear();
        index = 0;
        token++;
        gapMs = Math.max(0, intent == null ? 450 : intent.getIntExtra("gapMs", 450));
        String sequenceJson = intent == null ? null : intent.getStringExtra("sequenceJson");
        if (sequenceJson != null && sequenceJson.trim().length() > 0) parseSequence(sequenceJson);
        if (plan.isEmpty()) {
            String text = intent == null ? "" : intent.getStringExtra("freeText");
            String lang = intent == null ? "de-DE" : cleanLang(intent.getStringExtra("freeLang"), "de-DE");
            float speed = clamp(intent == null ? .9f : intent.getFloatExtra("speed", .9f), .35f, 1.8f);
            int repeat = Math.max(1, Math.min(50, intent == null ? 1 : intent.getIntExtra("freeRepeat", 1)));
            for (int i = 0; i < repeat; i++) if (text != null && text.trim().length() > 0) plan.add(new Seg(text.trim(), lang, speed, "default"));
        }
        if (plan.isEmpty()) { stopPlayback(); return; }
        playing = true;
        updateNotification("Lexora 后台发音", currentText());
        playCurrent();
    }

    private void parseSequence(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                String text = o.optString("text", "").trim();
                if (text.length() == 0) continue;
                String lang = cleanLang(o.optString("lang", "de-DE"), "de-DE");
                float rate = clamp((float)o.optDouble("rate", .9), .35f, 1.8f);
                String gender = o.optString("gender", "female");
                plan.add(new Seg(text, lang, rate, gender));
            }
        } catch (Exception ignored) {}
    }

    private void playCurrent() {
        if (!playing || !ttsReady || tts == null) return;
        if (index < 0) index = 0;
        if (index >= plan.size()) { updateNotification("播放结束", "已完成当前卡片发音"); playing = false; stopSelf(); return; }
        Seg s = plan.get(index);
        try {
            Locale locale = Build.VERSION.SDK_INT >= 21 ? Locale.forLanguageTag(s.lang) : Locale.GERMANY;
            tts.setLanguage(locale);
            tts.setSpeechRate(s.rate);
            chooseVoice(locale, s.gender);
            updateNotification("Lexora 后台发音", s.text);
            String id = "lexora_" + token + "_" + index;
            if (Build.VERSION.SDK_INT >= 21) {
                Bundle params = new Bundle();
                tts.speak(s.text, TextToSpeech.QUEUE_FLUSH, params, id);
            } else {
                tts.speak(s.text, TextToSpeech.QUEUE_FLUSH, null);
                handler.postDelayed(() -> nextSegment(), 1200);
            }
        } catch (Exception e) { nextSegment(); }
    }

    private void nextSegment() { if (!playing) return; index++; playCurrent(); }
    private void previousSegment() { if (!playing) return; index = Math.max(0, index - 1); playCurrent(); }
    private void pausePlayback() { playing = false; try { if (tts != null) tts.stop(); } catch(Exception ignored) {} updateNotification("已暂停", currentText()); }
    private void resumePlayback() { if (plan.isEmpty()) return; playing = true; playCurrent(); }
    private void stopPlayback() { playing = false; handler.removeCallbacksAndMessages(null); try { if (tts != null) tts.stop(); } catch(Exception ignored) {} stopForeground(true); stopSelf(); }

    private String currentText() { return index >= 0 && index < plan.size() ? plan.get(index).text : ""; }

    private void chooseVoice(Locale locale, String gender) {
        if (Build.VERSION.SDK_INT < 21 || tts == null) return;
        try {
            Set<Voice> voices = tts.getVoices(); if (voices == null || voices.isEmpty()) return;
            Voice fallback = null, match = null;
            String want = gender == null ? "default" : gender.toLowerCase(Locale.ROOT);
            for (Voice v : voices) {
                if (v == null || v.getLocale() == null) continue;
                if (!sameLanguage(v.getLocale(), locale)) continue;
                if (fallback == null) fallback = v;
                String n = voiceText(v).toLowerCase(Locale.ROOT);
                if ("female".equals(want) && (n.contains("female") || n.contains("woman") || n.contains("frau") || n.contains("weiblich") || n.contains("katja") || n.contains("anna") || n.contains("marlene") || n.contains("helena") || n.contains("vicki") || n.contains("amala") || n.contains("xiaoxiao") || n.contains("huihui") || n.contains("tingting") || n.contains("hanhan") || n.contains("yaoyao"))) { match = v; break; }
                if ("male".equals(want) && (n.contains("male") || n.contains("man") || n.contains("mann") || n.contains("männlich") || n.contains("hans") || n.contains("stefan") || n.contains("thorsten") || n.contains("markus") || n.contains("jonas") || n.contains("yunxi") || n.contains("yunyang"))) { match = v; break; }
            }
            Voice chosen = match != null ? match : fallback;
            if (chosen != null) tts.setVoice(chosen);
        } catch(Exception ignored) {}
    }

    private boolean sameLanguage(Locale a, Locale b) {
        return a != null && b != null && a.getLanguage() != null && a.getLanguage().equalsIgnoreCase(b.getLanguage());
    }

    private String voiceText(Voice v) {
        StringBuilder sb = new StringBuilder();
        try { sb.append(v.getName()).append(' '); } catch(Exception ignored) {}
        try { sb.append(v.getLocale()).append(' '); } catch(Exception ignored) {}
        try { if (v.getFeatures() != null) for (String f : v.getFeatures()) sb.append(f).append(' '); } catch(Exception ignored) {}
        return sb.toString();
    }

    private void ensureForeground(String title, String text) { startForeground(NOTIFICATION_ID, notification(title, text)); }
    private void updateNotification(String title, String text) { try { ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification(title, text)); } catch(Exception ignored) {} }

    private Notification notification(String title, String text) {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text == null ? "" : text).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(playing);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Lexora 后台发音", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public void onDestroy() { try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch(Exception ignored) {} super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private String cleanLang(String value, String fallback) { return value == null || value.trim().length() == 0 ? fallback : value.trim(); }

    private static class Seg {
        final String text, lang, gender;
        final float rate;
        Seg(String text, String lang, float rate, String gender) { this.text = text; this.lang = lang; this.rate = rate; this.gender = gender; }
    }
}
