# herramientas

Los scripts que generan y controlan los archivos del repositorio. Usan solo la biblioteca estándar de Python, salvo las pruebas de páginas, que necesitan Puppeteer (viene con mermaid-cli) y un Chrome. Se corren desde la raíz del repositorio.

## Generadores

Lo que generan no se edita a mano: se cambia la fuente y se vuelve a generar.

| Script | Genera | Desde |
|---|---|---|
| [`generar_kanban.py`](generar_kanban.py) | `kanban/index.html` | `tablero.md` y `kanban/plantilla.html` |
| [`generar_laminas.py`](generar_laminas.py) | `docs/laminas/<nombre>.svg` | `docs/laminas/<nombre>.json` |
| [`generar_tablero_arquitectura.py`](generar_tablero_arquitectura.py) | `docs/tablero-de-arquitectura/index.html` | `sistema.json` y `plantilla.html` de esa carpeta |

## Control y sincronización

| Script | Qué hace |
|---|---|
| [`verificar_docs.py`](verificar_docs.py) | El verificador. Se corre antes de cada entrega: guiones largos, enlaces, tablero, kanban, láminas, diagramas Mermaid, referencias a tarjetas y decisiones, la primera migración igual al documento de la base y el tablero de arquitectura igual al código. Sale con 1 si encuentra algo. |
| [`sincronizar_tablero.py`](sincronizar_tablero.py) | Sincroniza el kanban publicado con `tablero.md` ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md)). El procedimiento está en [arranque/como-se-trabaja.md](../arranque/como-se-trabaja.md), "Sincronizar el kanban". |

## Módulos

| Módulo | Qué es |
|---|---|
| [`tablero_md.py`](tablero_md.py) | Lee, valida y vuelve a escribir `tablero.md`. Lo usan el generador del kanban, la sincronización y el verificador. |
| [`tablero_arquitectura.py`](tablero_arquitectura.py) | Lee el modelo del tablero de arquitectura, lee del código lo que se puede leer sin compilarlo y compara los dos ([ADR 0012](../adrs/0012-tablero-de-arquitectura.md)). |

## Pruebas

| Prueba | Cómo se corre |
|---|---|
| [`pruebas/prueba_sincronizacion.py`](pruebas/prueba_sincronizacion.py) | `python3 herramientas/pruebas/prueba_sincronizacion.py`: la sincronización en una copia del repositorio. |
| [`pruebas/prueba_kanban.mjs`](pruebas/prueba_kanban.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_kanban.mjs`: el kanban editable con una base simulada. |
| [`pruebas/prueba_tablero_arquitectura.py`](pruebas/prueba_tablero_arquitectura.py) | `python3 herramientas/pruebas/prueba_tablero_arquitectura.py`: el control del tablero de arquitectura, con un cambio por vez. |
| [`pruebas/prueba_tablero_arquitectura.mjs`](pruebas/prueba_tablero_arquitectura.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_tablero_arquitectura.mjs`: la página del tablero de arquitectura en Chrome. |

Las pruebas de la aplicación no están acá: están en `app/` y se corren con `mvn test` ([app/README.md](../app/README.md)).
