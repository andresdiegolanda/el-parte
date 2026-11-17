# Cómo se hizo: el explorador de IMPO

La quinta fuente de la versión 0: las leyes y los decretos del Diario Oficial que nombran a una entidad seguida, sin avisos y con las personas tapadas (tablero, T30). La decisión está en [adrs/0010-impo-como-quinta-fuente.md](../../adrs/0010-impo-como-quinta-fuente.md), y la fuente, en [docs/fuentes/impo.md](../fuentes/impo.md).

## Qué hace

```mermaid
flowchart LR
    I[corrida.iniciada] --> E[Explorador de IMPO]
    E -->|1 pedido| IX[Índice de la edición<br/>PDF, no se guarda]
    IX -->|leyes y decretos| E
    E -->|1 pedido por norma,<br/>10 s entre pedidos| J[JSON de cada norma]
    J --> T[Tapado: sin firmantes,<br/>sin nombres con título]
    T -->|¿nombra a una<br/>entidad seguida?| C[candidatos]
    C --> R[Refutador]
    R -->|vuelve a leer la norma| J
    R -->|los artículos que<br/>nombran a la entidad| M[Modelo]
```

1. **El índice.** El explorador pide el índice de la edición de la noche (`/diariooficial/AAAA/MM/DD/indice.pdf`). Si ese día no hubo edición, IMPO responde con un texto y no con un PDF, y no hay nada que leer. Del PDF saca las leyes y los decretos; del índice se graba esa lista, no el PDF.
2. **Cada norma.** Pide el JSON de cada ley y cada decreto, en ISO-8859-1, a diez segundos del pedido anterior. La pausa es una para toda la aplicación: el refutador tampoco pide más rápido.
3. **El tapado.** Antes de mirarla, saca los firmantes y cambia por `[persona]` los nombres que siguen a un título ("Sra.", "Dr. Mag.", "Esc."), a un tratamiento ("señora", "ciudadano") o que van en mayúsculas antes de un cargo, como en la firma de una ley.
4. **El filtro.** Busca cada entidad seguida en el nombre, los vistos y los artículos de la norma: por su nombre, sin importar tildes ni mayúsculas, y por su sigla, en mayúsculas y como palabra entera. Si no nombra a ninguna, no sigue: todo esto pasa antes de cualquier llamada al modelo.
5. **El candidato.** Uno por norma, con el tipo de hecho `norma` y la clave `impo:decretos-223-2026:norma`. El resumen sale del sumario del índice, y la evidencia, del campo que nombra a la entidad. La URL es la pública de la norma en IMPO, como pide la licencia.
6. **El refutador** vuelve a leer la norma, la tapa y le muestra al modelo sus datos, los vistos y hasta doce artículos que nombran a la entidad, cada uno de hasta 4.000 caracteres.

## Por qué existe

De los 19 hallazgos del registro del banco de pruebas, 2 son de IMPO: un decreto que reglamenta el régimen de facilidades de pago ante el BPS y la Ley de Rendición de Cuentas. Sin IMPO, quedan fuera del alcance de El Parte ([ADR 0010](../../adrs/0010-impo-como-quinta-fuente.md)).

## Cómo se construyó

- **Primero la fuente real.** Antes de escribir código se volvió a leer el robots.txt y se bajaron, a diez segundos entre pedidos, tres índices y dos normas, las de los dos hallazgos. Lo que mostraron está en [docs/fuentes/impo.md](../fuentes/impo.md), "Lo que se aprendió al construir el explorador": dónde está cada clase de norma, cómo se lee el índice, que la fecha de publicación no es la de promulgación y cómo firman las leyes.
- **PDFBox para el índice.** El índice es el único PDF que se lee. PDFBox, sin ordenar por posición, da cada documento en una línea que empieza con su número de orden, aunque el PDF esté a dos columnas.
- **El tapado, por reglas.** Como el de ARCE, sin el modelo. Se probó contra las dos normas reales: el decreto no tiene nombres en el texto, y la ley tenía dos en su firma, que la primera versión tapaba a medias.
- **El refutador, por fuente.** El mismo agente vuelve a leer ARCE o IMPO según de dónde viene el candidato. El pedido al modelo para ARCE no cambió, así las corridas grabadas se siguen repitiendo igual.
- **Una medición con algunas fuentes.** `ELPARTE_BANCO_FUENTES` acota la medición contra el banco a las fuentes que se nombran, para medir IMPO sin volver a pagar las 78 noches de ARCE ([app/README.md](../../app/README.md), "Medir contra el banco").

El código está en `app/src/main/java/io/github/andresdiegolanda/elparte/fuentes/impo/`.

## Qué se probó

- **Las piezas**, en `app/src/test/java/io/github/andresdiegolanda/elparte/fuentes/impo/PiezasDeImpoTest.java`: el índice da las leyes y los decretos con su identificador, y no las resoluciones; el tapado saca firmantes, nombres con título o tratamiento y firmas en mayúsculas, y no toca los cargos ni "Dirección Gral."; una entidad se reconoce por su nombre sin tildes y por su sigla, y la UTE no se confunde con la UTEC.
- **De punta a punta**, en `app/src/test/java/io/github/andresdiegolanda/elparte/fuentes/impo/ExploradorImpoTest.java`, con un IMPO de mentira que arma su índice en PDF: de una ley, dos decretos y una resolución, sale un candidato, el decreto que nombra al BPS; el que falla queda como `fuente.fallida`; la resolución no se pide; nada de lo grabado ni de los mensajes tiene a las personas; y la repetición da los mismos mensajes sin pedirle nada a IMPO.
- **Contra IMPO de verdad**, en la máquina del responsable, con una medición de una noche, la del 24/09/2026, solo con IMPO: leyó el índice y los tres decretos de esa edición, y se quedó con uno, el Decreto 223/026, el del hallazgo. [PROVEN: medición `2026-11-17-1`, registro de la aplicación, 2026-11-17] La medición no siguió: el modelo respondió que la clave había llegado al tope de gasto de su espacio de trabajo.

## Qué falló

- **La firma de las leyes.** Se esperaba que los nombres estuvieran solo en el campo de firmantes. En la ley de verdad, el último artículo trae la firma de la presidenta del Senado y de su secretario, con títulos encadenados ("Dr. Mag."): la primera versión tapó el primer título y dejó el nombre a la vista. Se vio al pasar el tapado por la ley real antes de medir. Ahora se tapan los títulos encadenados y los nombres en mayúsculas antes de un cargo.
- **"Dirección Gral."** La primera versión tomaba "Gral." como título de persona y tapaba nombres de oficinas. Se sacó de la lista.
- **El tope de gasto.** La medición de IMPO se cortó en su primera noche, sin gastar nada, porque la clave llegó al tope de gasto de su espacio de trabajo en la Claude Console. No es una falla del explorador; mientras dure, tampoco las corridas de cada noche pueden graduar nada.

## Qué falta

- **Medirlo contra el banco** con las 78 noches, solo con IMPO, cuando la clave tenga crédito otra vez, y revisar las correspondencias de los hechos de IMPO con los dos hallazgos.
