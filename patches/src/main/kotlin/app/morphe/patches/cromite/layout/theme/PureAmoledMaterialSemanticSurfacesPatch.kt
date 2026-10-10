package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.Locale

/**
 * Reconstructed from PureAmoledMaterialSemanticSurfacesPatchKt.class
 * in the original patches-1.50.0.mpp.
 *
 * Only semantic SURFACE roles are changed. Foreground roles
 * containing "on_surface" or "onsurface" are intentionally excluded.
 */
@Suppress("unused")
val pureAmoledMaterialSemanticSurfacesPatch = resourcePatch(
    name = "AMOLED - Material semantic surfaces",
    description = "Applies Pure Black / Pure White only to verified Material surface semantic resources while preserving foreground roles.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val color = when (browserTheme.value ?: "device") {
            "device" -> return@execute
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }

        val exactNames = setOf(
            "color_surface",
            "color_surface_dark",
            "color_surface_bright",
            "color_surface_bright_dark",
            "color_surface_dim",
            "color_surface_dim_dark",
            "color_surface_container_lowest",
            "color_surface_container_lowest_dark",
            "color_surface_container_low",
            "color_surface_container_low_dark",
            "color_surface_container",
            "color_surface_container_dark",
            "color_surface_container_high",
            "color_surface_container_high_dark",
            "color_surface_container_highest",
            "color_surface_container_highest_dark",
            "gm3_baseline_surface_dark",
            "gm3_baseline_surface_bright_dark",
            "gm3_baseline_surface_dim_dark",
            "gm3_baseline_surface_container_lowest_dark",
            "gm3_baseline_surface_container_low_dark",
            "gm3_baseline_surface_container_dark",
            "gm3_baseline_surface_container_high_dark",
            "gm3_baseline_surface_container_highest_dark",
        )

        fun isMaterialSurface(name: String): Boolean {
            val lower = name.lowercase(Locale.ROOT)
            if ("on_surface" in lower || "onsurface" in lower) return false
            return lower in exactNames ||
                lower.startsWith("gm3_baseline_surface") ||
                lower.startsWith("m3_sys_color_light_surface") ||
                lower.startsWith("m3_sys_color_dark_surface") ||
                lower.startsWith("m3_sys_color_dynamic_light_surface") ||
                lower.startsWith("m3_sys_color_dynamic_dark_surface") ||
                (lower.startsWith("material_dynamic_neutral") && "surface" in lower)
        }

        var changed = 0
        get("res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            .orEmpty()
            .forEach { directory ->
                directory.listFiles()
                    ?.filter { it.isFile && it.extension == "xml" }
                    .orEmpty()
                    .forEach { file ->
                        document("res/${directory.name}/${file.name}").use { xml ->
                            val elements = xml.getElementsByTagName("*")
                            for (i in 0 until elements.length) {
                                val element = elements.item(i) as? Element ?: continue
                                val name = element.getAttribute("name")
                                if (!isMaterialSurface(name)) continue
                                val type = element.getAttribute("type")
                                if (element.tagName == "color" || element.tagName == "macro" ||
                                    (element.tagName == "item" && type == "color")
                                ) {
                                    element.textContent = color
                                    changed++
                                }
                            }
                        }
                    }
            }
        check(changed > 0) {
            "AMOLED Material semantic surfaces verification failed for Cromite 153.0.8010.37: verified surface roles were not found."
        }
    }
}
