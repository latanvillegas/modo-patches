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
 * Source-verified lifecycle point:
 * FullscreenHtmlApiHandlerCompat.onApplyWindowInsets(View, WindowInsets).
 *
 * R8 mapping for Cromite 153.0.8010.37 confirms:
 *   FullscreenHtmlApiHandlerCompat -> ng5
 *   FullscreenHtmlApiHandlerBase -> mg5
 *   getPersistentFullscreenMode() -> h
 *   onApplyWindowInsets(View, WindowInsets) keeps its source name/signature.
 *
 * DeviceFormFactor itself is not obfuscated in this build. R8 inlines
 * isForceTabletUI(), but preserves its backing static boolean as field "a".
 */
private val windowInsetsPolicyFingerprint = Fingerprint(
    definingClass = "Lng5;",
    name = "onApplyWindowInsets",
    returnType = "Landroid/view/WindowInsets;",
    parameters = listOf("Landroid/view/View;", "Landroid/view/WindowInsets;"),
)

private val hideStatusBarPreferenceResourcesPatch = resourcePatch(
    description = "Adds the tablet-only status-bar preference to Cromite Accessibility settings.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        val accessibilityPreferences = get("res").walk()
            .filter { it.isFile && it.extension == "xml" }
            .firstOrNull { file ->
                runCatching {
                    document(file.absolutePath).use { document ->
                        val root = document.documentElement ?: return@use false
                        (0 until root.childNodes.length)
                            .mapNotNull { root.childNodes.item(it) as? Element }
                            .any { it.getAttribute("android:key") == "force_tablet_ui" }
                    }
                }.getOrDefault(false)
            }
            ?: throw IllegalStateException(
                "Cromite $CROMITE_VERSION Accessibility XML containing force_tablet_ui was not found"
            )

        document(accessibilityPreferences.absolutePath).use { document ->
            val root = document.documentElement
                ?: throw IllegalStateException(
                    "${accessibilityPreferences.absolutePath} has no root element"
                )

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
 * Persistent HTML/video fullscreen remains authoritative: while it is active
 * this hook does nothing and Chromium handles both status and navigation bars.
 *
 * Outside persistent fullscreen, only the status-bar type is changed:
 * API 30+ uses WindowInsetsController + WindowInsets.Type.statusBars(); API 29
 * uses the legacy FULLSCREEN bit (0x4). Navigation bars and gesture navigation
 * are never modified.
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
        val method = windowInsetsPolicyFingerprint.method
        val implementation = method.implementation
            ?: throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.onApplyWindowInsets has no implementation"
            )

        // Instance method: p0=this, p1=View, p2=WindowInsets.
        // Cromite 153's method has two local registers. Chromium source confirms
        // that the original method never reads p1 and only returns p2, so after
        // obtaining the Context we can safely reuse p1 as a temporary register.
        // p2 remains untouched. This avoids growing the register file and shifting
        // parameter registers in the existing DEX implementation.
        val localRegisters = implementation.registerCount - 3
        if (localRegisters < 2) {
            throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.onApplyWindowInsets requires 2 local registers; found $localRegisters"
            )
        }

        method.addInstructionsWithLabels(
            0,
            """
                invoke-virtual {p0}, Lmg5;->h()Z
                move-result v0
                if-eqz v0, :morphe_status_not_persistent
                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=1 action=skip"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                goto :morphe_status_bar_done

                :morphe_status_not_persistent
                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "$DEFAULT_PREFERENCES"
                const/4 p1, 0x0
                invoke-virtual {v0, v1, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "$HIDE_STATUS_BAR_PREF"
                invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :morphe_show_status_bar_pref

                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_show_status_bar_phone

                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=0 tablet=1 pref=1 action=hide statusBars"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

                invoke-virtual {p0}, Lng5;->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->hide(I)V
                goto :morphe_status_bar_done

                :morphe_show_status_bar_pref
                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=0 pref=0 action=show statusBars"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                goto :morphe_show_status_bar

                :morphe_show_status_bar_phone
                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=0 tablet=0 action=show statusBars"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

                :morphe_show_status_bar
                invoke-virtual {p0}, Lng5;->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->show(I)V
            """,
            ExternalLabel("morphe_status_bar_done", method.getInstruction(0)),
        )
    }
}
