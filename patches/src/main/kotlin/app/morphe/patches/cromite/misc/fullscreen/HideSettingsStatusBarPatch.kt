package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.toInstructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// Restored from HideSettingsStatusBarPatchKt.class in patches-1.50.0.mpp.
private val settingsActivityFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/settings/SettingsActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
)

@Suppress("unused")
val hideSettingsStatusBarPatch = bytecodePatch(
    name = "Hide status bar in Settings",
    description = "Independent Cromite Settings patch. Hides the Android status bar only in SettingsActivity and does not modify the working main-browser status-bar patch.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        val settingsClass = settingsActivityFingerprint.classDef
        check(settingsClass.methods.none {
            it.name == "onWindowFocusChanged" &&
                it.parameterTypes == listOf("Z") &&
                it.returnType == "V"
        }) {
            "Cromite 153.0.8010.37 SettingsActivity already overrides onWindowFocusChanged"
        }

        val instructions = """
            invoke-super {p0, p1}, Landroid/app/Activity;->onWindowFocusChanged(Z)V
            if-eqz p1, :morphe_settings_status_done
            invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
            move-result-object v0
            invoke-virtual {v0}, Landroid/view/Window;->getInsetsController()Landroid/view/WindowInsetsController;
            move-result-object v0
            if-eqz v0, :morphe_settings_status_done
            const/4 p1, 0x1
            invoke-interface {v0, p1}, Landroid/view/WindowInsetsController;->hide(I)V
            :morphe_settings_status_done
            return-void
        """.trimIndent().toInstructions()

        settingsClass.methods.add(
            ImmutableMethod(
                settingsClass.type,
                "onWindowFocusChanged",
                listOf(ImmutableMethodParameter("Z", null, null)),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                null,
                null,
                ImmutableMethodImplementation(3, instructions, null, null),
            ).toMutable(),
        )
    }
}
