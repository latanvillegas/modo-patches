package app.morphe.extension.filemanager;

import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Arrays;

/** Target-side, bounded WAV/ID3v2.3/APIC artwork reader. Never throws to player. */
public final class WavApic {
    private static final int MAX_TAG = 16 * 1024 * 1024;
    private static final int MAX_IMAGE = 10 * 1024 * 1024;
    private WavApic() {}


    /**
     * Preserves Android's original embedded artwork extraction.
     * Only falls back to WAV/APIC when Android returns no image.
     */
    public static byte[] getEmbeddedPicture(
            android.media.MediaMetadataRetriever retriever,
            Object albumTask) {

        byte[] original = retriever.getEmbeddedPicture();
        if (original != null) return original;

        try {
            Class<?> cls = albumTask.getClass();

            java.lang.reflect.Field contextField =
                    cls.getDeclaredField("q0");
            java.lang.reflect.Field sourceField =
                    cls.getDeclaredField("r0");

            contextField.setAccessible(true);
            sourceField.setAccessible(true);

            Context context = (Context) contextField.get(albumTask);
            String source = (String) sourceField.get(albumTask);

            return extract(context, source);
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    public static byte[] extract(Context context, String source) {
        if (context == null || source == null) return null;
        try {
            Uri uri = Uri.parse(source);
            String scheme = uri.getScheme();
            InputStream stream;
            if ("content".equalsIgnoreCase(scheme)) {
                stream = context.getContentResolver().openInputStream(uri);
            } else if ("file".equalsIgnoreCase(scheme)) {
                stream = new FileInputStream(uri.getPath());
            } else if (scheme == null || scheme.isEmpty()) {
                stream = new FileInputStream(source);
            } else return null;
            if (stream == null) return null;
            try (InputStream in = stream) { return readWave(in); }
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }

    private static byte[] readWave(InputStream in) throws Exception {
        byte[] header = new byte[12];
        if (!read(in, header) || !ascii(header, 0, "RIFF") || !ascii(header, 8, "WAVE")) return null;
        long riffRemaining = le32(header, 4) - 4;
        if (riffRemaining < 0) return null;
        byte[] chunk = new byte[8];
        while (riffRemaining >= 8 && read(in, chunk)) {
            riffRemaining -= 8;
            long size = le32(chunk, 4);
            long padded = size + (size & 1L);
            if (size > Integer.MAX_VALUE || padded > riffRemaining) return null;
            boolean id3 = ascii(chunk, 0, "id3 ") || ascii(chunk, 0, "ID3 ");
            if (id3 && size >= 10 && size <= MAX_TAG) {
                byte[] tag = new byte[(int) size];
                if (!read(in, tag)) return null;
                byte[] image = parseTag(tag);
                if (image != null) return image;
            } else if (!skip(in, size)) return null;
            if ((size & 1) != 0 && !skip(in, 1)) return null;
            riffRemaining -= padded;
        }
        return null;
    }

    private static byte[] parseTag(byte[] b) {
        if (b.length < 10 || !ascii(b, 0, "ID3") || (b[3] & 255) != 3) return null;
        int flags = b[5] & 255;
        if ((flags & 0x80) != 0) return null;
        long length = syncsafe(b, 6);
        if (length < 0 || length > b.length - 10) return null;
        int end = 10 + (int) length, p = 10;
        if ((flags & 0x40) != 0) {
            if (p + 4 > end) return null;
            long extra = be32(b, p);
            if (extra > end - p - 4) return null;
            p += 4 + (int) extra;
        }
        while (p + 10 <= end && b[p] != 0) {
            long size = be32(b, p + 4);
            if (size == 0 || size > end - p - 10) break;
            if (ascii(b, p, "APIC") && b[p + 8] == 0 && b[p + 9] == 0) {
                byte[] result = parseApic(b, p + 10, (int) size);
                if (result != null) return result;
            }
            p += 10 + (int) size;
        }
        return null;
    }

    private static byte[] parseApic(byte[] b, int p, int size) {
        int end = p + size;
        if (p >= end) return null;
        int encoding = b[p++] & 255;
        while (p < end && b[p] != 0) p++;
        if (++p >= end) return null; // MIME separator and picture type
        p++; // picture type
        if (encoding == 0 || encoding == 3) {
            while (p < end && b[p] != 0) p++;
            p++;
        } else if (encoding == 1 || encoding == 2) {
            while (p + 1 < end && (b[p] != 0 || b[p + 1] != 0)) p += 2;
            p += 2;
        } else return null;
        int length = end - p;
        if (length <= 0 || length > MAX_IMAGE) return null;
        boolean jpeg = length >= 3 && (b[p] & 255) == 255 && (b[p+1] & 255) == 216 && (b[p+2] & 255) == 255;
        boolean png = length >= 8 && (b[p] & 255) == 137 && b[p+1] == 80 && b[p+2] == 78 && b[p+3] == 71 &&
                b[p+4] == 13 && b[p+5] == 10 && b[p+6] == 26 && b[p+7] == 10;
        return jpeg || png ? Arrays.copyOfRange(b, p, end) : null;
    }

    private static boolean ascii(byte[] b, int offset, String s) {
        if (offset < 0 || offset + s.length() > b.length) return false;
        for (int i = 0; i < s.length(); i++) if ((b[offset + i] & 255) != s.charAt(i)) return false;
        return true;
    }
    private static long le32(byte[] b, int p) {
        return (b[p] & 255L) | ((b[p+1] & 255L) << 8) | ((b[p+2] & 255L) << 16) | ((b[p+3] & 255L) << 24);
    }
    private static long be32(byte[] b, int p) {
        return ((b[p] & 255L) << 24) | ((b[p+1] & 255L) << 16) | ((b[p+2] & 255L) << 8) | (b[p+3] & 255L);
    }
    private static long syncsafe(byte[] b, int p) {
        for (int i=0; i<4; i++) if ((b[p+i] & 128) != 0) return -1;
        return ((b[p] & 127L) << 21) | ((b[p+1] & 127L) << 14) | ((b[p+2] & 127L) << 7) | (b[p+3] & 127L);
    }
    private static boolean read(InputStream in, byte[] data) throws Exception {
        int p = 0;
        while (p < data.length) {
            int n = in.read(data, p, data.length - p);
            if (n < 0) return false;
            if (n == 0) { int one = in.read(); if (one < 0) return false; data[p++] = (byte) one; continue; }
            p += n;
        }
        return true;
    }
    private static boolean skip(InputStream in, long bytes) throws Exception {
        byte[] buffer = new byte[8192];
        while (bytes > 0) {
            int n = in.read(buffer, 0, (int)Math.min(bytes, buffer.length));
            if (n < 0) return false;
            if (n == 0) { if (in.read() < 0) return false; bytes--; }
            else bytes -= n;
        }
        return true;
    }

    /**
     * Calculates album-art sampling with an 8 MiB bitmap budget.
     * Does not change the original thumbnail cache.
     */
    public static int calculateSampleSize(
            int width,
            int height,
            android.graphics.Bitmap.Config config,
            int originalBudget) {

        if (width <= 0 || height <= 0) {
            return 1;
        }

        final long budget = 8L * 1024L * 1024L;
        final int bytesPerPixel =
                config == android.graphics.Bitmap.Config.RGB_565 ? 2 : 4;

        long bytes = (long) width * height * bytesPerPixel;
        int sampleSize = 1;

        while (bytes > budget && sampleSize < (1 << 29)) {
            bytes /= 4;
            sampleSize *= 2;
        }

        return sampleSize;
    }

}
