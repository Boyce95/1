package com.xiaoxing.control;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * 无障碍服务：用系统手势执行点击/滑动/按键，无需 ROOT。
 * 单例引用供 JS Bridge 调用；所有手势必须切回主线程派发。
 */
public class ControlService extends AccessibilityService {
    private static final String TAG = "ControlService";
    private static volatile ControlService instance;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不主动采集屏幕内容，仅在收到指令时派发手势
    }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static ControlService getInstance() {
        return instance;
    }

    /** 点击坐标（屏幕绝对像素）。 */
    public void tap(final int x, final int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { toast("系统版本过低，不支持手势"); return; }
        main.post(new Runnable() {
            @Override public void run() {
                Path p = new Path();
                p.moveTo(x, y);
                p.lineTo(x, y + 1);
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(p, 0, 80);
                dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
            }
        });
    }

    /** 滑动手势。 */
    public void swipe(final int x1, final int y1, final int x2, final int y2, final int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { toast("系统版本过低，不支持手势"); return; }
        main.post(new Runnable() {
            @Override public void run() {
                Path p = new Path();
                p.moveTo(x1, y1);
                p.lineTo(x2, y2);
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(p, 0, duration);
                dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
            }
        });
    }

    /** 系统按键：HOME / BACK / RECENTS / POWER(部分机型) 等。 */
    public void keyevent(String key) {
        final int action;
        if ("HOME".equalsIgnoreCase(key)) action = GLOBAL_ACTION_HOME;
        else if ("BACK".equalsIgnoreCase(key)) action = GLOBAL_ACTION_BACK;
        else if ("MENU".equalsIgnoreCase(key) || "RECENTS".equalsIgnoreCase(key)) action = GLOBAL_ACTION_RECENTS;
        else if ("NOTIFICATIONS".equalsIgnoreCase(key)) action = GLOBAL_ACTION_NOTIFICATIONS;
        else if ("POWER".equalsIgnoreCase(key)) action = GLOBAL_ACTION_POWER_DIALOG;
        else { toast("不支持的按键: " + key); return; }
        main.post(new Runnable() {
            @Override public void run() { performGlobalAction(action); }
        });
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
