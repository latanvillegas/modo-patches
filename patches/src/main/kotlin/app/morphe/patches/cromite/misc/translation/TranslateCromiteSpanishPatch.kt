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
            "Ad Blocking" to "Bloqueo de anuncios",
            "Autoupdate disabled" to "Actualización automática desactivada",
            "Filters URL" to "URL de filtros",
            "Visit help page" to "Visitar la página de ayuda",
            "Enable Adblock Plus" to "Activar Adblock Plus",
            "Block ads on websites" to "Bloquear anuncios en sitios web",
            "Enable anti-circumvention and snippets" to "Activar protección contra evasión y fragmentos de código",
            "Check for updates now" to "Buscar actualizaciones ahora",
            "Add custom filter urls" to "Añadir URL de filtros personalizados",
            "Add custom filters commands" to "Añadir reglas de filtrado personalizadas",
            "Use for new tabs" to "Usar para pestañas nuevas",
            "Show NTP at startup" to "Mostrar la página de nueva pestaña al iniciar",
            "Ask to restart on connection change" to "Solicitar reinicio al cambiar la conexión",
            "Cromite’s homepage" to "Página principal de Cromite",
            "Mobile User Agent" to "Agente de usuario móvil",
            "Desktop Mode User Agent" to "Agente de usuario de escritorio",
            "Use standard user agent" to "Usar agente de usuario estándar",
            "Insert a valid user agent" to "Introduce un agente de usuario válido",
            "Use custom user agent" to "Usar agente de usuario personalizado",
            "Activate User Scripts" to "Activar scripts de usuario",
            "Experimental support for Greasemonkey-style user scripts." to "Compatibilidad experimental con scripts de usuario de tipo Greasemonkey.",
            "Add script" to "Añadir script",
            "Developer options" to "Opciones de desarrollador",
            "Tracing" to "Seguimiento de ejecución",
            "Enable support for cromite test" to "Activar compatibilidad con pruebas de Cromite",
            "Enable webview support for cromite test" to "Activar compatibilidad de WebView con pruebas de Cromite",
            "Android GPU sandbox" to "Entorno aislado de GPU de Android",
            "Android fonts fingerprinting mitigation" to "Protección contra identificación mediante fuentes de Android",
            "Pixel Perfect Mode" to "Modo de píxeles exactos",
            "Enable Extensions" to "Activar extensiones",
            "Enable experimental support for extensions in Android" to "Activa la compatibilidad experimental con extensiones en Android",
            "About Cromite" to "Acerca de Cromite",
            "Allow checking for updates" to "Permitir buscar actualizaciones",
            "Notify about new releases by periodically checking for their availability" to "Notificar nuevas versiones mediante comprobaciones periódicas",
            "Close all open tabs on exit" to "Cerrar todas las pestañas al salir",
            "Expire history days threshold" to "Días hasta eliminar el historial",
            "Number of days to keep navigation history" to "Número de días que se conserva el historial",
            "Data exposed to the websites" to "Datos expuestos a los sitios web",
            "Data exposed in the device" to "Datos expuestos en el dispositivo",
            "HTTP Referer header policy" to "Política de la cabecera HTTP Referer",
            "Throttle frame rate to 60hz" to "Limitar la frecuencia de fotogramas a 60 Hz",
            "Allow custom tab intents." to "Permitir intents de pestañas personalizadas",
            "Use ephemeral mode for CCT" to "Usar modo temporal para pestañas personalizadas",
            "Allow forward URL requests to external intents" to "Permitir enviar solicitudes URL a aplicaciones externas",
            "Use list mode for tab switcher" to "Usar vista de lista para cambiar de pestaña",
            "Use only icons in the tab grid" to "Mostrar solo iconos en la cuadrícula de pestañas",
            "Open external links in incognito" to "Abrir enlaces externos en incógnito",
            "Lock Incognito windows when you leave Cromite" to "Bloquear ventanas de incógnito al salir de Cromite",
            "Always incognito mode" to "Usar siempre el modo incógnito",
            "Incognito navigation settings" to "Configuración de navegación de incógnito",
            "Other services" to "Otros servicios",
            "Proxy configuration" to "Configuración de proxy",
            "Open Cromite flags list" to "Abrir lista de funciones experimentales de Cromite",
            "Enable Dangling Ptr Detection (DPD) via BackupRefPtr (BRP)" to "Activar detección de punteros colgantes mediante BackupRefPtr",
            "Open ABP Snippets Overview" to "Abrir descripción de fragmentos de código de ABP",
            "Open ABP anti-circumvention filter list repo" to "Abrir repositorio de filtros antievasión de ABP",
            "Custom ad filtering settings (0 selected)" to "Filtros de anuncios personalizados (0 seleccionados)",
            "Custom Filters (0 selected)" to "Filtros personalizados (0 seleccionados)",
            "Disable if cross-origin and user begins navigation" to "Desactivar si el usuario inicia una navegación entre orígenes",
            "Page reloads triggered by vertically overscrolling content." to "La página se recarga al deslizar verticalmente más allá del límite.",
            "Immediately bring new open tabs to the foreground" to "Mostrar inmediatamente las pestañas nuevas en primer plano",
            "Opens the search in a tab in the current group, if deactivated opens a new group" to "Abre la búsqueda en una pestaña del grupo actual; si se desactiva, crea otro grupo",
            "Smart text selection automatically augments the selected boundaries and classifies the selected text by sending it together with its surrounding context" to "La selección inteligente amplía y clasifica el texto seleccionado utilizando su contexto",
            "Blocks the toolbar's hiding" to "Impide que se oculte la barra de herramientas",
            "Enable showing a NewTabPage at startup and allows selection of the last open tab" to "Muestra una pestaña nueva al iniciar y permite seleccionar la última pestaña abierta",
            "If the IP of the connection changes, access to the network is blocked and a browser restart is requested" to "Si cambia la dirección IP, se bloquea el acceso a la red y se solicita reiniciar el navegador",
            "Use a separate storage partition when opening custom tab intents" to "Usar almacenamiento separado al abrir pestañas personalizadas",
            "Activates the list view of tabs in the tab switcher" to "Activa la vista de lista en el selector de pestañas",
            "Disable saving snapshots to disk (even for incognito sessions)" to "Desactiva el guardado de capturas en disco, incluso en incógnito",
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
