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
package org.floens.chan.core.net;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.HurlStack;

import org.floens.chan.core.offline.OfflineStore;
import org.floens.chan.core.settings.ChanSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class ProxiedHurlStack extends HurlStack {
    private final String userAgent;
    private final OfflineStore offlineStore;

    public ProxiedHurlStack(String userAgent, OfflineStore offlineStore) {
        super();
        this.userAgent = userAgent;
        this.offlineStore = offlineStore;
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        // Thumbnails and flags of threads saved for offline viewing
        if (request.getMethod() == Request.Method.GET) {
            File savedFile = offlineStore.findMedia(request.getUrl());
            if (savedFile != null && savedFile.exists()) {
                return new HttpResponse(HttpURLConnection.HTTP_OK, Collections.emptyList(),
                        (int) savedFile.length(), new FileInputStream(savedFile));
            }
        }

        return super.executeRequest(request, additionalHeaders);
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        // Start the connection by specifying a proxy server
        Proxy proxy = ChanSettings.getProxy();
        HttpURLConnection connection;
        if (proxy != null) {
            connection = (HttpURLConnection) url.openConnection(proxy);
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        // Use the same workaround as described in Volley's HurlStack:
        // Workaround for the M release HttpURLConnection not observing the
        // HttpURLConnection.setFollowRedirects() property.
        // https://code.google.com/p/android/issues/detail?id=194495
        connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());

        connection.setRequestProperty("User-Agent", userAgent);

        return connection;
    }
}
