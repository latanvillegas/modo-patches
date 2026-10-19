package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

/**
 * Bytecode recovery from the original PureAmoledSettingsPatchKt.class
 * in patches-1.50.0.mpp. The original contains four distinct mutation groups;
 * restores SettingsActivity, preference background setters, and the Lxx2 containment-card mutation.
 */
private val settingsActivityBackgroundFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/settings/SettingsActivity;",
    filters = listOf(
        methodCall("Ldr7;->e(Landroid/content/Context;ILjava/lang/String;)Landroid/util/TypedValue;"),
        methodCall("Lis7;->f(Landroid/content/Context;Landroid/util/TypedValue;)I"),
    ),
)

private val chromeImageViewPreferenceBindFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/components/browser_ui/settings/ChromeImageViewPreference;",
    name = "y",
    returnType = "V",
    parameters = listOf("Lepa;"),
)

private val chromeSwitchPreferenceBindFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/components/browser_ui/settings/ChromeSwitchPreference;",
    name = "y",
    returnType = "V",
    parameters = listOf("Lepa;"),
)

private val settingsContainmentDrawableFingerprint = Fingerprint(
    definingClass = "Lxx2;",
    name = "d",
    returnType = "Landroid/graphics/drawable/RippleDrawable;",
    parameters = listOf("Landroid/content/Context;", "F", "F", "I"),
)

private val settingsContextColorCall =
    methodCall("Lis7;->f(Landroid/content/Context;Landroid/util/TypedValue;)I")

private fun settingsContextColorSmali(theme: String, contextRegister: Int, colorRegister: Int): String =
    when (theme) {
        "black" -> "const v$colorRegister, -0x1000000"
        "white" -> "const v$colorRegister, -0x1"
        else -> """
            invoke-virtual {v$contextRegister}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
            move-result-object v$colorRegister
            invoke-virtual {v$colorRegister}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object v$colorRegister
            iget v$colorRegister, v$colorRegister, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 v$colorRegister, v$colorRegister, 0x20
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x5
            neg-int v$colorRegister, v$colorRegister
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x8
            not-int v$colorRegister, v$colorRegister
        """.trimIndent()
    }

private val settingsSetBackgroundColorCall =
    methodCall("Landroid/view/View;->setBackgroundColor(I)V")

private fun settingsBackgroundSmali(theme: String, viewRegister: Int, colorRegister: Int): String =
    when (theme) {
        "black" -> """
            const v$colorRegister, -0x1000000
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        "white" -> """
            const v$colorRegister, -0x1
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        else -> """
            invoke-virtual {v$viewRegister}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object v$colorRegister
            invoke-virtual {v$colorRegister}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object v$colorRegister
            iget v$colorRegister, v$colorRegister, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 v$colorRegister, v$colorRegister, 0x20
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x5
            neg-int v$colorRegister, v$colorRegister
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x8
            not-int v$colorRegister, v$colorRegister
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
    }

@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Recovers SettingsActivity, preference backgrounds, and the original containment-card color override.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val theme = browserTheme.value ?: "device"

        val activity = settingsActivityBackgroundFingerprint.method
        val activityIndices = activity.findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
        check(activityIndices.size == 2) {
            "AMOLED Settings: SettingsActivity must contain exactly two background setters; found ${activityIndices.size}"
        }
        activityIndices.forEach { index ->
            val instruction = activity.getInstruction(index) as FiveRegisterInstruction
            activity.addInstructions(
                index + 1,
                settingsBackgroundSmali(theme, instruction.registerC, instruction.registerD),
            )
        }

        fun recoverPreferenceBackground(
            method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
            preference: String,
        ) {
            val indices = method.findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
            check(indices.size in 1..2) {
                "AMOLED Settings: $preference must contain one or two background setters; found ${indices.size}"
            }
            val index = indices.first()
            val instruction = method.getInstruction(index) as FiveRegisterInstruction
            method.addInstructions(
                index + 1,
                settingsBackgroundSmali(theme, instruction.registerC, instruction.registerD),
            )
        }

        recoverPreferenceBackground(
            chromeImageViewPreferenceBindFingerprint.method,
            "ChromeImageViewPreference",
        )
        recoverPreferenceBackground(
            chromeSwitchPreferenceBindFingerprint.method,
            "ChromeSwitchPreference",
        )

        // Restore the original containment-card override at the verified call site.
        val containment = settingsContainmentDrawableFingerprint.method
        val colorCalls = containment.findInstructionIndicesReversedOrThrow(settingsContextColorCall)
        check(colorCalls.size == 1) {
            "AMOLED Settings: expected one containment-card color lookup; found ${colorCalls.size}"
        }
        val resultIndex = colorCalls.single() + 1
        check(containment.getInstruction(resultIndex).opcode ==
            com.android.tools.smali.dexlib2.Opcode.MOVE_RESULT) {
            "AMOLED Settings: expected move-result after containment color lookup"
        }
        // Verified in the original patches-1.50.0.mpp bytecode:
        // settingsContextColorSmali(theme, 11, 14), not registers inferred
        // from the Lis7.f call. Preserve the original method's register mapping.
        check(containment.implementation!!.registerCount > 14) {
            "AMOLED Settings: containment method lacks original v14 register"
        }
        containment.addInstructions(resultIndex + 1, settingsContextColorSmali(theme, 11, 14))
    }
}
