package app.morphe.patches.filemanager.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
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
    description = "Prioritizes high-quality embedded artwork with 32-bit color and an 8 MiB decode budget.",
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
        // Preserve the existing sample-size calculation and thumbnails.
        val decoder = Fingerprint(
            definingClass = "Lax/V4/a\$b;",
            name = "r",
            returnType = "[Landroid/graphics/Bitmap;",
            parameters = listOf("[Ljava/lang/Void;"),
        ).method
        // Install the fallback in the existing target class. A Kotlin helper
        // compiled into the patcher itself would NOT be present in the APK.
        val targetClass = mutableClassDefBy("Lax/V4/a\$b;")
        if (targetClass.methods.any { it.name == "morpheWavApic" }) {
            throw PatchException("Album art: WAV fallback already installed")
        }
        targetClass.methods.add(
            ImmutableMethod(
                targetClass.type,
                "morpheWavApic",
                emptyList(),
                "[B",
                AccessFlags.PRIVATE.value,
                null,
                null,
                MutableMethodImplementation(14),
            ).toMutable().apply {
                addInstructions(0, """
                    # Only local files are handled by this first fallback.
                    # Other URI schemes continue through the original pipeline.
                    iget-object v1, p0, Lax/V4/a\\$b;->r0:Ljava/lang/String;
                    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                    move-result-object v4
                    invoke-virtual {v4}, Landroid/net/Uri;->getScheme()Ljava/lang/String;
                    move-result-object v5
                    if-eqz v5, :morphe_open
                    const-string v6, "file"
                    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v6
                    if-eqz v6, :morphe_null
                    invoke-virtual {v4}, Landroid/net/Uri;->getPath()Ljava/lang/String;
                    move-result-object v1
                    :morphe_open
                    new-instance v0, Ljava/io/RandomAccessFile;
                    const-string v4, "r"
                    invoke-direct {v0, v1, v4}, Ljava/io/RandomAccessFile;-><init>(Ljava/lang/String;Ljava/lang/String;)V
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->length()J
                    move-result-wide v10
                    const-wide/16 v2, 0xc
                    cmp-long v4, v10, v2
                    if-ltz v4, :morphe_close_null
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->readInt()I
                    move-result v4
                    const v5, 0x52494646
                    if-ne v4, v5, :morphe_close_null
                    const-wide/16 v2, 0x8
                    invoke-virtual {v0, v2, v3}, Ljava/io/RandomAccessFile;->seek(J)V
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->readInt()I
                    move-result v4
                    const v5, 0x57415645
                    if-ne v4, v5, :morphe_close_null
                    const-wide/16 v2, 0xc
                    :morphe_chunk_loop
                    const-wide/16 v6, 0x8
                    add-long v8, v2, v6
                    cmp-long v4, v8, v10
                    if-gtz v4, :morphe_close_null
                    invoke-virtual {v0, v2, v3}, Ljava/io/RandomAccessFile;->seek(J)V
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->readInt()I
                    move-result v5
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->readInt()I
                    move-result v4
                    invoke-static {v4}, Ljava/lang/Integer;->reverseBytes(I)I
                    move-result v4
                    invoke-static {v4}, Ljava/lang/Integer;->toUnsignedLong(I)J
                    move-result-wide v6
                    add-long v2, v8, v6
                    cmp-long v4, v2, v10
                    if-gtz v4, :morphe_close_null
                    const v4, 0x69643320
                    if-eq v5, v4, :morphe_read_id3
                    const v4, 0x49443320
                    if-eq v5, v4, :morphe_read_id3
                    const-wide/16 v4, 0x1
                    and-long/2addr v4, v6
                    add-long/2addr v2, v4
                    goto :morphe_chunk_loop
                    :morphe_read_id3
                    const-wide/16 v4, 0xa
                    cmp-long v4, v6, v4
                    if-ltz v4, :morphe_close_null
                    const-wide v4, 0x1000000
                    cmp-long v4, v6, v4
                    if-gtz v4, :morphe_close_null
                    long-to-int v4, v6
                    new-array v6, v4, [B
                    invoke-virtual {v0, v6}, Ljava/io/RandomAccessFile;->readFully([B)V
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->close()V
                    const/4 v7, 0x0
                    aget-byte v4, v6, v7
                    const/16 v5, 0x49
                    if-ne v4, v5, :morphe_null
                    const/4 v7, 0x1
                    aget-byte v4, v6, v7
                    const/16 v5, 0x44
                    if-ne v4, v5, :morphe_null
                    const/4 v7, 0x2
                    aget-byte v4, v6, v7
                    const/16 v5, 0x33
                    if-ne v4, v5, :morphe_null
                    const/4 v7, 0x3
                    aget-byte v4, v6, v7
                    if-ne v4, v7, :morphe_null
                    const/16 v7, 0x9
                    array-length v8, v6
                    :morphe_apic_loop
                    add-int/lit8 v7, v7, 0x1
                    if-ge v7, v8, :morphe_null
                    aget-byte v4, v6, v7
                    const/16 v5, 0x41
                    if-ne v4, v5, :morphe_apic_loop
                    add-int/lit8 v9, v7, 0x1
                    aget-byte v4, v6, v9
                    const/16 v5, 0x50
                    if-ne v4, v5, :morphe_apic_loop
                    add-int/lit8 v9, v7, 0x2
                    aget-byte v4, v6, v9
                    const/16 v5, 0x49
                    if-ne v4, v5, :morphe_apic_loop
                    add-int/lit8 v9, v7, 0x3
                    aget-byte v4, v6, v9
                    const/16 v5, 0x43
                    if-ne v4, v5, :morphe_apic_loop
                    add-int/lit8 v9, v7, 0xa
                    if-ge v9, v8, :morphe_null
                    # ID3v2.3 frame length is big-endian.
                    add-int/lit8 v4, v7, 0x4
                    aget-byte v5, v6, v4
                    and-int/lit16 v5, v5, 0xff
                    shl-int/lit8 v5, v5, 0x18
                    add-int/lit8 v4, v7, 0x5
                    aget-byte v4, v6, v4
                    and-int/lit16 v4, v4, 0xff
                    shl-int/lit8 v4, v4, 0x10
                    or-int/2addr v5, v4
                    add-int/lit8 v4, v7, 0x6
                    aget-byte v4, v6, v4
                    and-int/lit16 v4, v4, 0xff
                    shl-int/lit8 v4, v4, 0x8
                    or-int/2addr v5, v4
                    add-int/lit8 v4, v7, 0x7
                    aget-byte v4, v6, v4
                    and-int/lit16 v4, v4, 0xff
                    or-int/2addr v5, v4
                    if-lez v5, :morphe_null
                    add-int v5, v5, v9
                    if-gt v5, v8, :morphe_null
                    :morphe_image_loop
                    add-int/lit8 v4, v9, 0x3
                    if-gt v4, v5, :morphe_null
                    aget-byte v4, v6, v9
                    and-int/lit16 v4, v4, 0xff
                    const/16 v7, 0xff
                    if-ne v4, v7, :morphe_image_next
                    add-int/lit8 v4, v9, 0x1
                    aget-byte v4, v6, v4
                    and-int/lit16 v4, v4, 0xff
                    const/16 v7, 0xd8
                    if-ne v4, v7, :morphe_image_next
                    invoke-static {v6, v9, v5}, Ljava/util/Arrays;->copyOfRange([BII)[B
                    move-result-object v4
                    return-object v4
                    :morphe_image_next
                    add-int/lit8 v9, v9, 0x1
                    goto :morphe_image_loop
                    :morphe_close_null
                    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->close()V
                    :morphe_null
                    const/4 v0, 0x0
                    return-object v0
                """.trimIndent())
            }
        )

        // Only attempt the fallback after a null result from Android.
        val pictureIndex = uniqueReferenceIndex(
            decoder,
            "Landroid/media/MediaMetadataRetriever;->getEmbeddedPicture()[B",
        )
        val afterResult = decoder.implementation?.instructions?.elementAtOrNull(pictureIndex + 1)
        if (afterResult?.opcode != Opcode.MOVE_RESULT_OBJECT ||
            (afterResult as? OneRegisterInstruction)?.registerA != 2
        ) throw PatchException("Album art: unexpected embedded-picture result")
        decoder.addInstructions(
            pictureIndex + 2,
            """
                if-nez v2, :morphe_picture_available
                invoke-direct {p0}, Lax/V4/a${'[B
                move-result-object v2
                :morphe_picture_available
                nop
            """.trimIndent(),
        )


        val configIndex = uniqueReferenceIndex(
            decoder,
            "Landroid/graphics/Bitmap\$Config;->RGB_565:Landroid/graphics/Bitmap\$Config;",
        )
        decoder.replaceInstruction(
            configIndex,
            "sget-object v7, Landroid/graphics/Bitmap\$Config;->ARGB_8888:Landroid/graphics/Bitmap\$Config;",
        )

        // The stock decoder passes a 1 MiB budget to k(). With ARGB_8888,
        // that can downsample artwork even more aggressively than RGB_565.
        // Override only this decoder call, not the global cache budget.
        val budgetCallIndex = uniqueReferenceIndex(
            decoder,
            "Lax/V4/a;->d()I",
        )
        val budgetResult = decoder.implementation?.instructions?.elementAtOrNull(budgetCallIndex + 1)
        if (budgetResult?.opcode != Opcode.MOVE_RESULT ||
            (budgetResult as? OneRegisterInstruction)?.registerA != 8
        ) {
            throw PatchException("Album art: unexpected decode budget register")
        }
        // Keep the invoke-result pair structurally valid by replacing both.
        decoder.replaceInstruction(budgetCallIndex + 1, "nop")
        decoder.replaceInstruction(budgetCallIndex, "const v8, 0x800000")
    }
}
}b;->morpheWavApic()[B
                move-result-object v2
                :morphe_picture_available
                nop
            """.trimIndent(),
        )


        val configIndex = uniqueReferenceIndex(
            decoder,
            "Landroid/graphics/Bitmap\$Config;->RGB_565:Landroid/graphics/Bitmap\$Config;",
        )
        decoder.replaceInstruction(
            configIndex,
            "sget-object v7, Landroid/graphics/Bitmap\$Config;->ARGB_8888:Landroid/graphics/Bitmap\$Config;",
        )

        // The stock decoder passes a 1 MiB budget to k(). With ARGB_8888,
        // that can downsample artwork even more aggressively than RGB_565.
        // Override only this decoder call, not the global cache budget.
        val budgetCallIndex = uniqueReferenceIndex(
            decoder,
            "Lax/V4/a;->d()I",
        )
        val budgetResult = decoder.implementation?.instructions?.elementAtOrNull(budgetCallIndex + 1)
        if (budgetResult?.opcode != Opcode.MOVE_RESULT ||
            (budgetResult as? OneRegisterInstruction)?.registerA != 8
        ) {
            throw PatchException("Album art: unexpected decode budget register")
        }
        // Keep the invoke-result pair structurally valid by replacing both.
        decoder.replaceInstruction(budgetCallIndex + 1, "nop")
        decoder.replaceInstruction(budgetCallIndex, "const v8, 0x800000")
    }
}
