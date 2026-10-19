package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Privacy v3 ad requests: avoid network requests while notifying host callbacks.
 * File Manager Plus 3.8.2 only. Experimental, disabled by default.
 *
 * Native methods A/C/E/x/y(Activity, ads.a.k) use k.c(int) on load error.
 * Banner methods B/z(Activity,int,ads.a.i) and D(Activity,ads.a.i)
 * use i.a(View,int) on load error. Null view is accepted by observed host
 * failure callbacks. Error code 3 is SDK no-fill, not a Premium entitlement.
 *
 * Does not change premium checks, rewarded ad flow, cloud or remote access.
 */
private const val HOST_ADS = "Lcom/alphainventor/filemanager/ads/a;"
private const val NATIVE_CALLBACK = "Lcom/alphainventor/filemanager/ads/a\$k;"
private const val BANNER_CALLBACK = "Lcom/alphainventor/filemanager/ads/a\$i;"

private object ExitNativeAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "A", returnType = "V",
    parameters = listOf("Landroid/app/Activity;", NATIVE_CALLBACK),
)
private object RecycleNativeAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "C", returnType = "V",
    parameters = listOf("Landroid/app/Activity;", NATIVE_CALLBACK),
)
private object VideoNativeAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "E", returnType = "V",
    parameters = listOf("Landroid/app/Activity;", NATIVE_CALLBACK),
)
private object AnalysisNativeAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "x", returnType = "V",
    parameters = listOf("Landroid/app/Activity;", NATIVE_CALLBACK),
)
private object DesktopNativeAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "y", returnType = "V",
    parameters = listOf("Landroid/app/Activity;", NATIVE_CALLBACK),
)
private object ExitBannerAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "B", returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/app/Activity;", "I", BANNER_CALLBACK),
)
private object VideoBannerAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "D", returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/app/Activity;", BANNER_CALLBACK),
)
private object DesktopBannerAdFingerprint : Fingerprint(
    definingClass = HOST_ADS, name = "z", returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/app/Activity;", "I", BANNER_CALLBACK),
)

@Suppress("unused")
val fileManagerPrivacyV3AdRequestsPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v3 (Ad requests experimental)",
    description = "Evita solicitudes de banners y anuncios nativos, notificando fallo a la interfaz. Requiere pruebas en Android.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Static methods: p0 Activity, p1 native callback. Reuse p0 as int.
        val nativeFailure = """
            const/4 p0, 0x3
            invoke-interface {p1, p0}, $NATIVE_CALLBACK->c(I)V
            return-void
        """.trimIndent()
        listOf(
            ExitNativeAdFingerprint,
            RecycleNativeAdFingerprint,
            VideoNativeAdFingerprint,
            AnalysisNativeAdFingerprint,
            DesktopNativeAdFingerprint,
        ).forEach { it.method.addInstructions(0, nativeFailure) }

        // Static banners with size: p0 Activity, p1 size, p2 callback.
        val bannerWithSizeFailure = """
            const/4 p0, 0x0
            const/4 p1, 0x3
            invoke-interface {p2, p0, p1}, $BANNER_CALLBACK->a(Landroid/view/View;I)V
            return-object p0
        """.trimIndent()
        ExitBannerAdFingerprint.method.addInstructions(0, bannerWithSizeFailure)
        DesktopBannerAdFingerprint.method.addInstructions(0, bannerWithSizeFailure)

        // Static video banner: p0 Activity, p1 callback.
        VideoBannerAdFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                invoke-interface {p1, p0, p0}, $BANNER_CALLBACK->a(Landroid/view/View;I)V
                return-object p0
            """.trimIndent()
        )
    }
}
