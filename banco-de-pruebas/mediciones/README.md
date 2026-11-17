# banco-de-pruebas/mediciones

Una carpeta por cada medición de El Parte contra el banco de pruebas, con el nombre de la medición, `AAAA-MM-DD-N` (la fecha en que se midió y un número de orden).

| Medición | Qué es |
|---|---|
| `banco-de-pruebas/mediciones/2026-11-15-1/` | Una prueba de tres noches, del 2026-11-11 al 2026-11-13, para conocer el costo y la duración antes de correr todas: 1,20 USD y 18 minutos (tablero, T20). No tiene `medicion.json` porque el registro todavía no está revisado. |
| `banco-de-pruebas/mediciones/2026-11-15-2/` | Las 78 noches del registro, del 2026-08-29 al 2026-11-14: 12,71 USD y 3 horas y 23 minutos. El conteo es con el borrador de la revisión: 5 de 5 publicables encontrados, 0 de 1 hallazgo, y una precisión estimada del 95 % con un sorteo de 60 de los 789 extras (T20). |
| `2026-11-16-1` (sin carpeta) | Las 78 noches con el analista (T21), el presupuesto (T22) y el explorador arreglado (T39). Se quedó sin crédito de la API en la noche del 2026-09-24, después de 22 noches y 6,11 USD, y se paró sin escribir la salida: no se usa. Lo que pasó está en [docs/como-se-hizo/medicion-contra-el-banco.md](../../docs/como-se-hizo/medicion-contra-el-banco.md), "La medición que se quedó sin crédito". |
| `banco-de-pruebas/mediciones/2026-11-16-2/` | Las 78 noches con el analista (T21), el presupuesto (T22) y el explorador arreglado (T39): 19,11 USD, 0,245 por noche, ninguna cortada. 5 de 5 publicables, 0 de 1 hallazgo, y un sorteo nuevo de 60 de los 796 extras, con la semilla 20261117: los 60 bien, con una precisión estimada de 1,0, de 0,94 a 1,0 (T20). El conteo es con el borrador de la revisión. |

| Archivo | Qué es | Quién lo escribe |
|---|---|---|
| `salida-de-el-parte.json` | Lo que produjeron las corridas en modo banco: cada noche con su estado, costo y duración, y cada hecho con su grado. La forma está en [banco-de-pruebas/formato.md](../formato.md). | La aplicación con el perfil `banco`, al terminar ([app/README.md](../../app/README.md), "Medir contra el banco"). |
| `medicion.json` | Lo que dio el conteo contra el registro y su revisión: cobertura, precisión, costo y duración. La forma está en [banco-de-pruebas/metricas.md](../metricas.md). | `python3 herramientas/banco_de_pruebas.py contar`. |

Las dos se versionan: llevan huellas, claves de hechos de fuentes públicas, fechas y conteos, y nada del registro, que no se versiona ([adrs/0007-registro-del-banco-fuera-del-repositorio.md](../../adrs/0007-registro-del-banco-fuera-del-repositorio.md)). Cómo se producen está en [adrs/0014-medicion-contra-el-banco.md](../../adrs/0014-medicion-contra-el-banco.md).
