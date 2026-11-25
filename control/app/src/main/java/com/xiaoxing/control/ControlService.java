package com.xiaoxing.control;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 无障碍服务：用系统手势执行点击/滑动/按键，无需 ROOT。
 * 单例引用供 JS Bridge 调用；所有手势必须切回主线程派发。
 */
public class ControlService extends AccessibilityService {
    private static final String TAG = "ControlService";
    private static volatile ControlService instance;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 蓝心小V 的对话页；用户说的话在 ask_text_view 里，AI 的回复没有稳定 id。 */
    private static final String VIVO_PKG = "com.vivo.ai.copilot";
    private static final String VIVO_ASK_ID = "com.vivo.ai.copilot:id/ask_text_view";
    /** 小V 联动开关：开启后监听小V 对话里用户说的话，推给网页当指令。 */
    private static volatile boolean watchVivo = false;
    private volatile String lastVivoCmd = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 默认不采集屏幕内容；只有"小V 联动"开启时才读小V 的对话
        if (!watchVivo || event == null) return;
        try {
            CharSequence pkg = event.getPackageName();
            if (pkg == null || !VIVO_PKG.contentEquals(pkg)) return;
            int type = event.getEventType();
            if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            String cmd = readLastVivoAsk();
            if (cmd == null || cmd.length() == 0 || cmd.equals(lastVivoCmd)) return;
            lastVivoCmd = cmd;
            Log.i(TAG, "vivo command: " + cmd);
            MainActivity act = MainActivity.getInstance();
            if (act != null) {
                act.runJs("window.__onVivoCommand && window.__onVivoCommand("
                        + JSONObject.quote(cmd) + ");");
            }
        } catch (Exception e) {
            Log.w(TAG, "onAccessibilityEvent: " + e.getMessage());
        }
    }

    /** 取小V 对话里最后一条"用户说的话"。找不到返回 null（比如小V 不在前台）。 */
    private String readLastVivoAsk() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(VIVO_ASK_ID);
            if (list == null || list.isEmpty()) return null;
            String last = null;
            for (AccessibilityNodeInfo n : list) {
                CharSequence tx = n.getText();
                if (tx == null) continue;
                String s = tx.toString().trim();
                if (s.length() > 0) last = s;
            }
            return last;
        } catch (Exception e) {
            return null;
        } finally {
            try { root.recycle(); } catch (Exception ignored) {}
        }
    }

    /** 开关小V 联动。开启时清空上次记录，避免立刻重放旧指令。 */
    public static void setVivoWatch(boolean on) {
        watchVivo = on;
        ControlService s = instance;
        if (s != null) s.lastVivoCmd = "";
        Log.i(TAG, "vivo watch = " + on);
    }

    public static boolean isVivoWatchOn() {
        return watchVivo;
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

    /**
     * 读取当前屏幕的可交互元素，返回 JSON 数组。
     * 每项含：text(可见文本)、desc(内容描述)、cls(控件类型)、clickable、editable、
     * bounds(边界[x1,y1,x2,y2])。只采集有文本或可点击/可编辑的节点，控制体积。
     */
    public String dumpScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return "[]";
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "[]";
        JSONArray arr = new JSONArray();
        try {
            collectNodes(root, arr, 0);
        } finally {
            try { root.recycle(); } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    private void collectNodes(AccessibilityNodeInfo node, JSONArray arr, int depth) {
        if (node == null || depth > 12 || arr.length() >= 120) return;
        try {
            CharSequence txt = node.getText();
            CharSequence desc = node.getContentDescription();
            boolean clickable = node.isClickable();
            boolean editable = node.isEditable();
            boolean scrollable = node.isScrollable();
            String text = txt == null ? "" : txt.toString().trim();
            String cd = desc == null ? "" : desc.toString().trim();
            // 只有带文本、可点击、可编辑或可滚动的节点才纳入，避免噪音
            if ((text.length() > 0 || cd.length() > 0 || clickable || editable || scrollable) && arr.length() < 120) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    JSONObject o = new JSONObject();
                    o.put("text", text.length() > 80 ? text.substring(0, 80) : text);
                    o.put("desc", cd.length() > 60 ? cd.substring(0, 60) : cd);
                    o.put("cls", node.getClassName() == null ? "" : node.getClassName().toString());
                    o.put("clickable", clickable);
                    o.put("editable", editable);
                    o.put("scrollable", scrollable);
                    JSONArray b = new JSONArray();
                    b.put(r.left); b.put(r.top); b.put(r.right); b.put(r.bottom);
                    o.put("bounds", b);
                    arr.put(o);
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    collectNodes(child, arr, depth + 1);
                } finally {
                    if (child != null) { try { child.recycle(); } catch (Exception ignored) {} }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "collectNodes: " + e.getMessage());
        }
    }

    /** 按文本点击：先找完全匹配，再找包含匹配；找到就点击。返回是否成功。 */
    public boolean tapText(String keyword) {
        if (keyword == null || keyword.trim().length() == 0) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(keyword);
            if (nodes == null || nodes.isEmpty()) return false;
            // 优先可点击且文本完全匹配的
            AccessibilityNodeInfo best = null;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null) continue;
                CharSequence t = n.getText();
                if (t != null && t.toString().trim().equals(keyword.trim()) && n.isClickable()) { best = n; break; }
            }
            if (best == null) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null && n.isClickable()) { best = n; break; }
                }
            }
            if (best == null) {
                // 没有可点击的，用文本节点中心坐标点一下
                for (AccessibilityNodeInfo n : nodes) {
                    if (n == null) continue;
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    if (r.width() > 0 && r.height() > 0) {
                        tap(r.centerX(), r.centerY());
                        return true;
                    }
                }
                return false;
            }
            final AccessibilityNodeInfo target = best;
            try { return target.performAction(AccessibilityNodeInfo.ACTION_CLICK); } catch (Exception e) { return false; }
        } catch (Exception e) {
            Log.w(TAG, "tapText: " + e.getMessage());
            return false;
        } finally {
            try { root.recycle(); } catch (Exception ignored) {}
        }
    }

    /** 给当前聚焦的输入框填入文本（清空后写入）。 */
    public boolean inputText(String text) {
        if (text == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) return false;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            final AccessibilityNodeInfo target = focused;
            final Bundle a = args;
            try { return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a); } catch (Exception e) { return false; }
        } catch (Exception e) {
            Log.w(TAG, "inputText: " + e.getMessage());
            return false;
        } finally {
            try { root.recycle(); } catch (Exception ignored) {}
        }
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
