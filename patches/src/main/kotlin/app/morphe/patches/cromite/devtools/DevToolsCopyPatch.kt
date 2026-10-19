package app.morphe.patches.cromite.devtools

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.security.MessageDigest

/**
 * Experimental, disabled-by-default Cromite 153 DevTools integration.
 *
 * Bundle resources must be packaged at:
 *   cromite/devtools/bootstrap.dex
 *   cromite/devtools/devtools-cromite-153.zip
 *
 * This patch intentionally fails closed if the bundle or startup anchor differs.
 * It has NOT passed a runtime startup test.
 */
private const val VERSION = "153.0.8010.37"
private const val ARCHIVE_RESOURCE = "cromite/devtools/devtools-cromite-153.zip"
private const val ASSET_PATH = "assets/devtools-cromite-153.zip"
private const val FRONTEND_SHA256 = "dadba37f7641d133423799c722c7d5136c05d246639b83c157c5f280f08c8840"
private const val BOOTSTRAP_RESOURCE = "cromite/devtools/bootstrap.dex"
private const val BOOTSTRAP_DESCRIPTOR = "Lapp/morphe/cromite/devtools/DevToolsBootstrap;"

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private val frontendAssetPatch = resourcePatch(
    description = "Internal verified DevTools frontend ZIP for Cromite 153."
) {
    compatibleWith("org.cromite.cromite"(VERSION))
    execute {
        val loader = DevToolsCopyPatchResourceAnchor::class.java.classLoader
            ?: error("DevTools patch class loader missing")
        val bytes = loader.getResourceAsStream(ARCHIVE_RESOURCE)?.use { it.readBytes() }
            ?: error("Missing bundled frontend resource: $ARCHIVE_RESOURCE")
        check(sha256(bytes) == FRONTEND_SHA256) { "DevTools frontend SHA-256 mismatch" }
        check(ASSET_PATH !in listApkEntries()) { "DevTools asset already exists in target APK" }
        get(ASSET_PATH, copy = false).writeBytes(bytes)
    }
}

private object DevToolsCopyPatchResourceAnchor

private val startupFingerprint = Fingerprint(
    definingClass = "Lj72;",
    name = "b",
    returnType = "V",
    parameters = listOf("Lkm1;"),
)

@Suppress("unused")
val devToolsCopyPatch = bytecodePatch(
    name = "Cromite DevTools: native COPY button (experimental)",
    description = "Embed verified DevTools frontend and bootstrap for Cromite 153; runtime behavior unverified.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(VERSION))
    dependsOn(frontendAssetPatch)

    // Morphe merges this precompiled DEX into its bytecode model, then writes multidex.
    // Never write classes3.dex using ResourcePatchContext: DEX entries have a separate pipeline.
    extendWith(BOOTSTRAP_RESOURCE)

    execute {
        check(classDefByOrNull(BOOTSTRAP_DESCRIPTOR) != null) {
            "Bootstrap DEX was not merged into target bytecode"
        }
        val method = startupFingerprint.method
        val implementation = method.implementation ?: error("Startup method has no code")
        val instructions = implementation.instructions.toList()
        val newInstances = instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.NEW_INSTANCE
        }
        val candidates = newInstances.filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            (reference as? TypeReference)?.type == "Le72;"
        }
        check(candidates.size == 1) {
            val descriptors = newInstances.take(12).joinToString { (_, instruction) ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                (reference as? TypeReference)?.type ?: reference.toString()
            }
            "Unsupported Cromite startup anchor: Le72; " +
                "matches=${candidates.size}, newInstances=${newInstances.size}, " +
                "descriptors=[$descriptors], instructions=${instructions.size}"
        }
        val anchor = candidates.single().index

        // Verified experimentally in the apktool Smali method with .locals 4:
        // v1 is dead at this anchor; context is initialized in Ln13.a.
        // This is not proof that native Chromium command-line parsing happens afterward.
        method.addInstructions(
            anchor,
            """
                sget-object v1, Ln13;->a:Landroid/content/Context;
                invoke-static {v1}, Lapp/morphe/cromite/devtools/DevToolsBootstrap;->initializeSafely(Landroid/content/Context;)V
            """.trimIndent(),
        )
    }
}
