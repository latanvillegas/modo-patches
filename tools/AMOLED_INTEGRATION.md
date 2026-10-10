# Pure AMOLED Web — integración en Cromite 153

## Estado comprobado

- Motor prototipo: `tools/pure-amoled-web.user.js` (v0.2.0).
- Parche Kotlin: `PureAmoledWebPatch.kt` es un NO-OP; **no inyecta JavaScript**.
- No se ha compilado APK ni lanzado GitHub Actions.
- La compatibilidad con un gestor de UserScripts en Cromite no está confirmada.

## Contrato para la integración real

La ejecución de JavaScript dentro de cada página requiere una vía real en Chromium/Blink:
un punto de inyección nativo o de navegador que pueda cargar el script en el
documento de cada frame HTTP(S), preferentemente en `document_start`. No basta
con añadir el JS a `assets/` ni con modificar un método Java arbitrario.

Condiciones de aceptación:

1. El script se ejecuta en navegación normal, no solamente en WebView.
2. Respeta `prefers-color-scheme: dark`; en modo claro no modifica fondos.
3. Solo convierte fondos opacos, oscuros y casi neutros a `#000000`.
4. No modifica imágenes, vídeos, canvas, SVG, fondos con imágenes ni transparencias.
5. Gestiona DOM dinámico y navegación entre documentos.
6. No introduce errores de aplicación del parche.
7. Pruebas con HTML local: modo oscuro, modo claro, fondos semitransparentes,
   fondos con gradientes, contenido dinámico y navegación.
8. Revisión de rendimiento antes de habilitar por defecto.

## Próximo paso de ingeniería

Identificar en el APK Cromite 153.0.8010.37 una ruta **real y comprobable**
de ejecución de scripts para pestañas Chromium. Si no existe una ruta segura
desde DEX, la integración deberá hacerse en el código fuente de Chromium/Cromite
y no con una huella DEX inventada.

El UserScript es un prototipo experimental: no cubre Shadow DOM, frames de
otro origen ni todos los fondos dibujados por el motor; tampoco se ha
validado aún en un navegador real.
