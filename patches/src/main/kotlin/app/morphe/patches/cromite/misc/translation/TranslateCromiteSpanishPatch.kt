package app.morphe.patches.cromite.misc.translation

import app.morphe.patcher.patch.resourcePatch

@Suppress("unused")
val translateCromiteSpanishPatch = resourcePatch(
    name = "Translate Cromite to Spanish",
    description = "Adds Spanish translations for Cromite-specific settings.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    execute {
        val translations = mapOf(
            "Accessibility and UI" to "Accesibilidad e interfaz",
            "Force Tablet Mode" to "Forzar modo tableta",
            "Open cromite in Tablet Mode" to "Abrir Cromite en modo tableta",
            "Disable pull to refresh" to "Desactivar deslizar para actualizar",
            "Move toolbar to bottom" to "Mover la barra de herramientas abajo",
            "Disable toolbar swipe up" to "Desactivar deslizar hacia arriba en la barra de herramientas",
            "Always show toolbar" to "Mostrar siempre la barra de herramientas",
            "Blocks the toolbar's hiding" to "Impide que se oculte la barra de herramientas",
            "Customize selection popup" to "Personalizar menú de selección",
            "Opens tabs in foreground" to "Abrir pestañas en primer plano",
            "Move web search to the top of items" to "Mover la búsqueda web al principio",
            "Web search as first choice" to "Mostrar la búsqueda web como primera opción",
            "Web search in tab group" to "Búsqueda web en grupos de pestañas",
            "Enable smart text selection" to "Activar selección inteligente de texto",
            "Customising the navigation manager" to "Personalización de la navegación",
            "Disable system gestures for web browsing" to "Desactivar gestos del sistema al navegar",
            "Forces activation of the old page navigation mode" to "Fuerza el modo anterior de navegación",
        )

        val root = get("res")
        val entry = Regex("""<string\s+name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val replacements = linkedMapOf<String, String>()
        root.resolve("values").listFiles()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.forEach { file ->
                entry.findAll(file.readText()).forEach { match ->
                    translations[match.groupValues[3].trim()]?.let {
                        replacements[match.groupValues[1]] = it
                    }
                }
            }
        check(replacements.isNotEmpty()) {
            "No matching Cromite English string resources found in APK."
        }

        val targetDirectory = root.resolve("values-es")
        targetDirectory.mkdirs()
        val target = targetDirectory.resolve("morphe_cromite_es.xml")
        check(!target.exists()) { "Morphe Spanish translations already exist" }
        fun xml(value: String) = value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;")
        target.writeText(buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<resources>")
            replacements.forEach { (name, translated) ->
                appendLine("""    <string name="$name">${xml(translated)}</string>""")
            }
            appendLine("</resources>")
        })
    }
}
