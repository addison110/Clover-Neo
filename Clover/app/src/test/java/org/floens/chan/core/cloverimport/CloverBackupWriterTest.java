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
package org.floens.chan.core.cloverimport;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CloverBackupWriterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void exportedBackupImportsAgain() throws IOException {
        // Not a multiple of the tar block size, to check the padding
        byte[] database = database(5000);
        File databaseFile = folder.newFile();
        Files.write(databaseFile.toPath(), database);

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("preference_theme", "dark");
        preferences.put("preference_reply_name", "Anon & <friends> \"quoted\"");
        preferences.put("preference_confirm_exit", true);
        preferences.put("preference_font_size", 16);
        preferences.put("preference_long", 1600000000000L);
        preferences.put("preference_scale", 1.5f);
        preferences.put("preference_set", new HashSet<>(Arrays.asList("a", "b & c")));

        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        CloverBackupWriter.write(backup, databaseFile, SharedPreferencesXml.write(preferences));

        File imported = folder.newFile();
        CloverBackupReader.Contents contents =
                CloverBackupReader.read(new ByteArrayInputStream(backup.toByteArray()), imported);

        assertArrayEquals(database, Files.readAllBytes(imported.toPath()));
        assertEquals(preferences, SharedPreferencesXml.parse(contents.preferences));
    }

    @Test
    public void exportsWithoutPreferences() throws IOException {
        byte[] database = database(512);
        File databaseFile = folder.newFile();
        Files.write(databaseFile.toPath(), database);

        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        CloverBackupWriter.write(backup, databaseFile, null);

        File imported = folder.newFile();
        CloverBackupReader.Contents contents =
                CloverBackupReader.read(new ByteArrayInputStream(backup.toByteArray()), imported);

        assertArrayEquals(database, Files.readAllBytes(imported.toPath()));
        assertNull(contents.preferences);
    }

    private static byte[] database(int size) {
        byte[] db = new byte[size];
        byte[] header = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, db, 0, header.length);
        for (int i = header.length; i < db.length; i++) {
            db[i] = (byte) i;
        }
        return db;
    }
}
