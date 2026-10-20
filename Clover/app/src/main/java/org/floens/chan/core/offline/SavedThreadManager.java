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
import android.util.JsonReader;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.RequestQueue;

import org.floens.chan.R;
import org.floens.chan.core.database.DatabaseLoadableManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.DatabaseSavedThreadManager;
import org.floens.chan.core.di.UserAgentProvider;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostHttpIcon;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.model.orm.SavedThread;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.loader.ChanLoaderRequestParams;
import org.floens.chan.core.site.loader.ChanLoaderResponse;
import org.floens.chan.core.site.parser.ChanReaderRequest;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.floens.chan.Chan.injector;
import static org.floens.chan.utils.AndroidUtils.runOnUiThread;

/**
 * Saves threads and their media for offline viewing.
 * <p>A save stores the thread's API response and downloads every image, video, thumbnail and flag
 * in it. {@link org.floens.chan.core.site.loader.ChanThreadLoader} shows the saved copy when the
 * thread can't be loaded, and the network code serves saved media instead of downloading it.
 * <p>Saved threads with auto-update on are saved again every time they load. They are kept
 * watched, so the thread watcher loads them in the background.
 * <p>All methods must be called on the main thread.
 */
@Singleton
public class SavedThreadManager {
    private static final String TAG = "SavedThreadManager";

    private static final int DOWNLOAD_THREADS = 3;
    private static final int TIMEOUT = 30000;

    private final Context applicationContext;
    private final DatabaseManager databaseManager;
    private final DatabaseSavedThreadManager databaseSavedThreadManager;
    private final DatabaseLoadableManager databaseLoadableManager;
    private final RequestQueue requestQueue;
    private final OfflineStore offlineStore;
    private final String userAgent;
    private final SavedThreadNotifications notifications;

    // Saves run one at a time, reading saved copies doesn't wait for them
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();

    private final List<SavedThread> savedThreads;

    // Saves waiting to run, by saved thread id. A newer save replaces a waiting one.
    private final Map<Integer, SaveJob> waitingJobs = new HashMap<>();
    // Saves that show progress, by saved thread id
    private final Set<Integer> manualSaves = new HashSet<>();

    @Inject
    public SavedThreadManager(Context applicationContext, DatabaseManager databaseManager,
                              RequestQueue requestQueue, OfflineStore offlineStore,
                              UserAgentProvider userAgentProvider) {
        this.applicationContext = applicationContext;
        this.databaseManager = databaseManager;
        this.requestQueue = requestQueue;
        this.offlineStore = offlineStore;
        userAgent = userAgentProvider.getUserAgent();
        notifications = new SavedThreadNotifications(applicationContext);

        databaseSavedThreadManager = databaseManager.getDatabaseSavedThreadManager();
        databaseLoadableManager = databaseManager.getDatabaseLoadableManager();
        savedThreads = databaseManager.runTask(databaseSavedThreadManager.getSavedThreads());

        Set<String> threadKeys = new HashSet<>();
        for (SavedThread savedThread : savedThreads) {
            String threadKey = getThreadKey(savedThread.loadable);
            if (threadKey != null) {
                threadKeys.add(threadKey);
            }
        }
        saveExecutor.execute(() -> removeUnusedFiles(threadKeys));
    }

    public List<SavedThread> getSavedThreads() {
        return Collections.unmodifiableList(savedThreads);
    }

    @Nullable
    public SavedThread findByLoadable(Loadable loadable) {
        for (int i = 0; i < savedThreads.size(); i++) {
            SavedThread savedThread = savedThreads.get(i);
            if (savedThread.loadable.equals(loadable)) {
                return savedThread;
            }
        }
        return null;
    }

    /**
     * If the saved copy of the thread is on this device. A backup restored on another device
     * has the saved threads, but not their files.
     */
    public boolean hasSavedCopy(Loadable loadable) {
        if (findByLoadable(loadable) == null) {
            return false;
        }
        String threadKey = getThreadKey(loadable);
        return threadKey != null && offlineStore.getThreadFile(threadKey).exists();
    }

    public boolean isSaving(SavedThread savedThread) {
        return manualSaves.contains(savedThread.id);
    }

    /**
     * Saves the thread with all its media, showing the progress in a notification.
     *
     * @param op the thread's op if it's loaded, used for the thumbnail and the bookmark title.
     */
    public void save(Loadable loadable, @Nullable Post op, boolean autoUpdate) {
        if (!loadable.isThreadMode()) {
            return;
        }
        if (loadable.id == 0) {
            loadable = databaseLoadableManager.get(loadable);
        }

        SavedThread savedThread = findByLoadable(loadable);
        if (savedThread == null) {
            savedThread = new SavedThread();
            savedThread.loadable = loadable;
            savedThread.autoUpdate = autoUpdate;
            if (op != null && op.image() != null) {
                savedThread.thumbnailUrl = op.image().getThumbnailUrl().toString();
            }
            databaseManager.runTask(databaseSavedThreadManager.createSavedThread(savedThread));
            savedThreads.add(0, savedThread);
        } else if (autoUpdate && !savedThread.autoUpdate) {
            savedThread.autoUpdate = true;
            databaseManager.runTaskAsync(databaseSavedThreadManager.updateSavedThread(savedThread));
        }

        if (savedThread.autoUpdate) {
            keepWatched(savedThread);
        }

        Toast.makeText(applicationContext, R.string.saved_thread_saving, Toast.LENGTH_SHORT).show();
        manualSaves.add(savedThread.id);
        notifications.showProgress(savedThread, 0, 0);
        postChanged(savedThread);
        fetch(savedThread);
    }

    public void setAutoUpdate(SavedThread savedThread, boolean autoUpdate) {
        if (savedThread.autoUpdate == autoUpdate) {
            return;
        }

        savedThread.autoUpdate = autoUpdate;
        databaseManager.runTaskAsync(databaseSavedThreadManager.updateSavedThread(savedThread));

        if (autoUpdate) {
            keepWatched(savedThread);
            // Catch up right away instead of waiting for the watcher
            fetch(savedThread);
        }
        postChanged(savedThread);
    }

    public void delete(SavedThread savedThread) {
        savedThreads.remove(savedThread);
        manualSaves.remove(savedThread.id);
        notifications.cancel(savedThread);
        databaseManager.runTaskAsync(databaseSavedThreadManager.deleteSavedThread(savedThread));

        Set<String> threadKeys = new HashSet<>();
        for (SavedThread other : savedThreads) {
            String threadKey = getThreadKey(other.loadable);
            if (threadKey != null) {
                threadKeys.add(threadKey);
            }
        }
        saveExecutor.execute(() -> removeUnusedFiles(threadKeys));

        EventBus.getDefault().post(new SavedThreadRemovedMessage(savedThread));
    }

    /**
     * Called by the thread loader after every successful load from the site.
     */
    public void onThreadLoaded(Loadable loadable, ChanLoaderResponse response) {
        if (response.rawData == null || response.savedCopy || savedThreads.isEmpty()) {
            return;
        }

        SavedThread savedThread = findByLoadable(loadable);
        if (savedThread != null && savedThread.autoUpdate) {
            enqueue(savedThread, response);
        }
    }

    /**
     * Reads the saved copy of the thread. The callback gets {@code null} if it can't be read.
     */
    public void loadSavedCopy(Loadable loadable, List<Post> cached, SavedCopyCallback callback) {
        String threadKey = getThreadKey(loadable);
        if (threadKey == null) {
            callback.onSavedCopy(null);
            return;
        }

        File file = offlineStore.getThreadFile(threadKey);
        ChanReaderRequest readerRequest = new ChanReaderRequest(new ChanLoaderRequestParams(
                loadable, loadable.getSite().chanReader(), cached, null, null));

        readExecutor.execute(() -> {
            ChanLoaderResponse response = null;
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                response = readerRequest.readJson(reader);
                response.savedCopy = true;
            } catch (Exception e) {
                Logger.e(TAG, "Could not read the saved copy of " + loadable, e);
            }

            ChanLoaderResponse result = response;
            runOnUiThread(() -> callback.onSavedCopy(result));
        });
    }

    @Nullable
    private String getThreadKey(Loadable loadable) {
        try {
            return OfflineStore.key(loadable.getSite().endpoints().thread(loadable.board, loadable).toString());
        } catch (RuntimeException e) {
            // The site or board is gone
            return null;
        }
    }

    private void keepWatched(SavedThread savedThread) {
        WatchManager watchManager = injector().instance(WatchManager.class);
        Pin pin = watchManager.findPinByLoadable(savedThread.loadable);
        if (pin == null) {
            pin = new Pin();
            pin.loadable = savedThread.loadable;
            pin.thumbnailUrl = savedThread.thumbnailUrl;
            watchManager.createPin(pin);
        } else if (!pin.watching && !pin.archived && !pin.isError) {
            watchManager.toggleWatch(pin);
        }
    }

    private void fetch(SavedThread savedThread) {
        Loadable loadable = savedThread.loadable;
        ChanReaderRequest request = new ChanReaderRequest(new ChanLoaderRequestParams(
                loadable, loadable.getSite().chanReader(), new ArrayList<>(),
                response -> enqueue(savedThread, response),
                error -> {
                    Logger.e(TAG, "Could not load " + loadable + " to save it", error);
                    if (manualSaves.remove(savedThread.id)) {
                        notifications.cancel(savedThread);
                        Toast.makeText(applicationContext, R.string.saved_thread_failed, Toast.LENGTH_LONG).show();
                        postChanged(savedThread);
                    }
                }));
        requestQueue.add(request);
    }

    private void enqueue(SavedThread savedThread, ChanLoaderResponse response) {
        if (!savedThreads.contains(savedThread)) {
            // Deleted while it was loading
            return;
        }

        String threadKey = getThreadKey(savedThread.loadable);
        if (threadKey == null || response.rawData == null || response.posts.isEmpty()) {
            return;
        }

        SaveJob job = new SaveJob();
        job.savedThread = savedThread;
        job.threadKey = threadKey;
        job.json = response.rawData;
        job.postCount = response.posts.size();
        job.mediaUrls = getMediaUrls(response.posts);

        Post op = response.posts.get(0);
        if (savedThread.thumbnailUrl == null && op.image() != null) {
            savedThread.thumbnailUrl = op.image().getThumbnailUrl().toString();
        }

        synchronized (waitingJobs) {
            if (waitingJobs.put(savedThread.id, job) != null) {
                // The waiting save picks up the newer data
                return;
            }
        }

        saveExecutor.execute(() -> {
            SaveJob waiting;
            synchronized (waitingJobs) {
                waiting = waitingJobs.remove(savedThread.id);
            }
            if (waiting != null) {
                runSave(waiting);
            }
        });
    }

    private List<String> getMediaUrls(List<Post> posts) {
        Set<String> urls = new LinkedHashSet<>();
        for (Post post : posts) {
            if (post.images != null) {
                for (PostImage image : post.images) {
                    if (image.thumbnailUrl != null) {
                        urls.add(image.thumbnailUrl.toString());
                    }
                    if (image.spoilerThumbnailUrl != null) {
                        urls.add(image.spoilerThumbnailUrl.toString());
                    }
                    if (image.imageUrl != null) {
                        urls.add(image.imageUrl.toString());
                    }
                }
            }
            if (post.httpIcons != null) {
                for (PostHttpIcon icon : post.httpIcons) {
                    urls.add(icon.url.toString());
                }
            }
        }
        return new ArrayList<>(urls);
    }

    // Runs on the save executor
    private void runSave(SaveJob job) {
        SavedThread savedThread = job.savedThread;
        long start = Time.startTiming();

        if (!offlineStore.ensureDirectories()) {
            Logger.e(TAG, "Could not create the offline directories");
            runOnUiThread(() -> onSaveFinished(job, false));
            return;
        }

        try {
            writeAtomically(offlineStore.getThreadFile(job.threadKey), job.json);
        } catch (IOException e) {
            Logger.e(TAG, "Could not save the thread " + savedThread.loadable, e);
            runOnUiThread(() -> onSaveFinished(job, false));
            return;
        }

        // Keep media of earlier saves, posts deleted from the site stay readable in the saved copy
        Map<String, String> media = new LinkedHashMap<>();
        for (String key : readManifest(job.threadKey)) {
            media.put(key, null);
        }
        List<String> toDownload = new ArrayList<>();
        for (String url : job.mediaUrls) {
            String key = OfflineStore.key(url);
            media.put(key, url);
            if (!offlineStore.hasMedia(key)) {
                toDownload.add(url);
            }
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(ChanSettings.getProxy())
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .build();

        int total = toDownload.size();
        int[] done = {0};
        List<Future<Boolean>> downloads = new ArrayList<>(total);
        for (String url : toDownload) {
            downloads.add(downloadExecutor.submit(() -> {
                boolean success = download(client, url);
                synchronized (done) {
                    done[0]++;
                    int progress = done[0];
                    runOnUiThread(() -> {
                        if (manualSaves.contains(savedThread.id)) {
                            notifications.showProgress(savedThread, progress, total);
                        }
                    });
                }
                return success;
            }));
        }

        int missing = 0;
        for (Future<Boolean> download : downloads) {
            try {
                if (!download.get()) {
                    missing++;
                }
            } catch (Exception e) {
                missing++;
            }
        }

        // Only list files that are on disk, a later save retries the rest
        List<String> saved = new ArrayList<>();
        long size = job.json.length;
        for (String key : media.keySet()) {
            if (offlineStore.hasMedia(key)) {
                saved.add(key);
                size += offlineStore.getMediaFile(key).length();
            }
        }
        writeManifest(job.threadKey, saved);

        Time.endTiming("Saved " + savedThread.loadable + " with " + total + " new files", start);

        job.fileCount = saved.size();
        job.missingCount = missing;
        job.size = size;
        runOnUiThread(() -> onSaveFinished(job, true));
    }

    private boolean download(OkHttpClient client, String url) {
        String key = OfflineStore.key(url);
        File file = offlineStore.getMediaFile(key);
        File temp = offlineStore.getTempFile(file);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Logger.w(TAG, "Could not download " + url + ": " + response.code());
                return false;
            }

            try (InputStream is = body.byteStream(); OutputStream os = new FileOutputStream(temp)) {
                IOUtils.copy(is, os);
            }

            if (!temp.renameTo(file)) {
                throw new IOException("Could not rename " + temp);
            }
            offlineStore.mediaAdded(key);
            return true;
        } catch (IOException e) {
            Logger.w(TAG, "Could not download " + url, e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    private void onSaveFinished(SaveJob job, boolean success) {
        SavedThread savedThread = job.savedThread;
        boolean manual = manualSaves.remove(savedThread.id);

        if (!savedThreads.contains(savedThread)) {
            // Deleted while saving, the files are removed by the delete
            notifications.cancel(savedThread);
            return;
        }

        if (success) {
            savedThread.savedDate = Time.get();
            savedThread.postCount = job.postCount;
            savedThread.fileCount = job.fileCount;
            savedThread.missingCount = job.missingCount;
            savedThread.size = job.size;
            databaseManager.runTaskAsync(databaseSavedThreadManager.updateSavedThread(savedThread));
        }

        if (manual) {
            if (success) {
                notifications.showDone(savedThread);
            } else {
                notifications.cancel(savedThread);
                Toast.makeText(applicationContext, R.string.saved_thread_failed, Toast.LENGTH_LONG).show();
            }
        }

        postChanged(savedThread);
    }

    // Runs on the save executor, after the saves that were queued before it
    private void removeUnusedFiles(Set<String> threadKeys) {
        Set<String> usedMedia = new HashSet<>();
        for (String threadKey : threadKeys) {
            usedMedia.addAll(readManifest(threadKey));
        }

        File[] threadFiles = offlineStore.getThreadsDirectory().listFiles();
        if (threadFiles != null) {
            for (File file : threadFiles) {
                String threadKey = offlineStore.threadKeyOf(file.getName());
                if (threadKey == null || !threadKeys.contains(threadKey)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }

        File[] mediaFiles = offlineStore.getMediaDirectory().listFiles();
        if (mediaFiles != null) {
            for (File file : mediaFiles) {
                String key = file.getName();
                if (!usedMedia.contains(key)) {
                    offlineStore.mediaRemoved(key);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
    }

    private List<String> readManifest(String threadKey) {
        List<String> keys = new ArrayList<>();
        File manifest = offlineStore.getManifestFile(threadKey);
        if (!manifest.exists()) {
            return keys;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    keys.add(line);
                }
            }
        } catch (IOException e) {
            Logger.e(TAG, "Could not read " + manifest, e);
        }
        return keys;
    }

    private void writeManifest(String threadKey, List<String> keys) {
        File manifest = offlineStore.getManifestFile(threadKey);
        File temp = offlineStore.getTempFile(manifest);
        try (Writer writer = new FileWriter(temp)) {
            for (String key : keys) {
                writer.write(key);
                writer.write('\n');
            }
        } catch (IOException e) {
            Logger.e(TAG, "Could not write " + manifest, e);
            return;
        }
        if (!temp.renameTo(manifest)) {
            Logger.e(TAG, "Could not rename " + temp);
        }
    }

    private void writeAtomically(File file, byte[] data) throws IOException {
        File temp = offlineStore.getTempFile(file);
        try (OutputStream os = new FileOutputStream(temp)) {
            os.write(data);
        }
        if (!temp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not rename " + temp);
        }
    }

    private void postChanged(SavedThread savedThread) {
        EventBus.getDefault().post(new SavedThreadChangedMessage(savedThread));
    }

    private static class SaveJob {
        SavedThread savedThread;
        String threadKey;
        byte[] json;
        int postCount;
        List<String> mediaUrls;

        int fileCount;
        int missingCount;
        long size;
    }

    public interface SavedCopyCallback {
        void onSavedCopy(@Nullable ChanLoaderResponse response);
    }

    public static class SavedThreadChangedMessage {
        public final SavedThread savedThread;

        public SavedThreadChangedMessage(SavedThread savedThread) {
            this.savedThread = savedThread;
        }
    }

    public static class SavedThreadRemovedMessage {
        public final SavedThread savedThread;

        public SavedThreadRemovedMessage(SavedThread savedThread) {
            this.savedThread = savedThread;
        }
    }
}
