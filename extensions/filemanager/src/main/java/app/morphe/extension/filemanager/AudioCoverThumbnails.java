package app.morphe.extension.filemanager;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Optional WAV-only thumbnail fallback. Never replaces an existing thumbnail.
 * Called from the original audio-cover provider, not from a recycled list view.
 */
public final class AudioCoverThumbnails {
    private AudioCoverThumbnails() {}

    public static InputStream fallback(InputStream original, Object provider, String source) {
        if (original != null || provider == null || source == null) return original;
        String path = source;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (!path.toLowerCase(Locale.ROOT).endsWith(".wav")) return original;
        try {
            // The local file provider exposes its application Context through p().
            // Resolve it through the hierarchy because it may be inherited/protected.
            Class<?> type = provider.getClass();
            Method method = null;
            while (type != null) {
                try {
                    method = type.getDeclaredMethod("p");
                    break;
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (method == null) return original;
            method.setAccessible(true);
            Object value = method.invoke(provider);
            if (!(value instanceof Context)) return original;
            byte[] art = WavApic.extract((Context) value, source);
            return art == null ? original : new ByteArrayInputStream(art);
        } catch (Exception | LinkageError | OutOfMemoryError ignored) {
            return original;
        }
    }
}
