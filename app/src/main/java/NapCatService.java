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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class NapCatService extends Service {
    private static final String TAG = "NapCatService";
    private static final String CHANNEL_ID = "NapCatServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private NodeRunner nodeRunner;
    private boolean serviceStarted = false;
    private Handler mainHandler;
    private int restartAttempts = 0;
    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long RESTART_DELAY_MS = 10000; // 10 seconds
    
    // Keep track of the last restart time to avoid rapid restarts
    private long lastRestartTime = 0;
    private static final long MIN_RESTART_INTERVAL_MS = 30000; // 30 seconds
    private ErrorHandler errorHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NapCatService created");
        mainHandler = new Handler(Looper.getMainLooper());
        errorHandler = new ErrorHandler(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "NapCatService started");
        
        // Show foreground notification
        startForeground(NOTIFICATION_ID, createNotification("NapCat Service Starting...", false));
        
        // Initialize NodeRunner with context
        if (nodeRunner == null) {
            nodeRunner = new NodeRunner(this);
            
            // Deploy necessary files before starting
            nodeRunner.deployNapCatFiles();
            nodeRunner.deployProotRootfs();
        }
        
        // Start NapCat process in a background thread
        startNapCatProcess();
        
        // Return START_STICKY to ensure service is restarted if killed
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "NapCat Service Channel";
            String description = "Notifications for NapCat background service";
            int importance = NotificationManager.IMPORTANCE_LOW; // Low importance to avoid打扰 user
            
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            serviceChannel.setDescription(description);
            
            // Register the channel with the system
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification(String content, boolean isError) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NapCat Service")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority
                .setOngoing(true); // Ongoing notification for foreground service

        if (isError) {
            builder.setColor(0xFFFF0000); // Red color for errors
        } else {
            builder.setColor(0xFF4CAF50); // Green color for normal status
        }

        return builder.build();
    }

    private void startNapCatProcess() {
        // Update notification to show starting status
        updateNotification("Starting NapCat service...", false);
        
        boolean started = nodeRunner.startNapCat(new NodeRunner.NodeRunnerCallback() {
            @Override
            public void onOutput(String output) {
                Log.d(TAG, "NapCat output: " + output);
                
                // Only update notification for significant messages
                if (output.toLowerCase().contains("error") || 
                    output.toLowerCase().contains("warning") ||
                    output.toLowerCase().contains("success") ||
                    output.toLowerCase().contains("ready") ||
                    output.toLowerCase().contains("running")) {
                    
                    String displayText = output.length() > 50 ? 
                        output.substring(0, 50) + "..." : output;
                    updateNotification("NapCat: " + displayText, false);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "NapCat error: " + error);
                
                // Use error handler to process the error
                String errorType = analyzeErrorType(error);
                errorHandler.handleError(error, errorType);
                
                // Update notification with error status
                updateNotification("NapCat Error: " + error, true);
            }

            @Override
            public void onExit(int exitCode) {
                Log.d(TAG, "NapCat exited with code: " + exitCode);
                
                // Record the exit with error handler
                errorHandler.handleError("NapCat exited with code: " + exitCode, "process_exit");
                
                // Update notification about exit
                updateNotification("NapCat exited (code: " + exitCode + "). Restarting...", true);
                
                // Check if we should restart based on the exit code and timing
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastRestartTime < MIN_RESTART_INTERVAL_MS) {
                    Log.d(TAG, "Too soon to restart, waiting...");
                    // Update notification with wait status
                    updateNotification("Waiting before restart...", true);
                    return;
                }
                
                // Limit restart attempts
                if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
                    Log.e(TAG, "Max restart attempts reached, stopping service");
                    updateNotification("Max restart attempts reached. Service stopped.", true);
                    return;
                }
                
                lastRestartTime = currentTime;
                restartAttempts++;
                
                Log.d(TAG, "Attempting to restart NapCat process... (attempt " + restartAttempts + "/" + MAX_RESTART_ATTEMPTS + ")");
                
                // Try to restart the service after a delay
                mainHandler.postDelayed(() -> {
                    if (nodeRunner != null) {
                        Log.d(TAG, "Restarting NapCat process...");
                        startNapCatProcess();
                    }
                }, RESTART_DELAY_MS);
            }
        });

        if (started) {
            serviceStarted = true;
            restartAttempts = 0; // Reset restart attempts on successful start
            Log.d(TAG, "NapCat process started successfully");
            updateNotification("NapCat Service Running", false);
        } else {
            Log.e(TAG, "Failed to start NapCat process");
            
            // Try again after delay, but limit attempts
            if (restartAttempts < MAX_RESTART_ATTEMPTS) {
                restartAttempts++;
                mainHandler.postDelayed(() -> {
                    if (nodeRunner != null) {
                        Log.d(TAG, "Retrying to start NapCat process... (attempt " + restartAttempts + "/" + MAX_RESTART_ATTEMPTS + ")");
                        startNapCatProcess();
                    }
                }, RESTART_DELAY_MS);
            } else {
                Log.e(TAG, "Max start attempts reached, stopping service");
                updateNotification("Failed to start NapCat after " + MAX_RESTART_ATTEMPTS + " attempts", true);
            }
        }
    }
    
    /**
     * 分析错误类型
     */
    private String analyzeErrorType(String errorLine) {
        String lowerError = errorLine.toLowerCase();
        
        if (lowerError.contains("permission") || lowerError.contains("access")) {
            return "file_error";
        } else if (lowerError.contains("network") || lowerError.contains("connection") || 
                   lowerError.contains("socket") || lowerError.contains("http")) {
            return "network_error";
        } else if (lowerError.contains("proot")) {
            return "proot_error";
        } else if (lowerError.contains("node") || lowerError.contains("javascript")) {
            return "nodejs_error";
        } else {
            return "general_error";
        }
    }
    
    private void updateNotification(String content, boolean isError) {
        Notification notification = createNotification(content, isError);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NapCatService destroyed");
        
        // Clean up NapCat process
        if (nodeRunner != null) {
            nodeRunner.stopNapCat();
        }
        
        // Cancel any pending restart attempts
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding for this service
    }
}