package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Privacy v4: disable app-owned Firebase Remote Config fetch while retaining
 * the complete local configuration map.
 *
 * JADX File Manager Plus 3.8.2:
 * ax.p387y3.d.E(Context, boolean) calls a() to populate defaults, then
 * ax.A3.b.f(context, defaults, enabled). For enabled=false, b.f retains
 * the defaults and skips Firebase Remote Config initialization and fetch.
 *
 * This patch changes only the enabled parameter at entry. It does not change
 * Firebase Installations globally, billing entitlements, cloud functionality,
 * app update behavior, or any default values.
 *
 * Experimental: requires fingerprint resolution and on-device regression tests.
 */
private object DynamicConfigInitializationFingerprint : Fingerprint(
    definingClass = "Lax/y3/d;",
    name = "E",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Z"),
)

@Suppress("unused")
val fileManagerPrivacyV4RemoteConfigPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v4 (Remote Config experimental)",
    description = "Evita la descarga de Firebase Remote Config y conserva los valores locales. Experimental.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Instance method: p2 is the existing boolean argument. No new locals.
        DynamicConfigInitializationFingerprint.method.addInstructions(
            0,
            """
                const/4 p2, 0x0
            """.trimIndent()
        )
    }
}
