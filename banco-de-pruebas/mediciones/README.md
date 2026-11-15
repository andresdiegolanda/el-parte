# banco-de-pruebas/mediciones

Una carpeta por cada medición de El Parte contra el banco de pruebas, con el nombre de la medición, `AAAA-MM-DD-N` (la fecha en que se midió y un número de orden). Todavía no hay ninguna real (tablero, T20).

| Archivo | Qué es | Quién lo escribe |
|---|---|---|
| `salida-de-el-parte.json` | Lo que produjeron las corridas en modo banco: cada noche con su estado, costo y duración, y cada hecho con su grado. La forma está en [banco-de-pruebas/formato.md](../formato.md). | La aplicación con el perfil `banco`, al terminar ([app/README.md](../../app/README.md), "Medir contra el banco"). |
| `medicion.json` | Lo que dio el conteo contra el registro y su revisión: cobertura, precisión, costo y duración. La forma está en [banco-de-pruebas/metricas.md](../metricas.md). | `python3 herramientas/banco_de_pruebas.py contar`. |

Las dos se versionan: llevan huellas, claves de hechos de fuentes públicas, fechas y conteos, y nada del registro, que no se versiona ([adrs/0007-registro-del-banco-fuera-del-repositorio.md](../../adrs/0007-registro-del-banco-fuera-del-repositorio.md)). Cómo se producen está en [adrs/0014-medicion-contra-el-banco.md](../../adrs/0014-medicion-contra-el-banco.md).
