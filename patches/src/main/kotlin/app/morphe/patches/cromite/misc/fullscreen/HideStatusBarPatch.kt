package app.morphe.patches.cromite.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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
 * The compositor's Lzl1 field is discovered from the concrete insets listener
 * callback itself, rather than from p(). The installed Cromite bytecode proves
 * nt2 reads CVH and immediately invokes the no-arg reconciliation method, while
 * p() is free to evolve/in-line its Window/Insets implementation.
 */
/**
 * Any CompositorViewHolder method that reads its browser-controls Lzl1 owner.
 * The matched FieldReference is used to recover CVH -> zl1 without relying on
 * the obfuscated field name.
 */
private val compositorOwnerFieldFingerprint = Fingerprint(
    definingClass = COMPOSITOR_VIEW_HOLDER,
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Lzl1;"),
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

        // The policy stays physically in FullscreenHtmlApiHandlerCompat.
        // The compositor trigger jumps into this callback using the structurally
        // discovered authority object, so nt2/CVH never become visibility writers.
        // The original callback then continues normally and returns its WindowInsets.
        authorityCallback.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lmg5;->h()Z
                move-result v0
                if-eqz v0, :morphe_status_not_persistent
                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=1 action=skip"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                goto :morphe_status_policy_done

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
                if-eqz v0, :morphe_status_show

                sget-boolean v0, Lorg/chromium/ui/base/DeviceFormFactor;->a:Z
                if-eqz v0, :morphe_status_show

                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=0 tablet=1 pref=1 action=hide statusBars"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                invoke-virtual {p0}, $authorityClass->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->hide(I)V
                goto :morphe_status_policy_done

                :morphe_status_show
                const-string v0, "MorpheStatusBar"
                const-string v1, "persistent=0 action=show statusBars"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                invoke-virtual {p0}, $authorityClass->E()Llng;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Llng;->show(I)V

                :morphe_status_policy_done
            """
        )

        // The listener class is nt2, so classDef.fields here would inspect nt2,
        // not CompositorViewHolder. Recover the actual CVH class from the listener's
        // field reference and resolve it through the patch context.
        val cvhReference = compositorInsetsListenerFingerprint.instructionMatches
            .first { match ->
                val reference =
                    (match.instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.type == COMPOSITOR_VIEW_HOLDER
            }
            .let { (it.instruction as ReferenceInstruction).reference as FieldReference }

        // Resolve CVH -> zl1 from a real CVH method match instead of nt2.classDef.
        // The fingerprint is declared at patch scope so the patcher resolves it
        // normally before execute; no manual context/match invocation is required.
        val cvhField = compositorOwnerFieldFingerprint.instructionMatches
            .mapNotNull { match ->
                (match.instruction as? ReferenceInstruction)?.reference as? FieldReference
            }
            .firstOrNull {
                it.definingClass == cvhReference.type && it.type == "Lzl1;"
            }
            ?: throw IllegalStateException(
                "CompositorViewHolder -> Lzl1 field access was not found"
            )

        val authorityField = fullscreenAuthorityOwnerFingerprint.instructionMatches
            .first { match ->
                val reference = (match.instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.type == "Lmg5;"
            }
            .let { (it.instruction as ReferenceInstruction).reference as FieldReference }

        fun fieldSmali(reference: com.android.tools.smali.dexlib2.iface.reference.FieldReference) =
            "${reference.definingClass}->${reference.name}:${reference.type}"

        // nt2 is only a lifecycle trigger. It does not hide/show bars itself.
        // It walks the structurally discovered CVH -> zl1 -> mg5 ownership chain,
        // casts to the already verified compat authority and calls the single helper.
        val trigger = compositorInsetsListenerFingerprint.method
        val returnIndex = trigger.implementation?.instructions
            ?.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
            ?.takeIf { it >= 0 }
            ?: throw IllegalStateException(
                "Compositor OnApplyWindowInsetsListener has no RETURN_OBJECT"
            )

        // At this point the original listener has already converted p0 from nt2
        // to CompositorViewHolder and called p(). p1 is dead (the original code
        // overwrote it with CVH.A0), while p2 still holds the WindowInsets return
        // value. Reuse p1 for the ownership walk and keep p0 as the View argument.
        trigger.addInstructionsWithLabels(
            returnIndex,
            """
                iget-object p1, p0, ${fieldSmali(cvhField)}
                if-eqz p1, :morphe_compositor_policy_done
                iget-object p1, p1, ${fieldSmali(authorityField)}
                if-eqz p1, :morphe_compositor_policy_done
                check-cast p1, $authorityClass
                invoke-virtual {p1, p0, p2}, $authorityClass->onApplyWindowInsets(Landroid/view/View;Landroid/view/WindowInsets;)Landroid/view/WindowInsets;
                move-result-object p2
                :morphe_compositor_policy_done
                nop
            """,
        )
    }}
