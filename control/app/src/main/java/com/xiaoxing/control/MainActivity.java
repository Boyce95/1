package com.xiaoxing.control;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;

/** 主界面：顶部权限状态 + WebView 加载云端网页；网页通过 JS Bridge 执行设备动作。 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREF = "xiaoxing";
    private static final String KEY_URL = "server_url";
    private static final String DEFAULT_URL = "https://xiaoxing-ai-assistant.app.workbuddy.host/?sharecode=EEKAN2MQLR";

    private WebView web;
    private TextView tvStatus;
    private Button btnPerm;

    /** 供无障碍服务反向调用网页（小V 联动时把用户说的话推进来）。 */
    private static volatile MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    /** 在 UI 线程执行一段 JS；网页没加载好或已销毁时静默忽略。 */
    public void runJs(final String js) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    if (web != null) web.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);

        web = (WebView) findViewById(R.id.webview);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        btnPerm = (Button) findViewById(R.id.btn_permission);

        SharedPreferences sp = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String url = sp.getString(KEY_URL, DEFAULT_URL);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setUserAgentString(ws.getUserAgentString() + " XiaoXingControl/1.0");

        web.addJavascriptInterface(new JsBridge(this), "XiaoXingBridge");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl(url);

        btnPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPermissionDialog(); }
        });

        // 长按标题栏可修改服务器地址
        tvStatus.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showUrlDialog(); return true; }
        });

        requestAudioPermission();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean ready = ControlService.isReady();
        tvStatus.setText(ready ? "控制服务：已就绪（长按改地址）" : "控制服务：未开启（点右侧按钮）");
        btnPerm.setText(ready ? "权限已开" : "开启控制权限");
    }

    void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); } catch (Exception e) { Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show(); }
    }

    private void showPermissionDialog() {
        if (ControlService.isReady()) {
            Toast.makeText(this, "控制权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("开启小星控制权限")
            .setMessage("将在系统设置中打开无障碍列表，找到“小星控制服务”并开启开关。开启后 AI 才能模拟点击和按键。")
            .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { openAccessibilitySettings(); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showUrlDialog() {
        final SharedPreferences sp = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(sp.getString(KEY_URL, DEFAULT_URL));
        new AlertDialog.Builder(this)
            .setTitle("云端服务地址")
            .setMessage("输入公网链接（长按输入框可全选）")
            .setView(input)
            .setPositiveButton("保存并刷新", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
                    sp.edit().putString(KEY_URL, url).apply();
                    web.loadUrl(url);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
