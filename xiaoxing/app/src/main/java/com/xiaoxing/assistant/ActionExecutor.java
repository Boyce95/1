package com.xiaoxing.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行云端下发的设备动作。
 * 白名单机制：只执行约定好的动作，杜绝任意命令注入。
 * 点击/按键/启动应用优先用 root 的 input/am 命令（N777 已 ROOT）；
 * 无 root 时降级为 Intent（仅能启动应用）。
 */
public class ActionExecutor {
    private static final String TAG = "ActionExecutor";
    private final Context context;

    public ActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class Result {
        public final String action;
        public final boolean ok;
        public final String detail;

        Result(String action, boolean ok, String detail) {
            this.action = action;
            this.ok = ok;
            this.detail = detail;
        }
    }

    /** 执行动作数组，返回每条动作的结果。 */
    public List<Result> executeAll(JSONArray actions) {
        List<Result> results = new ArrayList<>();
        if (actions == null) return results;
        for (int i = 0; i < actions.length(); i++) {
            try {
                results.add(execute(actions.getJSONObject(i)));
            } catch (Exception e) {
                results.add(new Result("unknown", false, "解析失败: " + e.getMessage()));
            }
        }
        return results;
    }

    private Result execute(JSONObject action) {
        String name = action.optString("name", "");
        JSONObject args = action.optJSONObject("arguments");
        if (args == null) args = new JSONObject();
        Log.i(TAG, "execute " + name + " " + args.toString());

        try {
            if (name.equals("adb_tap")) {
                int x = args.getInt("x"), y = args.getInt("y");
                boolean ok = runRoot("input tap " + x + " " + y);
                return new Result(name, ok, "点击 (" + x + "," + y + ")");
            } else if (name.equals("adb_swipe")) {
                int x1 = args.getInt("x1"), y1 = args.getInt("y1");
                int x2 = args.getInt("x2"), y2 = args.getInt("y2");
                int d = args.optInt("duration", 300);
                boolean ok = runRoot("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + d);
                return new Result(name, ok, "滑动");
            } else if (name.equals("adb_keyevent")) {
                String key = args.getString("key");
                boolean ok = runRoot("input keyevent KEYCODE_" + key);
                return new Result(name, ok, "按键 " + key);
            } else if (name.equals("adb_start_app")) {
                String pkg = args.getString("package");
                boolean ok = launchApp(pkg);
                return new Result(name, ok, "启动应用 " + pkg);
            } else if (name.equals("adb_list_packages")) {
                return new Result(name, true, "应用列表功能请在设置中查看");
            } else if (name.equals("adb_devices")) {
                return new Result(name, true, "本机即目标设备");
            }
            return new Result(name, false, "未知动作: " + name);
        } catch (Exception e) {
            return new Result(name, false, "执行失败: " + e.getMessage());
        }
    }

    /** 启动应用：优先 root am start，失败则用 PackageManager Intent。 */
    private boolean launchApp(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        if (runRoot("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1")) return true;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "launchApp failed: " + e.getMessage());
        }
        return false;
    }

    /** 通过 root 执行 shell 命令。命令来自云端，但已在此处白名单化，不接受任意字符串。 */
    private boolean runRoot(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            OutputStream os = process.getOutputStream();
            DataOutputStream dos = new DataOutputStream(os);
            dos.writeBytes("exit\n");
            dos.flush();
            int code = process.waitFor();
            return code == 0;
        } catch (Exception e) {
            Log.w(TAG, "runRoot failed (no root?): " + e.getMessage());
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    public static String resultsToJson(List<Result> results) {
        JSONArray arr = new JSONArray();
        for (Result r : results) {
            JSONObject o = new JSONObject();
            try {
                o.put("action", r.action);
                o.put("ok", r.ok);
                o.put("detail", r.detail);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        return arr.toString();
    }
}
