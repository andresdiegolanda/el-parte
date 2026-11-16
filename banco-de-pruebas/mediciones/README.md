# banco-de-pruebas/mediciones

Una carpeta por cada medición de El Parte contra el banco de pruebas, con el nombre de la medición, `AAAA-MM-DD-N` (la fecha en que se midió y un número de orden).

| Medición | Qué es |
|---|---|
| `banco-de-pruebas/mediciones/2026-11-15-1/` | Una prueba de tres noches, del 2026-11-11 al 2026-11-13, para conocer el costo y la duración antes de correr todas: 1,20 USD y 18 minutos (tablero, T20). No tiene `medicion.json` porque el registro todavía no está revisado. |
| `banco-de-pruebas/mediciones/2026-11-15-2/` | Las 78 noches del registro, del 2026-08-29 al 2026-11-14: 12,71 USD y 3 horas y 23 minutos. El conteo es con el borrador de la revisión: 5 de 5 publicables encontrados, 0 de 1 hallazgo, y 789 extras sin juzgar (T20). |

| Archivo | Qué es | Quién lo escribe |
|---|---|---|
| `salida-de-el-parte.json` | Lo que produjeron las corridas en modo banco: cada noche con su estado, costo y duración, y cada hecho con su grado. La forma está en [banco-de-pruebas/formato.md](../formato.md). | La aplicación con el perfil `banco`, al terminar ([app/README.md](../../app/README.md), "Medir contra el banco"). |
| `medicion.json` | Lo que dio el conteo contra el registro y su revisión: cobertura, precisión, costo y duración. La forma está en [banco-de-pruebas/metricas.md](../metricas.md). | `python3 herramientas/banco_de_pruebas.py contar`. |

Las dos se versionan: llevan huellas, claves de hechos de fuentes públicas, fechas y conteos, y nada del registro, que no se versiona ([adrs/0007-registro-del-banco-fuera-del-repositorio.md](../../adrs/0007-registro-del-banco-fuera-del-repositorio.md)). Cómo se producen está en [adrs/0014-medicion-contra-el-banco.md](../../adrs/0014-medicion-contra-el-banco.md).
