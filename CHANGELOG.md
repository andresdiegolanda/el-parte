# CHANGELOG

Cada entrada dice qué cambió, dónde, y enlaza al documento que tiene el porqué. Las más nuevas van arriba.

## 2026-11-07

- Se agrega `LICENSE` con la licencia MIT, y el README suma etiquetas de licencia, tema, fuentes, estado e idioma. T13 pasa a Hecho. Por qué: [decisiones-iniciales.md](arranque/decisiones-iniciales.md), D13.
- T17 pasa a Hecho: el repositorio se sumó a la app de GitHub de Claude y el push desde la nube anda. Por qué: [tablero.md](tablero.md).
- `tablero.md` pasa de tablas a tarjetas por columna, con tipo, etapas, lista y registro, y es la única fuente de un kanban nuevo: `kanban/index.html`, generado con `herramientas/generar_kanban.py` desde `kanban/plantilla.html`. El verificador suma las reglas del tablero y el control de que el kanban esté al día. Se agregan las tarjetas T16 (el kanban) y T17 (acceso de escritura al repositorio), y `.gitignore` deja afuera la caché de Python de esas herramientas. Por qué: [ADR 0001](adrs/0001-tablero-fuente-unica-kanban-generado.md).
- Se crea `adrs/` con su índice y el primer ADR. Por qué: [arranque/como-se-trabaja.md](arranque/como-se-trabaja.md).
- El nombre El Parte deja de ser provisorio. Por qué: [decisiones-iniciales.md](arranque/decisiones-iniciales.md), D12.
- Se crea la base documental: [`README.md`](README.md) con las reglas, este CHANGELOG, el [tablero](tablero.md), la carpeta [`arranque/`](arranque/README.md), [`obsoletos/`](obsoletos/README.md), `.gitignore` y el verificador [`herramientas/verificar_docs.py`](herramientas/verificar_docs.py). Por qué: [arranque/proyecto.md](arranque/proyecto.md).
- La guía para sesiones nuevas pasa a ser la carpeta `arranque/` en lugar de `docs/arranque.md`, y las reglas 1 y 10 la nombran así. Por qué: [decisiones-iniciales.md](arranque/decisiones-iniciales.md), D11.
- Se fijan las decisiones de arranque D1 a D10: banco de pruebas, datos personales, modelo y tope de gasto, fuentes, revisión humana, idioma del código, repositorio, despliegue y calendario. Por qué: [decisiones-iniciales.md](arranque/decisiones-iniciales.md).
- Se anota lo que enseña una noche de muestra del banco de pruebas, sin incluir la muestra. Por qué: [proyecto.md](arranque/proyecto.md), sección "El banco de pruebas".
