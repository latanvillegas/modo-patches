package app.morphe.patches.movie.misc.quality

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Preparation for a separate, opt-in quality selector.
 *
 * The control is deliberately GONE until the runtime track-selection listener
 * is implemented. This prevents shipping a visible button that cannot work.
 * No existing playback control or battery view is modified.
 */
@Suppress("unused")
val movieVideoQualitySelectorPatch = resourcePatch(
    name = "Movie - Video Quality Selector (development)",
    description = "Prepares an independent quality control; hidden until real ExoPlayer track switching is implemented.",
    default = false,
) {
    compatibleWith("com.movievn.cinevi"("4.0.0"))

    execute {
        document("res/layout/exo_playback_top_my_new.xml").use { doc ->
            val controls = doc.getElementsByTagName("LinearLayout")
            var audio: Element? = null
            for (index in 0 until controls.length) {
                val element = controls.item(index) as? Element ?: continue
                if (element.getAttribute("android:id") == "@id/ll_audio_subtitle") {
                    audio = element
                    break
                }
            }
            val anchor = checkNotNull(audio) {
                "Movie: audio/subtitle control missing from fullscreen player"
            }
            val parent = anchor.parentNode as? Element
                ?: error("Movie: invalid player controls container")
            val siblings = parent.childNodes
            var speedFound = false
            for (index in 0 until siblings.length) {
                val element = siblings.item(index) as? Element ?: continue
                if (element.getAttribute("android:id") == "@id/rl_speed") speedFound = true
            }
            check(speedFound) { "Movie: speed control missing from fullscreen player" }

            val quality = doc.createElement("TextView")
            quality.setAttribute("android:tag", "morphe_movie_video_quality")
            quality.setAttribute("android:text", "Calidad")
            quality.setAttribute("android:textSize", "@dimen/sp_12")
            quality.setAttribute("android:textColor", "@color/white")
            quality.setAttribute("android:gravity", "center")
            quality.setAttribute("android:background", "@drawable/detail_player_view_bot_button")
            quality.setAttribute("android:paddingLeft", "@dimen/dp_5")
            quality.setAttribute("android:paddingRight", "@dimen/dp_5")
            quality.setAttribute("android:layout_marginLeft", "@dimen/dp_4")
            quality.setAttribute("android:layout_width", "wrap_content")
            quality.setAttribute("android:layout_height", "match_parent")
            quality.setAttribute("android:visibility", "gone")
            parent.insertBefore(quality, anchor.nextSibling)
        }
    }
}
