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
package org.floens.chan.core.offline;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import okio.ByteString;

/**
 * The files of the threads saved for offline viewing, in the app's private storage.
 * <p>Each file is named after the SHA-1 of the url it came from, so the network code can serve a
 * saved copy for any url without knowing which thread it belongs to:
 * <ul>
 * <li>{@code offline/threads/<key>.json}: the thread as the site's API returned it.</li>
 * <li>{@code offline/threads/<key>.files}: the keys of the thread's media, one per line.</li>
 * <li>{@code offline/media/<key>}: images, videos, thumbnails and flags.</li>
 * </ul>
 */
public class OfflineStore {
    private static final String THREAD_EXTENSION = ".json";
    private static final String MANIFEST_EXTENSION = ".files";
    private static final String TEMP_EXTENSION = ".tmp";

    private final File threadsDirectory;
    private final File mediaDirectory;

    // Keys of the media on disk, so a lookup doesn't touch the disk
    private final Set<String> mediaKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public OfflineStore(Context applicationContext) {
        File root = new File(applicationContext.getFilesDir(), "offline");
        threadsDirectory = new File(root, "threads");
        mediaDirectory = new File(root, "media");

        String[] media = mediaDirectory.list();
        if (media != null) {
            for (String name : media) {
                if (!name.endsWith(TEMP_EXTENSION)) {
                    mediaKeys.add(name);
                }
            }
        }
    }

    public static String key(String url) {
        return ByteString.encodeUtf8(url).sha1().hex();
    }

    /**
     * The saved copy of a media url, or {@code null} if it isn't saved.
     */
    @Nullable
    public File findMedia(String url) {
        if (mediaKeys.isEmpty()) {
            return null;
        }

        String key = key(url);
        return mediaKeys.contains(key) ? new File(mediaDirectory, key) : null;
    }

    public boolean hasMedia(String key) {
        return mediaKeys.contains(key);
    }

    public File getMediaFile(String key) {
        return new File(mediaDirectory, key);
    }

    public File getThreadFile(String threadKey) {
        return new File(threadsDirectory, threadKey + THREAD_EXTENSION);
    }

    public File getManifestFile(String threadKey) {
        return new File(threadsDirectory, threadKey + MANIFEST_EXTENSION);
    }

    public File getTempFile(File file) {
        return new File(file.getPath() + TEMP_EXTENSION);
    }

    public File getThreadsDirectory() {
        return threadsDirectory;
    }

    public File getMediaDirectory() {
        return mediaDirectory;
    }

    /**
     * The thread key a file in the threads directory belongs to, or {@code null} for other files.
     */
    @Nullable
    public String threadKeyOf(String fileName) {
        if (fileName.endsWith(THREAD_EXTENSION)) {
            return fileName.substring(0, fileName.length() - THREAD_EXTENSION.length());
        } else if (fileName.endsWith(MANIFEST_EXTENSION)) {
            return fileName.substring(0, fileName.length() - MANIFEST_EXTENSION.length());
        } else {
            return null;
        }
    }

    public void mediaAdded(String key) {
        mediaKeys.add(key);
    }

    public void mediaRemoved(String key) {
        mediaKeys.remove(key);
    }

    public boolean ensureDirectories() {
        return (threadsDirectory.isDirectory() || threadsDirectory.mkdirs())
                && (mediaDirectory.isDirectory() || mediaDirectory.mkdirs());
    }
}
