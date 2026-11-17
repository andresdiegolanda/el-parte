# kanban

El kanban de El Parte: las tarjetas de [`tablero.md`](../tablero.md) en columnas, para verlas en una página. Es de solo lectura: `tablero.md` es lo que manda, y las tarjetas se cambian ahí ([ADR 0017](../adrs/0017-forma-de-trabajo-mas-liviana.md)).

No confundir con el [tablero de arquitectura](../docs/tablero-de-arquitectura/README.md): el kanban muestra lo pendiente; el tablero de arquitectura, el sistema.

| Archivo | Qué es | ¿Se edita a mano? |
|---|---|---|
| [`kanban/plantilla.html`](plantilla.html) | El diseño de la página. | Sí |
| `kanban/index.html` | La página generada desde `tablero.md` con `python3 herramientas/generar_kanban.py`. No se versiona. Se abre en cualquier navegador. | No |

Está publicado en <https://andresdiegolanda.github.io/el-parte/kanban/>, y se vuelve a publicar en cada push a `main` con `.github/workflows/publicar.yml`. La versión editable anterior, con su base y su sincronización, está en [`obsoletos/kanban/plantilla.html`](../obsoletos/kanban/plantilla.html) ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md), reemplazado).
