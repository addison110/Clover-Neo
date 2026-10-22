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
package org.floens.chan.core.site;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public abstract class ImageSearch {
    public static final List<ImageSearch> engines = new ArrayList<>();

    public abstract int getId();

    public abstract String getName();

    public abstract String getUrl(String imageUrl);

    /**
     * Encodes the image url so it is passed as a single query parameter, even when it contains
     * characters like {@code &} or {@code ?}.
     */
    static String encode(String imageUrl) {
        try {
            return URLEncoder.encode(imageUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static {
        engines.add(new ImageSearch() {
            public int getId() {
                return 0;
            }

            public String getName() {
                return "Google";
            }

            public String getUrl(String imageUrl) {
                return "https://lens.google.com/uploadbyurl?url=" + encode(imageUrl);
            }
        });

        engines.add(new ImageSearch() {
            public int getId() {
                return 1;
            }

            public String getName() {
                return "iqdb";
            }

            public String getUrl(String imageUrl) {
                return "https://iqdb.org/?url=" + encode(imageUrl);
            }
        });

        engines.add(new ImageSearch() {
            public int getId() {
                return 2;
            }

            public String getName() {
                return "SauceNAO";
            }

            public String getUrl(String imageUrl) {
                return "https://saucenao.com/search.php?url=" + encode(imageUrl);
            }
        });

        engines.add(new ImageSearch() {
            public int getId() {
                return 3;
            }

            public String getName() {
                return "TinEye";
            }

            public String getUrl(String imageUrl) {
                return "https://tineye.com/search/?url=" + encode(imageUrl);
            }
        });

        engines.add(new ImageSearch() {
            public int getId() {
                return 4;
            }

            public String getName() {
                return "trace.moe";
            }

            public String getUrl(String imageUrl) {
                return "https://trace.moe/?url=" + encode(imageUrl);
            }
        });

        engines.add(new ImageSearch() {
            public int getId() {
                return 5;
            }

            public String getName() {
                return "Yandex";
            }

            public String getUrl(String imageUrl) {
                return "https://yandex.com/images/search?rpt=imageview&url=" + encode(imageUrl);
            }
        });
    }
}
