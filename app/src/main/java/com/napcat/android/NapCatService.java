package com.napcat.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class NapCatService extends Service {
    private static final String TAG = "NapCatService";
    private static final String CHANNEL_ID = "NapCatServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String BROADCAST_ACTION = "com.napcat.android.SERVICE_STATUS";

    private NodeRunner nodeRunner;
    private boolean serviceStarted = false;
    private Handler mainHandler;
    private int restartAttempts = 0;
    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long RESTART_DELAY_MS = 10000;

    // 保活相关
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NapCatService created");
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "NapCatService started");

        startForeground(NOTIFICATION_ID, createNotification("正在启动 NapCat...", false));
        broadcastStatus("starting", "正在初始化 NapCat 服务...");

        if (nodeRunner == null) {
            nodeRunner = new NodeRunner(this);
        }

        if (!serviceStarted) {
            startNapCatProcess();
            serviceStarted = true;
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "NapCat 服务";
            String description = "NapCat 后台服务";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String content, boolean isError) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NapCat")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        builder.setColor(isError ? 0xFFFF0000 : 0xFF4CAF50);
        return builder.build();
    }

    private void broadcastStatus(String status, String message) {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra("status", status);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "napcat:NapCatService"
                );
                wakeLock.acquire(10 * 60 * 1000L); // 10分钟超时
                Log.d(TAG, "WakeLock acquired");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            } catch (Exception e) {
                Log.e(TAG, "Failed to release WakeLock", e);
            }
        }
    }

    private void startNapCatProcess() {
        updateNotification("正在启动 NapCat...", false);
        broadcastStatus("starting", "正在启动 NapCat 进程...");

        isRunning.set(true);

        boolean started = nodeRunner.startNapCat(new NodeRunner.NodeRunnerCallback() {
            @Override
            public void onOutput(String output) {
                Log.d(TAG, "NapCat: " + output);

                // 检测关键启动信号
                if (output.contains("Now listening") || 
                    output.contains("listening on") ||
                    output.contains("HTTP server started") ||
                    output.contains(":6099") ||
                    output.contains("WebUI") ||
                    output.contains("started")) {
                    
                    // 延迟一点再通知就绪，确保端口真正绑定
                    mainHandler.postDelayed(() -> {
                        broadcastStatus("ready", "NapCat 服务已就绪");
                        updateNotification("NapCat 运行中 - WebUI 端口 6099", false);
                        isRunning.set(true);
                        restartAttempts = 0;
                    }, 2000);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "NapCat error: " + error);
                
                // 检测是否是启动错误
                if (error.contains("Error") || error.contains("error") || error.contains("failed")) {
                    broadcastStatus("error", error);
                }
            }

            @Override
            public void onExit(int exitCode) {
                Log.d(TAG, "NapCat exited: " + exitCode);
                isRunning.set(false);
                serviceStarted = false;

                if (exitCode != 0 && restartAttempts < MAX_RESTART_ATTEMPTS) {
                    restartAttempts++;
                    updateNotification("重启中... (" + restartAttempts + "/" + MAX_RESTART_ATTEMPTS + ")", true);
                    broadcastStatus("restarting", "正在重启 NapCat...");

                    mainHandler.postDelayed(() -> startNapCatProcess(), RESTART_DELAY_MS);
                } else {
                    updateNotification("NapCat 已停止", true);
                    broadcastStatus("stopped", "NapCat 已停止 (退出码: " + exitCode + ")");
                }
            }

            @Override
            public void onProgress(int percent, String message) {
                broadcastStatus("deploying", message);
                updateNotification(message, false);
            }
        });

        if (started) {
            updateNotification("NapCat 启动中...", false);
            broadcastStatus("starting", "NapCat 进程已启动，等待 WebUI 就绪...");
        } else {
            updateNotification("启动 NapCat 失败", true);
            broadcastStatus("error", "启动 NapCat 进程失败");
        }
    }

    private void updateNotification(String content, boolean isError) {
        Notification notification = createNotification(content, isError);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NapCatService destroyed");

        isRunning.set(false);
        
        if (nodeRunner != null) {
            nodeRunner.stopNapCat();
        }

        releaseWakeLock();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
