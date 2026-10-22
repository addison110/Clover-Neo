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
package org.floens.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.archive.Archive;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Opens the archive's API page for a thread, so the user can get past the archive's Cloudflare
 * check, which only guards the API. Once the page shows the thread's JSON, that JSON is handed
 * back to be shown. The check also leaves a cookie for the app's later requests.
 */
public class ArchiveCheckController extends Controller {
    private final Archive archive;
    private final Loadable loadable;
    private final Callback callback;

    private WebView webView;
    private boolean passed;

    public ArchiveCheckController(Context context, Archive archive, Loadable loadable, Callback callback) {
        super(context);
        this.archive = archive;
        this.loadable = loadable;
        this.callback = callback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate() {
        super.onCreate();
        navigation.title = archive.name;

        webView = new WebView(context);
        CookieManager.getInstance().setAcceptCookie(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // The cookie only works with the user agent that got it, so the app's own requests to the
        // archive use the same default user agent
        settings.setUserAgentString(Archive.userAgent(context));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (passed) {
                    return;
                }
                // The check page is HTML, the thread is JSON
                view.evaluateJavascript("document.body ? document.body.innerText : ''", result -> {
                    String text = unquote(result);
                    if (!passed && text.trim().startsWith("{")) {
                        passed = true;
                        CookieManager.getInstance().flush();
                        navigationController.popController();
                        callback.onArchiveJson(archive, text);
                    }
                });
            }
        });
        webView.loadUrl(archive.apiThreadUrl(loadable).toString());
        view = webView;
    }

    // evaluateJavascript gives the result as a JSON string literal
    private static String unquote(String result) {
        try {
            return new JSONArray("[" + result + "]").optString(0, "");
        } catch (JSONException e) {
            return "";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webView.stopLoading();
        webView.destroy();
    }

    public interface Callback {
        void onArchiveJson(Archive archive, String json);
    }
}
