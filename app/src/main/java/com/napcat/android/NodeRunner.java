package com.napcat.android;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NodeRunner {
    private static final String TAG = "NodeRunner";
    private Process napcatProcess;
    private boolean isRunning = false;
    private Context appContext;
    private File logFile;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public interface NodeRunnerCallback {
        void onOutput(String output);
        void onError(String error);
        void onExit(int exitCode);
        void onProgress(int percent, String message);
    }

    public NodeRunner(Context context) {
        this.appContext = context;
        this.logFile = new File(context.getFilesDir(), "napcat.log");
    }

    private void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        Log.d(TAG, logLine.trim());
        
        // 写入日志文件
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.append(logLine);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }

    public String getLogContent() {
        try {
            return readFile(logFile);
        } catch (IOException e) {
            return "无法读取日志: " + e.getMessage();
        }
    }

    public void clearLog() {
        if (logFile.exists()) {
            logFile.delete();
        }
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public boolean startNapCat(NodeRunnerCallback callback) {
        log("Starting NapCat with PRoot...");

        try {
            if (isRunning) {
                log("Already running");
                return false;
            }

            // 部署核心文件
            if (!deployFiles(callback)) {
                log("Failed to deploy files");
                if (callback != null) callback.onError("Failed to deploy files");
                return false;
            }

            return startWithPRoot(callback);
        } catch (Exception e) {
            log("Start failed: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
            return false;
        }
    }

    private boolean deployFiles(NodeRunnerCallback callback) {
        try {
            File filesDir = appContext.getFilesDir();
            File marker = new File(filesDir, ".napcat_deployed");

            if (marker.exists()) {
                log("Files already deployed");
                // 即使已部署，也要确保所有关键文件和 rootfs 有正确权限
                ensureExecutablePermissions();
                log("Permissions re-verified");
                return true;
            }

            log("Deploying assets...");
            if (callback != null) callback.onProgress(0, "正在部署文件...");

            // 部署各个目录
            String[] dirs = {"proot", "rootfs", "node", "napcat", "qqnt"};
            int total = dirs.length;
            int current = 0;

            for (String dir : dirs) {
                current++;
                int percent = (current * 100) / total;
                String msg = "正在部署 " + dir + "...";
                log(msg);
                if (callback != null) callback.onProgress(percent, msg);
                
                File destDir = new File(filesDir, dir);
                copyAssetDir(dir, destDir);
            }

            // 设置关键文件执行权限
            ensureExecutablePermissions();

            marker.createNewFile();
            log("Assets deployed successfully");
            if (callback != null) callback.onProgress(100, "文件部署完成");
            return true;
        } catch (Exception e) {
            log("Deploy failed: " + e.getMessage());
            return false;
        }
    }

    private void ensureExecutablePermissions() {
        File filesDir = appContext.getFilesDir();
        // 设置关键二进制文件的执行权限
        setExecutablePermission(new File(filesDir, "proot/proot").getAbsolutePath());
        setExecutablePermission(new File(filesDir, "proot/loader").getAbsolutePath());
        setExecutablePermission(new File(filesDir, "node/node").getAbsolutePath());
        
        // 修复 rootfs 权限 - 递归设置 bin 和 lib 目录的权限
        File rootfsDir = new File(filesDir, "rootfs");
        log("Fixing rootfs permissions: " + rootfsDir.getAbsolutePath());
        fixRootfsPermissions(rootfsDir);
        
        log("Executable permissions ensured");
    }
    
    private void fixRootfsPermissions(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            log("fixRootfsPermissions: dir not found: " + dir.getAbsolutePath());
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            log("fixRootfsPermissions: listFiles returned null");
            return;
        }
        
        log("fixRootfsPermissions: processing " + files.length + " items in " + dir.getName());
        
        for (File file : files) {
            // 设置目录权限为 755 (rwxr-xr-x)
            if (file.isDirectory()) {
                file.setExecutable(true, false);
                file.setReadable(true, false);
                file.setWritable(true, true);
                fixRootfsPermissions(file); // 递归处理子目录
            } else {
                // 设置文件权限
                file.setReadable(true, false);
                file.setWritable(true, true);
                // 对于 bin 和 lib 目录下的文件，设置执行权限
                String path = file.getAbsolutePath();
                if (path.contains("/bin/") || path.contains("/lib/") || path.contains("/sbin/")) {
                    file.setExecutable(true, false);
                    log("Set executable: " + file.getName());
                }
            }
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
                copyAssetDir(assetFile, destFile);
            } else {
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

            // 设置执行权限 - 关键步骤！
            setExecutablePermission(prootBin);
            setExecutablePermission(new File(prootDir, "loader").getAbsolutePath());
            setExecutablePermission(nodeFile.getAbsolutePath());

            // 检查必要文件
            if (!new File(prootBin).exists()) {
                log("ERROR: proot not found at " + prootBin);
                if (callback != null) callback.onError("proot not found");
                return false;
            }
            if (!nodeFile.exists()) {
                log("ERROR: node not found at " + nodeFile.getAbsolutePath());
                if (callback != null) callback.onError("node not found");
                return false;
            }

            // 创建 proot 临时目录
            File prootTmpDir = new File(prootDir, "tmp");
            if (!prootTmpDir.exists()) {
                prootTmpDir.mkdirs();
                log("Created PROOT_TMP_DIR: " + prootTmpDir.getAbsolutePath());
            }
            String prootTmpPath = prootTmpDir.getAbsolutePath();

            // 构建启动命令
            // 使用 linker64 加载 proot（Android SELinux 限制需要这样做）
            // proot 使用 PROOT_LOADER 环境变量来指定 loader 路径
            // loader 负责在 proot 环境中加载 Linux ELF 二进制文件
            String loaderPath = new File(prootDir, "loader").getAbsolutePath();
            String[] command = {
                "/system/bin/linker64",
                prootBin,
                "-v", "1",  // 启用调试输出
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

            String cmdStr = String.join(" ", command);
            log("Command: " + cmdStr);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(napcatDir);
            pb.environment().remove("LD_PRELOAD");
            // 设置 PROOT_TMP_DIR 环境变量（正确的变量名）
            pb.environment().put("PROOT_TMP_DIR", prootTmpPath);
            pb.environment().put("PROOT_TMP", prootTmpPath);
            pb.environment().put("TMPDIR", prootTmpPath);
            pb.environment().put("PROOT_LOADER", new File(prootDir, "loader").getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH", prootDir.getAbsolutePath());
            pb.environment().put("PROOT_NO_SECCOMP", "1");  // 禁用 seccomp 避免 Android 兼容性问题
            log("Environment: PROOT_TMP_DIR=" + prootTmpPath);

            log("Starting process...");
            napcatProcess = pb.start();
            isRunning = true;
            log("Process started, PID: " + (napcatProcess.isAlive() ? "alive" : "dead"));

            // 输出线程
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(napcatProcess.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null && isRunning) {
                        log("[OUT] " + l);
                        if (callback != null) callback.onOutput(l);
                    }
                } catch (Exception e) {
                    log("[OUT] Exception: " + e.getMessage());
                }
            }).start();

            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(napcatProcess.getErrorStream()))) {
                    String l;
                    while ((l = r.readLine()) != null && isRunning) {
                        log("[ERR] " + l);
                        if (callback != null) callback.onError(l);
                    }
                } catch (Exception e) {
                    log("[ERR] Exception: " + e.getMessage());
                }
            }).start();

            new Thread(() -> {
                try {
                    int code = napcatProcess.waitFor();
                    isRunning = false;
                    log("Process exited with code: " + code);
                    if (callback != null) callback.onExit(code);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("Process interrupted");
                }
            }).start();

            return true;
        } catch (Exception e) {
            log("PRoot start failed: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
            return false;
        }
    }

    private void setExecutablePermission(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log("File not found for chmod: " + filePath);
                return;
            }
            
            // 先尝试 Java API
            if (file.setExecutable(true, false)) {
                log("Set executable via Java API: " + filePath);
            }
            
            // 再用 shell 命令确保权限生效
            ProcessBuilder pb = new ProcessBuilder("chmod", "755", filePath);
            Process p = pb.start();
            int exitCode = p.waitFor();
            log("chmod 755 " + filePath + " -> exit code: " + exitCode);
        } catch (Exception e) {
            log("Failed to set executable permission for " + filePath + ": " + e.getMessage());
        }
    }

    public void stopNapCat() {
        if (napcatProcess != null && isRunning) {
            log("Stopping NapCat...");
            napcatProcess.destroy();
            isRunning = false;
        }
    }

    public boolean isRunning() { return isRunning; }
}
