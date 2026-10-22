# TV360 Bitel 3.5 — integración controlada

- Paquete objetivo: `com.bitel.tv360`; versión observada: `3.5` (versionCode 338).
- Rama de trabajo: `feature/tv360-bitel-patches`.
- Estado: auditoría y preparación; **no hay parches TV360 activos**.
- No ejecutar GitHub Actions, compilar APK, publicar releases ni modificar `main` sin autorización expresa.

## Diseño de parches independientes

1. **Barra de estado**: localizar el método de actividad y la gestión de `WindowInsets` mediante huellas verificables. Ocultar solo `statusBars()`; preservar `navigationBars()`, gestos y PiP.
2. **Publicidad**: inventariar y clasificar los puntos de presentación de anuncios. No desactivar lógica de autenticación ni funciones de reproducción.
3. **Telemetría**: identificar llamadas de analítica y logging separadas de diagnóstico, autenticación y métricas necesarias para el reproductor. Rechazar coincidencias ambiguas.
4. **Exportación de descargas**: no integrar ni exponer hasta confirmar autorización, ausencia de DRM, integridad del origen y destino seguro. No extraer claves ni eludir licencias.

## Evidencia local aún no subida

En Termux se prepararon `Tv360ExportValidator.java`, `Tv360SafeCopy.java` y una copia de `HomeBoxDownloadContentAdapter.smali`. Son prototipos no compilados, no probados en Android ni adaptados a la API Morphe; no deben tratarse como parches listos.

## Criterios de implementación

- Usar `bytecodePatch` y `compatibleWith` con huellas y firmas exactas.
- Mantener un parche por funcionalidad, deshabilitado por defecto durante desarrollo.
- Comprobar unicidad de anclajes y lanzar `PatchException` ante cambios inesperados.
- No insertar instrucciones sin verificar registros, flujo de control y tipos DEX.
- Registrar resultados de pruebas estáticas antes de solicitar autorización para compilación.
