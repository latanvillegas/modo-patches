package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val CROMITE_VERSION = "153.0.8010.37"
private const val SEMANTIC_COLOR_UTILS = "Lpac;"
private const val CHROME_COLORS = "Lq72;"
private const val CONTEXT = "Landroid/content/Context;"

/*
 * Verified against arm64_ChromePublic.apk.mapping shipped with Cromite 153.0.8010.37:
 *
 * SemanticColorUtils -> pac
 *   c = getColorSurface(Context)
 *   d = getColorSurfaceBright(Context)
 *   e = getColorSurfaceContainer(Context)
 *   f = getColorSurfaceContainerHighest(Context)
 *   g = getColorSurfaceContainerLow(Context)
 *   h = getDefaultBgColor(Context)
 *   l = getSettingsBackgroundColor(Context)
 *
 * ChromeColors -> q72
 *   b = getPrimaryBackgroundColor(Context, boolean)
 *
 * Do NOT patch pac.i/pac.j/pac.k: those are icon/text/link colors.
 */
private fun semanticSurfaceFingerprint(name: String) = Fingerprint(
    definingClass = SEMANTIC_COLOR_UTILS,
    name = name,
    returnType = "I",
    parameters = listOf(CONTEXT),
)

private val colorSurfaceFingerprint = semanticSurfaceFingerprint("c")
private val colorSurfaceBrightFingerprint = semanticSurfaceFingerprint("d")
private val colorSurfaceContainerFingerprint = semanticSurfaceFingerprint("e")
private val colorSurfaceContainerHighestFingerprint = semanticSurfaceFingerprint("f")
private val colorSurfaceContainerLowFingerprint = semanticSurfaceFingerprint("g")
private val defaultBgColorFingerprint = semanticSurfaceFingerprint("h")
private val settingsBackgroundColorFingerprint = semanticSurfaceFingerprint("l")

private val primaryBackgroundColorFingerprint = Fingerprint(
    definingClass = CHROME_COLORS,
    name = "b",
    returnType = "I",
    parameters = listOf(CONTEXT, "Z"),
)

/** Resource-side fallback for compiled night resources that do not pass through SemanticColorUtils. */
private val pureAmoledResourcesPatch = resourcePatch(
    name = "Pure AMOLED resources",
    description = "Internal resource component for Cromite Pure AMOLED.",
    use = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))

    execute {
        val candidateFiles = listOf(
            "bottom_sheet_bg_color.xml",
            "context_menu_header_circle_bg_color.xml",
            "dialog_bg_color.xml",
            "distilled_page_pref_background_color.xml",
            "fake_search_box_base_color.xml",
            "home_surface_background_color.xml",
            "home_surface_ui_background_color.xml",
            "hub_search_box_bg_color.xml",
            "interactable_chip_bg_color.xml",
        )

        var filesPatched = 0
        var valuesPatched = 0

        fun blacken(element: Element) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                    attribute.nodeValue = "#FF000000"
                    valuesPatched++
                }
            }

            val children = element.childNodes
            var hasElementChildren = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    blacken(child as Element)
                }
            }
            if (!hasElementChildren && element.textContent.trim().startsWith("#")) {
                element.textContent = "#FF000000"
                valuesPatched++
            }
        }

        candidateFiles.forEach { fileName ->
            runCatching {
                document("res/color-night-v8/$fileName").use { document ->
                    val before = valuesPatched
                    blacken(document.documentElement)
                    if (valuesPatched > before) filesPatched++
                }
            }
        }

        if (filesPatched == 0) {
            throw IllegalStateException("No verified Cromite 153 night resources could be patched.")
        }
    }
}

/**
 * Forces the runtime Chromium/Material surface roles used by Cromite 153 to true OLED black.
 *
 * This is intentionally version-specific. The obfuscated class/method mapping above was taken from
 * the mapping file belonging to the exact supported ARM64 build, rather than guessed from upstream
 * Java names. Foreground roles (text, icons, links and accents) are deliberately untouched.
 *
 * The runtime overrides are intended for Cromite's dark theme. If the browser is switched to a
 * light theme, these background methods remain black while light-theme foreground colors may be
 * unsuitable; use this patch with Cromite dark mode enabled.
 */
@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "Pure AMOLED theme",
    description = "Forces Cromite 153 runtime UI surfaces to true black (#000000). Use with dark mode.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    dependsOn(pureAmoledResourcesPatch)

    execute {
        val targets = listOf(
            colorSurfaceFingerprint,
            colorSurfaceBrightFingerprint,
            colorSurfaceContainerFingerprint,
            colorSurfaceContainerHighestFingerprint,
            colorSurfaceContainerLowFingerprint,
            defaultBgColorFingerprint,
            settingsBackgroundColorFingerprint,
            primaryBackgroundColorFingerprint,
        )

        targets.forEach { fingerprint ->
            // ARGB #FF000000. Every verified target already has at least one local register in the
            // original tiny resolver body, so v0 can safely be reused for the immediate return.
            fingerprint.method.addInstructions(
                0,
                """
                    const v0, -0x1000000
                    return v0
                """
            )
        }
    }
}
