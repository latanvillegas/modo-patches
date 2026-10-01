package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
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
private val windowInsetsPolicyFingerprint = Fingerprint(
    definingClass = "Lng5;",
    name = "onApplyWindowInsets",
    returnType = "Landroid/view/WindowInsets;",
    parameters = listOf("Landroid/view/View;", "Landroid/view/WindowInsets;"),
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
 * Reapplies the tablet status-bar policy from Chromium's own insets callback.
 *
 * FullscreenHtmlApiHandlerCompat.onApplyWindowInsets() is the source-verified
 * recovery point used by Chromium when system insets change. The injected
 * policy exits immediately while persistent HTML/video fullscreen is active,
 * leaving Chromium's fullscreen state machine authoritative in that mode.
 *
 * Outside persistent fullscreen, only the status-bar type is changed:
 * API 30+ uses WindowInsetsController + WindowInsets.Type.statusBars(); API 29
 * uses the legacy FULLSCREEN bit (0x4), which also affects only the status bar.
 * Navigation bars and gesture navigation are never modified.
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
        windowInsetsPolicyFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-virtual {p0}, Lmg5;->h()Z
                move-result v0
                if-nez v0, :morphe_status_bar_done

                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "$DEFAULT_PREFERENCES"
                const/4 v2, 0x0
                invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "$HIDE_STATUS_BAR_PREF"
                invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :morphe_show_status_bar

                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_show_status_bar

                sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
                const/16 v1, 0x1e
                if-lt v0, v1, :morphe_hide_status_bar_legacy

                invoke-virtual {p1}, Landroid/view/View;->getWindowInsetsController()Landroid/view/WindowInsetsController;
                move-result-object v0
                if-eqz v0, :morphe_status_bar_done
                invoke-static {}, Landroid/view/WindowInsets$Type;->statusBars()I
                move-result v1
                invoke-virtual {v0, v1}, Landroid/view/WindowInsetsController;->hide(I)V
                goto :morphe_status_bar_done

                :morphe_hide_status_bar_legacy
                invoke-virtual {p1}, Landroid/view/View;->getSystemUiVisibility()I
                move-result v0
                or-int/lit8 v0, v0, 0x4
                invoke-virtual {p1, v0}, Landroid/view/View;->setSystemUiVisibility(I)V
                goto :morphe_status_bar_done

                :morphe_show_status_bar
                sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
                const/16 v1, 0x1e
                if-lt v0, v1, :morphe_show_status_bar_legacy

                invoke-virtual {p1}, Landroid/view/View;->getWindowInsetsController()Landroid/view/WindowInsetsController;
                move-result-object v0
                if-eqz v0, :morphe_status_bar_done
                invoke-static {}, Landroid/view/WindowInsets$Type;->statusBars()I
                move-result v1
                invoke-virtual {v0, v1}, Landroid/view/WindowInsetsController;->show(I)V
                goto :morphe_status_bar_done

                :morphe_show_status_bar_legacy
                invoke-virtual {p1}, Landroid/view/View;->getSystemUiVisibility()I
                move-result v0
                and-int/lit8 v0, v0, -0x5
                invoke-virtual {p1, v0}, Landroid/view/View;->setSystemUiVisibility(I)V
            """,
            ExternalLabel("morphe_status_bar_done", windowInsetsPolicyFingerprint.method.getInstruction(0)),
        )
    }
}
