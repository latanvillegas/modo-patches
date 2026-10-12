package app.morphe.cromite.devtools;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Standalone Android runtime payload intended for compilation into an extra DEX.
 * No dependency on Morphe or Kotlin runtime.
 *
 * Integration requirement: call initialize(Context) before Chromium's native
 * browser startup reads command-line switches. This class is not injected yet.
 */
public final class DevToolsBootstrap {
    private static final String HASH =
        "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840";
    private static final String ASSET = "devtools-cromite-153.zip";
    private static final String DIRECTORY = "devtools-cromite-153";
    private static final String SWITCH = "custom-devtools-frontend";
    private DevToolsBootstrap() {}

    public static synchronized void initialize(Context context) throws Exception {
        File target = extract(context.getApplicationContext());
        String url = target.toURI().toASCIIString();
        if (!url.endsWith("/")) url += "/";
        // Chromium 153 target API: org.chromium.base.CommandLine.d.a(String,String).
        // The direct call is deliberate: it must be resolved against the target APK.
        org.chromium.base.CommandLine.d.a(SWITCH, url);
    }

    private static File extract(Context context) throws Exception {
        File root = context.getFilesDir().getCanonicalFile();
        File target = new File(root, DIRECTORY);
        File marker = new File(target, ".archive-sha256");
        if (marker.isFile() && HASH.equals(readSmall(marker)) &&
                new File(target, "devtools_app.html").isFile() &&
                new File(target, "entrypoints/main/main.js").isFile()) return target;

        File archive = new File(root, "." + DIRECTORY + ".archive");
        File stage = new File(root, "." + DIRECTORY + ".stage");
        File backup = new File(root, "." + DIRECTORY + ".backup");
        try {
            try (InputStream in = context.getAssets().open(ASSET);
                 FileOutputStream out = new FileOutputStream(archive)) {
                copy(in, out, Long.MAX_VALUE);
            }
            if (!HASH.equals(sha256(archive))) throw new SecurityException("DevTools asset hash mismatch");
            if (stage.exists() && !deleteTree(stage)) throw new Exception("Cannot clear staging");
            if (!stage.mkdir()) throw new Exception("Cannot create staging");
            final String prefix = stage.getCanonicalPath() + File.separator;
            long total = 0;
            int entries = 0;
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 1024) throw new SecurityException("Too many entries");
                    String name = entry.getName();
                    if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0)
                        throw new SecurityException("Invalid ZIP path");
                    for (String part : name.split("/"))
                        if ("..".equals(part) || ".".equals(part))
                            throw new SecurityException("ZIP traversal");
                    File output = new File(stage, name);
                    if (!output.getCanonicalPath().startsWith(prefix))
                        throw new SecurityException("ZIP traversal");
                    if (entry.isDirectory()) {
                        if (!output.isDirectory() && !output.mkdirs()) throw new Exception("Cannot mkdir");
                    } else {
                        File parent = output.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("Cannot mkdir");
                        try (FileOutputStream out = new FileOutputStream(output)) {
                            byte[] buffer = new byte[65536];
                            long perFile = 0;
                            int n;
                            while ((n = zip.read(buffer)) != -1) {
                                perFile += n;
                                total += n;
                                if (perFile > 16L * 1024 * 1024 || total > 64L * 1024 * 1024)
                                    throw new SecurityException("ZIP too large");
                                out.write(buffer, 0, n);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (entries != 537 || !new File(stage, "devtools_app.html").isFile() ||
                    !new File(stage, "entrypoints/main/main.js").isFile())
                throw new SecurityException("Incomplete DevTools frontend");
            try (FileOutputStream out = new FileOutputStream(new File(stage, ".archive-sha256"))) {
                out.write(HASH.getBytes("US-ASCII"));
            }
            if (backup.exists() && !deleteTree(backup)) throw new Exception("Cannot clear backup");
            if (target.exists() && !target.renameTo(backup)) throw new Exception("Cannot back up frontend");
            if (!stage.renameTo(target)) {
                if (backup.exists() && !backup.renameTo(target))
                    throw new Exception("Activation failed; restore failed");
                throw new Exception("Activation failed");
            }
            if (backup.exists()) deleteTree(backup);
            return target;
        } finally {
            archive.delete();
            if (stage.exists()) deleteTree(stage);
        }
    }

    private static long copy(InputStream in, FileOutputStream out, long max) throws Exception {
        byte[] buffer = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > max) throw new SecurityException("Size exceeded");
            out.write(buffer, 0, n);
        }
        return total;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }

    private static String readSmall(File file) throws Exception {
        if (file.length() > 128) return "";
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(data) != data.length) return "";
        }
        return new String(data, "US-ASCII");
    }

    private static boolean deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) if (!deleteTree(child)) return false;
        }
        return file.delete();
    }
}
