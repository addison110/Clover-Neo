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

import android.text.TextUtils;

import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.common.CommonReplyHttpCall;
import org.floens.chan.core.site.http.Reply;

import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan4ReplyCall extends CommonReplyHttpCall {
    public Chan4ReplyCall(Site site, Reply reply) {
        super(site, reply);
    }

    @Override
    public void addParameters(MultipartBody.Builder formBuilder) {
        for (Map.Entry<String, String> field : textParameters().entrySet()) {
            formBuilder.addFormDataPart(field.getKey(), field.getValue());
        }

        if (reply.file != null) {
            formBuilder.addFormDataPart("upfile", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }
    }

    /**
     * The form fields of the reply, without the file.
     */
    public Map<String, String> textParameters() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mode", "regist");
        fields.put("pwd", replyResponse.password);

        if (reply.loadable.isThreadMode()) {
            fields.put("resto", String.valueOf(reply.loadable.no));
        }

        fields.put("name", reply.name);
        fields.put("email", reply.options);

        if (!reply.loadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
            fields.put("sub", reply.subject);
        }

        fields.put("com", reply.comment);

        if (reply.captchaChallenge != null && reply.captchaResponse != null) {
            fields.put("t-challenge", reply.captchaChallenge);
            fields.put("t-response", reply.captchaResponse);
        }

        if (reply.spoilerImage) {
            fields.put("spoiler", "on");
        }

        return fields;
    }
}
