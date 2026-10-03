package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val MATERIAL_SURFACE_VERSION = "153.0.8010.37"
private const val MATERIAL_SURFACE_BLACK = "#FF000000"
private const val MATERIAL_SURFACE_WHITE = "#FFFFFFFF"

@Suppress("unused")
val pureAmoledMaterialSemanticSurfacesPatch = resourcePatch(
    name = "AMOLED - Material semantic surfaces",
    description = "Applies Pure Black / Pure White only to verified Material surface semantic resources while preserving foreground roles.",
) {
    compatibleWith("org.cromite.cromite"(MATERIAL_SURFACE_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute
        val target = when (selectedTheme) {
            "black" -> MATERIAL_SURFACE_BLACK
            "white" -> MATERIAL_SURFACE_WHITE
            else -> return@execute
        }

        val exactNames = setOf(
            "color_surface", "color_surface_dark", "color_surface_bright", "color_surface_bright_dark",
            "color_surface_dim", "color_surface_dim_dark", "color_surface_container_lowest",
            "color_surface_container_lowest_dark", "color_surface_container_low", "color_surface_container_low_dark",
            "color_surface_container", "color_surface_container_dark", "color_surface_container_high",
            "color_surface_container_high_dark", "color_surface_container_highest", "color_surface_container_highest_dark",
            "gm3_baseline_surface_dark", "gm3_baseline_surface_bright_dark", "gm3_baseline_surface_dim_dark",
            "gm3_baseline_surface_container_lowest_dark", "gm3_baseline_surface_container_low_dark",
            "gm3_baseline_surface_container_dark", "gm3_baseline_surface_container_high_dark",
            "gm3_baseline_surface_container_highest_dark",
        )

        fun isMaterialSurface(name: String): Boolean {
            val normalized = name.lowercase()
            if ("on_surface" in normalized || "onsurface" in normalized) return false
            return name in exactNames ||
                normalized.startsWith("gm3_baseline_surface") ||
                normalized.startsWith("m3_sys_color_light_surface") ||
                normalized.startsWith("m3_sys_color_dark_surface") ||
                normalized.startsWith("m3_sys_color_dynamic_light_surface") ||
                normalized.startsWith("m3_sys_color_dynamic_dark_surface") ||
                (normalized.startsWith("material_dynamic_neutral") && "surface" in normalized)
        }

        val directories = get("res").listFiles()?.filter {
            it.isDirectory && it.name.startsWith("values")
        }.orEmpty()
        var hits = 0

        directories.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                runCatching {
                    document(file.absolutePath).use { document ->
                        val nodes = document.getElementsByTagName("*")
                        for (index in 0 until nodes.length) {
                            val element = nodes.item(index) as? Element ?: continue
                            val name = element.getAttribute("name")
                            val type = element.getAttribute("type")
                            if (!isMaterialSurface(name)) continue
                            if (element.tagName == "color" ||
                                element.tagName == "macro" ||
                                (element.tagName == "item" && type == "color")) {
                                element.textContent = target
                                hits++
                            }
                        }
                    }
                }
            }
        }

        if (hits == 0) {
            throw IllegalStateException(
                "AMOLED Material semantic surfaces verification failed for Cromite $MATERIAL_SURFACE_VERSION: verified surface roles were not found."
            )
        }
    }
}
