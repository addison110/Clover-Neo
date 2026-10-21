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

import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.sites.chan4.Chan4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.HttpUrl;

/**
 * The FoolFuuka archives that keep 4chan threads after they're gone from 4chan.
 */
public class Archive {
    private static final List<Archive> ARCHIVES = Collections.unmodifiableList(Arrays.asList(
            new Archive("4plebs", "archive.4plebs.org",
                    "adv", "f", "hr", "mlpol", "mo", "o", "pol", "s4s", "sp", "tg", "trv", "tv", "x"),
            new Archive("Desuarchive", "desuarchive.org",
                    "a", "aco", "an", "c", "cgl", "co", "d", "fit", "g", "his", "int", "k", "m",
                    "mlp", "mu", "q", "qa", "r9k", "tg", "trash", "vr", "wsg"),
            new Archive("B4K", "arch.b4k.dev",
                    "v", "vg", "vm", "vmg", "vp", "vrpg", "vst"),
            new Archive("Palanq", "archive.palanq.win",
                    "bant", "c", "con", "e", "i", "n", "news", "out", "p", "pw", "qst", "toy",
                    "vip", "vp", "vt", "w", "wg", "wsr"),
            new Archive("Archive of Sins", "archiveofsins.com",
                    "h", "hc", "hm", "i", "lgbt", "r", "s", "soc", "t", "u"),
            new Archive("TheBArchive", "thebarchive.com",
                    "b", "bant"),
            // Covers almost every board, so it's tried last
            new Archive("Archived.Moe", "archived.moe")
    ));

    public final String name;
    public final String domain;
    // Empty when the archive keeps every board
    private final Set<String> boards;

    private Archive(String name, String domain, String... boards) {
        this.name = name;
        this.domain = domain;
        this.boards = new HashSet<>(Arrays.asList(boards));
    }

    /**
     * The archives that may have this thread, best first. Empty if the site has no archives.
     */
    public static List<Archive> forLoadable(Loadable loadable) {
        List<Archive> archives = new ArrayList<>();
        if (loadable.isThreadMode() && loadable.site instanceof Chan4) {
            for (Archive archive : ARCHIVES) {
                if (archive.boards.isEmpty() || archive.boards.contains(loadable.boardCode)) {
                    archives.add(archive);
                }
            }
        }
        return archives;
    }

    public HttpUrl apiThreadUrl(Loadable loadable) {
        return new HttpUrl.Builder()
                .scheme("https")
                .host(domain)
                .addPathSegments("_/api/chan/thread/")
                .addQueryParameter("board", loadable.boardCode)
                .addQueryParameter("num", String.valueOf(loadable.no))
                .build();
    }

    public HttpUrl webThreadUrl(Loadable loadable) {
        return new HttpUrl.Builder()
                .scheme("https")
                .host(domain)
                .addPathSegment(loadable.boardCode)
                .addPathSegment("thread")
                .addPathSegment(String.valueOf(loadable.no))
                .addPathSegment("")
                .build();
    }
}
