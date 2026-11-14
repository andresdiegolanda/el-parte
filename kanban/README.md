# kanban

El kanban de El Parte: las tarjetas de [`tablero.md`](../tablero.md) en columnas, para verlas y editarlas en una página. `tablero.md` es lo que manda ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md)).

No confundir con el [tablero de arquitectura](../docs/tablero-de-arquitectura/README.md): el kanban muestra lo pendiente; el tablero de arquitectura, el sistema.

| Archivo | Qué es | ¿Se edita a mano? |
|---|---|---|
| [`plantilla.html`](plantilla.html) | El diseño de la página y sus reglas de edición. | Sí |
| [`index.html`](index.html) | La copia sin conexión, generada desde `tablero.md` con `python3 herramientas/generar_kanban.py`. Se abre en cualquier navegador. | No |

La misma página está publicada con una base propia, donde se puede editar: <https://claude.ai/artifact/4wQ9LjW4H98WnjUWpcFsf3>. Lo que se cambia ahí llega a `tablero.md` con la sincronización que hacen las sesiones de trabajo; cómo se hace está en [arranque/como-se-trabaja.md](../arranque/como-se-trabaja.md), "Sincronizar el kanban".
