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
package org.floens.chan.ui.captcha;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import org.floens.chan.core.site.Site;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import static org.floens.chan.ui.theme.ThemeHelper.theme;

/**
 * Shows 4chan's own captcha. The page runs 4chan's captcha script (the same one the website
 * uses), so the different kinds of captcha, the cooldowns and the Cloudflare check inside the
 * captcha frame are all handled by 4chan's code. The page hands the challenge and response back
 * when the user taps Post, and they are sent with the reply as t-challenge and t-response.
 */
public class Chan4CaptchaLayout extends WebView implements AuthenticationLayoutInterface {
    private static final String TAG = "Chan4CaptchaLayout";
    private static final String BASE_URL = "https://boards.4chan.org/";

    private final String boardCode;
    private final int threadNo;

    private AuthenticationLayoutCallback callback;

    public Chan4CaptchaLayout(Context context, String boardCode, int threadNo) {
        super(context);
        this.boardCode = boardCode;
        this.threadNo = threadNo;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        requestDisallowInterceptTouchEvent(true);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        // The captcha keeps its ticket in localStorage.
        settings.setDomStorageEnabled(true);
        // The captcha frame on sys.4chan.org needs its cookies, and so does the Cloudflare check.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message()
                        + " " + consoleMessage.sourceId());
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String host = Uri.parse(url).getHost();
                if (host == null || isCaptchaHost(host)) {
                    return false;
                } else {
                    AndroidUtils.openLink(url);
                    return true;
                }
            }
        });
        setBackgroundColor(0x00000000);

        addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    private static boolean isCaptchaHost(String host) {
        return host.equals("4chan.org") || host.endsWith(".4chan.org")
                || host.equals("4cdn.org") || host.endsWith(".4cdn.org")
                || host.equals("hcaptcha.com") || host.endsWith(".hcaptcha.com")
                || host.endsWith(".cloudflare.com");
    }

    @Override
    public void reset() {
        hardReset();
    }

    @Override
    public void hardReset() {
        boolean lightTheme = theme().isLightTheme;

        String html = IOUtils.assetAsString(getContext(), "captcha/chan4_captcha.html");
        html = html.replace("__board__", boardCode);
        html = html.replace("__thread_id__", String.valueOf(Math.max(threadNo, 0)));
        html = html.replace("__dark__", lightTheme ? "false" : "true");
        html = html.replace("__text_color__", lightTheme ? "#000000" : "#ffffff");

        loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
    }

    @Override
    public boolean requireResetAfterComplete() {
        // Loading a new captcha right away can put the user on a cooldown for the next one.
        return false;
    }

    @Override
    public void onDestroy() {
    }

    private void onCaptchaEntered(String challenge, String response) {
        callback.onAuthenticationComplete(this, challenge, response);
    }

    public static class CaptchaInterface {
        private final Chan4CaptchaLayout layout;

        public CaptchaInterface(Chan4CaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String challenge, final String response) {
            AndroidUtils.runOnUiThread(() -> layout.onCaptchaEntered(challenge, response));
        }
    }
}
