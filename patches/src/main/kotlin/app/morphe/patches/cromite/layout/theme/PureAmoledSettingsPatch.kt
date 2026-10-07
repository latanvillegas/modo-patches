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

private const val SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_ACTIVITY =
    "Lorg/chromium/chrome/browser/settings/SettingsActivity;"
private const val CHROME_IMAGE_VIEW_PREFERENCE =
    "Lorg/chromium/components/browser_ui/settings/ChromeImageViewPreference;"
private const val CHROME_SWITCH_PREFERENCE =
    "Lorg/chromium/components/browser_ui/settings/ChromeSwitchPreference;"
private const val SETTINGS_CONTAINMENT_HELPER = "Lxx2;"

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

private val settingsContainmentDrawableFingerprint = Fingerprint(
    definingClass = SETTINGS_CONTAINMENT_HELPER,
    name = "d",
    returnType = "Landroid/graphics/drawable/RippleDrawable;",
    parameters = listOf("Landroid/content/Context;", "F", "F", "I"),
    filters = listOf(
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

private fun settingsContextColorSmali(
    theme: String,
    contextRegister: Int,
    colorRegister: Int,
): String {
    val context = "v$contextRegister"
    val color = "v$colorRegister"

    return when (theme) {
        "black" -> "const $color, -0x1000000"
        "white" -> "const $color, -0x1"
        else -> """
            invoke-virtual {$context}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
            move-result-object $color
            invoke-virtual {$color}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $color
            iget $color, $color, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $color, $color, 0x20
            ushr-int/lit8 $color, $color, 0x5
            neg-int $color, $color
            ushr-int/lit8 $color, $color, 0x8
            not-int $color, $color
        """
    }
}

/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 resolves the Settings root/AppBar background inside SettingsActivity
 * and applies containment-card backgrounds through Lxx2.d(). Patch only those verified
 * consumers; do not change SemanticColorUtils or AppCompat SearchView globally.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies Device default / Pure Black / Pure White to verified Cromite Settings root, AppBar and containment-card backgrounds without recoloring global Material or SearchView surfaces.",
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

        // Lxx2.d(Context, float, float, int) is the shared containment
        // RippleDrawable factory. p3 is the default GradientDrawable surface
        // color used by the empty ColorStateList state. Resolve only p3 to the
        // selected AMOLED surface; keep Chromium's corner radii, pressed-state
        // composition, ripple mask and framework ripple ColorStateList intact.
        settingsContainmentDrawableFingerprint.method.apply {
            val semanticColorIndices = findInstructionIndicesReversedOrThrow(
                methodCall(
                    definingClass = "Lis7;",
                    name = "f",
                    returnType = "I",
                    parameters = listOf(
                        "Landroid/content/Context;",
                        "Landroid/util/TypedValue;",
                    ),
                ),
            )
            if (semanticColorIndices.size != 1) {
                throw PatchException(
                    "AMOLED Settings verification failed for Lxx2.d: expected exactly " +
                        "one Lis7.f(Context, TypedValue) semantic surface resolver, found " +
                        "${semanticColorIndices.size}.",
                )
            }

            val semanticIndex = semanticColorIndices.single()
            val resultIndex = semanticIndex + 1
            if (getInstruction(resultIndex).opcode != Opcode.MOVE_RESULT) {
                throw PatchException(
                    "AMOLED Settings verification failed for Lxx2.d: expected " +
                        "move-result immediately after Lis7.f.",
                )
            }

            // Lxx2.d has .locals 11 and four parameters, therefore physical
            // p0 = v11 and p3 = v14. p3 is the default containment surface color.
            addInstructions(
                resultIndex + 1,
                settingsContextColorSmali(selectedTheme, 11, 14),
            )
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
