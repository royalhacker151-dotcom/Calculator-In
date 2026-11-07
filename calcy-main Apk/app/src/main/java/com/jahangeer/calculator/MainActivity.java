package com.jahangeer.calculator;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        // Let the calculator handle "back" like the Escape key (close image / sessions / full screen)
        webView.evaluateJavascript(
                "(function(){document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));})();",
                null);
        // Second back press within the app exits normally
        if (!escHandled) {
            escHandled = true;
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    escHandled = false;
                }
            }, 1500);
        } else {
            super.onBackPressed();
        }
    }

    private boolean escHandled = false;

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Saves a JPEG and returns its content Uri (or null).
     * toDownloads = true  -> Downloads/Calculator (Files app > Downloads)
     * toDownloads = false -> Pictures/JahangeerCalculator (Gallery)
     */
    private Uri storeImage(String base64, String name, boolean toDownloads) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    toDownloads ? "Download/Calculator" : "Pictures/JahangeerCalculator");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(
                    toDownloads ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values);
            if (uri == null) return null;

            OutputStream os = resolver.openOutputStream(uri);
            if (os == null) return null;
            try {
                os.write(data);
                os.flush();
            } finally {
                os.close();
            }

            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    /** Methods callable from the page as window.AndroidBridge.* */
    private class Bridge {

        @JavascriptInterface
        public String saveImage(String base64, String name) {
            Uri uri = storeImage(base64, name, true);
            if (uri == null) {
                toast("Could not save image");
                return "error";
            }
            toast("Saved: Downloads/Calculator/" + name);
            return "ok";
        }

        @JavascriptInterface
        public String shareImage(String base64, String name) {
            final Uri uri = storeImage(base64, name, false);
            if (uri == null) {
                toast("Could not prepare image");
                return "error";
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/jpeg");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share image"));
                }
            });
            return "ok";
        }
    }
}
