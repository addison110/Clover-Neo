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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

/**
 * Reads an "adb backup" of the original Clover app (org.floens.chan) and extracts its database
 * and preferences.
 * <p>The backup is a short text header followed by a tar archive, usually zlib compressed.
 */
public class CloverBackupReader {
    public static final String CLOVER_PACKAGE = "org.floens.chan";

    private static final String MAGIC = "ANDROID BACKUP";
    static final String DATABASE_PATH = "apps/" + CLOVER_PACKAGE + "/db/ChanDB";
    static final String PREFERENCES_PATH =
            "apps/" + CLOVER_PACKAGE + "/sp/" + CLOVER_PACKAGE + "_preferences.xml";
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final int BLOCK = 512;

    public enum Problem {
        NOT_A_BACKUP,
        ENCRYPTED,
        NO_CLOVER_DATA
    }

    public static class BackupException extends IOException {
        public final Problem problem;

        public BackupException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }
    }

    public static class Contents {
        /**
         * The preferences XML, null when the backup has none.
         */
        public byte[] preferences;
    }

    /**
     * Reads the backup, writes the Clover database to {@code databaseOut} and returns the
     * preferences. Throws a {@link BackupException} when the backup can't be imported.
     */
    public static Contents read(InputStream input, File databaseOut) throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);

        if (!MAGIC.equals(readLine(in))) {
            throw new BackupException(Problem.NOT_A_BACKUP);
        }
        readLine(in); // format version
        boolean compressed = "1".equals(readLine(in));
        String encryption = readLine(in);
        if (!"none".equals(encryption)) {
            throw new BackupException(Problem.ENCRYPTED);
        }

        InputStream tar = compressed ? new InflaterInputStream(in) : in;
        Contents contents = new Contents();
        boolean database = false;

        byte[] header = new byte[BLOCK];
        String longName = null;
        while (readBlock(tar, header)) {
            if (isZeroBlock(header)) {
                break;
            }

            String name = longName != null ? longName : entryName(header);
            longName = null;
            long size = entrySize(header);
            char type = (char) header[156];

            if (type == 'x' || type == 'L') {
                // PAX header or GNU long name, holds the name of the next entry
                String data = new String(readData(tar, size), StandardCharsets.UTF_8);
                longName = type == 'L' ? data.replace("\0", "") : paxPath(data);
            } else if (DATABASE_PATH.equals(name)) {
                copyData(tar, size, databaseOut);
                database = true;
            } else if (PREFERENCES_PATH.equals(name)) {
                contents.preferences = readData(tar, size);
            } else {
                skipData(tar, size);
            }
        }

        if (!database || !isSqliteDatabase(databaseOut)) {
            throw new BackupException(Problem.NO_CLOVER_DATA);
        }

        return contents;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != '\n') {
            if (c < 0 || line.size() > 1024) {
                throw new BackupException(Problem.NOT_A_BACKUP);
            }
            line.write(c);
        }
        return line.toString("UTF-8");
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int read = 0;
        while (read < block.length) {
            int n = in.read(block, read, block.length - read);
            if (n < 0) {
                if (read == 0) {
                    return false;
                }
                throw new EOFException("Truncated tar header");
            }
            read += n;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String entryName(byte[] header) {
        String name = cString(header, 0, 100);
        String prefix = cString(header, 345, 155);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static long entrySize(byte[] header) throws IOException {
        if ((header[124] & 0x80) != 0) {
            // Base-256 encoding for large sizes
            long size = 0;
            for (int i = 125; i < 136; i++) {
                size = (size << 8) | (header[i] & 0xff);
            }
            return size;
        }
        String octal = cString(header, 124, 12).trim();
        try {
            return octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
        } catch (NumberFormatException e) {
            throw new BackupException(Problem.NOT_A_BACKUP);
        }
    }

    private static String cString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String paxPath(String records) {
        // Records are "<length> <key>=<value>\n"
        for (String record : records.split("\n")) {
            int space = record.indexOf(' ');
            if (space >= 0 && record.startsWith("path=", space + 1)) {
                return record.substring(space + 1 + "path=".length());
            }
        }
        return null;
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > 64 * 1024 * 1024) {
            throw new BackupException(Problem.NOT_A_BACKUP);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
        transfer(in, size, out);
        return out.toByteArray();
    }

    private static void copyData(InputStream in, long size, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            transfer(in, size, out);
        }
    }

    private static void skipData(InputStream in, long size) throws IOException {
        transfer(in, size, null);
    }

    /**
     * Copies {@code size} bytes to {@code out} (or drops them when null), then skips the padding
     * up to the next 512 byte block.
     */
    private static void transfer(InputStream in, long size, OutputStream out) throws IOException {
        long padded = (size + BLOCK - 1) / BLOCK * BLOCK;
        byte[] buffer = new byte[8192];
        long done = 0;
        while (done < padded) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, padded - done));
            if (n < 0) {
                throw new EOFException("Truncated tar entry");
            }
            if (out != null && done < size) {
                out.write(buffer, 0, (int) Math.min(n, size - done));
            }
            done += n;
        }
    }

    private static boolean isSqliteDatabase(File file) throws IOException {
        byte[] header = new byte[SQLITE_HEADER.length];
        try (InputStream in = new FileInputStream(file)) {
            return in.read(header) == header.length && Arrays.equals(header, SQLITE_HEADER);
        }
    }
}
