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
package org.floens.chan.core.site.archive;

import android.content.Context;
import android.text.TextUtils;
import android.util.JsonReader;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.AuthFailureError;
import com.android.volley.Response;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.loader.ChanLoaderRequestParams;
import org.floens.chan.core.site.loader.ChanLoaderResponse;
import org.floens.chan.core.site.parser.ChanReaderRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a thread that's gone from 4chan from one of its archives.
 */
public class ArchiveThreadRequest extends JsonReaderRequest<ChanLoaderResponse> {
    private static final int TIMEOUT_MS = 15000;

    private final Archive archive;
    private final ChanReaderRequest readerRequest;
    private final Map<String, String> headers = new HashMap<>();

    public ArchiveThreadRequest(Context context, Archive archive, Loadable loadable, List<Post> cached,
                                Response.Listener<ChanLoaderResponse> listener,
                                Response.ErrorListener errorListener) {
        super(archive.apiThreadUrl(loadable).toString(), listener, errorListener);
        this.archive = archive;

        FoolFuukaReader reader = new FoolFuukaReader(loadable.getSite().chanReader().getParser());
        // Only used to parse the posts, it's never sent
        readerRequest = new ChanReaderRequest(new ChanLoaderRequestParams(
                loadable, reader, cached, null, null));

        headers.put("User-Agent", Archive.userAgent(context));
        String cookies = archive.cookies();
        if (!TextUtils.isEmpty(cookies)) {
            headers.put("Cookie", cookies);
        }

        setShouldCache(false);
        setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1f));
    }

    public Archive getArchive() {
        return archive;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers;
    }

    @Override
    public ChanLoaderResponse readJson(JsonReader reader) throws Exception {
        ChanLoaderResponse response = readerRequest.readJson(reader);
        response.archive = archive;
        return response;
    }
}
