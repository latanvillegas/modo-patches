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

        // The decoded APK can use synthetic resource names (string_0x...).
        // arsclib cannot register those names from a newly created values XML.
        // Update existing resource entries in place, preserving the resource table.
        // Also update existing locale overrides so they do not shadow the defaults.
        fun xml(value: String) = value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;")
        var changed = 0
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.flatMap { directory ->
                directory.listFiles()?.filter { it.isFile && it.extension == "xml" }
                    ?: emptyList()
            }
            ?.forEach { file ->
                val original = file.readText()
                val updated = entry.replace(original) { match ->
                    val name = match.groupValues[1]
                    val translated = replacements[name]
                    if (translated == null) match.value else {
                        changed++
                        match.value.replaceRange(
                            match.groups[3]!!.range.first - match.range.first,
                            match.groups[3]!!.range.last - match.range.first + 1,
                            xml(translated),
                        )
                    }
                }
                if (updated != original) file.writeText(updated)
            }
        check(changed > 0) { "No existing resource entries were translated." }
    }
}
