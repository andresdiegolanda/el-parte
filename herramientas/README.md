# herramientas

Los scripts que generan y controlan los archivos del repositorio. Usan solo la biblioteca estándar de Python, salvo las pruebas de páginas, que necesitan Puppeteer (viene con mermaid-cli) y un Chrome. Se corren desde la raíz del repositorio.

## Generadores

Lo que generan no se edita a mano: se cambia la fuente y se vuelve a generar.

| Script | Genera | Desde |
|---|---|---|
| [`herramientas/generar_kanban.py`](generar_kanban.py) | `kanban/index.html` | `tablero.md` y `kanban/plantilla.html` |
| [`herramientas/generar_laminas.py`](generar_laminas.py) | `docs/laminas/<nombre>.svg` | `docs/laminas/<nombre>.json` |
| [`herramientas/generar_tablero_arquitectura.py`](generar_tablero_arquitectura.py) | `docs/tablero-de-arquitectura/index.html` y una página por vista, como `docs/tablero-de-arquitectura/usuarios.html` | `docs/tablero-de-arquitectura/sistema.json` y `docs/tablero-de-arquitectura/plantilla.html` |

## Control y sincronización

| Script | Qué hace |
|---|---|
| [`herramientas/verificar_docs.py`](verificar_docs.py) | El verificador. Se corre antes de cada entrega: guiones largos, enlaces, tablero, kanban, láminas, diagramas Mermaid, referencias a tarjetas y decisiones, la primera migración igual al documento de la base, el tablero de arquitectura igual al código y la muestra del banco de pruebas con su medición esperada. Sale con 1 si encuentra algo. |
| [`herramientas/banco_de_pruebas.py`](banco_de_pruebas.py) | Valida un registro del banco de pruebas y su revisión, y cuenta una medición: `validar [carpeta]` y `contar [carpeta salida.json [medicion.json]]`, por omisión sobre la muestra. Qué controla y qué cuenta está en [banco-de-pruebas/formato.md](../banco-de-pruebas/formato.md) y [banco-de-pruebas/metricas.md](../banco-de-pruebas/metricas.md). |
| [`herramientas/sincronizar_tablero.py`](sincronizar_tablero.py) | Sincroniza el kanban publicado con `tablero.md` ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md)). El procedimiento está en [arranque/como-se-trabaja.md](../arranque/como-se-trabaja.md), "Sincronizar el kanban". |

## Módulos

| Módulo | Qué es |
|---|---|
| [`herramientas/tablero_md.py`](tablero_md.py) | Lee, valida y vuelve a escribir `tablero.md`. Lo usan el generador del kanban, la sincronización y el verificador. |
| [`herramientas/tablero_arquitectura.py`](tablero_arquitectura.py) | Lee el modelo del tablero de arquitectura, lee del código lo que se puede leer sin compilarlo y compara los dos ([ADR 0012](../adrs/0012-tablero-de-arquitectura.md)). |

## Pruebas

| Prueba | Cómo se corre |
|---|---|
| [`herramientas/pruebas/prueba_sincronizacion.py`](pruebas/prueba_sincronizacion.py) | `python3 herramientas/pruebas/prueba_sincronizacion.py`: la sincronización en una copia del repositorio. |
| [`herramientas/pruebas/prueba_kanban.mjs`](pruebas/prueba_kanban.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_kanban.mjs`: el kanban editable con una base simulada. |
| [`herramientas/pruebas/prueba_tablero_arquitectura.py`](pruebas/prueba_tablero_arquitectura.py) | `python3 herramientas/pruebas/prueba_tablero_arquitectura.py`: el control del tablero de arquitectura, con un cambio por vez. |
| [`herramientas/pruebas/prueba_banco_de_pruebas.py`](pruebas/prueba_banco_de_pruebas.py) | `python3 herramientas/pruebas/prueba_banco_de_pruebas.py`: el formato y el cómputo del banco de pruebas, con un cambio por vez sobre una copia de la muestra. |
| [`herramientas/pruebas/prueba_tablero_arquitectura.mjs`](pruebas/prueba_tablero_arquitectura.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_tablero_arquitectura.mjs`: la página del tablero de arquitectura en Chrome. |

Las pruebas de la aplicación no están acá: están en `app/` y se corren con `mvn test` ([app/README.md](../app/README.md)).
