# Mapa del repositorio

Qué hay en cada carpeta. Cuando se agrega una carpeta o un archivo que una sesión nueva tiene que conocer, se suma acá.

| Ruta | Qué es |
|---|---|
| [`README.md`](../README.md) | Qué es el proyecto y sus reglas. |
| [`arranque/`](README.md) | Lo que lee una sesión nueva, en orden. |
| [`tablero.md`](../tablero.md) | Todo lo pendiente, en tarjetas con dueño. Es lo que manda. |
| [`CHANGELOG.md`](../CHANGELOG.md) | Cada cambio, corto, con el enlace a su porqué. |
| [`adrs/`](../adrs/README.md) | Las decisiones significativas, una por archivo, con su índice. |
| `docs/` | El diseño y, en `docs/como-se-hizo/`, una página por componente. |
| [`obsoletos/`](../obsoletos/README.md) | Lo que ya no se usa, con su ruta original y el motivo. Nada se borra. |
| `herramientas/` | El verificador, el generador del kanban y el armado del sitio. |
| `kanban/` | La plantilla del kanban. La página, `kanban/index.html`, se genera y no se versiona. |
| `.github/workflows/` | La publicación del sitio en cada push y las pruebas de noche. |
| `tmp/` | Lo temporal de cada sesión. No se versiona. |
