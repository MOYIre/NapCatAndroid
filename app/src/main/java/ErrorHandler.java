package com.napcat.android;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.Date;

/**
 * 错误处理和恢复管理器
 * 负责处理 NapCat 运行时的各种错误并尝试恢复
 */
public class ErrorHandler {
    private static final String TAG = "ErrorHandler";
    private Context context;
    private long lastCrashTime = 0;
    private int crashCount = 0;
    private static final int CRASH_RESET_THRESHOLD = 300000; // 5分钟内重置崩溃计数
    private static final int MAX_CRASHES_BEFORE_RESET = 5; // 5次崩溃后重置
    private static final long MIN_RESTART_INTERVAL = 10000; // 最小重启间隔10秒

    public ErrorHandler(Context context) {
        this.context = context;
    }

    /**
     * 处理一般性错误
     */
    public void handleError(String error, String errorType) {
        Log.e(TAG, "Handling error of type " + errorType + ": " + error);
        
        // 记录错误到日志文件
        logErrorToFile(error, errorType);
        
        // 根据错误类型采取不同措施
        switch (errorType.toLowerCase()) {
            case "nodejs_error":
                handleNodeJSError(error);
                break;
            case "proot_error":
                handleProotError(error);
                break;
            case "file_error":
                handleFileError(error);
                break;
            case "network_error":
                handleNetworkError(error);
                break;
            default:
                handleGenericError(error);
                break;
        }
    }

    /**
     * 处理 Node.js 错误
     */
    private void handleNodeJSError(String error) {
        Log.d(TAG, "Handling Node.js error: " + error);
        
        // 检查是否是致命错误
        if (isFatalNodeJSError(error)) {
            Log.e(TAG, "Fatal Node.js error detected, initiating recovery");
            initiateRecovery();
        } else {
            Log.d(TAG, "Non-fatal Node.js error, continuing");
        }
    }

    /**
     * 处理 PRoot 错误
     */
    private void handleProotError(String error) {
        Log.d(TAG, "Handling PRoot error: " + error);
        
        // 检查是否是 PRoot 二进制文件问题
        if (error.toLowerCase().contains("exec format error") || 
            error.toLowerCase().contains("no such file")) {
            Log.e(TAG, "PRoot binary issue detected");
        }
    }

    /**
     * 处理文件错误
     */
    private void handleFileError(String error) {
        Log.d(TAG, "Handling file error: " + error);
        
        // 检查是否是文件权限问题
        if (error.toLowerCase().contains("permission denied")) {
            Log.e(TAG, "File permission error detected");
        }
    }

    /**
     * 处理网络错误
     */
    private void handleNetworkError(String error) {
        Log.d(TAG, "Handling network error: " + error);
        
        // 网络错误通常可以自动恢复，不需要特殊处理
    }

    /**
     * 处理通用错误
     */
    private void handleGenericError(String error) {
        Log.d(TAG, "Handling generic error: " + error);
        
        // 检查错误是否包含致命关键词
        if (isFatalError(error)) {
            Log.e(TAG, "Fatal error detected, initiating recovery");
            initiateRecovery();
        }
    }

    /**
     * 检查是否为致命 Node.js 错误
     */
    private boolean isFatalNodeJSError(String error) {
        // 定义一些致命错误的关键词
        String[] fatalKeywords = {
            "segmentation fault",
            "stack overflow",
            "out of memory",
            "fatal error",
            "uncaught exception"
        };
        
        String lowerError = error.toLowerCase();
        for (String keyword : fatalKeywords) {
            if (lowerError.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为致命错误
     */
    private boolean isFatalError(String error) {
        // 检查是否为崩溃相关错误
        String lowerError = error.toLowerCase();
        return lowerError.contains("crash") || 
               lowerError.contains("killed") || 
               lowerError.contains("exit code") ||
               lowerError.contains("error code");
    }

    /**
     * 启动恢复流程
     */
    public void initiateRecovery() {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否在短时间内频繁崩溃
        if (currentTime - lastCrashTime < CRASH_RESET_THRESHOLD) {
            crashCount++;
            if (crashCount >= MAX_CRASHES_BEFORE_RESET) {
                Log.e(TAG, "Too many crashes in short time, pausing recovery attempts");
                // 在这里可以实现更激进的恢复措施，如清理缓存等
                performDeepRecovery();
                crashCount = 0; // 重置计数
            }
        } else {
            // 重置崩溃计数（超过阈值时间）
            crashCount = 0;
        }
        
        lastCrashTime = currentTime;
        
        Log.d(TAG, "Recovery initiated, crash count: " + crashCount);
    }

    /**
     * 执行深度恢复
     * 当常规恢复失败时使用
     */
    private void performDeepRecovery() {
        Log.d(TAG, "Performing deep recovery");
        
        try {
            // 尝试清理临时文件
            cleanupTempFiles();
            
            // 尝试重新部署 NapCat 文件
            NodeRunner runner = new NodeRunner(context);
            runner.deployNapCatFiles();
            runner.deployProotRootfs();
            
            Log.d(TAG, "Deep recovery completed");
        } catch (Exception e) {
            Log.e(TAG, "Deep recovery failed", e);
        }
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles() {
        try {
            File filesDir = context.getFilesDir();
            File[] files = filesDir.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    // 删除临时或日志文件，保留核心文件
                    if (file.getName().endsWith(".tmp") || 
                        file.getName().endsWith(".log") ||
                        file.getName().contains("temp")) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during temp file cleanup", e);
        }
    }

    /**
     * 记录错误到文件
     */
    private void logErrorToFile(String error, String errorType) {
        try {
            String fileName = "error_" + System.currentTimeMillis() + ".log";
            String errorContent = String.format(
                "[%s] %s Error: %s%n", 
                new Date(), 
                errorType, 
                error
            );
            
            // 写入错误日志
            java.io.FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(errorContent.getBytes());
            fos.close();
            
            Log.d(TAG, "Error logged to file: " + fileName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to log error to file", e);
        }
    }

    /**
     * 检查是否可以重启服务
     * 基于崩溃频率和时间间隔
     */
    public boolean canRestartService() {
        long currentTime = System.currentTimeMillis();
        
        // 如果距离上次崩溃时间太短，不建议立即重启
        if (currentTime - lastCrashTime < MIN_RESTART_INTERVAL && crashCount > 0) {
            Log.d(TAG, "Too soon to restart service after crash");
            return false;
        }
        
        // 如果崩溃次数过多，暂停重启
        if (crashCount >= MAX_CRASHES_BEFORE_RESET) {
            Log.d(TAG, "Too many crashes, pausing service restart");
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取当前错误状态信息
     */
    public String getErrorStatus() {
        return String.format(
            "Crash count: %d, Last crash: %s", 
            crashCount, 
            lastCrashTime > 0 ? new Date(lastCrashTime) : "Never"
        );
    }
}