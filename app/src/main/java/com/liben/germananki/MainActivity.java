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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private EditText deRepeatInput;
    private EditText zhRepeatInput;
    private EditText speedInput;
    private EditText startInput;
    private EditText gapInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildUi();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 34, 34, 44);
        root.setBackgroundColor(Color.rgb(20, 16, 12));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("德语 Anki 播放器");
        title.setTextColor(Color.rgb(255, 247, 231));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 8, 0, 12);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("这是原生 Android 播放器。开始播放后会进入前台媒体服务，通知栏常驻，息屏后继续播放。播放时需要联网。首次使用请允许通知权限，并把本 App 电池优化设为不限制。");
        tip.setTextColor(Color.rgb(235, 219, 195));
        tip.setTextSize(15);
        tip.setLineSpacing(2, 1.08f);
        tip.setPadding(0, 0, 0, 18);
        root.addView(tip);

        deRepeatInput = addInput(root, "德语读几遍", "2");
        zhRepeatInput = addInput(root, "中文读几遍", "1");
        speedInput = addInput(root, "播放速度，例如 0.85 / 1.0 / 1.2", "0.9");
        gapInput = addInput(root, "卡片间隔毫秒，例如 500", "500");
        startInput = addInput(root, "从第几张开始", "1");

        Button start = addButton(root, "开始息屏播放", true);
        start.setOnClickListener(v -> startPlayback());

        Button pause = addButton(root, "暂停", false);
        pause.setOnClickListener(v -> sendCommand(PlaybackService.ACTION_PAUSE));

        Button resume = addButton(root, "继续", false);
        resume.setOnClickListener(v -> sendCommand(PlaybackService.ACTION_RESUME));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 0);
        root.addView(row);

        Button prev = new Button(this);
        prev.setText("上一张");
        prev.setOnClickListener(v -> sendCommand(PlaybackService.ACTION_PREV));
        row.addView(prev, new LinearLayout.LayoutParams(0, 120, 1));

        Button next = new Button(this);
        next.setText("下一张");
        next.setOnClickListener(v -> sendCommand(PlaybackService.ACTION_NEXT));
        row.addView(next, new LinearLayout.LayoutParams(0, 120, 1));

        Button stop = addButton(root, "停止播放", false);
        stop.setOnClickListener(v -> sendCommand(PlaybackService.ACTION_STOP));

        Button battery = addButton(root, "打开电池设置", false);
        battery.setOnClickListener(v -> openBatterySettings());

        setContentView(scrollView);
    }

    private EditText addInput(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.rgb(255, 231, 190));
        tv.setTextSize(15);
        tv.setPadding(0, 12, 0, 6);
        root.addView(tv);

        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setPadding(22, 8, 22, 8);
        input.setBackgroundColor(Color.rgb(48, 39, 30));
        root.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110));
        return input;
    }

    private Button addButton(LinearLayout root, String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setAllCaps(false);
        if (primary) button.setTextColor(Color.rgb(37, 26, 17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 126);
        lp.setMargins(0, 16, 0, 0);
        root.addView(button, lp);
        return button;
    }

    private void startPlayback() {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_START);
        intent.putExtra("deRepeat", parseInt(deRepeatInput.getText().toString(), 2));
        intent.putExtra("zhRepeat", parseInt(zhRepeatInput.getText().toString(), 1));
        intent.putExtra("startIndex", Math.max(0, parseInt(startInput.getText().toString(), 1) - 1));
        intent.putExtra("gapMs", Math.max(0, parseInt(gapInput.getText().toString(), 500)));
        intent.putExtra("speed", parseFloat(speedInput.getText().toString(), 0.9f));
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private float parseFloat(String value, float fallback) {
        try { return Float.parseFloat(value.trim()); } catch (Exception e) { return fallback; }
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
}
