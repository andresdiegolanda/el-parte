# herramientas

Los scripts que generan y controlan los archivos del repositorio. Usan solo la biblioteca estándar de Python, salvo las pruebas de páginas, que necesitan Puppeteer (viene con mermaid-cli) y un Chrome. Se corren desde la raíz del repositorio.

## Generadores

Lo que generan no se edita a mano: se cambia la fuente y se vuelve a generar.

| Script | Genera | Desde |
|---|---|---|
| [`herramientas/generar_kanban.py`](generar_kanban.py) | `kanban/index.html`, que no se versiona | `tablero.md` y `kanban/plantilla.html` |
| [`herramientas/generar_laminas.py`](generar_laminas.py) | `docs/laminas/<nombre>.svg` | `docs/laminas/<nombre>.json` |
| [`herramientas/generar_presentacion.py`](generar_presentacion.py) | `docs/presentacion/imagenes/*.svg`, los dibujos de la presentación | Los datos y el dibujo, en el mismo script |
| [`herramientas/generar_tablero_arquitectura.py`](generar_tablero_arquitectura.py) | `docs/tablero-de-arquitectura/index.html` y una página por vista, como `docs/tablero-de-arquitectura/usuarios.html`, que no se versionan | `docs/tablero-de-arquitectura/sistema.json` y `docs/tablero-de-arquitectura/plantilla.html` |
| [`herramientas/armar_sitio.py`](armar_sitio.py) | El sitio de GitHub Pages, por omisión en `tmp/sitio/`: una portada, el kanban y el tablero de arquitectura. Lo corre `.github/workflows/publicar.yml` en cada push a `main` y se publica en <https://andresdiegolanda.github.io/el-parte/> ([ADR 0017](../adrs/0017-forma-de-trabajo-mas-liviana.md)). | Los dos generadores de arriba |

## Control

| Script | Qué hace |
|---|---|
| [`herramientas/verificar_docs.py`](verificar_docs.py) | El verificador. Se corre antes de cada entrega: guiones largos, enlaces, tablero, que el kanban y el tablero de arquitectura se puedan generar, láminas, diagramas Mermaid, referencias a tarjetas y decisiones, la primera migración igual al documento de la base, el tablero de arquitectura igual al código y la muestra del banco de pruebas con su medición esperada. Sale con 1 si encuentra algo. |
| [`herramientas/banco_de_pruebas.py`](banco_de_pruebas.py) | Valida un registro del banco de pruebas y su revisión, y cuenta una medición: `validar [carpeta]` y `contar [carpeta salida.json [medicion.json]]`, por omisión sobre la muestra, y `sortear carpeta salida.json tamaño semilla`, que elige los extras a juzgar cuando son demasiados. Qué controla y qué cuenta está en [banco-de-pruebas/formato.md](../banco-de-pruebas/formato.md) y [banco-de-pruebas/metricas.md](../banco-de-pruebas/metricas.md). |

## Módulos

| Módulo | Qué es |
|---|---|
| [`herramientas/tablero_md.py`](tablero_md.py) | Lee, valida y vuelve a escribir `tablero.md`. Lo usan el generador del kanban y el verificador. |
| [`herramientas/tablero_arquitectura.py`](tablero_arquitectura.py) | Lee el modelo del tablero de arquitectura, lee del código lo que se puede leer sin compilarlo y compara los dos ([ADR 0012](../adrs/0012-tablero-de-arquitectura.md)). |

## Pruebas

| Prueba | Cómo se corre |
|---|---|
| [`herramientas/pruebas/prueba_kanban.mjs`](pruebas/prueba_kanban.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_kanban.mjs`: genera el kanban y lo abre en Chrome; comprueba las columnas, las fichas y que no haya nada para editar. |
| [`herramientas/pruebas/prueba_tablero_arquitectura.py`](pruebas/prueba_tablero_arquitectura.py) | `python3 herramientas/pruebas/prueba_tablero_arquitectura.py`: el control del tablero de arquitectura, con un cambio por vez. |
| [`herramientas/pruebas/prueba_banco_de_pruebas.py`](pruebas/prueba_banco_de_pruebas.py) | `python3 herramientas/pruebas/prueba_banco_de_pruebas.py`: el formato y el cómputo del banco de pruebas, con un cambio por vez sobre una copia de la muestra. |
| [`herramientas/pruebas/prueba_tablero_arquitectura.mjs`](pruebas/prueba_tablero_arquitectura.mjs) | `NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_tablero_arquitectura.mjs`: genera el tablero de arquitectura y lo abre en Chrome; comprueba que ande, que nada se pise ni se salga de su franja, y deja una captura de cada vista en `tmp/capturas/` (o en la carpeta de `CAPTURAS`). |

Las pruebas de la aplicación no están acá: están en `app/` y se corren con `mvn test` ([app/README.md](../app/README.md)). Todas, las de acá y las de `app/`, corren de noche y a demanda en `.github/workflows/pruebas.yml`.

La sincronización del kanban editable y su prueba se movieron a [`obsoletos/herramientas/`](../obsoletos/herramientas/sincronizar_tablero.py) (ADR 0017).
