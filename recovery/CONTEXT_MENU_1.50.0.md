# Cromite 1.50.0 — contexto de recuperación: menú contextual

Fuente primaria: `PureAmoledContextMenuPatchKt.class` del bundle `patches-1.50.0.mpp` (SHA-256 `ca0e2184907fac3964c9f31ec798dfdb33bf723038272945497aeec12911e5f2`).

Datos verificados en el desensamblado Java de la clase original:

- Archivo fuente original: `PureAmoledContextMenuPatch.kt`.
- Nombre de clase objetivo: `Lg03;`.
- Método objetivo: `b(Ljava/util/List;Landroid/view/View;Lh75;Ll75;)Ljava/lang/Object;`.
- Constante de layout: `2131624183` (decimal).
- El fingerprint contiene llamadas a `LayoutInflater.inflate(ILandroid/view/ViewGroup;)Landroid/view/View;` y `View.findViewById(I)Landroid/view/View;`.
- La implementación comprueba **exactamente dos coincidencias** del fingerprint antes de modificar instrucciones.
- La función `contextMenuBackgroundSmali(theme, register1, register2)` genera inyección diferente para `black`, `white` y `device`.
- La opción de tema se recupera de `browserTheme`, con fallback `device`.

**Pendiente:** reconstruir con fidelidad la selección de registros y los puntos de inserción de las dos coincidencias. No se debe registrar este parche como restaurado hasta verificar esa lógica y compilarlo.

Este documento preserva evidencia concreta; no reemplaza la implementación.
