package lol.marse.anime;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.content.pm.ActivityInfo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Loads https://anime.marse.lol and nothing else.
 *  - network-level ad blocking (assets/blocklist.txt)
 *  - pop-up / new-window blocking
 *  - top-level navigation locked to the site (a few known sites open in the system browser)
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://anime.marse.lol/";
    private static final String HOME_HOST = "anime.marse.lol";

    /** Hosts that may be opened in the system browser when the user taps a link. */
    private static final Set<String> EXTERNAL_OK = new HashSet<>(Arrays.asList(
            "anilist.co", "myanimelist.net", "youtube.com", "youtu.be",
            "theanimecommunity.com", "discord.com", "discord.gg"));

    private final Set<String> blocked = new HashSet<>();

    private FrameLayout root;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private FullscreenChrome chrome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadBlocklist();

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF04040A);
        web = new WebView(this);
        web.setBackgroundColor(0xFF04040A);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(true);               // so onCreateWindow can refuse pop-ups
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);                     // assets still work
        s.setAllowContentAccess(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);        // video players need their own cookies

        web.setWebViewClient(new AdBlockClient());
        chrome = new FullscreenChrome();
        web.setWebChromeClient(chrome);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }
    }

    // ---------------------------------------------------------------- ad blocking

    private void loadBlocklist() {
        try (InputStream in = getAssets().open("blocklist.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (line.isEmpty() || line.startsWith("#")) continue;
                blocked.add(line);
            }
        } catch (IOException ignored) { }
    }

    /** true if host, or any parent domain of it, is on the list. */
    private boolean isBlocked(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (blocked.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0 || dot == host.length() - 1) return false;
            host = host.substring(dot + 1);
        }
    }

    private static boolean isHome(String host) {
        return host != null && (host.equals(HOME_HOST) || host.endsWith("." + HOME_HOST));
    }

    private static boolean isExternalOk(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (EXTERNAL_OK.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0) return false;
            host = host.substring(dot + 1);
        }
    }

    private class AdBlockClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (isBlocked(u.getHost())) {
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;          // iframes load normally (still filtered above)
            Uri u = request.getUrl();
            String host = u.getHost();
            if ("https".equals(u.getScheme()) && isHome(host)) return false;
            if ("file".equals(u.getScheme()) && u.getPath() != null && u.getPath().startsWith("/android_asset/")) return false;
            // anything else is an ad redirect or a link out: only a few trusted sites open, in the real browser
            if (request.hasGesture() && "https".equals(u.getScheme()) && isExternalOk(host) && !isBlocked(host)) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                view.loadUrl("file:///android_asset/offline.html");
            }
        }
    }

    // ---------------------------------------------------------------- pop-ups + fullscreen video

    private class FullscreenChrome extends WebChromeClient {

        /** window.open() / target=_blank: refuse every one. */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
            return false;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customCallback = callback;
            web.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            view.setKeepScreenOn(true);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            customView.setKeepScreenOn(false);
            root.removeView(customView);
            customView = null;
            web.setVisibility(View.VISIBLE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (customCallback != null) customCallback.onCustomViewHidden();
            customCallback = null;
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            chrome.onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onPause() { web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        if (web != null) { root.removeView(web); web.destroy(); }
        super.onDestroy();
    }
}
