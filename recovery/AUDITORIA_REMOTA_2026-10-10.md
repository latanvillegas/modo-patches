# Auditoría remota de recuperación Cromite 1.50.0

Fecha: 2026-10-10. Rama: `recovery/cromite-1.50.0`.

Fuente de referencia: las 23 clases principales `*PatchKt.class` bajo `app/morphe/patches/cromite/` en `patches-1.50.0.mpp`. Se consultó individualmente la existencia de sus 23 rutas Kotlin equivalentes en la rama mediante GitHub. **No se compiló ni se ejecutó una APK.**

## Resultado

- **14/23** archivos Kotlin correspondientes presentes.
- **9/23** archivos Kotlin correspondientes ausentes.
- Esta métrica mide archivos fuente, no parches funcionales ni opciones visibles en Manager.
- El archivo auxiliar `RecoveredBrowserThemeOption.kt` no cuenta entre las 23 clases originales.

### Ausentes

1. `layout/theme/PureAmoledAppMenuPatch.kt`
2. `layout/theme/PureAmoledForegroundResourcesPatch.kt`
3. `layout/theme/PureAmoledMaterialFilledFieldsPatch.kt`
4. `layout/theme/PureAmoledMaterialListItemsPatch.kt`
5. `layout/theme/PureAmoledMaterialSemanticSurfacesPatch.kt`
6. `layout/theme/PureAmoledMaterialThemeAttrsPatch.kt`
7. `layout/theme/PureAmoledNtpPatch.kt`
8. `layout/theme/PureAmoledOmniboxPatch.kt`
9. `layout/theme/PureAmoledSettingsPatch.kt`

### Presentes

- `PureAmoledCardsPatch.kt`
- `PureAmoledContextMenuPatch.kt`
- `PureAmoledCoreBackgroundsPatch.kt`
- `PureAmoledDialogsPatch.kt`
- `PureAmoledInteractableChipPatch.kt`
- `PureAmoledItemChooserPatch.kt`
- `PureAmoledMessagesPatch.kt`
- `PureAmoledPatch.kt`
- `PureAmoledTabsPatch.kt`
- `PureAmoledToolbarPatch.kt`
- `PureAmoledWidgetsPatch.kt`
- `PureAmoledXrPatch.kt`
- `misc/fullscreen/HideSettingsStatusBarPatch.kt`
- `misc/fullscreen/HideStatusBarPatch.kt`

## Limitaciones y siguientes pasos

1. Verificar las firmas reales de la API de `app.morphe.patcher` y el registro de `recoveredBrowserThemeOption`.
2. Reconstruir las nueve clases ausentes **desde el bytecode original**, sin inventar nombres de recursos.
3. Validar la compilación Kotlin antes de afirmar que los parches funcionan.
4. Mantener las pruebas sin construir Cromite ni consumir GitHub Actions hasta cerrar los problemas estáticos.
