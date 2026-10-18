package app.morphe.patches.filemanager.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Independent File Manager Plus 3.8.2 patch.
 * Leaves the existing thumbnail pipeline and all other patches untouched.
 */
@Suppress("unused")
val fileManagerHighQualityAlbumArtPatch = bytecodePatch(
    name = "File Manager Plus - High Quality Album Art",
    description = "Uses full-resolution album artwork in playback and cache, with ARGB_8888 decoding; WAV/APIC fallback is not yet enabled.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        fun uniqueReferenceIndex(
            method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
            reference: String,
        ): Int {
            val matches = method.implementation?.instructions?.withIndex()?.filter {
                (it.value as? ReferenceInstruction)?.reference?.toString() == reference
            }?.toList() ?: throw PatchException("Album art: missing method implementation")
            if (matches.size != 1) {
                throw PatchException("Album art: expected one reference to $reference, found ${matches.size}")
            }
            return matches.single().index
        }

        // The async callback's p2 contains the full decoded bitmap, but the
        // original callback overwrites p2 with a string. Preserve it in p4,
        // which was only used to check whether a thumbnail exists.
        val callback = Fingerprint(
            definingClass = "Lcom/alphainventor/filemanager/musicplayer/PlaybackControlsFragment\$c;",
            name = "c",
            returnType = "V",
            parameters = listOf(
                "Ljava/lang/String;",
                "Landroid/graphics/Bitmap;",
                "Landroid/graphics/Bitmap;",
                "Landroid/graphics/Bitmap;",
            ),
        ).method
        val callbackIndex = uniqueReferenceIndex(
            callback,
            "Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V",
        )
        callback.replaceInstruction(
            callbackIndex,
            "invoke-virtual {p1, p4}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V",
        )
        callback.addInstruction(0, "move-object p4, p2")

        // Cache hits must use the original decoded image, not the 384px copy.
        val player = Fingerprint(
            definingClass = "Lcom/alphainventor/filemanager/musicplayer/PlaybackControlsFragment;",
            name = "h3",
            returnType = "V",
            parameters = listOf("Landroid/support/v4/media/MediaMetadataCompat;"),
        ).method
        val cacheIndex = uniqueReferenceIndex(
            player,
            "Lax/V4/a;->j(Ljava/lang/String;)Landroid/graphics/Bitmap;",
        )
        // Preserve the original conditional branch and its DEX target.
        // Replacing a branch with a differently sized instruction can
        // invalidate target addresses and cause VerifyError on Android 15.
        player.replaceInstruction(
            cacheIndex,
            "invoke-virtual {v2, v0}, Lax/V4/a;->g(Ljava/lang/String;)Landroid/graphics/Bitmap;",
        )


        // Use 32-bit bitmap color instead of RGB_565 for the embedded cover.
        // Preserve the existing sample-size calculation and thumbnails.
        val decoder = Fingerprint(
            definingClass = "Lax/V4/a\$b;",
            name = "r",
            returnType = "[Landroid/graphics/Bitmap;",
            parameters = listOf("[Ljava/lang/Void;"),
        ).method
        // Safety rollback: the experimental WAV fallback is disabled until
        // its bytecode is verifier-safe and exceptions are handled. The
        // original Android extractor remains active.

        val configIndex = uniqueReferenceIndex(
            decoder,
            "Landroid/graphics/Bitmap\$Config;->RGB_565:Landroid/graphics/Bitmap\$Config;",
        )
        decoder.replaceInstruction(
            configIndex,
            "sget-object v7, Landroid/graphics/Bitmap\$Config;->ARGB_8888:Landroid/graphics/Bitmap\$Config;",
        )

        // Preserve the original invoke / move-result pair and its code size.
        // The 8 MiB budget override is suspended pending a verifier-safe
        // implementation that does not invalidate DEX branch targets.
    }
}
