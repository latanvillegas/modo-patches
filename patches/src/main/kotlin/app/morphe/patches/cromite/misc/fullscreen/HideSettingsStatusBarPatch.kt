package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val CROMITE_SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_ACTIVITY =
    "Lorg/chromium/chrome/browser/settings/SettingsActivity;"

private val settingsActivityFingerprint = Fingerprint(
    definingClass = SETTINGS_ACTIVITY,
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

/**
 * Independent Settings-only status-bar patch.
 *
 * SettingsActivity does not inherit Chromium's pc0 lifecycle used by the
 * physically validated main-browser patch. Add a SettingsActivity-local
 * onWindowFocusChanged(boolean) override instead. This gives the Settings
 * window the same stable focus-time boundary without touching pc0/ng5.
 *
 * p0 is never repurposed. p1 is reused only after the superclass callback,
 * and v0 is the single local register. Only statusBars() (mask 0x1) is hidden.
 */
@Suppress("unused")
val hideSettingsStatusBarPatch = bytecodePatch(
    name = "Hide status bar in Settings",
    description = "Independent Cromite Settings patch. Hides the Android status bar only in SettingsActivity and does not modify the working main-browser status-bar patch.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_SETTINGS_VERSION))

    execute {
        settingsActivityFingerprint.classDef.apply {
            check(methods.none {
                it.name == "onWindowFocusChanged" &&
                    it.parameterTypes == listOf("Z") &&
                    it.returnType == "V"
            }) {
                "Cromite $CROMITE_SETTINGS_VERSION SettingsActivity already overrides onWindowFocusChanged"
            }

            methods.add(
                ImmutableMethod(
                    type,
                    "onWindowFocusChanged",
                    listOf("Z"),
                    "V",
                    AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                    null,
                    null,
                    MutableMethodImplementation(3),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
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
                        """
                    )
                }
            )
        }
    }
}
