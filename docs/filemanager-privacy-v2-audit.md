# File Manager Plus 3.8.2 — Privacy v2: auditoría y criterios de implementación

## Estado y alcance

- Rama de trabajo: `feature/filemanager-privacy-v2`, derivada de `feature/filemanager-privacy-v1`.
- Mantener intactos `FileManagerPrivacyPatch.kt` (Privacy v1) y `FileManagerPureAmoledPatch.kt` (Pure AMOLED v6).
- Privacy v1 modifica `Lax/y3/k;->o(Landroid/content/Context;)Z` para devolver `false`, anulando la preferencia `usage_diagnostics`.
- Compilación general del repositorio validada por GitHub Actions #332; aplicación del parche y funcionamiento normal del administrador de archivos confirmados manualmente.
- **No está demostrado** que todas las comunicaciones de telemetría hayan cesado.

## Evidencia previa de auditoría (v13)

En el manifiesto del APK original:
- `firebase_analytics_collection_enabled=false` declarado explícitamente.
- No se encontró declaración explícita de `firebase_crashlytics_collection_enabled`, `firebase_data_collection_default_enabled`, `firebase_sessions_enabled` ni `google_analytics_adid_collection_enabled`.
- Se registraron `CrashlyticsRegistrar`, `FirebaseSessionsRegistrar`, `SessionLifecycleService` y `FirebaseInitProvider`.

Estas declaraciones **no prueban** que haya transmisión de datos. La ausencia de una bandera en el manifiesto tampoco prueba por sí sola que un servicio esté activo.

## Investigación requerida antes de implementar v2

1. Extraer manifiesto binario y DEX del APK de referencia. Confirmar versión 3.8.2 y SHA-256:
   `a5fec2ef61c107243108efa4f4c42eebf18dfd434992ebad7811f4fa78e6d829`.
2. Localizar en DEX el uso real de Crashlytics (`FirebaseCrashlytics`, `setCrashlyticsCollectionEnabled`, `recordException`, `log`, `CrashlyticsRegistrar`) y Firebase Sessions (`FirebaseSessionsRegistrar`, `SessionLifecycleService`, banderas de recopilación).
3. Determinar si existe un control por preferencias o inicialización de Firebase que permita desactivar cada componente de forma estable, sin impedir el arranque.
4. Separar claramente: (a) presencia de SDK, (b) inicialización, (c) habilitación de recopilación, (d) transmisión observada. No inferir (d) a partir de (a).
5. Evaluar primero banderas de configuración oficiales y puntos de entrada específicos; evitar eliminar `FirebaseInitProvider` o componentes genéricos sin demostrar su independencia funcional.
6. Si se añade un nuevo parche, hacerlo **opt-in** (`default = false`) y compatible exclusivamente con `com.alphainventor.filemanager` versión `3.8.2`. Añadir fingerprints precisos y fallos explícitos si no coinciden.
7. Validar compilación Kotlin mediante GitHub Actions; después parcheo de APK y pruebas funcionales en Android. Comprobar, cuando sea viable, tráfico de red antes/después con el mismo escenario.

## Criterios de aceptación

- Privacy v1 y Pure AMOLED v6 no se modifican.
- El nuevo control de Crashlytics/Sessions tiene evidencia concreta de ubicación y efecto.
- La compilación y aplicación del parche son correctas.
- Navegación, copia, movimiento, eliminación y arranque funcionan como antes.
- No afirmar “telemetría completamente bloqueada” sin observación de red y alcance definido.

## Siguiente dato necesario

Resultados de inspección de las rutas de inicialización y controles reales de Crashlytics y Firebase Sessions en el APK original. No implementar un parche DEX especulativo antes de obtenerlos.
