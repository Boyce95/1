package com.xiaoxing.assistant;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** 主界面：按住说话 → 上传 → 显示回答 → 朗读 → 执行设备动作。 */
public class MainActivity extends Activity {
    private TextView logView;
    private ScrollView scrollView;
    private Button talkBtn;
    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording = false;
    private final List<String> history = new ArrayList<>(); // 交替的 user/assistant 文本
    private ApiClient api;
    private ActionExecutor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = (TextView) findViewById(R.id.log);
        scrollView = (ScrollView) findViewById(R.id.scroll);
        talkBtn = (Button) findViewById(R.id.talk);

        String baseUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("server_url", "http://192.168.1.9:8765");
        api = new ApiClient(baseUrl);
        executor = new ActionExecutor(this);

        append("小星", "你好，我是小星。按住下方按钮说话即可对话。\n点右上角设置可修改服务器地址。");

        talkBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startRecord();
                    return true;
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    stopRecordAndSend();
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showServerDialog(); }
        });
    }

    private void showServerDialog() {
        final android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("服务器地址");
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(getSharedPreferences("settings", MODE_PRIVATE)
                .getString("server_url", "http://192.168.1.9:8765"));
        b.setView(input);
        b.setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                String url = input.getText().toString().trim();
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putString("server_url", url).commit();
                api = new ApiClient(url);
                Toast.makeText(MainActivity.this, "已保存: " + url, Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void startRecord() {
        try {
            audioFile = new File(Environment.getExternalStorageDirectory(), "xiaoxing_voice.m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            // AAC/M4A 是 Android 4.x 就支持的格式，云端千问 ASR 可直接识别
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            talkBtn.setText("松开发送…");
        } catch (Exception e) {
            Toast.makeText(this, "录音启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordAndSend() {
        if (!recording) return;
        try {
            recorder.stop();
            recorder.release();
        } catch (Exception ignored) {}
        recorder = null;
        recording = false;
        talkBtn.setText("按住说话");
        byte[] audio = readFile(audioFile);
        if (audio.length == 0) {
            Toast.makeText(this, "没录到声音", Toast.LENGTH_SHORT).show();
            return;
        }
        new Task().execute(audio);
    }

    private byte[] readFile(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStreamCompat bos = new ByteArrayOutputStreamCompat();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static class ByteArrayOutputStreamCompat extends java.io.ByteArrayOutputStream {}

    private class Task extends AsyncTask<byte[], Void, ApiClient.VoiceResult> {
        private String error;
        @Override
        protected ApiClient.VoiceResult doInBackground(byte[]... params) {
            try {
                String historyJson = buildHistoryJson();
                return api.sendVoice(params[0], historyJson);
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }
        @Override
        protected void onPostExecute(ApiClient.VoiceResult result) {
            if (result == null) {
                append("系统", "请求失败：" + error);
                return;
            }
            append("我", result.transcript);
            append("小星", result.reply);
            history.add(result.transcript);
            history.add(result.reply);
            if (history.size() > 20) history.remove(0);

            // 执行云端下发的设备动作
            if (result.actions != null && result.actions.length() > 0) {
                List<ActionExecutor.Result> results = executor.executeAll(result.actions);
                append("操作", ActionExecutor.resultsToJson(results));
            }
            // 朗读回答
            speak(result.reply);
        }
    }

    private String buildHistoryJson() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < history.size(); i++) {
            JSONObject o = new JSONObject();
            try {
                o.put("role", i % 2 == 0 ? "user" : "assistant");
                o.put("content", history.get(i));
            } catch (Exception ignored) {}
            arr.put(o);
        }
        return arr.toString();
    }

    private void speak(String text) {
        new AsyncTask<String, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(String... params) {
                try { return api.synthesize(params[0]); }
                catch (Exception e) { return null; }
            }
            @Override
            protected void onPostExecute(byte[] audio) {
                if (audio == null || audio.length == 0) return;
                try {
                    File f = new File(Environment.getExternalStorageDirectory(), "xiaoxing_reply.mp3");
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(audio);
                    fos.close();
                    MediaPlayer mp = new MediaPlayer();
                    mp.setDataSource(f.getAbsolutePath());
                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mp.prepare();
                    mp.start();
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer m) { m.release(); }
                    });
                } catch (Exception e) {
                    // 播放失败不影响对话
                }
            }
        }.execute(text);
    }

    private void append(String who, String text) {
        logView.append("\n【" + who + "】" + text + "\n");
        scrollView.post(new Runnable() {
            @Override public void run() { scrollView.fullScroll(ScrollView.FOCUS_DOWN); }
        });
    }
}
