package com.napcat.android;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NapCatAndroid";
    private static final String WEBUI_URL = "http://127.0.0.1:6099";

    private WebView webView;
    private LinearLayout splashScreen;
    private TextView statusText;
    private TextView errorText;
    private Button startButton;
    private Button logButton;
    private ProgressBar progressBar;

    private Handler mainHandler;
    private boolean serviceReady = false;
    private int webuiCheckAttempts = 0;
    private static final int MAX_WEBUI_CHECKS = 60;
    private boolean webuiLoaded = false;

    private NodeRunner nodeRunner;

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.napcat.android.SERVICE_STATUS".equals(action)) {
                String status = intent.getStringExtra("status");
                String message = intent.getStringExtra("message");
                handleServiceStatus(status, message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "NapCatAndroid started");
        mainHandler = new Handler(Looper.getMainLooper());

        webView = findViewById(R.id.webview);
        splashScreen = findViewById(R.id.splash_screen);
        statusText = findViewById(R.id.status);
        errorText = findViewById(R.id.error_text);
        startButton = findViewById(R.id.start_service);
        logButton = findViewById(R.id.log_button);
        progressBar = findViewById(R.id.progress);

        nodeRunner = new NodeRunner(this);

        setupWebView();

        startButton.setOnClickListener(v -> {
            errorText.setVisibility(View.GONE);
            startButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            serviceReady = false;
            webuiLoaded = false;
            startNapCatService();
        });

        logButton.setOnClickListener(v -> showLogDialog());

        IntentFilter filter = new IntentFilter("com.napcat.android.SERVICE_STATUS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceReceiver, filter);
        }

        // 自动启动服务
        startNapCatService();
    }

    private void showLogDialog() {
        String log = nodeRunner.getLogContent();
        
        TextView textView = new TextView(this);
        textView.setText(log.isEmpty() ? "暂无日志" : log);
        textView.setTextSize(10);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextIsSelectable(true);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
            .setTitle("运行日志")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNegativeButton("清空", (d, w) -> {
                nodeRunner.clearLog();
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "WebUI page loaded: " + url);
                if (!webuiLoaded) {
                    webuiLoaded = true;
                    showWebUI();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description + " for " + failingUrl);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d(TAG, "WebView loading: " + newProgress + "%");
            }
        });
    }

    private void startNapCatService() {
        Log.d(TAG, "Starting NapCat service...");
        updateStatus("starting", "正在启动 NapCat 服务...");

        Intent serviceIntent = new Intent(this, NapCatService.class);
        startForegroundService(serviceIntent);
    }

    private void handleServiceStatus(String status, String message) {
        Log.d(TAG, "Service status: " + status + " - " + message);

        switch (status) {
            case "starting":
                updateStatus(status, message != null ? message : "正在启动...");
                break;

            case "downloading":
                updateStatus(status, message != null ? message : "下载中...");
                break;

            case "deploying":
                updateStatus(status, message != null ? message : "部署中...");
                break;

            case "ready":
                serviceReady = true;
                updateStatus(status, "服务已就绪，正在连接 WebUI...");
                startWebUICheck();
                break;

            case "running":
                updateStatus(status, message);
                break;

            case "error":
                showError(message != null ? message : "发生错误");
                break;

            case "stopped":
                updateStatus(status, message);
                if (!webuiLoaded) {
                    showError("服务已停止，点击查看日志");
                }
                break;

            case "restarting":
                updateStatus(status, message);
                serviceReady = false;
                break;
        }
    }

    private void startWebUICheck() {
        webuiCheckAttempts = 0;
        checkWebUI();
    }

    private void checkWebUI() {
        if (webuiLoaded) {
            return;
        }

        if (webuiCheckAttempts >= MAX_WEBUI_CHECKS) {
            showError("无法连接到 WebUI，点击查看日志");
            return;
        }

        webuiCheckAttempts++;
        Log.d(TAG, "Checking WebUI... attempt " + webuiCheckAttempts);

        webView.loadUrl(WEBUI_URL);

        mainHandler.postDelayed(() -> {
            if (!webuiLoaded && serviceReady) {
                checkWebUI();
            }
        }, 1000);
    }

    private void showWebUI() {
        mainHandler.post(() -> {
            splashScreen.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        });
    }

    private void updateStatus(String status, String message) {
        mainHandler.post(() -> {
            statusText.setText(message != null ? message : status);

            if ("downloading".equals(status) || "deploying".equals(status)) {
                progressBar.setVisibility(View.VISIBLE);
                errorText.setVisibility(View.GONE);
                startButton.setVisibility(View.GONE);
            } else if ("ready".equals(status) || "running".equals(status)) {
                progressBar.setVisibility(View.VISIBLE);
                errorText.setVisibility(View.GONE);
                startButton.setVisibility(View.GONE);
            }
        });
    }

    private void showError(String message) {
        mainHandler.post(() -> {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            startButton.setVisibility(View.VISIBLE);
            startButton.setText("重试");
            logButton.setVisibility(View.VISIBLE);
            serviceReady = false;
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(serviceReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
}