package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Privacy v4: disable app-owned ad placement gates without removing SDK classes.
 *
 * Based on the File Manager Plus 3.8.2 JADX audit of ax.y3.d:
 * F analysis, G app-open, H desktop native, K exit,
 * I desktop ads to all, N recycle bin, O rewarded, P toolbar rewarded.
 *
 * Keeps purchase/license state, theme preferences, local defaults, and
 * cloud/remote-file functionality untouched. This complements the existing
 * v3 ad-request interception and does not assert that all network ads stop.
 *
 * p0 is the instance register; overwriting it with false immediately before
 * returning is safe for a zero-argument boolean instance method and requires
 * no new local registers.
 */
private const val DYNAMIC_CONFIG = "Lax/y3/d;"

private object AnalysisAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "F", returnType = "Z", parameters = emptyList(),
)
private object AppOpenAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "G", returnType = "Z", parameters = emptyList(),
)
private object DesktopAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "H", returnType = "Z", parameters = emptyList(),
)
private object DesktopAdsToAllGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "I", returnType = "Z", parameters = emptyList(),
)
private object ExitAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "K", returnType = "Z", parameters = emptyList(),
)
private object RecycleBinAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "N", returnType = "Z", parameters = emptyList(),
)
private object RewardedAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "O", returnType = "Z", parameters = emptyList(),
)

private object ToolbarRewardedAdsGate : Fingerprint(
    definingClass = DYNAMIC_CONFIG, name = "P", returnType = "Z", parameters = emptyList(),
)

@Suppress("unused")
val fileManagerPrivacyV4AdGatesPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v4 (Ad placement gates experimental)",
    description = "Desactiva ocho controles de anuncios sin eliminar bibliotecas ni alterar licencias o temas. Experimental.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        val returnDisabled = """
            const/4 p0, 0x0
            return p0
        """.trimIndent()

        listOf(
            AnalysisAdsGate,
            AppOpenAdsGate,
            DesktopAdsGate,
            DesktopAdsToAllGate,
            ExitAdsGate,
            RecycleBinAdsGate,
            RewardedAdsGate,
            ToolbarRewardedAdsGate,
        ).forEach { it.method.addInstructions(0, returnDisabled) }
    }
}
