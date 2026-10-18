package app.morphe.extension.filemanager;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

/** WAV-only fallback, invoked after the original thumbnail sources failed. */
public final class AudioCoverThumbnails {
    private AudioCoverThumbnails() {}

    public static InputStream fallback(Context context, String source) {
        if (context == null || source == null) return null;
        String path = source;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (!path.toLowerCase(Locale.ROOT).endsWith(".wav")) return null;
        try {
            byte[] art = WavApic.extract(context, source);
            return art == null ? null : new ByteArrayInputStream(art);
        } catch (Exception | LinkageError | OutOfMemoryError ignored) {
            return null;
        }
    }
}
