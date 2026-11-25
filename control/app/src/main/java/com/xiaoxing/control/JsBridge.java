package com.xiaoxing.control;

import android.webkit.JavascriptInterface;

/** 注入到网页的 JS 接口：网页拿它执行设备动作、查询已安装应用、检测权限状态。 */
public class JsBridge {
    private final MainActivity activity;
    private final ActionExecutor executor;

    JsBridge(MainActivity activity) {
        this.activity = activity;
        this.executor = new ActionExecutor(activity);
    }

    @JavascriptInterface
    public String execActions(String actionsJson) {
        return executor.executeAll(actionsJson);
    }

    @JavascriptInterface
    public String getInstalledApps() {
        return executor.listApps("");
    }

    /** 读取当前屏幕的可交互元素（JSON 数组字符串），供 AI 自主操控时“看屏幕”。 */
    @JavascriptInterface
    public String getScreen() {
        if (!ControlService.isReady()) return "[]";
        try {
            return ControlService.getInstance().dumpScreen();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public boolean isControlReady() {
        return ControlService.isReady();
    }

    @JavascriptInterface
    public void openPermissionSettings() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { activity.openAccessibilitySettings(); }
        });
    }

    /** 自动操控开始/结束时调用，前台服务保活防止后台被系统回收。 */
    @JavascriptInterface
    public void setAutoPilot(boolean on) {
        if (on) KeepAliveService.start(activity);
        else KeepAliveService.stop(activity);
    }
}
