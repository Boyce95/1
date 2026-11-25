package com.xiaoxing.control;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行云端下发的动作（白名单，无 ROOT）。
 * 与后端 adb.py 的工具名一一对应；未知动作一律拒绝。
 */
public class ActionExecutor {
    private static final String TAG = "ActionExecutor";
    private final Context ctx;

    public ActionExecutor(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public static class Result {
        final String action;
        final boolean ok;
        final String detail;
        Result(String a, boolean ok, String d) { this.action = a; this.ok = ok; this.detail = d; }
    }

    public String executeAll(String actionsJson) {
        List<Result> results = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(actionsJson);
            for (int i = 0; i < arr.length(); i++) {
                results.add(execute(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            results.add(new Result("parse", false, "解析失败: " + e.getMessage()));
        }
        return toJson(results);
    }

    private Result execute(JSONObject action) {
        String name = action.optString("name", "");
        JSONObject args = action.optJSONObject("arguments");
        if (args == null) args = new JSONObject();
        Log.i(TAG, "action " + name + " " + args);

        try {
            if ("adb_start_app".equals(name)) {
                String pkg = args.getString("package");
                boolean ok = launchApp(pkg);
                return new Result(name, ok, ok ? "已打开应用" : "打开失败，可能未安装: " + pkg);
            }
            if ("adb_keyevent".equals(name)) {
                String key = args.getString("key");
                if (!ControlService.isReady()) return new Result(name, false, "控制权限未开启");
                ControlService svc = ControlService.getInstance();
                svc.keyevent(key);
                return new Result(name, true, "已按键 " + key);
            }
            if ("adb_tap".equals(name)) {
                int x = args.getInt("x"), y = args.getInt("y");
                if (!ControlService.isReady()) return new Result(name, false, "控制权限未开启");
                ControlService.getInstance().tap(x, y);
                return new Result(name, true, "已点击 (" + x + "," + y + ")");
            }
            if ("adb_swipe".equals(name)) {
                int x1 = args.getInt("x1"), y1 = args.getInt("y1");
                int x2 = args.getInt("x2"), y2 = args.getInt("y2");
                int d = args.optInt("duration", 300);
                if (!ControlService.isReady()) return new Result(name, false, "控制权限未开启");
                ControlService.getInstance().swipe(x1, y1, x2, y2, d);
                return new Result(name, true, "已滑动");
            }
            if ("adb_list_packages".equals(name)) {
                String keyword = args.optString("keyword", "");
                return new Result(name, true, listApps(keyword));
            }
            if ("adb_devices".equals(name)) {
                return new Result(name, true, "本机即目标平板");
            }
            return new Result(name, false, "不支持的动作: " + name);
        } catch (Exception e) {
            return new Result(name, false, "执行失败: " + e.getMessage());
        }
    }

    /** 用 launch intent 启动应用，多数应用可用，不依赖无障碍。 */
    private boolean launchApp(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        try {
            Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "launchApp: " + e.getMessage());
            return false;
        }
    }

    /** 列出用户应用（名称=包名），供网页注入对话上下文，让 AI 知道能打开什么。 */
    public String listApps(String keyword) {
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        JSONArray arr = new JSONArray();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            CharSequence label = pm.getApplicationLabel(app);
            String name = label == null ? app.packageName : label.toString();
            String pkg = app.packageName;
            if (keyword != null && keyword.length() > 0
                    && !name.toLowerCase().contains(keyword.toLowerCase())
                    && !pkg.toLowerCase().contains(keyword.toLowerCase())) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("package", pkg);
                arr.put(o);
            } catch (Exception ignored) {}
            if (arr.length() >= 40) break;
        }
        return arr.toString();
    }

    private String toJson(List<Result> results) {
        JSONArray arr = new JSONArray();
        for (Result r : results) {
            try {
                JSONObject o = new JSONObject();
                o.put("action", r.action);
                o.put("ok", r.ok);
                o.put("detail", r.detail);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }
}
