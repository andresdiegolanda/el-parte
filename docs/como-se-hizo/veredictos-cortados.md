# Los veredictos cortados del refutador

El refutador pedía cada veredicto con un máximo de 1200 tokens de salida. Cuando el modelo llegaba a ese máximo, la respuesta se cortaba a mitad del JSON, no se podía convertir, y el candidato quedaba NOT_MENTIONED con "No hubo veredicto del modelo": descartado sin que nadie lo hubiera refutado. Ahora el máximo es 4000 y la falla dice que la respuesta se cortó (tablero, T48).

## Qué pasó

- **En la medición de IMPO y el BID.** La medición `2026-11-17-3`, 78 noches solo con IMPO y el BID, dejó seis hechos de IMPO, y cuatro salieron NOT_MENTIONED. Uno era el Decreto 223/026, el de un hallazgo del registro, que quedó como visto y descartado. [PROVEN: `banco-de-pruebas/mediciones/2026-11-17-3/salida-de-el-parte.json`]
- **La causa, en una repetición.** La medición `2026-11-18-1` repitió las mismas noches con el mismo código y se guardó la base. Los tres veredictos que alcanzó a pedir, de los decretos 213, 214 y 223, usaron los 1200 tokens y terminaban a mitad del JSON. El 214, que en la medición anterior salió PROVEN, esta vez se cortó: depende de cuánto escriba el modelo cada vez. [PROVEN: `parte.grabaciones` de la medición `2026-11-18-1`, 2026-11-18] Se paró en la noche del 2026-09-27, ya con la causa a la vista, y no escribió salida.
- **En la producción.** De los 70 veredictos de las corridas de El Parte publicado, 13 llegaron a los 1200 tokens, y son los 13 NOT_MENTIONED que hubo: ninguno fue una refutación. Los otros 57 usaron entre 373 y 1073 tokens. En los que se pudieron leer, el modelo ya había escrito que todo coincidía, o el grado INFERRED, cuando se cortó. [PROVEN: `parte.grabaciones` y `parte.mensajes` de El Parte publicado, corridas del 2026-11-11 al 2026-11-16, consultados el 2026-11-18]

## Por qué pasaba

Los 1200 tokens se fijaron con las publicaciones de ARCE de la primera rebanada. Pero el veredicto copia fragmentos de la publicación, y explica qué se buscó y por qué se deduce lo que se deduce: cuando la publicación es larga, como un artículo de una norma de IMPO, o el modelo se extiende, no alcanza. La conversión fallaba, y el refutador hacía lo que tiene que hacer con un veredicto que no llegó: no publicar. El mensaje, "la respuesta del modelo no tiene la forma pedida", no distinguía una respuesta cortada de una mal formada, y por eso no se vio antes.

## Cómo se arregló

- **El máximo del refutador pasó a 4000 tokens**, en `app/src/main/java/io/github/andresdiegolanda/elparte/refutador/Refutador.java`. Un veredicto que termina usa lo que usa, así que el costo es el mismo; lo que cambia es la reserva del presupuesto antes de cada llamada, que con el precio de salida de 10 USD por millón pasa de 0,012 a 0,04 USD ([docs/como-se-hizo/presupuesto.md](presupuesto.md)).
- **La falla dice que se cortó**, en `app/src/main/java/io/github/andresdiegolanda/elparte/modelo/Modelo.java`. La API avisa con `max_tokens` cuando una respuesta llegó al máximo. Si esa respuesta no se puede convertir, la falla dice "la respuesta del modelo se cortó en el máximo de 4000 tokens de salida", y la grabación lo guarda, así la repetición de la corrida falla con el mismo mensaje. Una respuesta que llegó al máximo pero está entera vale.

## Qué se probó

- **Las piezas**, en `app/src/test/java/io/github/andresdiegolanda/elparte/modelo/RespuestaCortadaTest.java`: `max_tokens` es un corte y `end_turn` no; una respuesta cortada que no se puede convertir dice dónde se cortó; una mal formada sin corte sigue diciendo que no tiene la forma pedida; una que llegó al máximo pero está entera se convierte.
- **Contra el modelo de verdad**, con una medición nueva de IMPO y el BID: [docs/como-se-hizo/impo.md](impo.md).

## Qué falta

- **La medición de ARCE no se repitió.** La `2026-11-16-2` descartó 45 hechos como NOT_MENTIONED ([docs/como-se-hizo/medicion-contra-el-banco.md](medicion-contra-el-banco.md)). Por lo que se vio en la producción, muchos pueden ser veredictos cortados y no refutaciones [INFERRED: en la producción, los 13 NOT_MENTIONED fueron cortes], pero su base ya no está. Repetirla cuesta unos 19 USD, más de lo que queda de esta tanda.
- **Dónde van los tokens.** En la producción, las respuestas que llegaron a los 1200 tokens tienen entre 591 y 2187 caracteres, y las que terminaron, entre 1,06 y 2,65 caracteres por token de salida: pocos para un texto en español. [PROVEN: `parte.grabaciones` de El Parte publicado, 2026-11-18] Puede ser que el modelo piense antes de responder y eso cuente como salida [INFERRED: por los caracteres por token; no se verificó]. Si es así, el costo de cada veredicto tiene una parte que no se ve en el texto.
