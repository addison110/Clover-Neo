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

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostHttpIcon;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.core.site.parser.ChanReader;
import org.floens.chan.core.site.parser.ChanReaderProcessingQueue;
import org.floens.chan.core.site.parser.PostParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static org.floens.chan.core.site.SiteEndpoints.makeArgument;

/**
 * Reads a thread from the FoolFuuka archive API, so it shows like a thread from the site itself.
 * The comments are turned into 4chan's HTML, so the site's own parser can parse them.
 */
public class FoolFuukaReader implements ChanReader {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("&gt;&gt;(\\d+)");

    private final PostParser postParser;

    public FoolFuukaReader(PostParser postParser) {
        this.postParser = postParser;
    }

    @Override
    public PostParser getParser() {
        return postParser;
    }

    @Override
    public void loadThread(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        // {"<thread no>": {"op": {...}, "posts": {"<post no>": {...}, ...}}}
        // or {"error": "Thread not found."}
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("error")) {
                throw new IOException("Archive error: " + reader.nextString());
            } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String threadKey = reader.nextName();
                    if (threadKey.equals("op")) {
                        readPostObject(reader, queue);
                    } else if (threadKey.equals("posts")) {
                        readPosts(reader, queue);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void readPosts(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                readPostObject(reader, queue);
            }
            reader.endArray();
        } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                readPostObject(reader, queue);
            }
            reader.endObject();
        } else {
            reader.skipValue();
        }
    }

    @Override
    public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) {
        throw new UnsupportedOperationException("Archives only load threads");
    }

    @Override
    public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        Post.Builder builder = new Post.Builder();
        builder.board(queue.getLoadable().board);

        SiteEndpoints endpoints = queue.getLoadable().getSite().endpoints();

        long no = -1;
        long subNo = 0;
        long threadNo = -1;
        boolean isOp = false;
        String comment = null;
        String countryCode = null;
        String countryName = null;
        PostImage image = null;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "num":
                    no = nextLong(reader, -1);
                    break;
                case "subnum":
                    subNo = nextLong(reader, 0);
                    break;
                case "thread_num":
                    threadNo = nextLong(reader, -1);
                    break;
                case "op":
                    isOp = nextLong(reader, 0) == 1;
                    break;
                case "timestamp":
                    builder.setUnixTimestampSeconds(nextLong(reader, 0));
                    break;
                case "title":
                    builder.subject(orEmpty(nextString(reader)));
                    break;
                case "name":
                    builder.name(orEmpty(nextString(reader)));
                    break;
                case "trip":
                    builder.tripcode(orEmpty(nextString(reader)));
                    break;
                case "poster_hash":
                    builder.posterId(orEmpty(nextString(reader)));
                    break;
                case "capcode":
                    builder.moderatorCapcode(capcode(nextString(reader)));
                    break;
                case "poster_country":
                    countryCode = nextString(reader);
                    break;
                case "poster_country_name":
                    countryName = nextString(reader);
                    break;
                case "sticky":
                    builder.sticky(nextLong(reader, 0) == 1);
                    break;
                case "locked":
                    builder.closed(nextLong(reader, 0) == 1);
                    break;
                case "comment":
                    comment = nextString(reader);
                    break;
                case "media":
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        image = readMedia(reader);
                    } else {
                        reader.skipValue();
                    }
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        // Ghost posts were made on the archive itself, after the thread died
        if (no < 0 || subNo != 0 || no > Integer.MAX_VALUE) {
            return;
        }

        builder.id((int) no);
        builder.op(isOp);
        builder.opId(isOp ? 0 : (int) threadNo);
        builder.comment(toHtml(comment));
        builder.archived(true);

        List<PostImage> images = new ArrayList<>(1);
        if (image != null) {
            images.add(image);
        }
        builder.images(images);

        if (isOp) {
            // Update OP fields later on the main thread
            Post.Builder op = new Post.Builder();
            op.closed(builder.closed);
            op.archived(true);
            op.sticky(builder.sticky);
            queue.setOp(op);
        }

        Post cached = queue.getCachedPost(builder.id);
        if (cached != null) {
            queue.addForReuse(cached);
            return;
        }

        if (!TextUtils.isEmpty(countryCode)) {
            HttpUrl countryUrl = endpoints.icon(builder, "country",
                    makeArgument("country_code", countryCode.toLowerCase()));
            builder.addHttpIcon(new PostHttpIcon(countryUrl,
                    TextUtils.isEmpty(countryName) ? countryCode : countryName));
        }

        queue.addForParse(builder);
    }

    private PostImage readMedia(JsonReader reader) throws IOException {
        String mediaLink = null;
        String remoteMediaLink = null;
        String thumbLink = null;
        String fileName = null;
        String originalName = null;
        int width = 0;
        int height = 0;
        long size = 0;
        boolean spoiler = false;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "media_link":
                    mediaLink = nextString(reader);
                    break;
                case "remote_media_link":
                    remoteMediaLink = nextString(reader);
                    break;
                case "thumb_link":
                    thumbLink = nextString(reader);
                    break;
                case "media_filename":
                    fileName = nextString(reader);
                    break;
                case "media_orig":
                    originalName = nextString(reader);
                    break;
                case "media_w":
                    width = (int) nextLong(reader, 0);
                    break;
                case "media_h":
                    height = (int) nextLong(reader, 0);
                    break;
                case "media_size":
                    size = nextLong(reader, 0);
                    break;
                case "spoiler":
                    spoiler = nextLong(reader, 0) == 1;
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        // Some archives keep only the thumbnails, and 4chan's copy of the file is gone with
        // the thread, but it's the best there is
        HttpUrl imageUrl = parseUrl(mediaLink);
        if (imageUrl == null) {
            imageUrl = parseUrl(remoteMediaLink);
        }
        HttpUrl thumbnailUrl = parseUrl(thumbLink);
        if (imageUrl == null || thumbnailUrl == null || originalName == null) {
            return null;
        }

        String name = originalName;
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            name = originalName.substring(0, dot);
            extension = originalName.substring(dot + 1);
        }
        if (fileName == null) {
            fileName = originalName;
        } else {
            int fileNameDot = fileName.lastIndexOf('.');
            if (fileNameDot >= 0) {
                fileName = fileName.substring(0, fileNameDot);
            }
        }

        return new PostImage.Builder()
                .originalName(name)
                .thumbnailUrl(thumbnailUrl)
                .spoilerThumbnailUrl(thumbnailUrl)
                .imageUrl(imageUrl)
                .filename(org.jsoup.parser.Parser.unescapeEntities(fileName, false))
                .extension(extension)
                .imageWidth(width)
                .imageHeight(height)
                .spoiler(spoiler)
                .size(size)
                .build();
    }

    /**
     * Turns the archive's plain comment into 4chan's HTML.
     */
    static String toHtml(String comment) {
        if (comment == null) {
            return "";
        }

        StringBuilder html = new StringBuilder(comment.length() + 64);
        String[] lines = comment.replace("\r", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                html.append("<br>");
            }

            String line = escape(lines[i]);
            Matcher matcher = QUOTE_PATTERN.matcher(line);
            line = matcher.replaceAll("<a href=\"#p$1\" class=\"quotelink\">&gt;&gt;$1</a>");

            if (lines[i].startsWith(">") && !lines[i].startsWith(">>")) {
                html.append("<span class=\"quote\">").append(line).append("</span>");
            } else {
                html.append(line);
            }
        }

        return html.toString()
                .replace("[spoiler]", "<s>")
                .replace("[/spoiler]", "</s>")
                .replace("[code]", "<pre class=\"prettyprint\">")
                .replace("[/code]", "</pre>")
                .replace("[b]", "<b>")
                .replace("[/b]", "</b>")
                .replace("[i]", "<i>")
                .replace("[/i]", "</i>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String capcode(String capcode) {
        if (capcode == null) {
            return "";
        }
        switch (capcode) {
            case "M":
                return "mod";
            case "A":
                return "admin";
            case "D":
                return "developer";
            case "V":
                return "verified";
            case "F":
                return "founder";
            case "G":
                return "manager";
            default:
                // "N" is a normal post
                return "";
        }
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private static HttpUrl parseUrl(String url) {
        return TextUtils.isEmpty(url) ? null : HttpUrl.parse(url);
    }

    // The archives send numbers as strings or numbers, and leave fields null
    private static long nextLong(JsonReader reader, long fallback) throws IOException {
        switch (reader.peek()) {
            case NUMBER:
                return reader.nextLong();
            case STRING:
                try {
                    return Long.parseLong(reader.nextString());
                } catch (NumberFormatException e) {
                    return fallback;
                }
            case BOOLEAN:
                return reader.nextBoolean() ? 1 : 0;
            default:
                reader.skipValue();
                return fallback;
        }
    }

    private static String nextString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (reader.peek() == JsonToken.STRING || reader.peek() == JsonToken.NUMBER) {
            return reader.nextString();
        }
        reader.skipValue();
        return null;
    }
}
