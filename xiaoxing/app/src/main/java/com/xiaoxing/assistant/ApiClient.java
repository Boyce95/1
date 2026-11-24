package com.xiaoxing.assistant;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.UUID;

/** 与云端服务的 HTTP 通信，使用 JDK 原生 HttpURLConnection，兼容 Android 4.x。 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    // StandardCharsets 是 API 19 才加入的，N777 的 Android 4.x(API 17) 用 Charset.forName
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static class VoiceResult {
        public String reply;
        public String transcript;
        public JSONArray actions;
    }

    /** 上传录音，拿回识别文本、回答、以及待执行的动作数组。 */
    public VoiceResult sendVoice(byte[] audio, String historyJson) throws Exception {
        String boundary = "----xiaoxing" + UUID.randomUUID().toString().replace("-", "");
        URL url = new URL(baseUrl + "/api/agent-voice");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            // history 字段
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"history\"\r\n\r\n");
            out.write(historyJson.getBytes("UTF-8"));
            out.writeBytes("\r\n");
            // audio 字段
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"audio\"; filename=\"voice.m4a\"\r\n");
            out.writeBytes("Content-Type: audio/mp4\r\n\r\n");
            out.write(audio);
            out.writeBytes("\r\n");
            out.writeBytes("--" + boundary + "--\r\n");
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            String body = readStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + body);
            }
            JSONObject json = new JSONObject(body);
            VoiceResult result = new VoiceResult();
            result.reply = json.optString("reply", "");
            result.transcript = json.optString("transcript", "");
            result.actions = json.optJSONArray("actions");
            return result;
        } finally {
            conn.disconnect();
        }
    }

    /** 文本对话（无语音时也可用）。 */
    public VoiceResult sendText(String message, String historyJson) throws Exception {
        URL url = new URL(baseUrl + "/api/agent");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            payload.put("history", new JSONArray(historyJson));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(conn.getOutputStream(), UTF8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();

            int code = conn.getResponseCode();
            String body = readStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body);
            JSONObject json = new JSONObject(body);
            VoiceResult result = new VoiceResult();
            result.reply = json.optString("reply", "");
            result.transcript = json.optString("transcript", message);
            result.actions = json.optJSONArray("actions");
            return result;
        } finally {
            conn.disconnect();
        }
    }

    /** 下载 TTS 音频。 */
    public byte[] synthesize(String text) throws Exception {
        URL url = new URL(baseUrl + "/api/tts");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(conn.getOutputStream(), UTF8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) return readBytes(conn.getInputStream());
            throw new Exception("TTS HTTP " + code);
        } finally {
            conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        return new String(readBytes(in), "UTF-8");
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
