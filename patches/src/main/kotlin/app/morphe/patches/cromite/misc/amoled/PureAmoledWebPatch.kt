package app.morphe.patches.cromite.misc.amoled

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

private const val LIBRARY = "lib/arm64-v8a/libchrome.so"
private const val ORIGINAL_SHA256 = "1659b2cd37a98d618fbefcc22427d79aa9711b3e04199cdf4d1cc0f5f33cf8e8"
private const val PATCHED_SHA256 = "9e85fdef2bcb17a94c1acd862609a4fc0e85d8dc071e35f5bf6423b7ad382dc7"
private const val EXPECTED_LENGTH = 224856648L
private const val PAYLOAD_OFFSET = 0xA4F4D04L
private const val HOOK_OFFSET = 0xBB8EB04L

private val payload = (
    "5f2403d5ff8300d1fe5701a9e007002de20f012dc1645a94bf0a0071210100540440201e2540201e4640201e6740201ee007402de20f412de003152a04000094fe5741a9ff8300916f675a145f2403d51f08007121040054490500100020201ec6030054" +
    "2020211e860300544020221e460300540020211ea10000542020221e610000541240201e100000141058211e105a221e1148211e314a221e323a301e330140bd4022331e8601005468010054330540bd1208331e330940bd3248131f330d40bd5248131f" +
    "331140bd4022331e46000054ca0000548040201ea140201ec240201ee340201ec0035fd6e003271ee103271ee203271ee340201ec0035fd68fc2f53cd0b3593e5917373f98dd933d6666663f"
).hexBytes()

private val hookBefore = "46fdff97".hexBytes()
private val hookAfter = "8098a517".hexBytes()

private fun String.hexBytes(): ByteArray {
    require(length % 2 == 0) { "Odd hex length" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { stream ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * ARM64 native Blink AMOLED hook for Cromite 153.0.8010.37 only.
 * The input ELF is checked in full before any mutation.
 */
@Suppress("unused")
val pureAmoledWebPatch = resourcePatch(
    name = "Pure AMOLED Web",
    description = "Makes eligible inverted web backgrounds pure black using a version-locked native ARM64 Blink hook.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    execute {
        if (LIBRARY !in listApkEntries("lib/")) {
            throw PatchException("Pure AMOLED Web requires the ARM64 libchrome.so")
        }
        val library = get(LIBRARY)
        if (!library.isFile || library.length() != EXPECTED_LENGTH) {
            throw PatchException("Unsupported Cromite ARM64 libchrome.so size")
        }
        if (library.sha256() != ORIGINAL_SHA256) {
            throw PatchException("Unsupported or already modified libchrome.so; refusing native AMOLED hook")
        }
        if (payload.size != 276) {
            throw PatchException("Unexpected native AMOLED payload size")
        }
        RandomAccessFile(library, "rw").use { file ->
            file.seek(PAYLOAD_OFFSET)
            val originalPayload = ByteArray(payload.size)
            file.readFully(originalPayload)
            if (originalPayload.any { it != 0.toByte() }) {
                throw PatchException("Native AMOLED code cave is not empty")
            }
            file.seek(HOOK_OFFSET)
            val originalHook = ByteArray(hookBefore.size)
            file.readFully(originalHook)
            if (!originalHook.contentEquals(hookBefore)) {
                throw PatchException("Native AMOLED hook does not match expected instructions")
            }
            file.seek(PAYLOAD_OFFSET)
            file.write(payload)
            file.seek(HOOK_OFFSET)
            file.write(hookAfter)
        }
        if (library.sha256() != PATCHED_SHA256) {
            throw PatchException("Native AMOLED output hash mismatch")
        }
    }
}
