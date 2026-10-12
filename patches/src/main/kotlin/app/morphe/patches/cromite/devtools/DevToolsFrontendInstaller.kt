package app.morphe.patches.cromite.devtools

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Experimental runtime helper; not connected to a Morphe patch yet. */
internal object DevToolsFrontendInstaller {
    const val VERIFIED_ARCHIVE_SHA256 = "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840"
    private const val ARCHIVE = "devtools-cromite-153.zip"
    private const val DIRECTORY = "devtools-cromite-153"

    fun install(context: Context): File = install(context, VERIFIED_ARCHIVE_SHA256)

    fun frontendUrl(context: Context): String = install(context).toURI().toASCIIString().let {
        if (it.endsWith("/")) it else "$it/"
    }

    fun install(context: Context, expectedSha256: String): File {
        require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val root = context.filesDir.canonicalFile
        val target = File(root, DIRECTORY)
        val marker = File(target, ".archive-sha256")
        if (marker.isFile && marker.readText().trim() == expectedSha256 &&
            File(target, "devtools_app.html").isFile &&
            File(target, "entrypoints/main/main.js").isFile) return target

        val archive = File(root, ".$DIRECTORY.archive")
        val stage = File(root, ".$DIRECTORY.stage")
        try {
            context.assets.open(ARCHIVE).use { source ->
                FileOutputStream(archive).use { source.copyTo(it) }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { source ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expectedSha256)
            if (stage.exists()) check(stage.deleteRecursively())
            check(stage.mkdir())
            val prefix = stage.canonicalPath + File.separator
            var entries = 0
            var total = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    check(entries <= 1024)
                    val name = entry.name
                    check(name.isNotBlank() && !name.startsWith("/") &&
                        !name.contains('\\') && name.split('/').none { it == "." || it == ".." })
                    val output = File(stage, name)
                    check(output.canonicalPath.startsWith(prefix))
                    if (entry.isDirectory) {
                        check(output.isDirectory || output.mkdirs())
                    } else {
                        check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
                        var length = 0L
                        FileOutputStream(output).use { sink ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                length += count
                                total += count
                                check(length <= 16L * 1024 * 1024 && total <= 64L * 1024 * 1024)
                                sink.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            check(File(stage, "devtools_app.html").isFile)
            check(File(stage, "entrypoints/main/main.js").isFile)
            File(stage, ".archive-sha256").writeText(expectedSha256)
            val backup = File(root, ".$DIRECTORY.backup")
            if (backup.exists()) check(backup.deleteRecursively())
            if (target.exists()) check(target.renameTo(backup))
            if (!stage.renameTo(target)) {
                if (backup.exists()) check(backup.renameTo(target))
                error("Unable to activate DevTools frontend")
            }
            if (backup.exists()) backup.deleteRecursively()
            return target
        } finally {
            archive.delete()
            if (stage.exists()) stage.deleteRecursively()
        }
    }
}
