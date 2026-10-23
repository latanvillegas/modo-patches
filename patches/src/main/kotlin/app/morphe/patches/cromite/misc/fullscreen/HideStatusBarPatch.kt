package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.toInstructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private val tabbedActivityResumeFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
)

/**
 * Cromite 153 Force Tablet Mode preference: force_tablet_ui_enabled.
 * Read it from the activity's default SharedPreferences, without relying on
 * initialization of obfuscated Ln13/Lm13 static fields.
 *
 * ChromeTabbedActivity2 inherits this onResume. The superclass Ld52 declares
 * onWindowFocusChanged final, so that callback cannot be overridden here.
 *
 * Do not set android:windowFullscreen in themes: that would be unconditional.
 */
@Suppress("unused")
val hideStatusBarPatch = bytecodePatch(
    name = "Hide Android status bar",
    description = "Hides the Android status bar only when Force Tablet Mode is enabled; shows it otherwise.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        val activity = tabbedActivityResumeFingerprint.classDef
        val resume = tabbedActivityResumeFingerprint.method
        check(activity.methods.none { it.name == "morpheUpdateTabletStatusBar" }) {
            "Cromite already defines morpheUpdateTabletStatusBar"
        }
        check(resume.implementation?.instructions?.isNotEmpty() == true) {
            "Cromite ChromeTabbedActivity.onResume implementation changed"
        }

        // Separate static helper preserves the existing onResume register
        // allocation and Chromium's TraceEvent try/finally structure.
        // toInstructions() parses p-register aliases without the helper
        // method register context; use the absolute parameter register v4.
        val helper = """
            invoke-static {v4}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;
            move-result-object v0
            const-string v1, "force_tablet_ui_enabled"
            const/4 v2, 0x0
            invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
            move-result v1
            invoke-virtual {v4}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
            move-result-object v0
            invoke-virtual {v0}, Landroid/view/Window;->getInsetsController()Landroid/view/WindowInsetsController;
            move-result-object v0
            if-eqz v0, :morphe_status_done
            const/4 v2, 0x1
            if-eqz v1, :morphe_status_show
            invoke-interface {v0, v2}, Landroid/view/WindowInsetsController;->hide(I)V
            goto :morphe_status_done
            :morphe_status_show
            invoke-interface {v0, v2}, Landroid/view/WindowInsetsController;->show(I)V
            :morphe_status_done
            return-void
        """.trimIndent().toInstructions()

        activity.methods.add(
            ImmutableMethod(
                activity.type,
                "morpheUpdateTabletStatusBar",
                listOf(ImmutableMethodParameter("Landroid/app/Activity;", null, null)),
                "V",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null,
                null,
                ImmutableMethodImplementation(5, helper, null, null),
            ).toMutable(),
        )

        resume.addInstructions(
            0,
            "invoke-static {p0}, Lorg/chromium/chrome/browser/ChromeTabbedActivity;->morpheUpdateTabletStatusBar(Landroid/app/Activity;)V",
        )
    }
}
