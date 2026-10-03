package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node
import app.morphe.util.findFreeRegister
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val NTP_LAYOUT = "Lorg/chromium/chrome/browser/ntp/NewTabPageLayout;"
private const val NTP_TILE_FACTORY = "Lc7f;"
private const val SUGGESTIONS_TILE_VIEW =
    "Lorg/chromium/chrome/browser/suggestions/tile/SuggestionsTileView;"
private const val NTP_VERSION = "153.0.8010.37"

private val pureAmoledNtpTileResourcesPatch = resourcePatch(
    description = "Internal resources for the New Tab Page tile icon background only.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        val targets = listOf(
            "res/color/tile_view_icon_background_color.xml" to
                if (selectedTheme == "black") "#FF000000" else "#FFFFFFFF",
            "res/color-night-v8/tile_view_icon_background_color.xml" to
                if (selectedTheme == "white") "#FFFFFFFF" else "#FF000000",
        )

        var verifiedHits = 0

        fun recolor(element: Element, color: String) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                    attribute.nodeValue = color
                }
            }

            val children = element.childNodes
            var hasElementChildren = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    recolor(child as Element, color)
                }
            }

            if (!hasElementChildren) {
                element.textContent = color
            }
        }

        targets.forEach { (path, color) ->
            runCatching {
                document(path).use { document ->
                    recolor(document.documentElement, color)
                    verifiedHits++
                }
            }
        }

        if (verifiedHits == 0) {
            throw PatchException(
                "AMOLED NTP tile resource verification failed for Cromite $NTP_VERSION: " +
                    "tile_view_icon_background_color.xml was not found.",
            )
        }
    }
}

private val ntpLayoutInflateFingerprint = Fingerprint(
    definingClass = NTP_LAYOUT,
    name = "onFinishInflate",
    returnType = "V",
    parameters = emptyList(),
)

private val setBackgroundColorCall = methodCall(
    definingClass = "Landroid/view/View;",
    name = "setBackgroundColor",
    returnType = "V",
    parameters = listOf("I"),
)

private val tileInflateVisibilityFingerprint = Fingerprint(
    definingClass = NTP_TILE_FACTORY,
    filters = listOf(
        checkCast(SUGGESTIONS_TILE_VIEW),
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setVisibility",
            returnType = "V",
            parameters = listOf("I"),
        ),
    ),
)

private val tileInflateSuggestionFingerprint = Fingerprint(
    definingClass = NTP_TILE_FACTORY,
    filters = listOf(
        checkCast(SUGGESTIONS_TILE_VIEW),
        methodCall(
            definingClass = "Ljava/lang/Object;",
            name = "getClass",
            returnType = "Ljava/lang/Class;",
            parameters = emptyList(),
        ),
    ),
)

private fun tileSurfaceTintSmali(
    theme: String,
    viewRegister: Int,
    scratchRegister: Int,
): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"

    val colorSmali = when (theme) {
        "black" -> "const $scratch, -0x1000000"
        "white" -> "const $scratch, -0x1"
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
        """.trimIndent()
    }

    return """
        invoke-virtual {$view}, Landroid/view/View;->getBackground()Landroid/graphics/drawable/Drawable;
        move-result-object $scratch
        if-eqz $scratch, :amoled_ntp_tile_skip
        $colorSmali
        invoke-static {$scratch}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
        move-result-object $scratch
        invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundTintList(Landroid/content/res/ColorStateList;)V
        :amoled_ntp_tile_skip
        nop
    """
}

private fun linearNtpBackgroundSmali(
    theme: String,
    viewRegister: Int,
    scratchRegister: Int,
): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"

    return when (theme) {
        "black" -> """
            const $scratch, -0x1000000
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        "white" -> """
            const $scratch, -0x1
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
    }
}

@Suppress("unused")
val pureAmoledNtpPatch = bytecodePatch(
    name = "AMOLED - New Tab Page",
    description = "Applies Device default / Pure Black / Pure White to the verified NewTabPageLayout background and NTP tile icon background without recoloring favicons, WebContents, toolbar, Settings, or system bars.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    dependsOn(pureAmoledNtpTileResourcesPatch)
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        ntpLayoutInflateFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow(setBackgroundColorCall).forEach { index ->
                val invoke = getInstruction<FiveRegisterInstruction>(index)
                val viewRegister = invoke.registerC
                val colorRegister = invoke.registerD

                // Chromium has just consumed the original color register.
                // Reuse it linearly and immediately override the same View.
                // No labels, branches, or new branch targets are introduced.
                addInstructions(
                    index + 1,
                    linearNtpBackgroundSmali(selectedTheme, viewRegister, colorRegister),
                )
            }
        }

        // c7f->j resolves to 0x7f0e03aa (-y.xml) or 0x7f0e03ab (d3.xml).
        // Only -y.xml declares android:background (0x7f09069e -> res/9G.xml).
        // At the post-inflate point the first c7f path has already reused p0,
        // so background presence is the runtime structural discriminator:
        // -y.xml has the verified selector background; d3.xml has none.
        // Tinting preserves the selector, hover/focus states, shape and 8dp corners.
        listOf(
            tileInflateVisibilityFingerprint,
            tileInflateSuggestionFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                val castIndex = findInstructionIndicesReversedOrThrow(
                    checkCast(SUGGESTIONS_TILE_VIEW),
                ).single()
                val viewRegister =
                    getInstruction<OneRegisterInstruction>(castIndex).registerA
                val scratchRegister = findFreeRegister(castIndex + 1, viewRegister)

                addInstructionsWithLabels(
                    castIndex + 1,
                    tileSurfaceTintSmali(
                        selectedTheme,
                        viewRegister,
                        scratchRegister,
                    ),
                )
            }
        }
    }
}
