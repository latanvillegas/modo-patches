package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import org.w3c.dom.Element

private const val CROMITE_VERSION = "153.0.8010.37"
private const val STATUS_PREF = "hide_status_bar_info_tablet_ui"

private val windowInsetsPolicyFingerprint = Fingerprint(
    definingClass = "Lng5;",
    name = "onApplyWindowInsets",
    returnType = "Landroid/view/WindowInsets;",
    parameters = listOf("Landroid/view/View;", "Landroid/view/WindowInsets;"),
)

private val activityWindowFocusFingerprint = Fingerprint(
    definingClass = "Lpc0;",
    name = "onWindowFocusChanged",
    returnType = "V",
    parameters = listOf("Z"),
)

private val hideStatusBarPreferenceResourcesPatch = resourcePatch(
    description = "Adds the original tablet status-bar option to Accessibility and UI.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    execute {
        var found = false
        val xmlFiles = get("res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.parentFile.name.startsWith("xml") }
            .toList()
        for (file in xmlFiles) {
            if (found) break
            val path = file.relativeTo(get("")).invariantSeparatorsPath
            document(path).use { doc ->
                val elements = doc.getElementsByTagName("*")
                var tablet: Element? = null
                var exists = false
                for (i in 0 until elements.length) {
                    val element = elements.item(i) as? Element ?: continue
                    if (element.getAttribute("android:key") == "force_tablet_ui") tablet = element
                    if (element.getAttribute("android:key") == STATUS_PREF) exists = true
                }
                if (tablet != null) {
                    val anchor = tablet!!
                    if (!exists) {
                        val pref = doc.createElement("org.chromium.components.browser_ui.settings.ChromeSwitchPreference")
                        pref.setAttribute("android:key", STATUS_PREF)
                        pref.setAttribute("android:title", "@string/hide_status_bar_info_tablet_ui_title")
                        pref.setAttribute("android:summary", "@string/hide_status_bar_info_tablet_ui_summary")
                        pref.setAttribute("android:defaultValue", "false")
                        pref.setAttribute("android:dependency", "force_tablet_ui")
                        val parent = anchor.parentNode
                        if (anchor.nextSibling != null) parent.insertBefore(pref, anchor.nextSibling)
                        else parent.appendChild(pref)
                    }
                    found = true
                }
            }
        }
        check(found) { "Accessibility XML containing force_tablet_ui was not found" }

        fun addString(path: String, key: String, value: String) {
            if (!get(path).isFile) return
            document(path).use { doc ->
                val root = doc.documentElement
                val strings = root.getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val item = strings.item(i) as? Element ?: continue
                    if (item.getAttribute("name") == key) return@use
                }
                val item = doc.createElement("string")
                item.setAttribute("name", key)
                item.textContent = value
                root.appendChild(item)
            }
        }
        addString("res/values/strings.xml", "hide_status_bar_info_tablet_ui_title", "Hide status bar information in tablet mode")
        addString("res/values/strings.xml", "hide_status_bar_info_tablet_ui_summary", "Hides the complete Android status bar (clock, battery, signal and notifications) when Force Tablet Mode is active. Applied after restarting Cromite.")
        addString("res/values-es/strings.xml", "hide_status_bar_info_tablet_ui_title", "Ocultar información de la barra de estado en modo tableta")
        addString("res/values-es/strings.xml", "hide_status_bar_info_tablet_ui_summary", "Oculta la barra de estado completa de Android (hora, batería, señal y notificaciones) cuando Force Tablet Mode está activo. Se aplica después de reiniciar Cromite.")
    }
}

@Suppress("unused")
val hideStatusBarPatch = bytecodePatch(
    name = "Hide status bar in main browser (tablet mode)",
    description = "Adds a tablet-only Accessibility switch and hides the status bar on focus when both tablet mode and the switch are enabled.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    dependsOn(hideStatusBarPreferenceResourcesPatch)
    execute {
        val insetsMethod = windowInsetsPolicyFingerprint.method
        val insetsImpl = insetsMethod.implementation
            ?: error("FullscreenHtmlApiHandlerCompat.onApplyWindowInsets has no implementation")
        val localRegisters = insetsImpl.registerCount - 3
        check(localRegisters >= 2) {
            "FullscreenHtmlApiHandlerCompat.onApplyWindowInsets requires 2 local registers; found ${localRegisters}"
        }
        val insetsClass = insetsMethod.definingClass
        insetsMethod.addInstructions(0, """
            invoke-virtual {p0}, Lmg5;->h()Z
            move-result v0
            if-nez v0, :morphe_status_policy_done
            invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
            move-result-object v0
            const-string v1, "org.cromite.cromite_preferences"
            const/4 p1, 0x0
            invoke-virtual {v0, v1, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            const-string v1, "hide_status_bar_info_tablet_ui"
            invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
            move-result v1
            if-eqz v1, :morphe_status_show
            const-string v1, "force_tablet_ui"
            invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
            move-result v1
            if-eqz v1, :morphe_status_show
            invoke-virtual {p0}, $insetsClass->E()Llng;
            move-result-object v0
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Llng;->hide(I)V
            goto :morphe_status_policy_done
            :morphe_status_show
            invoke-virtual {p0}, $insetsClass->E()Llng;
            move-result-object v0
            const/4 v1, 0x1
            invoke-virtual {v0, v1}, Llng;->show(I)V
            :morphe_status_policy_done
            nop
        """.trimIndent())

        val method = activityWindowFocusFingerprint.method
        val impl = method.implementation ?: error("pc0.onWindowFocusChanged has no implementation")
        check(impl.registerCount - method.parameters.size - 1 >= 2) {
            "pc0.onWindowFocusChanged requires at least two local registers"
        }
        method.addInstructions(0, """
            if-eqz p1, :morphe_activity_status_done
            const-string v0, "org.cromite.cromite_preferences"
            const/4 v1, 0x0
            invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences;->getAll()Ljava/util/Map;
            move-result-object v0
            const-string v1, "hide_status_bar_info_tablet_ui"
            invoke-interface {v0, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
            instance-of v1, v0, Ljava/lang/Boolean;
            if-eqz v1, :morphe_activity_status_done
            check-cast v0, Ljava/lang/Boolean;
            invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
            move-result v1
            if-eqz v1, :morphe_activity_status_done
            const-string v0, "org.cromite.cromite_preferences"
            const/4 v1, 0x0
            invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences;->getAll()Ljava/util/Map;
            move-result-object v0
            const-string v1, "force_tablet_ui"
            invoke-interface {v0, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
            instance-of v1, v0, Ljava/lang/Boolean;
            if-eqz v1, :morphe_activity_status_done
            check-cast v0, Ljava/lang/Boolean;
            invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
            move-result v1
            if-eqz v1, :morphe_activity_status_done
            invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
            move-result-object v0
            invoke-virtual {v0}, Landroid/view/Window;->getInsetsController()Landroid/view/WindowInsetsController;
            move-result-object v0
            if-eqz v0, :morphe_activity_status_done
            const/4 v1, 0x1
            invoke-interface {v0, v1}, Landroid/view/WindowInsetsController;->hide(I)V
            :morphe_activity_status_done
            nop
        """.trimIndent())
    }
}
