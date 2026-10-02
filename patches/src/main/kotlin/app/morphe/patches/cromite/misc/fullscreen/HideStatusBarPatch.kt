package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
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

private const val COMPOSITOR_VIEW_HOLDER =
    "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;"

/**
 * Stable compositor callback. It is identified by behavior, not by the R8
 * method name: it reads the browser-controls object, inspects DecorView insets
 * and schedules its reconciliation with View.postDelayed().
 */
private val compositorInsetsReconcileFingerprint = Fingerprint(
    definingClass = COMPOSITOR_VIEW_HOLDER,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Lzl1;"),
        methodCall(smali = "Landroid/view/Window;->getDecorView()Landroid/view/View;"),
        methodCall(smali = "Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z"),
    ),
)

/**
 * The real View.OnApplyWindowInsetsListener installed by CompositorViewHolder.
 * No obfuscated class/method name is used: the callback is matched by its
 * signature, its CVH field and the CVH no-arg reconciliation call.
 */
private val compositorInsetsListenerFingerprint = Fingerprint(
    returnType = "Landroid/view/WindowInsets;",
    parameters = listOf("Landroid/view/View;", "Landroid/view/WindowInsets;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = COMPOSITOR_VIEW_HOLDER),
        methodCall(definingClass = COMPOSITOR_VIEW_HOLDER, returnType = "V", parameters = listOf()),
    ),
)

/**
 * Finds zl1.w(...) structurally and, from it, the field that owns the
 * FullscreenHtmlApiHandlerBase instance. This deliberately avoids field O.
 */
private val fullscreenAuthorityOwnerFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lorg/chromium/chrome/browser/toolbar/top/ToolbarControlContainer;",
        "Lorg/chromium/chrome/browser/ActivityTabProvider;",
        "Lwde;",
        "I",
    ),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Lmg5;"),
        methodCall(
            definingClass = "Lorg/chromium/base/ApplicationStatus;",
            name = "h",
            returnType = "V",
        ),
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
        val helperName = "morpheApplyTabletStatusBarPolicy"

        // One policy implementation, physically owned by FullscreenHtmlApiHandlerCompat.
        // Both Chromium's own callback and the compositor callback call this helper.
        authorityCallback.classDef.methods.add(
            ImmutableMethod(
                authorityClass,
                helperName,
                listOf("Landroid/view/View;"),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                authorityCallback.annotations,
                null,
                MutableMethodImplementation(4),
            ).toMutable().apply {
                addInstructions(
                    0,
                    """
                        invoke-virtual {p0}, Lmg5;->h()Z
                        move-result v0
                        if-eqz v0, :morphe_status_not_persistent
                        const-string v0, "MorpheStatusBar"
                        const-string v1, "persistent=1 action=skip"
                        invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                        return-void

                        :morphe_status_not_persistent
                        invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                        move-result-object v0
                        const-string v1, "$DEFAULT_PREFERENCES"
                        const/4 v2, 0x0
                        invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                        move-result-object v0
                        const-string v1, "$HIDE_STATUS_BAR_PREF"
                        invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                        move-result v0
                        if-eqz v0, :morphe_status_show_pref

                        sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                        if-eqz v0, :morphe_status_show_phone

                        const-string v0, "MorpheStatusBar"
                        const-string v1, "persistent=0 tablet=1 pref=1 action=hide statusBars"
                        invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                        invoke-virtual {p0}, $authorityClass->E()Llng;
                        move-result-object v0
                        const/4 v1, 0x1
                        invoke-virtual {v0, v1}, Llng;->hide(I)V
                        return-void

                        :morphe_status_show_pref
                        const-string v0, "MorpheStatusBar"
                        const-string v1, "persistent=0 pref=0 action=show statusBars"
                        invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                        goto :morphe_status_show

                        :morphe_status_show_phone
                        const-string v0, "MorpheStatusBar"
                        const-string v1, "persistent=0 tablet=0 action=show statusBars"
                        invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

                        :morphe_status_show
                        invoke-virtual {p0}, $authorityClass->E()Llng;
                        move-result-object v0
                        const/4 v1, 0x1
                        invoke-virtual {v0, v1}, Llng;->show(I)V
                        return-void
                    """
                )
            }
        )

        // Chromium's own FullscreenHtmlApiHandlerCompat callback also uses the
        // same helper. The original callback remains intact after this call.
        authorityCallback.addInstructions(
            0,
            """
                invoke-virtual {p0, p1}, $authorityClass->$helperName(Landroid/view/View;)V
            """
        )

        val cvhField = compositorInsetsReconcileFingerprint.instructionMatches
            .first { match ->
                val reference = (match.instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.type == "Lzl1;"
            }
            .let { (it.instruction as ReferenceInstruction).reference as FieldReference }

        val authorityField = fullscreenAuthorityOwnerFingerprint.instructionMatches
            .first { match ->
                val reference = (match.instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.type == "Lmg5;"
            }
            .let { (it.instruction as ReferenceInstruction).reference as FieldReference }

        fun fieldSmali(reference: FieldReference) =
            "${reference.definingClass}->${reference.name}:${reference.type}"

        // nt2 is only a lifecycle trigger. It does not hide/show bars itself.
        // It walks the structurally discovered CVH -> zl1 -> mg5 ownership chain,
        // casts to the already verified compat authority and calls the single helper.
        val trigger = compositorInsetsListenerFingerprint.method
        trigger.addInstructionsWithLabels(
            0,
            """
                iget-object p0, p0, ${fieldSmali(
                    (compositorInsetsListenerFingerprint.instructionMatches.first().instruction as ReferenceInstruction)
                        .reference as FieldReference
                )}
                iget-object p0, p0, ${fieldSmali(cvhField)}
                if-eqz p0, :morphe_compositor_policy_done
                iget-object p0, p0, ${fieldSmali(authorityField)}
                if-eqz p0, :morphe_compositor_policy_done
                check-cast p0, $authorityClass
                invoke-virtual {p0, p1}, $authorityClass->$helperName(Landroid/view/View;)V
                :morphe_compositor_policy_done
            """,
        )
    }}
