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
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
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
private const val SEARCH_ENGINE_ADAPTER = "Lr2c;"\nprivate const val SETTINGS_PREFERENCE_HELPER = "Lvo7;"

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

private val settingsPreferenceDrawableFingerprint = Fingerprint(
    definingClass = SETTINGS_PREFERENCE_HELPER,
    name = "b",
    returnType = "Landroid/graphics/drawable/Drawable;",
    parameters = listOf("Lto7;", "Landroidx/preference/Preference;"),
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

        // r2c.getView() resolves the visible row container in v3, then Chromium
        // applies its containment styling through Lxx2.a/b(v3, rx2). The latter
        // overwrites an earlier setBackgroundColor(), so apply AMOLED only after
        // the final Lxx2.b call. At that exact point v9 is dead and is redefined
        // later before its next use, making it a safe scratch register.
        searchEngineAdapterViewFingerprint.method.apply {
            val containmentStyleIndices = findInstructionIndicesReversedOrThrow(
                methodCall(
                    definingClass = "Lxx2;",
                    name = "b",
                    returnType = "V",
                    parameters = listOf("Landroid/view/View;", "Lrx2;"),
                ),
            )
            if (containmentStyleIndices.size != 1) {
                throw PatchException(
                    "AMOLED Settings verification failed for r2c.getView: " +
                        "expected exactly one Lxx2.b(View, rx2) containment style call, " +
                        "found ${containmentStyleIndices.size}.",
                )
            }

            val styleIndex = containmentStyleIndices.single()
            val invoke = getInstruction<FiveRegisterInstruction>(styleIndex)
            if (invoke.registerC != 3 || invoke.registerD != 9) {
                throw PatchException(
                    "AMOLED Settings verification failed for r2c.getView: " +
                        "expected containment style registers v3/v9, found " +
                        "v${invoke.registerC}/v${invoke.registerD}.",
                )
            }

            addInstructions(
                styleIndex + 1,
                settingsBackgroundSmali(selectedTheme, 3, 9),
            )
        }

        // Lvo7.b() is the shared Settings containment drawable factory. Only
        // tint the drawable produced by Lfic.d(); the fallback Preference.C path
        // remains native. v1 is dead on the cond_5 path and is safe for the
        // resolved AMOLED color while p0 still contains Context.
        settingsPreferenceDrawableFingerprint.method.apply {
            val drawableFactoryIndices = findInstructionIndicesReversedOrThrow(
                methodCall(
                    definingClass = "Lfic;",
                    name = "d",
                    returnType = "Landroid/graphics/drawable/Drawable;",
                    parameters = listOf("Landroid/content/Context;", "I", "I"),
                ),
            )
            if (drawableFactoryIndices.size != 1) {
                throw PatchException(
                    "AMOLED Settings verification failed for Lvo7.b: expected exactly " +
                        "one Lfic.d(Context, int, int) containment drawable call, found " +
                        "${drawableFactoryIndices.size}.",
                )
            }

            val factoryIndex = drawableFactoryIndices.single()
            val factoryInvoke = getInstruction<FiveRegisterInstruction>(factoryIndex)
            if (factoryInvoke.registerC != 2 || factoryInvoke.registerD != 0 ||
                factoryInvoke.registerE != 3
            ) {
                throw PatchException(
                    "AMOLED Settings verification failed for Lvo7.b: expected " +
                        "Lfic.d(p0, v0, p1) physical registers v2/v0/v3, found " +
                        "v${factoryInvoke.registerC}/v${factoryInvoke.registerD}/" +
                        "v${factoryInvoke.registerE}.",
                )
            }

            val resultIndex = factoryIndex + 1
            if (getInstruction(resultIndex).opcode != Opcode.MOVE_RESULT_OBJECT ||
                getInstruction<OneRegisterInstruction>(resultIndex).registerA != 2
            ) {
                throw PatchException(
                    "AMOLED Settings verification failed for Lvo7.b: expected " +
                        "move-result-object p0 immediately after Lfic.d.",
                )
            }

            // Physical registers for .locals 3 + two parameters:
            // v1 = 1 (dead scratch), p0 = 3 before the call, result p0 = 3.
            addInstructions(
                resultIndex + 1,
                """
                    invoke-virtual {p0, v1}, Landroid/graphics/drawable/Drawable;->setTint(I)V
                """,
            )
            addInstructions(
                factoryIndex,
                settingsContextColorSmali(selectedTheme, 3, 1),
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
