# banco-de-pruebas

Lo que se versiona del banco de pruebas: el registro de una vigilancia anterior del BPS, contra el que se mide El Parte ([arranque/proyecto.md](../arranque/proyecto.md), "El banco de pruebas"). El registro en sí no se versiona, porque es material de otro proyecto y tiene datos personales ([adrs/0007-registro-del-banco-fuera-del-repositorio.md](../adrs/0007-registro-del-banco-fuera-del-repositorio.md)).

| Ruta | Qué tiene |
|---|---|
| [`banco-de-pruebas/formato.md`](formato.md) | La forma del registro, de su revisión y de la salida de El Parte que se compara, con la huella que identifica cada registro. |
| [`banco-de-pruebas/metricas.md`](metricas.md) | Cómo se mide: qué resultado tiene cada entrada y cada hecho, cómo se cuentan los descartes, las cuentas y la forma de una medición. |
| `banco-de-pruebas/muestra/registro/` | Un registro inventado con la forma del real, y su revisión en `revision/`. |
| [`banco-de-pruebas/muestra/salida-de-el-parte.json`](muestra/salida-de-el-parte.json) | Una salida de El Parte inventada, para esa muestra. |
| [`banco-de-pruebas/muestra/medicion-esperada.json`](muestra/medicion-esperada.json) | La medición que tienen que dar la muestra y su salida. |
| `banco-de-pruebas/privado/` | El registro real y su revisión. No se versiona: `.gitignore` lo deja afuera. |
| `banco-de-pruebas/mediciones/` | Va a tener una medición por archivo, con huellas y conteos y sin contenido del registro (tablero, T20). |

Todo lo que dice la muestra está inventado. Usa los dominios de las fuentes de El Parte para que la revisión tenga sentido, pero sus direcciones, sus claves y sus organismos no existen.

## Controlar un registro

Desde la raíz del repositorio:

```
python3 herramientas/banco_de_pruebas.py validar banco-de-pruebas/privado
python3 herramientas/banco_de_pruebas.py contar banco-de-pruebas/privado <salida de El Parte>
```

Sin argumentos, los dos usan la muestra. `validar` dice si el registro y su revisión cumplen el formato, con los avisos donde el registro se contradice, y su huella. `contar` escribe la medición. El verificador del repositorio controla que la muestra cumpla el formato sin avisos y que dé la medición esperada.
