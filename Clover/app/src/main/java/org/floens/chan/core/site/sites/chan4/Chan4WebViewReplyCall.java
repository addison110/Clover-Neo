/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.site.sites.chan4;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import org.floens.chan.core.site.SiteActions;
import org.floens.chan.core.site.http.ReplyResponse;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Sends a 4chan reply from a WebView instead of OkHttp, the way the quick reply on 4chan's
 * website does. The post then goes out with the cookies the captcha set in the WebView and with
 * the WebView's own browser identity, which 4chan and Cloudflare check.
 */
public class Chan4WebViewReplyCall {
    private static final String TAG = "Chan4WebViewReplyCall";
    private static final String UPLOAD_URL = "https://boards.4chan.org/__cloverneo_upload";
    private static final Random RANDOM = new Random();

    // Keeps the WebView of a running post from being collected
    private static final Set<Chan4WebViewReplyCall> running = new HashSet<>();

    private final Chan4ReplyCall call;
    private final String pageUrl;
    private final String postUrl;
    private final SiteActions.PostListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private boolean done = false;

    public Chan4WebViewReplyCall(Chan4ReplyCall call, String pageUrl, String postUrl,
                                 SiteActions.PostListener listener) {
        this.call = call;
        this.pageUrl = pageUrl;
        this.postUrl = postUrl;
        this.listener = listener;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void start() {
        running.add(this);
        call.replyResponse.password = Long.toHexString(RANDOM.nextLong());

        webView = new WebView(AndroidUtils.getAppContext());
        webView.getSettings().setJavaScriptEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message()
                        + " " + consoleMessage.sourceId());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (call.reply.file != null && UPLOAD_URL.equals(request.getUrl().toString())) {
                    try {
                        return new WebResourceResponse("application/octet-stream", null,
                                new FileInputStream(call.reply.file));
                    } catch (FileNotFoundException e) {
                        Logger.e(TAG, "Reply file not found", e);
                    }
                }
                return null;
            }
        });

        webView.addJavascriptInterface(new ReplyInterface(), "ReplyCallback");

        String html = IOUtils.assetAsString(AndroidUtils.getAppContext(), "captcha/chan4_post.html");
        // The page has to be on boards.4chan.org, the post is only accepted from there
        webView.loadDataWithBaseURL(pageUrl, html, "text/html", "UTF-8", null);
    }

    private void onResult(int status, String contentType, String body) {
        ReplyResponse replyResponse = call.replyResponse;
        try {
            if (contentType.contains("json")) {
                JSONObject json = new JSONObject(body);
                if (json.has("error")) {
                    replyResponse.errorMessage = Jsoup.parse(json.getString("error")).body().text();
                    replyResponse.probablyBanned = replyResponse.errorMessage.contains("banned");
                } else if (json.has("pid")) {
                    replyResponse.postNo = json.getInt("pid");
                    int threadNo = json.optInt("tid", 0);
                    replyResponse.threadNo = threadNo > 0 ? threadNo : replyResponse.postNo;
                    replyResponse.posted = true;
                }
            } else {
                call.process(null, body);
            }
        } catch (Exception e) {
            finish(() -> listener.onPostError(call, e));
            return;
        }

        if (!replyResponse.posted && replyResponse.errorMessage == null && status != 200) {
            replyResponse.errorMessage = "HTTP " + status;
        }
        finish(() -> listener.onPostComplete(call, replyResponse));
    }

    private void onError(String message) {
        finish(() -> listener.onPostError(call, new IOException(message)));
    }

    private void finish(Runnable result) {
        if (done) {
            return;
        }
        done = true;

        running.remove(this);
        webView.destroy();
        webView = null;

        result.run();
    }

    // Called by the page on a background thread
    public class ReplyInterface {
        @JavascriptInterface
        public String fields() {
            Map<String, String> fields = call.textParameters();
            return new JSONObject(fields).toString();
        }

        @JavascriptInterface
        public String postUrl() {
            return postUrl;
        }

        @JavascriptInterface
        public boolean hasFile() {
            return call.reply.file != null;
        }

        @JavascriptInterface
        public String fileName() {
            return call.reply.fileName;
        }

        @JavascriptInterface
        public String uploadUrl() {
            return UPLOAD_URL;
        }

        @JavascriptInterface
        public void onResult(int status, String contentType, String body) {
            handler.post(() -> Chan4WebViewReplyCall.this.onResult(status, contentType, body));
        }

        @JavascriptInterface
        public void onError(String message) {
            handler.post(() -> Chan4WebViewReplyCall.this.onError(message));
        }
    }
}
