package app.morphe.patches.movie.misc.fullscreen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import org.w3c.dom.Element

private object MovieStatusBarSpacerMeasureFingerprint : Fingerprint(
    definingClass = "Lcom/mgs/carparking/libutils/statusbar/StatusBarHeightView;",
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(methodCall(name = "setMeasuredDimension", returnType = "V")),
)

/**
 * The custom spacer overrides XML height during onMeasure. Force the height
 * argument to zero immediately before setMeasuredDimension(width, height).
 */
private val movieZeroStatusBarSpacerMeasurePatch = bytecodePatch {
    compatibleWith("com.movievn.cinevi"("4.0.0"))

    execute {
        MovieStatusBarSpacerMeasureFingerprint.method.apply {
            val measureIndex = indexOfFirstInstructionOrThrow(
                methodCall(
                    name = "setMeasuredDimension",
                    returnType = "V",
                    parameters = listOf("I", "I"),
                )
            )
            val call = getInstruction<FiveRegisterInstruction>(measureIndex)
            check(call.registerCount == 3) {
                "Movie: unexpected setMeasuredDimension argument count"
            }
            // Third invoke argument is measured height; registerE in format 35c.
            addInstruction(measureIndex, "const/4 v${call.registerE}, 0x0")
        }
    }
}

/**
 * Removes Movie's own status-bar-height spacers and the home header's 38dp top margin.
 * Does not change system WindowInsets or the navigation bar.
 */
@Suppress("unused")
val movieRemoveStatusBarInsetsPatch = resourcePatch(
    name = "Movie - Remove status bar spacing",
    description = "Removes Movie-owned status bar spacers, including their runtime measured height, and home header top spacing.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))
    dependsOn(movieZeroStatusBarSpacerMeasurePatch)

    execute {
        val spacerTag = "com.mgs.carparking.libutils.statusbar.StatusBarHeightView"
        val spacerLayouts = listOf(
            "res/layout/fragment_spiel.xml",
            "res/layout/action_bar_header.xml",
            "res/layout/activity_web.xml",
            "res/layout/activity_collection.xml",
        )

        for (path in spacerLayouts) {
            document(path).use { doc ->
                val nodes = doc.getElementsByTagName(spacerTag)
                check(nodes.length > 0) { "Movie: status bar spacer missing in $path" }
                for (index in 0 until nodes.length) {
                    val view = nodes.item(index) as? Element
                        ?: error("Movie: invalid status bar spacer in $path")
                    check(view.getAttribute("android:layout_height") == "wrap_content") {
                        "Movie: unexpected status bar spacer height in $path"
                    }
                    view.setAttribute("android:layout_height", "0.0dp")
                }
            }
        }

        document("res/layout/fragment_home.xml").use { doc ->
            val nodes = doc.getElementsByTagName("RelativeLayout")
            var matches = 0
            for (index in 0 until nodes.length) {
                val view = nodes.item(index) as? Element ?: continue
                if (view.getAttribute("android:id") != "@id/rl_top") continue
                check(view.getAttribute("android:layout_marginTop") == "@dimen/dp_38") {
                    "Movie: unexpected home header top margin"
                }
                view.setAttribute("android:layout_marginTop", "0.0dp")
                matches++
            }
            check(matches == 1) { "Movie: expected one home header, found $matches" }
        }
    }
}
