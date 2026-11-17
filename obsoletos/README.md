# Obsoletos

En El Parte nada se borra (regla 9). Lo que sobra se mueve acá, manteniendo su ruta relativa debajo de esta carpeta: `docs/viejo.md` pasa a `obsoletos/docs/viejo.md`.

Cada cosa que se mueve se anota en el índice: qué era, cuándo se movió, por qué y qué la reemplaza.

## Índice

| Ruta original | Fecha | Por qué se movió | Qué la reemplaza |
|---|---|---|---|
| `herramientas/sincronizar_tablero.py` | 2026-11-17 | El kanban pasó a ser de solo lectura y ya no hay base que sincronizar. | `herramientas/generar_kanban.py` y `.github/workflows/publicar.yml` ([ADR 0017](../adrs/0017-forma-de-trabajo-mas-liviana.md)) |
| `herramientas/pruebas/prueba_sincronizacion.py` | 2026-11-17 | Probaba la sincronización. | Nada: no hay sincronización |
| `herramientas/pruebas/prueba_kanban.mjs` | 2026-11-17 | Probaba el kanban editable con una base simulada. | `herramientas/pruebas/prueba_kanban.mjs`, para el kanban de solo lectura |
| `kanban/plantilla.html` | 2026-11-17 | Era el kanban editable, con su base y sus reglas de edición. | `kanban/plantilla.html`, de solo lectura |
