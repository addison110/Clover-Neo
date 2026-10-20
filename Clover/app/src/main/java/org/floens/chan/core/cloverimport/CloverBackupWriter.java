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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes the database and preferences as an "adb backup" in the layout of the original Clover
 * app, so {@link CloverBackupReader} can import it again.
 */
public class CloverBackupWriter {
    private static final String HEADER = "ANDROID BACKUP\n5\n1\nnone\n";
    private static final int BLOCK = 512;

    public static void write(OutputStream out, File database, byte[] preferences) throws IOException {
        out.write(HEADER.getBytes(StandardCharsets.US_ASCII));

        // Finishing the deflater leaves the output open for the caller to close
        DeflaterOutputStream tar = new DeflaterOutputStream(out);
        long now = System.currentTimeMillis() / 1000;
        try (InputStream in = new FileInputStream(database)) {
            writeEntry(tar, CloverBackupReader.DATABASE_PATH, database.length(), now, in);
        }
        if (preferences != null) {
            writeEntry(tar, CloverBackupReader.PREFERENCES_PATH, preferences.length, now,
                    new ByteArrayInputStream(preferences));
        }
        // The end of a tar archive is two empty blocks
        tar.write(new byte[BLOCK * 2]);
        tar.finish();
        out.flush();
    }

    private static void writeEntry(OutputStream tar, String name, long size, long time,
                                   InputStream data) throws IOException {
        tar.write(header(name, size, time));

        byte[] buffer = new byte[8192];
        long written = 0;
        int n;
        while (written < size && (n = data.read(buffer, 0, (int) Math.min(buffer.length, size - written))) > 0) {
            tar.write(buffer, 0, n);
            written += n;
        }
        if (written != size) {
            throw new IOException("The file changed while it was exported");
        }

        int padding = (int) ((BLOCK - size % BLOCK) % BLOCK);
        tar.write(new byte[padding]);
    }

    private static byte[] header(String name, long size, long time) {
        byte[] header = new byte[BLOCK];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 100) {
            throw new IllegalArgumentException("Name too long for a tar header: " + name);
        }
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        octal(header, 100, 8, 0600); // mode
        octal(header, 108, 8, 0); // uid
        octal(header, 116, 8, 0); // gid
        octal(header, 124, 12, size);
        octal(header, 136, 12, time);
        header[156] = '0';
        System.arraycopy("ustar\u000000".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 8);

        // The checksum is calculated with its own field filled with spaces
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        byte[] digits = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(digits, 0, header, 148, digits.length);
        header[154] = 0;
        return header;
    }

    private static void octal(byte[] header, int offset, int length, long value) {
        // Zero padded digits followed by a NUL
        byte[] digits = String.format("%0" + (length - 1) + "o", value).getBytes(StandardCharsets.US_ASCII);
        if (digits.length > length - 1) {
            throw new IllegalArgumentException("Value too large for a tar header: " + value);
        }
        System.arraycopy(digits, 0, header, offset, digits.length);
    }
}
