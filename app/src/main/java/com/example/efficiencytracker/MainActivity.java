package com.example.efficiencytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import org.json.JSONObject;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 兼容 Android 7.0 及以上的轻量 WebView 容器。 */
public class MainActivity extends Activity {
    private WebView webView;
    private static final String SHARE_DIR = "share";
    private static final int REQ_EXPORT = 4101;
    private static final int REQ_IMPORT = 4102;
    private static final int REQ_EXPORT_BINARY = 4104;
    private static final int REQ_FILE_CHOOSER = 4103;
    private String pendingExportJson;
    private String pendingBinaryBase64;
    private String pendingBinaryMime;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();


        try {
            createWebView();
        } catch (Throwable error) {
            showWebViewError(error);
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /**
     * 使用经过 V1.1.5 验证的 Android 15 Insets 处理方式。
     * 不修改 Window 的强制 edge-to-edge 行为，只把系统栏安全区交给根容器，
     * WebView 始终填满剩余的安全区域。
     */
    private void applySystemBarInsets(FrameLayout root, WebView view) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;

                // Android 15 + targetSdk 35 下 edge-to-edge 会让 IME 不再自动
                // 把 WebView 的可视区域压缩。把 IME bottom 安全区交给根容器，
                // 让 WebView 的布局高度随键盘出现而缩短，网页里的 fixed 弹窗
                // 才能始终停在键盘上方。
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                bottom = Math.max(bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }

            // 只由原生根容器吸收系统栏/IME 安全区，不同时修改 WebView padding。
            v.setPadding(0, top, 0, bottom);

            // 只由原生根容器吸收系统栏，不再同时修改 WebView 的 padding。
            v.setPadding(0, top, 0, bottom);

            // 兼容旧版网页：如果页面存在 setWebInsets，则同步一次；
            // 当前页面没有该函数时 evaluateJavascript 本身不会产生异常。
            if (view != null) {
                final String js = "window.setWebInsets && window.setWebInsets(" + top + "," + bottom + ")";
                try {
                    view.post(() -> view.evaluateJavascript(js, null));
                } catch (Throwable ignored) {
                }
            }
            return insets;
        });
        root.post(root::requestApplyInsets);
    }

    private void createWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        // 本地 assets 页面只需要访问自身资源，不开启跨 file:// 访问。
        // 关闭这两个旧版兼容开关可降低 Android System WebView 新版本的安全/兼容风险。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }

        webView.setBackgroundColor(Color.rgb(246, 248, 252));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new AndroidShareBridge(), "AndroidShare");
        webView.addJavascriptInterface(new AndroidDataBridge(), "AndroidData");
                webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel.sheet.macroEnabled.12",
                            "application/json", "text/plain", "application/octet-stream"
                    });
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setNegativeButton("取消", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        root.addView(webView);
        setContentView(root);
        applySystemBarInsets(root, webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private final class AndroidDataBridge {
        @JavascriptInterface
        public boolean exportData(String json, String fileName) {
            try {
                pendingExportJson = json == null ? "{}" : json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, (fileName == null || fileName.trim().isEmpty()) ? "个人效率数据备份.json" : fileName);
                runOnUiThread(() -> startActivityForResult(intent, REQ_EXPORT));
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public boolean exportBinary(String base64, String fileName, String mimeType) {
            try {
                pendingBinaryBase64 = base64 == null ? "" : base64;
                pendingBinaryMime = (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingBinaryMime);
                intent.putExtra(Intent.EXTRA_TITLE, (fileName == null || fileName.trim().isEmpty()) ? "项目工序备份.xlsx" : fileName);
                runOnUiThread(() -> startActivityForResult(intent, REQ_EXPORT_BINARY));
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public boolean pickImportFile() {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                runOnUiThread(() -> startActivityForResult(intent, REQ_IMPORT));
                return true;
            } catch (Exception e) { return false; }
        }
    }

    private final class AndroidShareBridge {
        @JavascriptInterface
        public boolean shareImage(String dataUrl, String fileName) {
            try {
                final boolean jpeg = dataUrl != null && dataUrl.startsWith("data:image/jpeg");
                final String safeName = (fileName == null || fileName.trim().isEmpty())
                        ? (jpeg ? "efficiency-report.jpg" : "efficiency-report.png")
                        : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                final String encoded = dataUrl.substring(dataUrl.indexOf(',') + 1);
                final byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);

                File dir = new File(getCacheDir(), SHARE_DIR);
                if (!dir.exists() && !dir.mkdirs()) return false;
                File file = new File(dir, safeName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(bytes);
                }

                Uri uri = Uri.parse("content://" + getPackageName() + ".share/" + safeName);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(jpeg ? "image/jpeg" : "image/png");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("image", uri));

                runOnUiThread(() -> startActivity(Intent.createChooser(intent, "分享效率报告")));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void showWebViewError(Throwable error) {
        TextView message = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding, padding, padding);
        message.setTextSize(16);
        message.setTextColor(Color.DKGRAY);
        message.setText("无法启动个人效率计算。请在系统设置中启用或更新 Android System WebView，然后重新打开应用。\n\n" + error.getClass().getSimpleName());
        setContentView(message);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQ_FILE_CHOOSER && fileChooserCallback != null) { fileChooserCallback.onReceiveValue(null); fileChooserCallback = null; }
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_FILE_CHOOSER) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(new Uri[]{uri});
                fileChooserCallback = null;
                return;
            } else if (requestCode == REQ_EXPORT_BINARY) {
                if (pendingBinaryBase64 == null) return;
                byte[] bytes = Base64.decode(pendingBinaryBase64, Base64.DEFAULT);
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("无法打开导出位置");
                    out.write(bytes);
                }
                pendingBinaryBase64 = null; pendingBinaryMime = null;
                if (webView != null) webView.evaluateJavascript("window.UI&&UI.toast&&UI.toast('Excel备份导出成功')", null);
                return;
            } else if (requestCode == REQ_EXPORT) {
                if (pendingExportJson == null) return;
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("无法打开导出位置");
                    out.write(pendingExportJson.getBytes("UTF-8"));
                }
                pendingExportJson = null;
                if (webView != null) webView.evaluateJavascript("window.UI&&UI.toast&&UI.toast('数据导出成功')", null);
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                if (webView != null) {
                    String js = "window.onAndroidImportData && window.onAndroidImportData(" + JSONObject.quote(sb.toString()) + ")";
                    webView.evaluateJavascript(js, null);
                }
            }
        } catch (Exception e) {
            if (webView != null) {
                String msg = requestCode == REQ_EXPORT ? "数据导出失败，请重试" : "读取备份文件失败";
                webView.evaluateJavascript("alert(" + JSONObject.quote(msg) + ")", null);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) return;

        // 单页 App 的“返回”由网页路由统一处理：先关弹窗，再返回上一级页面。
        // 根页面不退出 Activity，避免误返回到桌面。
        webView.evaluateJavascript(
                "(function(){ return window.handleAndroidBack ? window.handleAndroidBack() : false; })()",
                value -> {
                    // JS 返回 true：返回事件已被页面消费。
                    // JS 返回 false：当前已经是首页，交还给 Android，真正返回桌面。
                    if ("false".equals(value)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            finishAndRemoveTask();
                        } else {
                            finish();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {
                // WebView 在部分厂商 ROM 上销毁时可能抛出异常，不能让退出过程崩溃。
            }
            webView = null;
        }
        super.onDestroy();
    }
}
