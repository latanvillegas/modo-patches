package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Privacy v3 ads, stage 1: suppress app-open ad manager startup and ad fetch.
 *
 * File Manager Plus 3.8.2 JADX:
 * AppOpenManager.n(Context) creates/registers manager and calls q(Context).
 * AppOpenManager.q(Context) requests an app-open ad via ax.K5.a.c(...).
 *
 * Do not touch ad callbacks, premium state, SDK classes, cloud services,
 * or banner/native paths. Remaining ad formats require a separate audit.
 */
private object AppOpenManagerStartFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/ads/AppOpenManager;",
    name = "n",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
)

private object AppOpenManagerFetchFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/ads/AppOpenManager;",
    name = "q",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

@Suppress("unused")
val fileManagerPrivacyV3AppOpenAdsPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v3 (App Open Ads experimental)",
    description = "Impide iniciar y solicitar anuncios de apertura. No desactiva banners ni anuncios nativos.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Both methods return void; no scratch registers required.
        AppOpenManagerStartFingerprint.method.addInstructions(0, "return-void")
        AppOpenManagerFetchFingerprint.method.addInstructions(0, "return-void")
    }
}
