# 0001. El tablero en Markdown es la fuente única y el kanban se genera desde él

- Estado: reemplazado por [0009](0009-kanban-editable-sincronizado.md)
- Fecha: 2026-11-07

## Contexto

La regla 8 pide que todo pendiente tenga dueño y viva en un tablero en Markdown. Hace falta además una vista de kanban, con columnas, tarjetas, etapas y registro, para ver el estado de un vistazo. Y todo el contenido del tablero tiene que quedar guardado en un documento del repositorio, que es el único handoff (regla 10).

Un kanban interactivo, donde las tarjetas se arrastran entre columnas, guarda su estado en su propia base de datos. Eso deja dos copias del tablero: la del kanban y la del repositorio.

## Decisión

`tablero.md` es la única fuente. Cada tarjeta es un bloque `###` debajo de un `##` con el nombre de su columna. `kanban/index.html` se genera desde `tablero.md` con `herramientas/generar_kanban.py`, a partir de `kanban/plantilla.html`, y nunca se edita a mano. El verificador falla si la página no coincide con lo que genera el tablero. La página es de solo lectura: puede publicarse como copia en otro lado, pero siempre es derivada.

## Razones

- Una sola copia: lo que está en el repositorio es el estado del proyecto, sin sincronizaciones.
- Es el mismo patrón que las láminas de arquitectura (regla 5): fuente en texto, salida generada y un verificador que detecta la diferencia.
- El historial de git guarda cada movimiento de tarjeta, con fecha y motivo, igual que cualquier otro cambio.
- El verificador puede controlar que el tablero no mienta: que lo que está "Para empezar" no tenga dependencias abiertas, que lo "Hecho" tenga todas sus etapas, que lo que espera diga a quién espera.
- La página generada es un solo archivo que se abre en cualquier navegador, sin servidor ni cuenta. [PROVEN: `kanban/index.html` abierto desde disco con Chrome sin ventana, 2026-11-07]

## Alternativas rechazadas

- **Kanban interactivo con su propia base de datos y una exportación periódica al repositorio.** Son dos copias que se desincronizan, y entre exportación y exportación el repositorio no dice la verdad.
- **Tablero en un servicio externo, como GitHub Projects.** Las tarjetas no serían un archivo del repositorio. [ASSUMED]
- **Una tabla en Markdown**, como la primera versión de `tablero.md`. No entran la lista, las notas ni el registro de cada tarjeta.
- **JSON o YAML como fuente.** Se lee peor que un Markdown y no se puede leer como documento en el repositorio.

## Costos

- No se arrastran tarjetas: mover una es mover su bloque en `tablero.md` y volver a generar la página.
- El formato de las tarjetas tiene reglas, y el verificador las hace cumplir.
- Una copia publicada del kanban hay que volver a publicarla después de cada cambio.

## Qué la haría reversar

Que editar el tablero a mano se vuelva un freno real, por ejemplo que el tablero quede días sin reflejar el estado del proyecto, o que haga falta que varias personas muevan tarjetas al mismo tiempo.
