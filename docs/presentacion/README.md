# presentacion

Una presentación de El Parte para quien no conoce el proyecto: qué problema resuelve, qué hace, un ejemplo real, cómo funciona por dentro, por qué se le puede creer, cómo se mide y dónde está. Son siete diapositivas, con lo que se puede decir en cada una como notas del orador.

| Archivo | Qué es |
|---|---|
| [`docs/presentacion/el-parte.md`](el-parte.md) | La presentación, en Markdown de [Marp](https://marp.app/): cada diapositiva va separada de la siguiente por una línea `---`, y las notas del orador son comentarios HTML. |
| [`docs/presentacion/tema-el-parte.css`](tema-el-parte.css) | El tema: colores, tipografías y el armado en dos columnas del ejemplo. Parte del tema `default` de Marp. |
| [`docs/presentacion/el-parte.pdf`](el-parte.pdf) | La presentación exportada a PDF, para mandarla o mostrarla sin instalar nada. Se genera desde `docs/presentacion/el-parte.md` con el comando de abajo, y se vuelve a generar cada vez que la presentación cambia. |
| `docs/presentacion/imagenes/` | Los dibujos, en SVG, con sus textos adentro. Los genera [`herramientas/generar_presentacion.py`](../../herramientas/generar_presentacion.py) y no se editan a mano. |

## Por qué Marp

La presentación vive en el repositorio y se compara en GitHub línea por línea, como el resto de la documentación. Marp es Markdown casi puro, y desde el mismo archivo genera HTML, PDF o PowerPoint.

Lo que se resigna:

- El PowerPoint que genera tiene una imagen por diapositiva: no se puede editar en PowerPoint. Hay un modo editable, experimental, que además pide LibreOffice.
- Exportar a PDF, PowerPoint o imágenes pide un navegador instalado (Chrome, Edge o Firefox).
- El armado es más rígido que en un editor visual, y Marp no dibuja Mermaid: los diagramas van como SVG.
- En GitHub se ve como un documento, no como diapositivas.

## Cómo se presenta y se exporta

Con la extensión Marp for VS Code, se abre `docs/presentacion/el-parte.md` y se mira la vista previa. Para que la extensión encuentre el tema, en la configuración del espacio de trabajo va `"markdown.marp.themes": ["docs/presentacion/tema-el-parte.css"]`.

Desde la raíz del repositorio, con Node y un Chrome instalados:

```
npx @marp-team/marp-cli --theme-set docs/presentacion/tema-el-parte.css --allow-local-files --pdf docs/presentacion/el-parte.md
```

Ese comando escribe `docs/presentacion/el-parte.pdf`, el que se versiona. Con `--pptx` en lugar de `--pdf` sale el PowerPoint, y sin ninguno de los dos, una página HTML. `--allow-local-files` hace falta para que el PDF y el PowerPoint lleven los dibujos.

Para cambiar un dibujo, se cambia `herramientas/generar_presentacion.py` y se corre `python3 herramientas/generar_presentacion.py`.

## De dónde sale cada cifra

| Cifra | Fuente |
|---|---|
| Entre 550 y 670 publicaciones por día hábil en compras estatales, en octubre de 2026 | [`docs/fuentes/compras-estatales.md`](../fuentes/compras-estatales.md). [PROVEN: conteo sobre el feed mensual, 2026-11-07] |
| El llamado del ejemplo: lo que publicó compras estatales, lo que escribió El Parte y su grado | https://www.comprasestatales.gub.uy/ocds/release/llamado-1367309 y la base de la medición `2026-11-15-2`, corrida `c-2026-08-29-banco`. [PROVEN: consulta a la base, 2026-11-15] |
| Esa noche: 571 publicaciones leídas, 25 del BPS, 0,22 USD; 22 PROVEN, 1 INFERRED y 2 NOT_MENTIONED | El registro de la aplicación y la base de la misma corrida. [PROVEN: 2026-11-15] |
| 78 noches, del 2026-08-29 al 2026-11-14, y 19 hallazgos con sus noches | [`banco-de-pruebas/formato.md`](../../banco-de-pruebas/formato.md) y la lista de hallazgos del registro, que no se versiona (ADR 0007). [PROVEN: lectura del registro, 2026-11-15] |
| Tres noches de prueba: 1,20 USD y 18 minutos | [`docs/como-se-hizo/medicion-contra-el-banco.md`](../como-se-hizo/medicion-contra-el-banco.md). [PROVEN: corrida del 2026-11-15] |
| Las 78 noches: unos 31 USD y unas 8 horas | La misma página. [INFERRED: de tres noches] |
| Los cuatro fines de semana y qué entra en cada uno | [`docs/plan.md`](../plan.md) y el tablero, [`tablero.md`](../../tablero.md). [PROVEN: 2026-11-15] |

La presentación es una foto del 15 de noviembre de 2026: la diapositiva "Dónde estamos" y las cifras de la medición se actualizan cuando cambian.
