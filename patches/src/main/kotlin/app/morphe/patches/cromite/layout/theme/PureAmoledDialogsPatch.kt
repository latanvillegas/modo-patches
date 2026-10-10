package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

// Reconstructed from PureAmoledDialogsPatchKt.class in the verified 1.50.0 bundle.
// The original scans res/color* and res/values* for these three resources.
@Suppress("unused")
val pureAmoledDialogsPatch = resourcePatch(
    name = "AMOLED - Dialogs and bottom sheets",
    description = "Applies Device default / Pure Black / Pure White only to verified Cromite dialog and bottom-sheet background resources.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val theme = browserTheme.value ?: "device"
        if (theme == "device") return@execute
        val target = when (theme) {
            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            else -> return@execute
        }
        val files = setOf(
            "bottom_sheet_bg_color.xml",
            "dialog_bg_color.xml",
            "tab_group_dialog_bg_color.xml",
        )
        val names = setOf(
            "bottom_sheet_bg_color",
            "dialog_bg_color",
            "tab_group_dialog_bg_color",
        )
        val directories = get("res").listFiles()?.filter { it.isDirectory }.orEmpty()
        var hits = 0
        directories.filter { it.name.startsWith("color") }.forEach { directory ->
            files.forEach { name ->
                val file = directory.resolve(name)
                if (!file.isFile) return@forEach
                document("res/${directory.name}/${file.name}").use { document ->
                    val elements = document.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        val attributes = element.attributes
                        for (i in 0 until attributes.length) {
                            val attribute = attributes.item(i)
                            if (attribute.nodeName.endsWith(":color") || attribute.nodeName == "color") {
                                attribute.nodeValue = target
                                hits++
                            }
                        }
                        val child = element.firstChild
                        if (element.childNodes.length == 1 && child?.nodeType == Node.TEXT_NODE &&
                            element.textContent.trim().startsWith("#")
                        ) {
                            element.textContent = target
                            hits++
                        }
                    }
                }
            }
        }
        directories.filter { it.name.startsWith("values") }.forEach { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty().forEach { file ->
                runCatching {
                    document("res/${directory.name}/${file.name}").use { document ->
                        val elements = document.getElementsByTagName("*")
                        for (index in 0 until elements.length) {
                            val element = elements.item(index) as? Element ?: continue
                            if (element.getAttribute("name") !in names) continue
                            val type = element.getAttribute("type")
                            if (element.tagName == "color" || element.tagName == "macro" ||
                                (element.tagName == "item" && type == "color")
                            ) {
                                element.textContent = target
                                hits++
                            }
                        }
                    }
                }
            }
        }
        check(hits > 0) {
            "AMOLED dialog/sheet resource verification failed for Cromite 153.0.8010.37: no whitelisted resources were found."
        }
    }
}
