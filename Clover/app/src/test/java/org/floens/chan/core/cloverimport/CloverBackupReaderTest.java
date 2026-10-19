package org.floens.chan.core.cloverimport;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CloverBackupReaderTest {
    private static final String DB = "apps/org.floens.chan/db/ChanDB";
    private static final String PREFS = "apps/org.floens.chan/sp/org.floens.chan_preferences.xml";
    private static final byte[] DATABASE = database();
    private static final byte[] PREFERENCES = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
            "<map>\n" +
            "    <string name=\"preference_theme\">dark</string>\n" +
            "    <string name=\"preference_reply_name\">Anon &amp; &lt;friends&gt;</string>\n" +
            "    <boolean name=\"preference_confirm_exit\" value=\"true\" />\n" +
            "    <int name=\"preference_font_size\" value=\"16\" />\n" +
            "    <long name=\"update_check_time\" value=\"1600000000000\" />\n" +
            "    <float name=\"preference_scale\" value=\"1.5\" />\n" +
            "    <set name=\"preference_set\">\n" +
            "        <string>a</string>\n" +
            "        <string>b</string>\n" +
            "    </set>\n" +
            "</map>\n").getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsCompressedBackup() throws IOException {
        File out = folder.newFile();
        byte[] backup = backup(true, "none",
                entry("apps/org.floens.chan/_manifest", "manifest".getBytes(StandardCharsets.UTF_8)),
                entry(DB, DATABASE),
                entry(PREFS, PREFERENCES),
                entry("apps/org.floens.chan/f/other", new byte[1500]));

        CloverBackupReader.Contents contents = read(backup, out);

        assertArrayEquals(DATABASE, Files.readAllBytes(out.toPath()));
        assertArrayEquals(PREFERENCES, contents.preferences);
    }

    @Test
    public void readsUncompressedBackup() throws IOException {
        File out = folder.newFile();
        CloverBackupReader.Contents contents = read(backup(false, "none", entry(DB, DATABASE)), out);

        assertArrayEquals(DATABASE, Files.readAllBytes(out.toPath()));
        assertNull(contents.preferences);
    }

    @Test
    public void readsPaxLongName() throws IOException {
        File out = folder.newFile();
        String record = "path=" + PREFS + "\n";
        String pax = (record.length() + 4) + " " + record;
        byte[] backup = backup(true, "none",
                entry(DB, DATABASE),
                header("PaxHeader", pax.length(), 'x'), pad(pax.getBytes(StandardCharsets.UTF_8)),
                entry("short-name-replaced-by-pax", PREFERENCES));

        assertArrayEquals(PREFERENCES, read(backup, out).preferences);
    }

    @Test
    public void rejectsEncryptedBackup() throws IOException {
        assertProblem(backup(true, "AES-256", entry(DB, DATABASE)), CloverBackupReader.Problem.ENCRYPTED);
    }

    @Test
    public void rejectsOtherFiles() throws IOException {
        assertProblem("PK\u0003\u0004 a zip file".getBytes(StandardCharsets.UTF_8),
                CloverBackupReader.Problem.NOT_A_BACKUP);
    }

    @Test
    public void rejectsBackupWithoutCloverDatabase() throws IOException {
        assertProblem(backup(true, "none", entry("apps/com.other.app/db/other", DATABASE)),
                CloverBackupReader.Problem.NO_CLOVER_DATA);
    }

    @Test
    public void rejectsDatabaseThatIsNotSqlite() throws IOException {
        assertProblem(backup(true, "none", entry(DB, new byte[100])),
                CloverBackupReader.Problem.NO_CLOVER_DATA);
    }

    @Test
    public void parsesPreferences() throws IOException {
        Map<String, Object> values = SharedPreferencesXml.parse(PREFERENCES);

        assertEquals("dark", values.get("preference_theme"));
        assertEquals("Anon & <friends>", values.get("preference_reply_name"));
        assertEquals(true, values.get("preference_confirm_exit"));
        assertEquals(16, values.get("preference_font_size"));
        assertEquals(1600000000000L, values.get("update_check_time"));
        assertEquals(1.5f, values.get("preference_scale"));
        assertEquals(new java.util.HashSet<>(Arrays.asList("a", "b")), values.get("preference_set"));
    }

    private CloverBackupReader.Contents read(byte[] backup, File out) throws IOException {
        return CloverBackupReader.read(new ByteArrayInputStream(backup), out);
    }

    private void assertProblem(byte[] backup, CloverBackupReader.Problem problem) throws IOException {
        try {
            read(backup, folder.newFile());
            fail("Expected " + problem);
        } catch (CloverBackupReader.BackupException e) {
            assertEquals(problem, e.problem);
        }
    }

    private static byte[] database() {
        byte[] db = new byte[4096];
        byte[] header = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, db, 0, header.length);
        for (int i = header.length; i < db.length; i++) {
            db[i] = (byte) i;
        }
        return db;
    }

    private static byte[] backup(boolean compressed, String encryption, byte[]... parts) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            tar.write(part);
        }
        tar.write(new byte[1024]);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("ANDROID BACKUP\n5\n" + (compressed ? 1 : 0) + "\n" + encryption + "\n")
                .getBytes(StandardCharsets.UTF_8));
        if (compressed) {
            try (OutputStream deflate = new DeflaterOutputStream(out)) {
                deflate.write(tar.toByteArray());
            }
        } else {
            out.write(tar.toByteArray());
        }
        return out.toByteArray();
    }

    private static byte[] entry(String name, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header(name, data.length, '0'));
        out.write(pad(data));
        return out.toByteArray();
    }

    private static byte[] header(String name, long size, char type) {
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        String prefix = "";
        if (nameBytes.length > 100) {
            int slash = name.lastIndexOf('/', 154);
            prefix = name.substring(0, slash);
            nameBytes = name.substring(slash + 1).getBytes(StandardCharsets.UTF_8);
        }
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        byte[] sizeBytes = String.format("%011o", size).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(sizeBytes, 0, header, 124, sizeBytes.length);
        header[156] = (byte) type;
        byte[] magic = "ustar\u000000".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(prefixBytes, 0, header, 345, prefixBytes.length);
        return header;
    }

    private static byte[] pad(byte[] data) {
        return Arrays.copyOf(data, (data.length + 511) / 512 * 512);
    }
}
