package com.xiaoxing.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台保活服务：自动操控期间挂一个常驻通知，防止系统把进程回收导致循环中断。
 * 因为 AI 打开别的应用后，本应用的 WebView 会退到后台，vivo 等系统对后台进程管控较严。
 */
public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "xiaoxing_autopilot";
    private static final int NOTI_ID = 1001;

    public static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {}
    }

    public static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, KeepAliveService.class)); } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID, "自动操控", NotificationManager.IMPORTANCE_LOW);
                    ch.setDescription("小星正在操作平板时保持运行");
                    nm.createNotificationChannel(ch);
                }
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(this, CHANNEL_ID);
            } else {
                b = new Notification.Builder(this);
            }
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentTitle("小星正在操作平板")
                    .setContentText("任务进行中，完成后会自动结束")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentIntent(pi)
                    .setOngoing(true);
            startForeground(NOTI_ID, b.build());
        } catch (Exception e) {
            // 拿不到通知权限等情况，退化为普通服务
            try { stopSelf(); } catch (Exception ignored) {}
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
