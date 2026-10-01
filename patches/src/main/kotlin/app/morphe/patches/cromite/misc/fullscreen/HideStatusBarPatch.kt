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

private val showSystemBarsFingerprint = Fingerprint(
    definingClass = "Lng5;",
    name = "A",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
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
                val forceTabletPreference = (0 until root.childNodes.length)
                    .mapNotNull { root.childNodes.item(it) as? Element }
                    .firstOrNull { it.getAttribute("android:key") == "force_tablet_ui" }
                    ?: throw IllegalStateException(
                        "${accessibilityPreferences.absolutePath} no longer contains force_tablet_ui"
                    )
                val nextNode = forceTabletPreference.nextSibling
                if (nextNode != null) {
                    root.insertBefore(preference, nextNode)
                } else {
                    root.appendChild(preference)
                }
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
                if-nez v0, :morphe_status_bar_done

                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "$DEFAULT_PREFERENCES"
                const/4 p1, 0x0
                invoke-virtual {v0, v1, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "$HIDE_STATUS_BAR_PREF"
                invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :morphe_show_status_bar

                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_show_status_bar

                sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                const/16 v1, 0x1e
                if-lt v0, v1, :morphe_hide_status_bar_legacy

                iget-object v0, p0, Lmg5;->b:Landroid/app/Activity;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/View;->getWindowInsetsController()Landroid/view/WindowInsetsController;
                move-result-object v0
                if-eqz v0, :morphe_status_bar_done
                invoke-static {}, Landroid/view/WindowInsets${'$'}Type;->statusBars()I
                move-result v1
                invoke-virtual {v0, v1}, Landroid/view/WindowInsetsController;->hide(I)V
                goto :morphe_status_bar_done

                :morphe_hide_status_bar_legacy
                iget-object v0, p0, Lmg5;->b:Landroid/app/Activity;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                move-object v1, v0
                invoke-virtual {v1}, Landroid/view/View;->getSystemUiVisibility()I
                move-result v0
                or-int/lit8 v0, v0, 0x4
                invoke-virtual {v1, v0}, Landroid/view/View;->setSystemUiVisibility(I)V
                goto :morphe_status_bar_done

                :morphe_show_status_bar
                sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                const/16 v1, 0x1e
                if-lt v0, v1, :morphe_show_status_bar_legacy

                iget-object v0, p0, Lmg5;->b:Landroid/app/Activity;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/View;->getWindowInsetsController()Landroid/view/WindowInsetsController;
                move-result-object v0
                if-eqz v0, :morphe_status_bar_done
                invoke-static {}, Landroid/view/WindowInsets${'$'}Type;->statusBars()I
                move-result v1
                invoke-virtual {v0, v1}, Landroid/view/WindowInsetsController;->show(I)V
                goto :morphe_status_bar_done

                :morphe_show_status_bar_legacy
                iget-object v0, p0, Lmg5;->b:Landroid/app/Activity;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                move-object v1, v0
                invoke-virtual {v1}, Landroid/view/View;->getSystemUiVisibility()I
                move-result v0
                and-int/lit8 v0, v0, -0x5
                invoke-virtual {v1, v0}, Landroid/view/View;->setSystemUiVisibility(I)V
            """,
            ExternalLabel("morphe_status_bar_done", method.getInstruction(0)),

        // Chromium calls showSystemBars() when leaving fullscreen and while
        // reconciling system-bar state. Without guarding this method it can
        // immediately undo the statusBars().hide() above. Keep Chromium's
        // original implementation for every normal case, but while the
        // tablet-only preference is active, hide only statusBars and return.
        val showMethod = showSystemBarsFingerprint.method
        val showImplementation = showMethod.implementation
            ?: throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.showSystemBars has no implementation"
            )
        val showLocals = showImplementation.registerCount - 2
        if (showLocals < 2) {
            throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.showSystemBars requires 2 local registers; found $showLocals"
            )
        }

        showMethod.addInstructionsWithLabels(
            0,
            """
                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "$DEFAULT_PREFERENCES"
                const/4 v0, 0x0
                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v1
                const-string v0, "$DEFAULT_PREFERENCES"
                const/4 p1, 0x0
                invoke-virtual {v1, v0, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v1
                const-string v0, "$HIDE_STATUS_BAR_PREF"
                invoke-interface {v1, v0, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :morphe_show_original
                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_show_original

                iget-object v0, p0, Lmg5;->b:Landroid/app/Activity;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                sget v1, Landroid/os/Build$VERSION;->SDK_INT:I
                const/16 p1, 0x1e
                if-lt v1, p1, :morphe_show_hide_legacy
                invoke-virtual {v0}, Landroid/view/View;->getWindowInsetsController()Landroid/view/WindowInsetsController;
                move-result-object v0
                if-eqz v0, :morphe_show_return
                invoke-static {}, Landroid/view/WindowInsets$Type;->statusBars()I
                move-result v1
                invoke-virtual {v0, v1}, Landroid/view/WindowInsetsController;->hide(I)V
                return-void

                :morphe_show_hide_legacy
                move-object v1, v0
                invoke-virtual {v1}, Landroid/view/View;->getSystemUiVisibility()I
                move-result v0
                or-int/lit8 v0, v0, 0x4
                invoke-virtual {v1, v0}, Landroid/view/View;->setSystemUiVisibility(I)V
                :morphe_show_return
                return-void
            """,
            ExternalLabel("morphe_show_original", showMethod.getInstruction(0)),
        )
        )
    }
}
