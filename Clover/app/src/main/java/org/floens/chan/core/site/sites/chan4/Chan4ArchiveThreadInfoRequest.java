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

import android.util.JsonReader;

import com.android.volley.Response;

import org.floens.chan.core.model.Archive;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.Site;
import org.jsoup.Jsoup;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the opening post of one archived thread, to show its thumbnail, subject, reply count and
 * expiry time in the archive list.
 */
public class Chan4ArchiveThreadInfoRequest extends JsonReaderRequest<Archive.ThreadInfo> {
    private final Site site;
    private final Board board;

    public Chan4ArchiveThreadInfoRequest(Site site, Board board, int threadNo,
                                         Response.Listener<Archive.ThreadInfo> listener,
                                         Response.ErrorListener errorListener) {
        super(site.endpoints().thread(board, Loadable.forThread(site, board, threadNo)).toString(),
                listener, errorListener);
        this.site = site;
        this.board = board;
    }

    @Override
    public Archive.ThreadInfo readJson(JsonReader reader) throws Exception {
        Archive.ThreadInfo info = null;
        reader.beginObject();
        while (reader.hasNext()) {
            if (reader.nextName().equals("posts")) {
                reader.beginArray();
                if (reader.hasNext()) {
                    info = readOp(reader);
                }
                while (reader.hasNext()) {
                    reader.skipValue();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return info;
    }

    private Archive.ThreadInfo readOp(JsonReader reader) throws Exception {
        String subject = null;
        String comment = null;
        int replies = 0;
        int images = 0;
        long archivedOn = 0;
        String tim = null;
        boolean spoiler = false;
        boolean fileDeleted = false;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "sub":
                    subject = reader.nextString();
                    break;
                case "com":
                    comment = reader.nextString();
                    break;
                case "replies":
                    replies = reader.nextInt();
                    break;
                case "images":
                    images = reader.nextInt();
                    break;
                case "archived_on":
                    archivedOn = reader.nextLong();
                    break;
                case "tim":
                    tim = reader.nextString();
                    break;
                case "spoiler":
                    spoiler = reader.nextInt() == 1;
                    break;
                case "filedeleted":
                    fileDeleted = reader.nextInt() == 1;
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        String thumbnailUrl = null;
        if (tim != null && !fileDeleted) {
            Post.Builder builder = new Post.Builder().board(board);
            Map<String, String> args = new HashMap<>();
            args.put("tim", tim);
            thumbnailUrl = site.endpoints().thumbnailUrl(builder, spoiler, args).toString();
        }

        return new Archive.ThreadInfo(
                subject == null ? null : htmlToText(subject),
                comment == null ? null : htmlToText(comment),
                replies, images, archivedOn, thumbnailUrl);
    }

    private static String htmlToText(String html) {
        // Keep line breaks as spaces, so a two-line preview shows as much text as possible.
        return Jsoup.parse(html.replace("<br>", " ")).text().trim();
    }
}
