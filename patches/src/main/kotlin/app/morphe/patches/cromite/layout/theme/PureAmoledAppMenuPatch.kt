package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val APP_MENU_VERSION = "153.0.8010.37"
private const val APP_MENU_BLACK = "#FF000000"
private const val APP_MENU_WHITE = "#FFFFFFFF"

private val pureAmoledAppMenuResourcesPatch = resourcePatch(
    description = "Scoped AppMenu, popup and native list-menu resources for the selectable Cromite AMOLED palette.",
) {
    compatibleWith("org.cromite.cromite"(APP_MENU_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        if (selectedTheme == "device") return@execute

        val target = when (selectedTheme) {
            "black" -> APP_MENU_BLACK
            "white" -> APP_MENU_WHITE
            else -> return@execute
        }

        val exactColorFiles = setOf(
            "menu_item_bg_color.xml",
            "menu_item_bg_color_baseline.xml",
            "menu_item_bg_color_dark_baseline.xml",
            "menu_bg_color.xml",
            "menu_bg_color_baseline.xml",
            "menu_bg_color_dark_baseline.xml",
            "list_menu_background_color.xml",
            "list_menu_background_tint.xml",
            "mtrl_popupmenu_overlay_color.xml",
        )
        val exactDrawableFiles = setOf(
            "popup_bg.xml",
            "popup_bg_8dp.xml",
            "popup_bg_24dp.xml",
            "popup_bg_shape_16dp.xml",
            "popup_bg_shape_24dp.xml",
            "popup_bg_shape_24dp_baseline.xml",
            "list_menu_background.xml",
            "menu_bg_tinted.xml",
        )
        val exactValueNames = setOf(
            "menu_item_bg_color",
            "menu_item_bg_color_baseline",
            "menu_item_bg_color_dark_baseline",
            "menu_bg_color",
            "menu_bg_color_baseline",
            "menu_bg_color_dark_baseline",
            "list_menu_background_color",
            "list_menu_background_tint",
            "mtrl_popupmenu_overlay_color",
        )

        val resourceDirectories = get("res").listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
        val colorDirectories = resourceDirectories.filter { it.name.startsWith("color") }
        val drawableDirectories = resourceDirectories.filter { it.name.startsWith("drawable") }
        val valueDirectories = resourceDirectories.filter { it.name.startsWith("values") }

        var hits = 0

        fun recolor(element: Element) {
            val attributes = element.attributes
            for (index in 0 until attributes.length) {
                val attribute = attributes.item(index)
                if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color" ||
                    attribute.nodeName.endsWith(":tint") || attribute.nodeName == "tint") {
                    attribute.nodeValue = target
                    hits++
                }
            }
            val children = element.childNodes
            var hasElementChildren = false
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    hasElementChildren = true
                    recolor(child as Element)
                }
            }
            if (!hasElementChildren && element.textContent.trim().startsWith("#")) {
                element.textContent = target
                hits++
            }
        }

        colorDirectories.forEach { directory ->
            exactColorFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    recolor(document.documentElement)
                }
            }
        }

        drawableDirectories.forEach { directory ->
            exactDrawableFiles.forEach { fileName ->
                val file = java.io.File(directory, fileName)
                if (!file.isFile) return@forEach
                document(file.absolutePath).use { document ->
                    recolor(document.documentElement)
                }
            }
        }

        valueDirectories.forEach { directory ->
            directory.listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    runCatching {
                        document(file.absolutePath).use { document ->
                            val nodes = document.getElementsByTagName("*")
                            for (index in 0 until nodes.length) {
                                val element = nodes.item(index) as? Element ?: continue
                                val name = element.getAttribute("name")
                                val type = element.getAttribute("type")
                                if (name !in exactValueNames) continue
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
                "AMOLED AppMenu resource verification failed for Cromite $APP_MENU_VERSION: no whitelisted menu/popup resources were found."
            )
        }
    }
}

@Suppress("unused")
val pureAmoledAppMenuPatch = resourcePatch(
    name = "AMOLED - App menu and popups",
    description = "Applies Device default / Pure Black / Pure White only to Cromite AppMenu, popup and native list-menu surfaces.",
) {
    compatibleWith("org.cromite.cromite"(APP_MENU_VERSION))
    dependsOn(pureAmoledAppMenuResourcesPatch)
}
