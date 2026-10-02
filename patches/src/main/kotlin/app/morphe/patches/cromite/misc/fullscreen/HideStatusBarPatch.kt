package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
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

// FullscreenHtmlApiHandlerBase participates in Chromium's existing window-focus
// dispatch. The target method is identified structurally by its forwarding call
// to rh5.onWindowFocusChanged(boolean), avoiding an obfuscated method-name guess.
private val fullscreenWindowFocusFingerprint = Fingerprint(
    definingClass = "Lmg5;",
    filters = listOf(
        methodCall(
            definingClass = "Lrh5;",
            name = "onWindowFocusChanged",
            returnType = "V",
            parameters = listOf("Z"),
        )
    ),
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

            val preferences = (0 until root.childNodes.length)
                .mapNotNull { root.childNodes.item(it) as? Element }
            val existing = preferences.any { it.getAttribute("android:key") == HIDE_STATUS_BAR_PREF }
            val forceTabletPreference = preferences.firstOrNull {
                it.getAttribute("android:key") == "force_tablet_ui"
            } ?: throw IllegalStateException(
                "Cromite $CROMITE_VERSION force_tablet_ui preference disappeared during patching"
            )

            if (!existing) {
                val preference = document.createElement(
                    "org.chromium.components.browser_ui.settings.ChromeSwitchPreference"
                )
                preference.setAttribute("android:key", HIDE_STATUS_BAR_PREF)
                preference.setAttribute("android:title", "@string/hide_status_bar_info_tablet_ui_title")
                preference.setAttribute("android:summary", "@string/hide_status_bar_info_tablet_ui_summary")
                preference.setAttribute("android:defaultValue", "false")
                preference.setAttribute("android:dependency", "force_tablet_ui")
                // Keep the control discoverable: it belongs immediately after
                // Force Tablet Mode, not at the end of the Accessibility screen.
                val next = forceTabletPreference.nextSibling
                if (next != null) root.insertBefore(preference, next) else root.appendChild(preference)
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
        // Keep Chromium's FullscreenHtmlApiHandlerCompat as the single visibility
        // authority. Do not inject synthetic Activity lifecycle methods: those proved
        // unsafe on the target DEX and duplicate Chromium's fullscreen ownership.
        val authorityCallback = windowInsetsPolicyFingerprint.method
        val implementation = authorityCallback.implementation
            ?: throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.onApplyWindowInsets has no implementation"
            )

        val localRegisters = implementation.registerCount - 3
        if (localRegisters < 2) {
            throw IllegalStateException(
                "FullscreenHtmlApiHandlerCompat.onApplyWindowInsets requires 2 local registers; found $localRegisters"
            )
        }

        val authorityClass = authorityCallback.definingClass

        // The policy stays physically in FullscreenHtmlApiHandlerCompat.
        // The compositor trigger jumps into this callback using the structurally
        // discovered authority object, so nt2/CVH never become visibility writers.
        // The original callback then continues normally and returns its WindowInsets.
        authorityCallback.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lmg5;->h()Z
                move-result v0
                if-nez v0, :morphe_status_policy_done

                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "$DEFAULT_PREFERENCES"
                const/4 p1, 0x0
                invoke-virtual {v0, v1, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0

                const-string v1, "$HIDE_STATUS_BAR_PREF"
                invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v1
                if-eqz v1, :morphe_status_show

                const-string v1, "force_tablet_ui"
                invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v1
                if-eqz v1, :morphe_status_show

                invoke-virtual {p0}, $authorityClass->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->hide(I)V
                goto :morphe_status_policy_done

                :morphe_status_show
                invoke-virtual {p0}, $authorityClass->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->show(I)V

                :morphe_status_policy_done
            """
        )

        // Reapply through Chromium's existing focus lifecycle instead of becoming a
        // second status-bar writer. On focus gain this only asks Android to dispatch
        // insets again; ng5.onApplyWindowInsets remains the sole hide/show authority.
        val focusMethod = fullscreenWindowFocusFingerprint.method
        val focusImplementation = focusMethod.implementation
            ?: throw IllegalStateException(
                "FullscreenHtmlApiHandlerBase window-focus method has no implementation"
            )
        val focusLocalRegisters =
            focusImplementation.registerCount - focusMethod.parameters.size - 1
        if (focusMethod.parameters.lastOrNull() != "Z" || focusLocalRegisters < 1) {
            throw IllegalStateException(
                "Fullscreen window-focus bridge shape changed: params=" +
                    focusMethod.parameters + ", locals=" + focusLocalRegisters
            )
        }

        focusMethod.addInstructionsWithLabels(
            0,
            """
                if-eqz p2, :morphe_focus_reapply_done
                iget-object v0, p0, Lmg5;->r:Ld52;
                invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                if-eqz v0, :morphe_focus_reapply_done
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v0
                if-eqz v0, :morphe_focus_reapply_done
                invoke-virtual {v0}, Landroid/view/View;->requestApplyInsets()V
                :morphe_focus_reapply_done
                nop
            """
        )

        // CompositorViewHolder/nt2 remains untouched and never writes visibility.
    }}
