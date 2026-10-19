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

        // Top offsets in secondary tabs are independent of StatusBarHeightView.
        // Match the exact original values to avoid silently changing unrelated layouts.
        val secondaryTopOffsets = listOf(
            Triple("res/layout/fragment_category.xml", "RelativeLayout", "@dimen/dp_35"),
            Triple("res/layout/fragment_rank_new.xml", "TextView", "@dimen/dp_40"),
            Triple("res/layout/fragment_mine.xml", "androidx.constraintlayout.widget.ConstraintLayout", "@dimen/dp_50"),
        )
        for ((path, tag, originalMargin) in secondaryTopOffsets) {
            document(path).use { doc ->
                val nodes = doc.getElementsByTagName(tag)
                var matches = 0
                for (index in 0 until nodes.length) {
                    val view = nodes.item(index) as? Element ?: continue
                    if (view.getAttribute("android:layout_marginTop") != originalMargin) continue
                    view.setAttribute("android:layout_marginTop", "0.0dp")
                    matches++
                }
                check(matches == 1) {
                    "Movie: expected one secondary tab top offset in $path; found $matches"
                }
            }
        }

        // These gradient/header placeholders retain 77dp even after the status bar is hidden.
        // Keep a compact 40dp header region so tab controls remain below the title.
        for (path in listOf(
            "res/layout/fragment_rank_new.xml",
            "res/layout/fragment_category.xml",
        )) {
            document(path).use { doc ->
                val views = doc.getElementsByTagName("View")
                var matches = 0
                for (index in 0 until views.length) {
                    val view = views.item(index) as? Element ?: continue
                    if (view.getAttribute("android:id") != "@id/view_top") continue
                    check(view.getAttribute("android:layout_height") == "@dimen/dp_77") {
                        "Movie: unexpected top placeholder height in $path"
                    }
                    view.setAttribute("android:layout_height", "40.0dp")
                    matches++
                }
                check(matches == 1) { "Movie: expected one top placeholder in $path; found $matches" }
            }
        }

        // Category header needs a small top safe area for its title/search control.
        // Its 40dp placeholder remains unchanged, so filters and posters do not shift.
        document("res/layout/fragment_category.xml").use { doc ->
            val layouts = doc.getElementsByTagName("RelativeLayout")
            var matches = 0
            for (index in 0 until layouts.length) {
                val layout = layouts.item(index) as? Element ?: continue
                if (layout.getAttribute("android:layout_marginTop") != "0.0dp") continue
                if (layout.getElementsByTagName("com.google.android.material.tabs.TabLayout").length != 1) continue
                layout.setAttribute("android:layout_marginTop", "8.0dp")
                matches++
            }
            check(matches == 1) { "Movie: expected one category header; found $matches" }
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
