package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

private const val CROMITE_VERSION = "153.0.8010.37"
private const val HIDE_STATUS_BAR_PREF = "hide_status_bar_info_tablet_ui"
private const val DEFAULT_PREFERENCES = "org.cromite.cromite_preferences"

/*
 * Verified against the R8 mapping shipped with Cromite 153.0.8010.37:
 *   FullscreenHtmlApiHandlerCompat -> ng5
 *   getWindowInsetsController() -> E
 *   WindowInsetsControllerCompat -> lng
 *   hide(int) -> a
 *
 * DeviceFormFactor itself is intentionally not obfuscated in this build. R8
 * inlines isForceTabletUI(), but preserves its backing static boolean as field
 * "a". Reading that field is therefore the exact runtime equivalent for this
 * one pinned Cromite version.
 */
private val fullscreenHandlerConstructorFingerprint = Fingerprint(
    definingClass = "Lng5;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Lorg/chromium/base/supplier/NonNullObservableSupplier;",
        "Z",
        "Lorg/chromium/chrome/browser/multiwindow/MultiWindowModeStateDispatcher;",
    ),
)

/**
 * Adds the browser-side preference. ChromeSwitchPreference is persistent by
 * default, so AndroidX stores the boolean in Cromite's default preference file.
 *
 * The preference depends on Force Tablet Mode. The bytecode hook below reads
 * both persisted/effective states when the browser UI is created.
 */
private val hideStatusBarPreferenceResourcesPatch = resourcePatch(
    description = "Adds the tablet-only status-bar preference to Cromite Accessibility settings.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        document("res/xml/accessibility_preferences.xml").use { document ->
            val root = document.documentElement
                ?: throw IllegalStateException("accessibility_preferences.xml has no root element")

            val existing = (0 until root.childNodes.length)
                .mapNotNull { root.childNodes.item(it) as? Element }
                .any { it.getAttribute("android:key") == HIDE_STATUS_BAR_PREF }

            if (!existing) {
                val preference = document.createElement(
                    "org.chromium.components.browser_ui.settings.ChromeSwitchPreference"
                )
                preference.setAttribute("android:key", HIDE_STATUS_BAR_PREF)
                preference.setAttribute("android:title", "@string/hide_status_bar_info_tablet_ui_title")
                preference.setAttribute("android:summary", "@string/hide_status_bar_info_tablet_ui_summary")
                preference.setAttribute("android:defaultValue", "false")
                preference.setAttribute("android:dependency", "force_tablet_ui")
                root.appendChild(preference)
            }
        }

        fun addString(path: String, name: String, value: String) {
            document(path).use { document ->
                val resources = document.documentElement
                    ?: throw IllegalStateException("$path has no resources root")
                val existing = (0 until resources.childNodes.length)
                    .mapNotNull { resources.childNodes.item(it) as? Element }
                    .firstOrNull { it.tagName == "string" && it.getAttribute("name") == name }

                if (existing != null) {
                    existing.textContent = value
                } else {
                    val string = document.createElement("string")
                    string.setAttribute("name", name)
                    string.textContent = value
                    resources.appendChild(string)
                }
            }
        }

        addString(
            "res/values/strings.xml",
            "hide_status_bar_info_tablet_ui_title",
            "Hide status bar information in tablet mode",
        )
        addString(
            "res/values/strings.xml",
            "hide_status_bar_info_tablet_ui_summary",
            "Hides the complete Android status bar (clock, battery, signal and notifications) when Force Tablet Mode is active. Applied after restarting Cromite.",
        )
        addString(
            "res/values-es/strings.xml",
            "hide_status_bar_info_tablet_ui_title",
            "Ocultar información de la barra de estado en modo tableta",
        )
        addString(
            "res/values-es/strings.xml",
            "hide_status_bar_info_tablet_ui_summary",
            "Oculta la barra de estado completa de Android (hora, batería, señal y notificaciones) cuando Force Tablet Mode está activo. Se aplica después de reiniciar Cromite.",
        )
    }
}

/**
 * Hides only WindowInsetsCompat.Type.statusBars() (type bit 0x1). Navigation
 * bars and gesture navigation are deliberately untouched.
 *
 * The switch is persisted by AndroidX. Cromite's existing Force Tablet Mode
 * already requests an application restart when changed; this preference is
 * documented as restart-applied as well. A new window naturally restores the
 * status bar when either condition is false, so no permanent system setting is
 * changed.
 */
@Suppress("unused")
val hideStatusBarPatch = bytecodePatch(
    name = "Hide status bar information in tablet mode",
    description = "Adds a persisted Accessibility option that hides the complete Android status bar only while Force Tablet Mode is active.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    dependsOn(hideStatusBarPreferenceResourcesPatch)

    execute {
        val method = fullscreenHandlerConstructorFingerprint.method
        val returnIndex = method.indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)
        val returnInstruction = method.getInstruction(returnIndex)

        method.addInstructionsWithLabels(
            returnIndex,
            """
                const-string v0, "$DEFAULT_PREFERENCES"
                const/4 v1, 0x0
                invoke-virtual {p1, v0, v1}, Landroid/app/Activity;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "$HIDE_STATUS_BAR_PREF"
                const/4 v2, 0x0
                invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :morphe_status_bar_done

                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_status_bar_done

                invoke-direct {p0}, Lng5;->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->a(I)V
            """,
            ExternalLabel("morphe_status_bar_done", returnInstruction),
        )
    }
}
