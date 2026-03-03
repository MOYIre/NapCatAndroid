package com.napcat.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class NodeRunner {
    private static final String TAG = "NodeRunner";
    private Process nodeProcess;
    private boolean isRunning = false;
    private Context appContext;
    private ErrorHandler errorHandler;
    
    // PRoot 方案相关路径 (基于 SealDice 实现)
    private static final String PROOT_DIR = "proot";
    private static final String PROOT_BINARY_NAME = "proot";
    private static final String PROOT_LOADER_NAME = "loader";
    private static final String PROOT_LIBTALLOC_NAME = "libtalloc.so.2";
    private static final String PROOT_TMP_DIR_NAME = "tmp";
    private static final String ROOTFS_DIR = "rootfs";
    private static final String NAPCAT_DIR = "napcat";
    private static final String START_SCRIPT_NAME = "start.sh";
    private static final String BOOT_SCRIPT_NAME = "boot.sh";
    private static final String NAPCAT_ENTRY_FILE = "napcat.js";
    private static final String NAPCAT_CORE_BINARY = "napcat-core"; // placeholder for compiled NapCat binary

    public interface NodeRunnerCallback {
        void onOutput(String output);
        void onError(String error);
        void onExit(int exitCode);
    }

    public NodeRunner(Context context) {
        this.appContext = context;
        this.errorHandler = new ErrorHandler(context);
    }

    /**
     * 在 Android 上启动 Node.js 进程来运行 NapCat
     * 支持多种运行方案：
     * 1. PRoot 方案 (推荐)：使用 PRoot 在 Android 上创建 Linux 环境（基于 SealDice 实现）
     * 2. Termux 方案：使用 Termux 中的 Node.js
     * 3. 直接 Node.js 方案：使用 Android 上直接部署的 Node.js
     */
    public boolean startNapCat(NodeRunnerCallback callback) {
        Log.d(TAG, "Attempting to start NapCat...");
        
        // 检查是否可以安全重启（基于错误处理机制）
        if (!errorHandler.canRestartService()) {
            Log.e(TAG, "Service restart blocked by error handler due to frequent crashes");
            if (callback != null) {
                callback.onError("Service temporarily disabled due to repeated crashes. Please check logs.");
            }
            return false;
        }
        
        try {
            // 检查是否已有运行的进程
            if (isRunning) {
                Log.w(TAG, "NapCat is already running");
                return false;
            }

            // 尝试 PRoot 方案（基于 SealDice 实现）
            boolean started = startWithPRoot(callback);
            if (started) {
                Log.d(TAG, "NapCat started with PRoot successfully");
                return true;
            }

            // 如果 PRoot 不可用，尝试 Termux 方案
            started = startWithTermux(callback);
            if (started) {
                Log.d(TAG, "NapCat started with Termux successfully");
                return true;
            }

            // 如果 Termux 也不可用，尝试直接 Node.js 方案
            started = startWithDirectNode(callback);
            if (started) {
                Log.d(TAG, "NapCat started with direct Node.js successfully");
                return true;
            }

            Log.e(TAG, "Failed to start NapCat with any available method");
            if (callback != null) {
                callback.onError("Failed to start NapCat: No suitable Node.js runtime found");
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start NapCat process", e);
            errorHandler.handleError(e.getMessage(), "startup_error");
            if (callback != null) {
                callback.onError("Failed to start NapCat: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 使用 PRoot 方案启动 NapCat
     * 这是推荐的方案，基于 SealDice Android 的实现方式
     */
    private boolean startWithPRoot(NodeRunnerCallback callback) {
        try {
            Log.d(TAG, "Attempting to start NapCat with PRoot (SealDice approach)...");
            
            // 确保 PRoot 环境已部署
            if (!deployPRootEnvironment()) {
                Log.e(TAG, "Failed to deploy PRoot environment");
                return false;
            }

            // 确保 NapCat 文件已部署
            String napcatPath = getNapCatPath();
            if (!new File(napcatPath + "/" + NAPCAT_ENTRY_FILE).exists()) {
                Log.w(TAG, "NapCat files not found at: " + napcatPath);
                // 如果 JS 文件不存在，尝试使用二进制版本
                if (!new File(napcatPath + "/" + NAPCAT_CORE_BINARY).exists()) {
                    Log.e(TAG, "NapCat binary not found at: " + napcatPath);
                    return false;
                }
            }

            // 构建启动脚本命令
            String startScriptPath = new File(appContext.getFilesDir(), START_SCRIPT_NAME).getAbsolutePath();
            String napcatBinaryPath = new File(napcatPath, NAPCAT_ENTRY_FILE).getAbsolutePath();
            
            // 创建启动脚本
            createStartScript();
            
            String[] command = {
                "sh",
                "-c",
                String.format(
                    "cd %s && chmod 755 %s %s && %s %s",
                    appContext.getFilesDir().getAbsolutePath(),
                    startScriptPath,
                    napcatBinaryPath,
                    startScriptPath,
                    napcatBinaryPath
                )
            };

            Log.d(TAG, "Executing PRoot command: " + command[2]);
            nodeProcess = Runtime.getRuntime().exec(command);
            isRunning = true;

            startOutputThreads(callback);
            Log.d(TAG, "NapCat process started with PRoot successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start NapCat with PRoot", e);
            errorHandler.handleError(e.getMessage(), "proot_error");
            return false;
        }
    }

    /**
     * 部署完整的 PRoot 环境（基于 SealDice 实现）
     */
    private boolean deployPRootEnvironment() {
        try {
            File filesDir = appContext.getFilesDir();
            
            // 创建 PRoot 目录结构
            File prootDir = new File(filesDir, PROOT_DIR);
            if (!prootDir.exists()) {
                prootDir.mkdirs();
            }
            
            // 创建 PRoot tmp 目录
            File prootTmpDir = new File(prootDir, PROOT_TMP_DIR_NAME);
            if (!prootTmpDir.exists()) {
                prootTmpDir.mkdirs();
            }
            
            // 部署 PRoot 二进制文件
            String prootPath = new File(prootDir, PROOT_BINARY_NAME).getAbsolutePath();
            if (!new File(prootPath).exists()) {
                deployBinaryFromAssets(PROOT_DIR + "/" + PROOT_BINARY_NAME, prootPath);
            }
            
            // 部署 loader
            String loaderPath = new File(prootDir, PROOT_LOADER_NAME).getAbsolutePath();
            if (!new File(loaderPath).exists()) {
                deployBinaryFromAssets(PROOT_DIR + "/" + PROOT_LOADER_NAME, loaderPath);
            }
            
            // 部署 libtalloc
            String libtallocPath = new File(prootDir, PROOT_LIBTALLOC_NAME).getAbsolutePath();
            if (!new File(libtallocPath).exists()) {
                deployBinaryFromAssets(PROOT_DIR + "/" + PROOT_LIBTALLOC_NAME, libtallocPath);
            }
            
            // 部署 rootfs（简化版 - 实际部署时可能需要完整 Linux 环境）
            File rootfsDir = new File(filesDir, ROOTFS_DIR);
            if (!rootfsDir.exists()) {
                rootfsDir.mkdirs();
            }
            
            // 部署启动脚本
            createStartScript();
            createBootScript();
            
            Log.d(TAG, "PRoot environment deployed successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to deploy PRoot environment", e);
            errorHandler.handleError(e.getMessage(), "file_error");
            return false;
        }
    }

    /**
     * 创建启动脚本
     */
    private void createStartScript() throws IOException {
        File startScriptFile = new File(appContext.getFilesDir(), START_SCRIPT_NAME);
        
        String startScript = String.format(
            "#!/bin/sh\n" +
            "# NapCat for Android startup script\n" +
            "# Based on the SealDice implementation approach\n\n" +
            "DIR=$(realpath $(dirname \"$0\"))\n\n" +
            "unset LD_PRELOAD\n" +
            "export LD_LIBRARY_PATH=$DIR/%s:$LD_LIBRARY_PATH\n" +
            "export PROOT_TMP_DIR=$DIR/%s/%s\n" +
            "export PROOT_LOADER=$DIR/%s/%s\n\n" +
            "EXE=$1\n" +
            "if [ -z \"$EXE\" ]; then\n" +
            "    echo \"Usage: $0 [napcat-binary]\"\n" +
            "    exit 1\n" +
            "fi\n\n" +
            "if [ \"$1\" != \"shell\" ]; then\n" +
            "    EXE_PATH=$(realpath $(dirname \"$1\"))\n" +
            "    EXE_FN=$(basename \"$1\")\n" +
            "else\n" +
            "    EXE_PATH=$(pwd)\n" +
            "fi\n\n" +
            "# Run NapCat binary inside PRoot environment\n" +
            "$DIR/%s/%s -r $DIR/%s -w / -b /proc -b /dev -b /sys -b /data -b /sdcard -b $(pwd):/work -b $EXE_PATH:/root /bin/busybox sh /boot.sh $EXE_FN\n",
            PROOT_DIR, PROOT_DIR, PROOT_TMP_DIR_NAME, PROOT_DIR, PROOT_LOADER_NAME, 
            PROOT_DIR, PROOT_BINARY_NAME, ROOTFS_DIR
        );
        
        try (FileOutputStream fos = new FileOutputStream(startScriptFile)) {
            fos.write(startScript.getBytes());
            startScriptFile.setExecutable(true, true);
            Log.d(TAG, "Start script created: " + startScriptFile.getAbsolutePath());
        }
    }

    /**
     * 创建 boot 脚本
     */
    private void createBootScript() throws IOException {
        File bootScriptFile = new File(appContext.getFilesDir(), BOOT_SCRIPT_NAME);
        
        String bootScript = 
            "#!/bin/sh\n" +
            "# NapCat for Android boot script\n" +
            "# Based on the SealDice implementation approach\n\n" +
            "export HOME=/root\n" +
            "export PATH=/bin:/sbin:/usr/bin:/usr/sbin\n\n" +
            "cd /work\n\n" +
            "if [ -z \"$1\" ]; then\n" +
            "    /bin/busybox sh\n" +
            "    exit 0\n" +
            "fi\n\n" +
            "# Make the NapCat binary executable\n" +
            "chmod +x /root/\"$1\"\n\n" +
            "# Check if it's a Node.js file or binary\n" +
            "if echo \"$1\" | grep -q \"\\.js$\"; then\n" +
            "    # If it's a JS file, try to run with node\n" +
            "    if [ -x /usr/bin/node ]; then\n" +
            "        /usr/bin/node /root/\"$1\"\n" +
            "    else\n" +
            "        echo \"Node.js not found in PRoot environment\"\n" +
            "        exit 1\n" +
            "    fi\n" +
            "else\n" +
            "    # If it's a binary, run directly\n" +
            "    /root/\"$1\"\n" +
            "fi\n";
        
        try (FileOutputStream fos = new FileOutputStream(bootScriptFile)) {
            fos.write(bootScript.getBytes());
            bootScriptFile.setExecutable(true, true);
            Log.d(TAG, "Boot script created: " + bootScriptFile.getAbsolutePath());
        }
    }

    /**
     * 使用 Termux 方案启动 NapCat
     */
    private boolean startWithTermux(NodeRunnerCallback callback) {
        try {
            Log.d(TAG, "Attempting to start NapCat with Termux...");
            
            // 检查 Termux Node.js 是否可用
            String termuxNodePath = "/data/data/com.termux/files/usr/bin/node";
            if (!new File(termuxNodePath).exists()) {
                Log.w(TAG, "Termux Node.js not found at: " + termuxNodePath);
                return false;
            }

            // 确保 NapCat 文件已部署
            String napcatPath = getNapCatPath();
            if (!new File(napcatPath + "/" + NAPCAT_ENTRY_FILE).exists()) {
                Log.w(TAG, "NapCat files not found at: " + napcatPath);
                return false;
            }

            // 构建 Termux 命令
            String[] command = {
                "sh",
                "-c",
                String.format(
                    "cd %s && %s %s",
                    napcatPath,
                    termuxNodePath,
                    NAPCAT_ENTRY_FILE
                )
            };

            Log.d(TAG, "Executing Termux command: " + command[2]);
            nodeProcess = Runtime.getRuntime().exec(command);
            isRunning = true;

            startOutputThreads(callback);
            Log.d(TAG, "NapCat process started with Termux successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start NapCat with Termux", e);
            errorHandler.handleError(e.getMessage(), "termux_error");
            return false;
        }
    }

    /**
     * 使用直接 Node.js 方案启动 NapCat
     */
    private boolean startWithDirectNode(NodeRunnerCallback callback) {
        try {
            Log.d(TAG, "Attempting to start NapCat with direct Node.js...");
            
            // 这里可以尝试使用 Android 上直接部署的 Node.js 二进制文件
            // 或者检查是否有其他 Node.js 运行时
            Log.w(TAG, "Direct Node.js scheme not yet implemented");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start NapCat with direct Node.js", e);
            errorHandler.handleError(e.getMessage(), "nodejs_error");
            return false;
        }
    }

    /**
     * 启动输出和错误读取线程
     */
    private void startOutputThreads(NodeRunnerCallback callback) {
        // 读取标准输出
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && isRunning) {
                    Log.d(TAG, "Node output: " + line);
                    if (callback != null) {
                        callback.onOutput(line);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading Node output", e);
                String errorMsg = "Error reading Node output: " + e.getMessage();
                errorHandler.handleError(errorMsg, "output_error");
                if (callback != null) {
                    callback.onError(errorMsg);
                }
            }
        });

        // 读取错误输出
        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null && isRunning) {
                    Log.e(TAG, "Node error: " + line);
                    
                    // 分析错误类型并报告给错误处理器
                    String errorType = analyzeErrorType(line);
                    errorHandler.handleError(line, errorType);
                    
                    if (callback != null) {
                        callback.onError(line);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading Node error stream", e);
                String errorMsg = "Error reading Node error stream: " + e.getMessage();
                errorHandler.handleError(errorMsg, "error_stream_error");
            }
        });

        outputThread.start();
        errorThread.start();

        // 监控进程退出
        Thread exitThread = new Thread(() -> {
            try {
                int exitCode = nodeProcess.waitFor();
                Log.d(TAG, "Node process exited with code: " + exitCode);
                isRunning = false;
                
                // 根据退出码判断是否为异常退出
                if (exitCode != 0) {
                    errorHandler.handleError("Process exited with code: " + exitCode, "process_exit");
                }
                
                if (callback != null) {
                    callback.onExit(exitCode);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Node process monitoring interrupted", e);
                errorHandler.handleError(e.getMessage(), "monitoring_error");
                Thread.currentThread().interrupt();
            }
        });
        exitThread.start();
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

    /**
     * 部署二进制文件
     */
    private boolean deployBinaryFromAssets(String assetName, String outputPath) throws IOException {
        try (InputStream in = appContext.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outputPath)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            
            // 设置执行权限
            File binaryFile = new File(outputPath);
            boolean setExec = binaryFile.setExecutable(true, true); // owner only
            Log.d(TAG, "Binary deployed: " + outputPath + ", exec=" + setExec);
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to deploy binary from assets: " + assetName, e);
            // 注意：这里不记录错误，因为某些文件可能不存在（这是正常的）
            return false;
        }
    }

    /**
     * 获取设备架构
     */
    private String getDeviceArchitecture() {
        String arch = Build.CPU_ABI.toLowerCase();
        if (arch.startsWith("arm64")) {
            return "arm64-v8a";
        } else if (arch.startsWith("armeabi")) {
            return "armeabi-v7a";
        } else if (arch.startsWith("x86_64")) {
            return "x86_64";
        } else if (arch.startsWith("x86")) {
            return "x86";
        } else {
            // 默认返回 arm64，因为这是目前 Android 设备最常见的架构
            return "arm64-v8a";
        }
    }

    /**
     * 部署 NapCat 核心文件到内部存储
     */
    public boolean deployNapCatFiles() {
        try {
            String napcatPath = getNapCatPath();
            File napcatDir = new File(napcatPath);
            
            if (!napcatDir.exists()) {
                boolean created = napcatDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create NapCat directory: " + napcatPath);
                    errorHandler.handleError("Failed to create NapCat directory: " + napcatPath, "file_error");
                    return false;
                }
            }
            
            // 从 assets 中解压 NapCat 核心文件
            // 注意：在实际实现中，这将从 APK assets 目录解压 NapCat 文件
            String[] napcatFiles = {
                NAPCAT_ENTRY_FILE,
                "package.json",
                "index.js"
            };
            
            for (String fileName : napcatFiles) {
                try (InputStream in = appContext.getAssets().open(NAPCAT_DIR + "/" + fileName);
                     OutputStream out = new FileOutputStream(new File(napcatDir, fileName))) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                } catch (IOException e) {
                    // 文件可能不存在于 assets 中，这在开发阶段是正常的
                    Log.d(TAG, "NapCat file not found in assets: " + NAPCAT_DIR + "/" + fileName);
                }
            }
            
            Log.d(TAG, "NapCat files deployment attempted at: " + napcatPath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deploying NapCat files", e);
            errorHandler.handleError(e.getMessage(), "file_error");
            return false;
        }
    }

    /**
     * 部署 PRoot 根文件系统
     */
    public boolean deployProotRootfs() {
        try {
            String rootfsPath = getRootfsPath();
            File rootfsDir = new File(rootfsPath);
            
            if (!rootfsDir.exists()) {
                boolean created = rootfsDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create PRoot rootfs directory: " + rootfsPath);
                    errorHandler.handleError("Failed to create PRoot rootfs directory: " + rootfsPath, "file_error");
                    return false;
                }
            }
            
            // 从 assets 中解压 PRoot 根文件系统
            // 这在实际实现中会解压完整的 Linux 环境（类似 SealDice 的 rootfs）
            Log.d(TAG, "PRoot rootfs deployment attempted at: " + rootfsPath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deploying PRoot rootfs", e);
            errorHandler.handleError(e.getMessage(), "file_error");
            return false;
        }
    }

    /**
     * 获取错误处理状态
     */
    public String getErrorStatus() {
        return errorHandler.getErrorStatus();
    }

    private String getRootfsPath() {
        return appContext.getFilesDir().getAbsolutePath() + "/" + ROOTFS_DIR;
    }

    private String getNapCatPath() {
        return appContext.getFilesDir().getAbsolutePath() + "/" + NAPCAT_DIR;
    }

    public void stopNapCat() {
        Log.d(TAG, "Stopping NapCat...");
        if (nodeProcess != null && isRunning) {
            nodeProcess.destroy();
            isRunning = false;
            Log.d(TAG, "NapCat process stopped");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}