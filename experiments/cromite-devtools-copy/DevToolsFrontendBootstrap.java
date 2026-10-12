package app.morphe.patches.cromite.devtools;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Experimental Java helper intended to be compiled into the target APK's DEX.
 * NOT a Morphe patch by itself. The assets must first be bundled under
 * assets/devtools-cromite-153/ and the startup call must be wired separately.
 */
public final class DevToolsFrontendBootstrap {
    private static final String ASSET_ROOT = "devtools-cromite-153";
    private static final String DIR = "devtools-cromite-153";
    private static final String ENTRY = "devtools_app.html";
    private static final String SWITCH = "custom-devtools-frontend";
    private static final Object LOCK = new Object();

    private DevToolsFrontendBootstrap() {}

    public static String prepare(Context context) throws IOException {
        synchronized (LOCK) {
            File parent = context.getFilesDir();
            File target = new File(parent, DIR);
            File marker = new File(target, ENTRY);
            // An existing entry point alone is not proof of complete assets.
            // Use a manifest with hashes in the production implementation.
            if (!marker.isFile()) {
                File staging = new File(parent, DIR + ".staging");
                deleteTree(staging);
                if (!staging.mkdirs()) throw new IOException("Cannot create staging directory");
                try {
                    copyTree(context.getAssets(), ASSET_ROOT, staging);
                    if (!new File(staging, ENTRY).isFile())
                        throw new IOException("Missing DevTools entry point");
                    deleteTree(target);
                    if (!staging.renameTo(target))
                        throw new IOException("Cannot commit DevTools frontend");
                } finally {
                    deleteTree(staging);
                }
            }
            return target.toURI().toString();
        }
    }

    private static void copyTree(AssetManager assets, String path, File dest) throws IOException {
        String[] names = assets.list(path);
        if (names == null || names.length == 0) {
            try (InputStream in = assets.open(path);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            return;
        }
        if (!dest.isDirectory() && !dest.mkdirs())
            throw new IOException("Cannot create directory");
        for (String name : names) {
            if (name.isEmpty() || name.equals(".") || name.equals("..")
                    || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
                throw new IOException("Unsafe asset name");
            copyTree(assets, path + "/" + name, new File(dest, name));
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot enumerate directory");
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file.getName());
    }
}
