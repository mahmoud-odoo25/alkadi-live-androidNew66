package com.alkadi.livebarcode;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "alkadi_live_barcode_settings";
    private static final String PREF_ODOO_BASE_URL = "odoo_base_url";
    private static final String PREF_BARCODE_TARGET = "barcode_target";
    private static final String PREF_STRICT_MODE = "strict_barcode_mode";
    private static final String PREF_WATERMARK = "watermark_enabled";

    private static final String DEFAULT_BARCODE_ACTION = "stock_barcode.stock_barcode_action_main_menu";
    private static final String BRAND_BLUE = "#4D4D93";
    private static final String BRAND_BLUE_DARK = "#3F3F7E";
    private static final int REQ_CAMERA_PERMISSION_WEB = 2002;
    private static final int TOOLBAR_BASE_HEIGHT_DP = 96;

    private SharedPreferences preferences;
    private WebView webView;
    private ProgressBar progressBar;
    private ImageView watermarkView;
    private TextView titleView;
    private PermissionRequest pendingWebPermissionRequest;
    private int barcodeRedirectAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        configureSystemBars();

        String configuredUrl = preferences.getString(PREF_ODOO_BASE_URL, "");
        if (configuredUrl == null || configuredUrl.trim().isEmpty()) {
            showConfigurationScreen();
        } else {
            showBarcodeWebScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color(BRAND_BLUE));
            getWindow().setNavigationBarColor(color(BRAND_BLUE_DARK));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    private void showConfigurationScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(dp(24), dp(32), dp(24), dp(24));
        container.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_al_kadi_barcode);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(300), dp(130));
        logoParams.bottomMargin = dp(18);
        container.addView(logo, logoParams);

        TextView heading = new TextView(this);
        heading.setText("Al-Kadi Live Barcode");
        heading.setTextSize(24);
        heading.setTextColor(color(BRAND_BLUE));
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(heading, matchWrap());

        TextView subHeading = new TextView(this);
        subHeading.setText("Dedicated Odoo Barcode module launcher for handheld Android devices.");
        subHeading.setTextSize(14);
        subHeading.setTextColor(color("#555555"));
        subHeading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.setMargins(0, dp(8), 0, dp(20));
        container.addView(subHeading, subParams);

        EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("Odoo server URL, e.g. https://erp.company.com");
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        urlInput.setText(preferences.getString(PREF_ODOO_BASE_URL, ""));
        container.addView(urlInput, fullWrap());

        EditText actionInput = new EditText(this);
        actionInput.setSingleLine(true);
        actionInput.setHint("Barcode action or exact Barcode URL");
        actionInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        actionInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        actionInput.setText(preferences.getString(PREF_BARCODE_TARGET, DEFAULT_BARCODE_ACTION));
        LinearLayout.LayoutParams actionParams = fullWrap();
        actionParams.setMargins(0, dp(8), 0, 0);
        container.addView(actionInput, actionParams);

        CheckBox strictMode = new CheckBox(this);
        strictMode.setText("Barcode-only mode: open Barcode directly and hide general Odoo navigation");
        strictMode.setTextSize(14);
        strictMode.setChecked(preferences.getBoolean(PREF_STRICT_MODE, true));
        container.addView(strictMode, fullWrap());

        CheckBox watermarkCheck = new CheckBox(this);
        watermarkCheck.setText("Show Al-Kadi Barcode watermark");
        watermarkCheck.setTextSize(14);
        watermarkCheck.setChecked(preferences.getBoolean(PREF_WATERMARK, true));
        container.addView(watermarkCheck, fullWrap());

        Button saveButton = new Button(this);
        saveButton.setText("Save and Open Barcode");
        saveButton.setAllCaps(false);
        LinearLayout.LayoutParams saveParams = fullWrap();
        saveParams.setMargins(0, dp(18), 0, 0);
        container.addView(saveButton, saveParams);

        Button clearButton = new Button(this);
        clearButton.setText("Clear saved settings");
        clearButton.setAllCaps(false);
        container.addView(clearButton, fullWrap());

        View.OnClickListener save = v -> saveAndOpen(urlInput, actionInput, strictMode, watermarkCheck);
        saveButton.setOnClickListener(save);
        actionInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (isDone || isEnter) {
                saveAndOpen(urlInput, actionInput, strictMode, watermarkCheck);
                return true;
            }
            return false;
        });
        clearButton.setOnClickListener(v -> {
            preferences.edit().clear().apply();
            urlInput.setText("");
            actionInput.setText(DEFAULT_BARCODE_ACTION);
            strictMode.setChecked(true);
            watermarkCheck.setChecked(true);
            Toast.makeText(this, "Saved settings cleared", Toast.LENGTH_SHORT).show();
        });

        setContentView(container);
        applyRootInsets(container, dp(32), dp(24));
    }

    private void saveAndOpen(EditText urlInput, EditText actionInput, CheckBox strictMode, CheckBox watermarkCheck) {
        String baseUrl = normalizeBaseUrl(urlInput.getText() == null ? "" : urlInput.getText().toString());
        if (baseUrl.isEmpty()) {
            urlInput.setError("Please enter a valid Odoo server URL");
            return;
        }

        String target = actionInput.getText() == null ? "" : actionInput.getText().toString().trim();
        if (target.isEmpty()) {
            target = DEFAULT_BARCODE_ACTION;
        }

        preferences.edit()
                .putString(PREF_ODOO_BASE_URL, baseUrl)
                .putString(PREF_BARCODE_TARGET, target)
                .putBoolean(PREF_STRICT_MODE, strictMode.isChecked())
                .putBoolean(PREF_WATERMARK, watermarkCheck.isChecked())
                .apply();

        barcodeRedirectAttempts = 0;
        showBarcodeWebScreen();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBarcodeWebScreen() {
        FrameLayout root = new FrameLayout(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        int toolbarBaseHeight = dp(TOOLBAR_BASE_HEIGHT_DP);
        LinearLayout toolbar = buildToolbar();
        main.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                toolbarBaseHeight
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        main.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        main.addView(webView);

        watermarkView = new ImageView(this);
        watermarkView.setImageResource(R.drawable.logo_al_kadi_barcode);
        watermarkView.setAlpha(0.10f);
        watermarkView.setAdjustViewBounds(true);
        watermarkView.setClickable(false);
        watermarkView.setFocusable(false);
        watermarkView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams waterParams = new FrameLayout.LayoutParams(dp(210), dp(84));
        waterParams.gravity = Gravity.BOTTOM | Gravity.END;
        waterParams.setMargins(0, 0, dp(12), dp(12));
        root.addView(watermarkView, waterParams);
        watermarkView.setVisibility(preferences.getBoolean(PREF_WATERMARK, true) ? View.VISIBLE : View.GONE);

        setupWebView();
        setContentView(root);
        applyWebScreenInsets(root, toolbar, toolbarBaseHeight, dp(12));
        loadBarcodeApp();
    }

    private LinearLayout buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), dp(4));
        toolbar.setBackgroundColor(color(BRAND_BLUE));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_al_kadi_barcode);
        logo.setAdjustViewBounds(true);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(126), dp(44)));

        titleView = new TextView(this);
        titleView.setText("Al-Kadi Live Barcode");
        titleView.setTextSize(16);
        titleView.setTextColor(Color.WHITE);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        titleParams.setMargins(dp(8), 0, 0, 0);
        brandRow.addView(titleView, titleParams);

        toolbar.addView(brandRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.addView(toolbarButton("Open Barcode", v -> loadBarcodeApp()), new LinearLayout.LayoutParams(dp(128), dp(36)));
        actionRow.addView(toolbarButton("Reload", v -> { if (webView != null) webView.reload(); }), new LinearLayout.LayoutParams(dp(82), dp(36)));
        actionRow.addView(toolbarButton("URL", v -> showConfigurationScreen()), new LinearLayout.LayoutParams(dp(58), dp(36)));

        toolbar.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        ));

        return toolbar;
    }

    private TextView toolbarButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        return button;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " AlKadiLiveBarcodeAndroid/1.1");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new BarcodeWebViewClient());
        webView.setWebChromeClient(new BarcodeWebChromeClient());
        webView.requestFocus();
    }

    private void loadBarcodeApp() {
        if (webView == null) return;
        barcodeRedirectAttempts = 0;
        String url = buildBarcodeUrl();
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No network connection. Opening anyway; WebView may show cached data.", Toast.LENGTH_LONG).show();
        }
        webView.loadUrl(url);
    }

    private String buildBarcodeUrl() {
        String baseUrl = preferences.getString(PREF_ODOO_BASE_URL, "");
        String target = preferences.getString(PREF_BARCODE_TARGET, DEFAULT_BARCODE_ACTION);
        if (target == null || target.trim().isEmpty()) {
            target = DEFAULT_BARCODE_ACTION;
        }
        target = target.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        return trimTrailingSlash(baseUrl) + "/web#action=" + Uri.encode(target, ".-_");
    }

    private boolean isLoginPage(String url) {
        return url != null && (url.contains("/web/login") || url.contains("/web?redirect"));
    }

    private boolean isBarcodeUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("stock_barcode") || lower.contains("barcode");
    }

    private void maybeRedirectToBarcodeAfterLogin(String url) {
        if (!preferences.getBoolean(PREF_STRICT_MODE, true)) return;
        if (webView == null || url == null || isLoginPage(url) || isBarcodeUrl(url)) return;
        if (!url.contains("/web")) return;
        if (barcodeRedirectAttempts >= 2) return;

        barcodeRedirectAttempts++;
        webView.postDelayed(() -> {
            if (webView != null) webView.loadUrl(buildBarcodeUrl());
        }, 900);
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception ignored) {
            return true;
        }
    }

    private String normalizeBaseUrl(String input) {
        String url = input == null ? "" : input.trim();
        if (url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        Uri parsed = Uri.parse(url);
        if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) {
            return "";
        }
        String scheme = parsed.getScheme();
        String authority = parsed.getAuthority();
        if (scheme == null || authority == null) return "";
        return scheme + "://" + authority;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private class BarcodeWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                return handleUrl(request.getUrl());
            }
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        private boolean handleUrl(Uri uri) {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(MainActivity.this, "No application can open this link", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            CookieManager.getInstance().flush();
            applyBarcodeBrandingAndFocus();
            maybeRedirectToBarcodeAfterLogin(url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request != null && request.isForMainFrame()) {
                Toast.makeText(MainActivity.this, "Page error: " + error.getDescription(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class BarcodeWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progressBar == null) return;
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (titleView != null) titleView.setText("Al-Kadi Live Barcode");
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || request == null) {
                return;
            }
            runOnUiThread(() -> handleWebPermissionRequest(request));
        }
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        List<String> resourcesToGrant = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                resourcesToGrant.add(resource);
            }
        }
        if (resourcesToGrant.isEmpty()) {
            request.deny();
            return;
        }
        pendingWebPermissionRequest = request;
        if (requiresCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION_WEB);
        } else {
            grantPendingWebPermission();
        }
    }

    private void grantPendingWebPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || pendingWebPermissionRequest == null) {
            return;
        }
        List<String> resourcesToGrant = new ArrayList<>();
        for (String resource : pendingWebPermissionRequest.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                resourcesToGrant.add(resource);
            }
        }
        pendingWebPermissionRequest.grant(resourcesToGrant.toArray(new String[0]));
        pendingWebPermissionRequest = null;
    }

    private boolean requiresCameraPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    private void applyBarcodeBrandingAndFocus() {
        if (webView == null) return;
        boolean strictMode = preferences.getBoolean(PREF_STRICT_MODE, true);
        String strict = strictMode ? "true" : "false";
        String appTitle = JSONObject.quote("Al-Kadi Live Barcode");
        String blue = JSONObject.quote(BRAND_BLUE);
        String script = "(function(){" +
                "try{" +
                "var appTitle=" + appTitle + ";" +
                "var blue=" + blue + ";" +
                "var strict=" + strict + ";" +
                "document.title=appTitle;" +
                "var css='.o_loading_indicator{background:'+blue+'!important}'+" +
                "'.o_main_navbar{border-bottom-color:'+blue+'!important}';" +
                "if(strict){css += '.o_main_navbar .o_menu_toggle,.o_main_navbar .o_menu_sections,.o_main_navbar .o_menu_systray{display:none!important}.o_home_menu{display:none!important}';}" +
                "var style=document.getElementById('alkadi-barcode-only-style');" +
                "if(!style){style=document.createElement('style');style.id='alkadi-barcode-only-style';document.head.appendChild(style);}" +
                "style.textContent=css;" +
                "var targets=document.querySelectorAll(\".o_barcode_client_action input,input[autofocus],input[placeholder*='scan'],input[placeholder*='barcode']\");" +
                "if(targets.length){targets[0].focus();}" +
                "else{document.body.setAttribute('tabindex','0');document.body.focus();}" +
                "}catch(e){console.log('Al-Kadi Barcode branding skipped',e);}" +
                "})();";
        webView.evaluateJavascript(script, null);
        webView.requestFocus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION_WEB) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                grantPendingWebPermission();
            } else if (pendingWebPermissionRequest != null) {
                pendingWebPermissionRequest.deny();
                pendingWebPermissionRequest = null;
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void applyRootInsets(View root, int originalTopPadding, int originalBottomPadding) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        final int left = root.getPaddingLeft();
        final int right = root.getPaddingRight();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(left, originalTopPadding + getSystemBarTopInset(insets), right, originalBottomPadding + getSystemBarBottomInset(insets));
            return insets;
        });
        root.requestApplyInsets();
    }

    private void applyWebScreenInsets(FrameLayout root, LinearLayout toolbar, int toolbarBaseHeight, int watermarkBaseBottomMargin) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        final int toolbarLeft = toolbar.getPaddingLeft();
        final int toolbarTop = toolbar.getPaddingTop();
        final int toolbarRight = toolbar.getPaddingRight();
        final int toolbarBottom = toolbar.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = getSystemBarTopInset(insets);
            int bottomInset = getSystemBarBottomInset(insets);

            ViewGroup.LayoutParams toolbarParams = toolbar.getLayoutParams();
            if (toolbarParams != null) {
                toolbarParams.height = toolbarBaseHeight + topInset;
                toolbar.setLayoutParams(toolbarParams);
            }
            toolbar.setPadding(toolbarLeft, toolbarTop + topInset, toolbarRight, toolbarBottom);

            if (watermarkView != null) {
                ViewGroup.LayoutParams params = watermarkView.getLayoutParams();
                if (params instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
                    frameParams.setMargins(0, 0, dp(12), watermarkBaseBottomMargin + bottomInset);
                    watermarkView.setLayoutParams(frameParams);
                }
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private int getSystemBarTopInset(WindowInsets insets) {
        if (insets == null) return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.systemBars()).top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private int getSystemBarBottomInset(WindowInsets insets) {
        if (insets == null) return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.systemBars()).bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }
}
