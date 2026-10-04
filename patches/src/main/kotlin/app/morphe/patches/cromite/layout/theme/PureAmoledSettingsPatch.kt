package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_ACTIVITY =
    "Lorg/chromium/chrome/browser/settings/SettingsActivity;"
private const val CHROME_IMAGE_VIEW_PREFERENCE =
    "Lorg/chromium/components/browser_ui/settings/ChromeImageViewPreference;"
private const val CHROME_SWITCH_PREFERENCE =
    "Lorg/chromium/components/browser_ui/settings/ChromeSwitchPreference;"
private const val SEARCH_ENGINE_SETTINGS =
    "Lorg/chromium/chrome/browser/search_engines/settings/SearchEngineSettings;"
private const val SEARCH_ENGINE_ADAPTER = "Lr2c;"

private val settingsActivityBackgroundFingerprint = Fingerprint(
    definingClass = SETTINGS_ACTIVITY,
    filters = listOf(
        methodCall(
            definingClass = "Ldr7;",
            name = "e",
            returnType = "Landroid/util/TypedValue;",
            parameters = listOf(
                "Landroid/content/Context;",
                "I",
                "Ljava/lang/String;",
            ),
        ),
        methodCall(
            definingClass = "Lis7;",
            name = "f",
            returnType = "I",
            parameters = listOf(
                "Landroid/content/Context;",
                "Landroid/util/TypedValue;",
            ),
        ),
    ),
)

private val chromeImageViewPreferenceBindFingerprint = Fingerprint(
    definingClass = CHROME_IMAGE_VIEW_PREFERENCE,
    name = "y",
    returnType = "V",
    parameters = listOf("Lepa;"),
)

private val chromeSwitchPreferenceBindFingerprint = Fingerprint(
    definingClass = CHROME_SWITCH_PREFERENCE,
    name = "y",
    returnType = "V",
    parameters = listOf("Lepa;"),
)

private val searchEngineAdapterViewFingerprint = Fingerprint(
    definingClass = SEARCH_ENGINE_ADAPTER,
    name = "getView",
    returnType = "Landroid/view/View;",
    parameters = listOf("I", "Landroid/view/View;", "Landroid/view/ViewGroup;"),
)

private val settingsSetBackgroundColorCall = methodCall(
    definingClass = "Landroid/view/View;",
    name = "setBackgroundColor",
    returnType = "V",
    parameters = listOf("I"),
)

private fun settingsBackgroundSmali(
    theme: String,
    viewRegister: Int,
    scratchRegister: Int,
): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"

    return when (theme) {
        "black" -> """
            const $scratch, -0x1000000
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        "white" -> """
            const $scratch, -0x1
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
    }
}

/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 resolves the Settings root/AppBar background inside SettingsActivity
 * and also applies explicit row/container backgrounds in ChromeImageViewPreference
 * and ChromeSwitchPreference. Patch only those verified consumers; do not change
 * SemanticColorUtils or AppCompat SearchView globally.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies Device default / Pure Black / Pure White to verified Cromite Settings root, AppBar and preference container backgrounds without recoloring global Material or SearchView surfaces.",
) {
    compatibleWith("org.cromite.cromite"(SETTINGS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        settingsActivityBackgroundFingerprint.method.apply {
            val backgroundIndices =
                findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
            if (backgroundIndices.size != 2) {
                throw PatchException(
                    "AMOLED Settings verification failed for Cromite $SETTINGS_VERSION: " +
                        "expected exactly two SettingsActivity View.setBackgroundColor(I) calls, " +
                        "found ${backgroundIndices.size}.",
                )
            }

            backgroundIndices.forEach { index ->
                val invoke = getInstruction<FiveRegisterInstruction>(index)
                addInstructions(
                    index + 1,
                    settingsBackgroundSmali(
                        selectedTheme,
                        invoke.registerC,
                        invoke.registerD,
                    ),
                )
            }
        }

        chromeImageViewPreferenceBindFingerprint.method.apply {
            val backgroundIndices =
                findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
            if (backgroundIndices.size != 2) {
                throw PatchException(
                    "AMOLED Settings verification failed for ChromeImageViewPreference.y: " +
                        "expected transparent image + container background setters, " +
                        "found ${backgroundIndices.size}.",
                )
            }

            // Reversed order makes the first match the later o0 container setter.
            // Leave the earlier ImageView.setBackgroundColor(0) transparency untouched.
            val containerIndex = backgroundIndices.first()
            val invoke = getInstruction<FiveRegisterInstruction>(containerIndex)
            addInstructions(
                containerIndex + 1,
                settingsBackgroundSmali(
                    selectedTheme,
                    invoke.registerC,
                    invoke.registerD,
                ),
            )
        }

        // r2c.getView() owns the SearchEngine row views. Keep the previously
        // runtime-stable outer-row override while the verified child hook is
        // redesigned to avoid clobbering live registers.
        searchEngineAdapterViewFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow(Opcode.RETURN_OBJECT).forEach { index ->
                val returnRegister =
                    getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(
                    index,
                    """
                        move-object/from16 v14, v$returnRegister
                        ${settingsBackgroundSmali(selectedTheme, 14, 15)}
                    """,
                )
            }
        }

        // SearchView remains intentionally untouched until its visible rounded
        // background owner is mapped to an exact XML/view.

        chromeSwitchPreferenceBindFingerprint.method.apply {
            val backgroundIndices =
                findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
            if (backgroundIndices.size != 1) {
                throw PatchException(
                    "AMOLED Settings verification failed for ChromeSwitchPreference.y: " +
                        "expected exactly one container View.setBackgroundColor(I) call, " +
                        "found ${backgroundIndices.size}.",
                )
            }

            val index = backgroundIndices.single()
            val invoke = getInstruction<FiveRegisterInstruction>(index)
            addInstructions(
                index + 1,
                settingsBackgroundSmali(
                    selectedTheme,
                    invoke.registerC,
                    invoke.registerD,
                ),
            )
        }
    }
}
