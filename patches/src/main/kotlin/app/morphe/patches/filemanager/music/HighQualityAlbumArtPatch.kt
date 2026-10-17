package app.morphe.patches.filemanager.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Independent File Manager Plus 3.8.2 patch.
 * Leaves the existing thumbnail pipeline and all other patches untouched.
 */
@Suppress("unused")
val fileManagerHighQualityAlbumArtPatch = bytecodePatch(
    name = "File Manager Plus - High Quality Album Art",
    description = "Uses full-resolution embedded artwork and 32-bit color in the music player.",
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
        // Avoid using a low-resolution bitmap from MediaDescriptionCompat.
        // A cache miss must trigger the original embedded-art decoder.
        val earlyExitIndex = cacheIndex - 2
        val earlyExit = player.implementation?.instructions?.elementAtOrNull(earlyExitIndex)
        if (earlyExit?.opcode != Opcode.IF_NEZ ||
            (earlyExit as? OneRegisterInstruction)?.registerA != 0
        ) {
            throw PatchException("Album art: unexpected metadata shortcut in h3")
        }
        player.replaceInstruction(earlyExitIndex, "const/4 v0, 0x0")
        player.replaceInstruction(
            cacheIndex,
            "invoke-virtual {v2, v0}, Lax/V4/a;->g(Ljava/lang/String;)Landroid/graphics/Bitmap;",
        )

        // Use 32-bit bitmap color instead of RGB_565 for the embedded cover.
        // The existing sample-size calculation and thumbnails remain in place.
        val decoder = Fingerprint(
            definingClass = "Lax/V4/a\$b;",
            name = "r",
            returnType = "[Landroid/graphics/Bitmap;",
            parameters = listOf("[Ljava/lang/Void;"),
        ).method
        val configIndex = uniqueReferenceIndex(
            decoder,
            "Landroid/graphics/Bitmap\$Config;->RGB_565:Landroid/graphics/Bitmap\$Config;",
        )
        decoder.replaceInstruction(
            configIndex,
            "sget-object v7, Landroid/graphics/Bitmap\$Config;->ARGB_8888:Landroid/graphics/Bitmap\$Config;",
        )
    }
}
