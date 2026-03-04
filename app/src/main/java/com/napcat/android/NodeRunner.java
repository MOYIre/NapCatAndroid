package com.napcat.android;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NodeRunner {
    private static final String TAG = "NodeRunner";
    private Process napcatProcess;
    private boolean isRunning = false;
    private Context appContext;

    public interface NodeRunnerCallback {
        void onOutput(String output);
        void onError(String error);
        void onExit(int exitCode);
        void onProgress(int percent, String message);
    }

    public NodeRunner(Context context) {
        this.appContext = context;
    }

    public boolean startNapCat(NodeRunnerCallback callback) {
        Log.d(TAG, "Starting NapCat with PRoot...");

        try {
            if (isRunning) {
                Log.w(TAG, "Already running");
                return false;
            }

            // 部署核心文件
            if (!deployFiles(callback)) {
                if (callback != null) callback.onError("Failed to deploy files");
                return false;
            }

            return startWithPRoot(callback);
        } catch (Exception e) {
            Log.e(TAG, "Start failed", e);
            if (callback != null) callback.onError(e.getMessage());
            return false;
        }
    }

    private boolean deployFiles(NodeRunnerCallback callback) {
        try {
            File filesDir = appContext.getFilesDir();
            File marker = new File(filesDir, ".napcat_deployed");

            if (marker.exists()) {
                Log.d(TAG, "Files already deployed");
                return true;
            }

            Log.d(TAG, "Deploying assets...");
            if (callback != null) callback.onProgress(0, "正在部署文件...");

            // 部署各个目录
            String[] dirs = {"proot", "rootfs", "node", "napcat", "qqnt"};
            int total = dirs.length;
            int current = 0;

            for (String dir : dirs) {
                current++;
                int percent = (current * 100) / total;
                if (callback != null) callback.onProgress(percent, "正在部署 " + dir + "...");
                
                File destDir = new File(filesDir, dir);
                copyAssetDir(dir, destDir);
            }

            marker.createNewFile();
            Log.d(TAG, "Assets deployed successfully");
            if (callback != null) callback.onProgress(100, "文件部署完成");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Deploy failed", e);
            return false;
        }
    }

    private void copyAssetDir(String assetPath, File destDir) throws IOException {
        String[] files = appContext.getAssets().list(assetPath);
        if (files == null || files.length == 0) {
            return;
        }

        destDir.mkdirs();

        for (String file : files) {
            String assetFile = assetPath + "/" + file;
            File destFile = new File(destDir, file);

            String[] subFiles = appContext.getAssets().list(assetFile);
            if (subFiles != null && subFiles.length > 0) {
                // 是目录
                copyAssetDir(assetFile, destFile);
            } else {
                // 是文件
                copyAssetFile(assetFile, destFile);
            }
        }
    }

    private void copyAssetFile(String assetPath, File destFile) throws IOException {
        destFile.getParentFile().mkdirs();
        try (InputStream is = appContext.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
        destFile.setExecutable(true);
        destFile.setReadable(true);
        destFile.setWritable(true);
    }

    private boolean startWithPRoot(NodeRunnerCallback callback) {
        try {
            File filesDir = appContext.getFilesDir();
            File prootDir = new File(filesDir, "proot");
            File rootfsDir = new File(filesDir, "rootfs");
            File napcatDir = new File(filesDir, "napcat");
            File qqntDir = new File(filesDir, "qqnt");
            File nodeFile = new File(filesDir, "node/node");

            String prootBin = new File(prootDir, "proot").getAbsolutePath();

            // 构建启动命令
            String[] command = {
                prootBin,
                "-r", rootfsDir.getAbsolutePath(),
                "-w", "/root",
                "-b", "/proc",
                "-b", "/dev",
                "-b", "/sys",
                "-b", "/data",
                "-b", "/sdcard",
                "-b", napcatDir.getAbsolutePath() + ":/root",
                "-b", qqntDir.getAbsolutePath() + ":/qqnt",
                "-b", new File(filesDir, "node").getAbsolutePath() + ":/node",
                "/bin/bash", "-c",
                "export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/node; " +
                "export HOME=/root; " +
                "export LD_LIBRARY_PATH=/lib/aarch64-linux-gnu:/usr/lib/aarch64-linux-gnu:/qqnt; " +
                "export NAPCAT_WRAPPER_PATH=/qqnt/wrapper.node; " +
                "cd /root; " +
                "chmod +x /node/node 2>/dev/null || true; " +
                "/node/node --no-warnings loadNapCat.js"
            };

            Log.d(TAG, "Command: " + String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(napcatDir);
            pb.environment().remove("LD_PRELOAD");
            pb.environment().put("PROOT_TMP", new File(prootDir, "tmp").getAbsolutePath());

            napcatProcess = pb.start();
            isRunning = true;

            // 输出线程
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(napcatProcess.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null && isRunning) {
                        Log.d(TAG, "[OUT] " + l);
                        if (callback != null) callback.onOutput(l);
                    }
                } catch (Exception e) { }
            }).start();

            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(napcatProcess.getErrorStream()))) {
                    String l;
                    while ((l = r.readLine()) != null && isRunning) {
                        Log.e(TAG, "[ERR] " + l);
                        if (callback != null) callback.onError(l);
                    }
                } catch (Exception e) { }
            }).start();

            new Thread(() -> {
                try {
                    int code = napcatProcess.waitFor();
                    isRunning = false;
                    if (callback != null) callback.onExit(code);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "PRoot start failed", e);
            if (callback != null) callback.onError(e.getMessage());
            return false;
        }
    }

    public void stopNapCat() {
        if (napcatProcess != null && isRunning) {
            napcatProcess.destroy();
            isRunning = false;
        }
    }

    public boolean isRunning() { return isRunning; }
}