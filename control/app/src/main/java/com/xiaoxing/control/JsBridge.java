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
}
