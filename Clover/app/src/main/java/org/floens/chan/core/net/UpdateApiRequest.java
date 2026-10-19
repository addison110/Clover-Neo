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

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import com.android.volley.Response;

import org.floens.chan.BuildConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * Reads the latest release of the GitHub repository at {@link BuildConfig#UPDATE_API_ENDPOINT}.
 * The release tag is the version name (e.g. v3.1.0) and the first attached .apk is the update.
 */
public class UpdateApiRequest extends JsonReaderRequest<UpdateApiRequest.UpdateApiResponse> {
    public static final String TYPE_UPDATE = "update";

    private static final Pattern VERSION_TAG = Pattern.compile("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public UpdateApiRequest(Response.Listener<UpdateApiResponse> listener,
                            Response.ErrorListener errorListener) {
        super(BuildConfig.UPDATE_API_ENDPOINT, listener, errorListener);
    }

    @Override
    public UpdateApiResponse readJson(JsonReader reader) throws Exception {
        UpdateApiMessage message = new UpdateApiMessage();
        message.type = TYPE_UPDATE;
        String tag = "";
        String body = "";

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name":
                    tag = reader.nextString();
                    message.code = versionCodeFromTag(tag);
                    break;
                case "body":
                    body = readNullableString(reader);
                    break;
                case "assets":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        HttpUrl apkUrl = readApkAsset(reader);
                        if (message.apkUrl == null) {
                            message.apkUrl = apkUrl;
                        }
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        message.messageHtml = "<h3>" + TextUtils.htmlEncode(tag) + "</h3>" +
                TextUtils.htmlEncode(body).replace("\n", "<br>");

        UpdateApiResponse response = new UpdateApiResponse();
        response.messages.add(message);
        return response;
    }

    /**
     * Converts a tag like v3.1.0 to the versionCode format XXYYZZ, 0 if it is not a version.
     */
    public static int versionCodeFromTag(String tag) {
        Matcher matcher = VERSION_TAG.matcher(tag);
        if (!matcher.find()) {
            return 0;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        return major * 10000 + minor * 100 + patch;
    }

    private HttpUrl readApkAsset(JsonReader reader) throws IOException {
        String name = "";
        String url = null;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "name":
                    name = reader.nextString();
                    break;
                case "browser_download_url":
                    url = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return name.endsWith(".apk") && url != null ? HttpUrl.parse(url) : null;
    }

    private String readNullableString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return "";
        }
        return reader.nextString();
    }

    public static class UpdateApiResponse {
        public List<UpdateApiMessage> messages = new ArrayList<>();
    }

    public static class UpdateApiMessage {
        public String type;
        public int code;
        public String messageHtml;
        public HttpUrl apkUrl;
    }
}
