# Cómo se hizo: la medición contra el banco

La parte de El Parte que produce lo que se mide contra el banco de pruebas: las corridas en modo banco, una por cada noche del registro, en un Docker Compose aparte, y la salida que después cuenta [`herramientas/banco_de_pruebas.py`](../../herramientas/banco_de_pruebas.py) (tablero, T20). El diseño está en [adrs/0014-medicion-contra-el-banco.md](../../adrs/0014-medicion-contra-el-banco.md), y el código, en `app/src/main/java/io/github/andresdiegolanda/elparte/banco/`.

## Qué hace

```mermaid
sequenceDiagram
    participant A as Arranque del banco
    participant M as Medición
    participant I as Inicio de corrida
    participant N as La noche
    participant B as Base de la medición
    A->>M: medir del desde al hasta
    loop cada noche, en orden
        M->>B: ¿ya terminó esta noche?
        alt ya terminó
            M->>M: la saltea
        else no
            M->>B: lo gastado en el modelo
            M->>I: iniciar en modo banco
            I->>N: corrida.iniciada
            N->>B: candidatos, grados, hechos, mensajes
            M->>B: espera a que termine
        end
    end
    M->>B: corridas y mensajes del banco
    M->>M: escribe la salida en la carpeta de la medición
```

- **Arranca solo.** Con el perfil `banco`, que activa [`compose.banco.yaml`](../../compose.banco.yaml), la aplicación lee la medición y sus noches de las variables de entorno y mide en un hilo aparte. Mientras tanto, su sala de máquinas se puede mirar en el puerto 8081. Si falta una variable, lo dice en el registro y no mide.
- **Una noche por vez, en orden.** Inicia una corrida en modo banco, que sigue solo la entidad medida, y espera a que termine antes de la siguiente. Saltea las noches que ya terminaron en esa base, así una medición cortada se retoma levantándola de nuevo.
- **Tope de gasto.** Antes de cada noche suma lo gastado en el modelo por todas las corridas del banco; si llegó al tope, no inicia más y lo anota.
- **Cuida a ARCE.** El lector espera 250 ms antes de cada pedido en el perfil `banco`, y el explorador no vuelve a pedir lo que la corrida de la noche anterior ya leyó de una publicación: lo toma de su grabación y lo graba igual en la corrida actual, para que se pueda repetir.
- **La salida.** Al terminar escribe `salida-de-el-parte.json` en `banco-de-pruebas/mediciones/<medición>/`, con la forma de [banco-de-pruebas/formato.md](../../banco-de-pruebas/formato.md).

Cómo se corre está en [app/README.md](../../app/README.md), "Medir contra el banco".

## Por qué existe

La medición contra el banco es parte de la versión 0 y es lo que dice si El Parte sirve ([docs/plan.md](../plan.md)). Las reglas de cómo se cuenta ya estaban escritas y probadas (T12); faltaba producir lo que se cuenta sin mezclarlo con lo que ven los invitados, con la memoria que El Parte tendría noche a noche, y sin castigar a ARCE ni al presupuesto.

## Cómo se construyó

- **Sin código para separar.** La separación la da el nombre de proyecto de Compose: otra base, otro Kafka, otros volúmenes. `compose.banco.yaml` cierra los puertos de la base, de Kafka y del correo con `!reset`, y publica la aplicación en el 8081.
- **El modo banco es casi el normal.** Los agentes solo miran si una corrida es de reproducción, así que una del banco hace todo lo que hace una normal, sobre su propia base. Lo nuevo en el inicio es la fecha del registro y la entidad medida en lugar de las suscripciones. La repetición acepta como original una corrida del banco.
- **Reusar sin perder la repetición.** Lo que la noche anterior ya leyó se busca por la clave de la grabación (`arce:comprador:<id>` y `arce:publicacion:<id>`) en la corrida del banco de la fecha anterior, que sale con una sola consulta sobre la clave primaria. Una falla grabada no se reusa: se vuelve a intentar. Lo reusado pasa por la grabación de la corrida actual, así repetirla no necesita la anterior.
- **La salida desde la base.** Las corridas salen de `parte.corridas`; el costo y la duración, de `corrida.terminada`; las fuentes no repetibles y las lecturas fallidas, de `fuente.fallida`; y de cada clave de hecho, la primera noche en que salió PROVEN o INFERRED, o si nunca, la primera en que salió NOT_MENTIONED.

## Qué se probó

- **`MedicionContraElBancoTest`**, contra un PostgreSQL y un Kafka en contenedores, con tres noches de octubre del ARCE de mentira. [PROVEN: `mvn test`, 19 pruebas, 2026-11-15]
  - Las tres noches corren en orden, con tres llamadas al modelo y 0,012 USD.
  - ARCE recibe 14 pedidos; sin reusar lo de la noche anterior serían 17.
  - La salida tiene la forma del formato, y `herramientas/banco_de_pruebas.py` la valida y la cuenta.
  - Medir otra vez saltea las tres noches y no pide nada.
  - Una fuente que no deja pedir la fecha sale como no repetible y no como lectura fallida.
  - Con lo gastado en el tope, no se inicia ninguna noche más.
  - Una noche del banco se repite sin red ni costo, con los mismos mensajes.
  - Con el modelo fallando como falla la API sin crédito, la medición se detiene en la primera noche y no escribe la salida. [PROVEN: `mvn test`, 2026-11-16]
- **`LectorArceTest`** controla que el lector espere la pausa antes de cada pedido.
- **En Docker Compose**, en un espacio en la nube, con `compose.banco.yaml`. [PROVEN: dos mediciones de prueba, 2026-11-15]
  - Sin las variables, la aplicación lo dice y no mide.
  - Con ellas, corre dos noches en orden y escribe la salida en la carpeta montada.
  - Al levantarla otra vez, saltea lo hecho.
  - `docker compose down -v` borra todo sin pedir las variables.
  - Ahí ARCE no se alcanza desde los contenedores, así que las noches terminaron con la fuente fallida: esa prueba cubre el armado, no lo que encuentra El Parte.

## Qué falló

- **Las dos pruebas grandes comparten la base.** La de la primera rebanada contaba todos los hechos e informes de la base, y la medición agrega los suyos. Se cambió para que cuente solo lo de su noche de noviembre, y la medición usa noches de octubre con compras distintas. Las dos pasan en cualquier orden. [PROVEN: corridas en orden alfabético e inverso, 2026-11-15]
- **Un bloque de texto de Java se comió un espacio.** Los bloques de texto borran los espacios al final de cada línea, y una consulta armada con uno quedó `andh.clave...`. Se resolvió con `\s`.
- **El control de datos personales vio una URL de prueba.** La prueba agregaba a mano un `fuente.fallida` con una URL de `ejemplo.invalid`, que es justo lo que busca el control de la primera rebanada. Se usó una URL de ARCE.
- **La aplicación no podía escribir la salida en Linux.** La imagen corre con un usuario sin privilegios, y la carpeta montada era de otro usuario. La aplicación de la medición corre como root; la de todos los días sigue sin privilegios.
- **Compose pedía las variables hasta para bajar.** Con `${VARIABLE:?}`, `docker compose down` fallaba si no estaban. Las variables pasaron a ser opcionales para Compose, y la aplicación las controla.

## Las primeras tres noches reales

Corrieron en la máquina del responsable, con la clave, del 2026-11-11 al 2026-11-13, con un tope de 1 USD. La salida está en `banco-de-pruebas/mediciones/2026-11-15-1/salida-de-el-parte.json`. [PROVEN: corrida del 2026-11-15, de la 01:38 a la 01:56]

| Noche | Duración | Costo | Publicaciones leídas | De la noche anterior | Candidatos | Sin procesar | Llamadas al modelo |
|---|---|---|---|---|---|---|---|
| 2026-11-11 | 8,0 min | 0,489 USD | 1.597 | 0 | 60 | 13 | 60 |
| 2026-11-12 | 5,8 min | 0,448 USD | 1.518 | 806 | 60 | 12 | 49 |
| 2026-11-13 | 3,7 min | 0,266 USD | 1.119 | 712 | 55 | 0 | 27 |

- **Costo y tiempo.** 1,20 USD y 18 minutos las tres; unos 0,009 USD por llamada al refutador. La primera noche cuesta más porque todo es nuevo; las siguientes, menos, porque lo del margen ya es conocido.
- **Lo que se ahorra a ARCE.** De 4.234 publicaciones leídas, 1.518 se tomaron de la noche anterior: un 36 % menos de pedidos.
- **El tope de gasto se pasa por una noche.** Se mira antes de empezar cada una: después de la segunda iban 0,94 USD, y la tercera llevó el total a 1,20.
- **El tope de 60 candidatos se quedó corto.** En dos noches quedaron compras del BPS sin procesar, y las del día de margen, que la noche anterior ya había visto, ocupaban lugares. Desde entonces, al tope entran primero las compras de la fecha objetivo (`ExploradorArceTest`).
- **Las 78 noches costarían unos 31 USD** con ese promedio de 0,40 USD por noche, y tardarían unas 8 horas. [INFERRED: de tres noches] Es más que el tope mensual del espacio de trabajo de la clave, de 20 USD (T14).
- **El conteo en PowerShell.** Guardar la medición con `>` en Windows PowerShell 5.1 la dejaba en UTF-16. El conteo ahora escribe el archivo él mismo, en UTF-8.

## Las 78 noches

Corrieron en la máquina del responsable, del 2026-08-29 al 2026-11-14, con un tope de 35 USD, después de subir los topes de gasto de la clave. La salida y el conteo están en `banco-de-pruebas/mediciones/2026-11-15-2/`. [PROVEN: medición del 2026-11-15, de las 20:16 a las 23:41]

| Qué | Cuánto |
|---|---|
| Noches | 78 completas, ninguna cortada ni fallida |
| Costo | 12,71 USD, 0,16 USD por noche |
| Duración | 2,6 minutos por noche, 3 horas y 23 minutos en total |
| Publicaciones de ARCE leídas | 64.700 |
| Candidatos del BPS | 2.139, de los que 196 quedaron sin procesar |
| Llamadas al modelo | 1.333 |
| Hechos que salieron | 800: 725 PROVEN y 75 INFERRED; 51 descartados |

El conteo, con el borrador de la revisión: [PROVEN: `banco-de-pruebas/mediciones/2026-11-15-2/medicion.json`]

- **Publicables: 5 de 5.** Las cinco compras del BPS que la vigilancia vio el 2026-11-01 y descartó por tema salieron en El Parte la noche en que se publicaron, con una mediana de 26 días de adelanto. Las cinco tienen su llamado y su aclaración, y una también su adjudicación.
- **Hallazgos: 0 de 1.** De los 19 hallazgos, el único que esta medición podía encontrar es la compra directa del BPS a ATOS (`2026-09-29-1`), y no está. Ningún hecho la menciona, y entre las publicaciones del BPS que el feed trajo en el período y quedaron grabadas no hay ninguna de ATOS ni de un servicio de desarrollo y mantenimiento de software. [PROVEN: búsqueda en la base de la medición, 2026-11-16] Como el feed no trae nada del 23 al 29 de octubre, no se puede descartar que se haya publicado esa semana, ni que se publique después del 14 de noviembre. [INFERRED] Qué fuente lleva esa entrada lo decide quien confirma la revisión. Los otros 18 son de fuentes que esta medición no lee, o de ninguna.
- **Precisión: 95 %.** Los 11 hechos con correspondencia están bien. Los otros 789 no corresponden a nada del registro, y de ellos se juzgó un sorteo de 60, con la semilla 20261116 (`banco-de-pruebas/metricas.md`, "Cuando los extras son demasiados"): 57 bien y 3 con el contenido equivocado. La precisión estimada de los extras es 0,95, con un intervalo al 95 % de 0,86 a 0,98, y la de todo lo publicado, 0,95. Ninguno era de otra entidad ni material viejo.
- **El error que encontró el sorteo.** Los tres hechos equivocados son adjudicaciones de compras repartidas entre varios proveedores: El Parte dice que la compra se adjudicó al primero, con sus ítems, y no nombra a los demás. El explorador arma la afirmación solo con la primera adjudicación de la publicación (`app/src/main/java/io/github/andresdiegolanda/elparte/fuentes/arce/CandidatosDeArce.java`), y el refutador la da por PROVEN porque cada pedazo está en la fuente. Se arregla en T39.
- **Más barata que lo estimado.** Las tres noches de prueba hacían esperar unos 31 USD; costó 12,71, porque muchas noches son fines de semana con pocas compras.

Dos problemas que la medición dejó a la vista:

- **Una semana sin datos en ARCE.** El feed de octubre no trae nada publicado del 2026-10-23 al 2026-10-29, cinco días hábiles; el resto del mes trae entre 550 y 670 publicaciones por día hábil. [PROVEN: feed de 2026-10 bajado el 2026-11-15 a las 23:05] Las noches del 24 al 29 terminaron completas y sin leer nada: El Parte no avisa cuando una fuente viene vacía un día hábil.
- **El tope de 60 candidatos.** Dejó 196 compras del BPS sin procesar, casi todas en noviembre; la noche del 2026-11-04, 60.

## La medición que se quedó sin crédito

La medición `2026-11-16-1` iba a medir de una vez el analista (T21), el presupuesto que corta (T22) y el explorador arreglado (T39), con 2 USD y 400 llamadas por noche y un tope de 25 USD.

- Las primeras 22 noches, del 2026-08-29 al 2026-09-23, anduvieron bien: 6,11 USD y unos 110 segundos por noche. El analista escribió 395 análisis, y en 20 el control de cifras descartó el "qué dice" y dejó la afirmación (ADR 0015). [PROVEN: base de la medición, 2026-11-16]
- En la noche del 2026-09-24 la API empezó a responder que no había crédito, y desde ahí falló cada llamada al modelo: 1.029 del refutador y una del analista. [PROVEN: base de la medición, 2026-11-16]
- **La medición no se dio cuenta.** El refutador gradúa NOT_MENTIONED lo que no puede verificar, así que cada noche siguiente terminó completa y sin hechos, igual que una noche sin novedades. Se paró a mano en la noche del 2026-11-05, sin escribir la salida, y no se usa.

Por eso la medición ahora se detiene en la primera noche con errores del modelo y no escribe la salida (`app/src/main/java/io/github/andresdiegolanda/elparte/banco/MedicionContraElBanco.java`). Esa noche no se puede volver a correr en la misma base, porque ya está terminada: se corrige la causa y se mide de nuevo, con otro nombre. En la medición `2026-11-15-2` no hubo ningún error del modelo en 1.333 llamadas, así que la regla no la habría frenado. [PROVEN: base de esa medición, 2026-11-16]

## Las 78 noches con el analista, el presupuesto y el explorador arreglado

La medición `2026-11-16-2` repitió las 78 noches con el commit `a3c8adc`: el analista completo (T21), el presupuesto que corta, con 2 USD y 400 llamadas por noche para no cortar ninguna (T22), y el explorador arreglado (T39). La salida y el conteo están en `banco-de-pruebas/mediciones/2026-11-16-2/`, contados con el borrador de la revisión. [PROVEN: `banco-de-pruebas/mediciones/2026-11-16-2/medicion.json`, 2026-11-17]

| | `2026-11-15-2` | `2026-11-16-2` |
|---|---|---|
| Costo | 12,71 USD, 0,163 por noche | 19,11 USD, 0,245 por noche |
| Duración por noche | 156 segundos | 166 segundos |
| Noches | 78 completas | 78 completas, ninguna cortada |
| Publicables encontrados | 5 de 5 | 5 de 5, con 26 días de adelanto |
| Hallazgos encontrados | 0 de 1 | 0 de 1 |
| Hechos PROVEN o INFERRED | 800 | 806: 727 PROVEN y 79 INFERRED |
| Sorteo de 60 extras | 57 bien: 0,95, de 0,86 a 0,98 | 60 bien: 1,0, de 0,94 a 1,0 |

- **El arreglo de las adjudicaciones repartidas funciona.** Los 60 extras del sorteo nuevo, con la semilla 20261117, dicen lo que dice la publicación que El Parte leyó de ARCE, entre ellos cuatro adjudicaciones repartidas entre varios proveedores, que ahora los nombran a todos. De los tres hechos que el sorteo anterior encontró mal, dos están en esta salida y ahora están bien; el tercero no salió en esta medición. [PROVEN: lectura de las publicaciones grabadas en la base de la medición, 2026-11-17]
- **El aviso de día vacío funciona.** Las noches del 23 y del 26 al 29 de octubre, días hábiles, terminaron con `fuente.fallida` de tipo `sinPublicaciones` y no como noches tranquilas: el feed de octubre de ARCE no trae nada de esos días, y sí del 22 y del 30. [PROVEN: `https://www.comprasestatales.gub.uy/ocds/rss/2026/10`, leído el 2026-11-17] En la salida figuran como noches con la fuente no repetible.
- **Dos lecturas fallidas,** de la misma publicación: la adjudicación 1352991 respondió NotFound en las noches del 15 y el 16 de setiembre. [PROVEN: base de la medición]
- **Lo que cuesta el analista:** 5,62 USD en 1.217 llamadas, 0,072 por noche. El refutador gastó 13,49 USD en 1.396 llamadas, algo más que en la medición anterior, porque ya no hay tope de candidatos. [PROVEN: mensajes `uso.modelo` de la medición] De las 1.217 versiones PROVEN o INFERRED, el control dejó "Sin análisis" el "por qué importa" en 7 y el "qué mirar" en 33, por una cifra que no está en la evidencia o porque el modelo no lo escribió; en 94, el "qué dice" quedó igual a la afirmación. [PROVEN: base de la medición] El banco no juzga los análisis: eso pide su propio sorteo.
- **El costo por noche,** para fijar el presupuesto (T22): la mediana es 0,21 USD, el 90 % de las noches cuesta menos de 0,56, y la más cara, la del 2026-11-04, costó 1,14 USD en 618 segundos. 13 de las 78 noches pasaron los 0,50 USD del presupuesto por omisión, y se habrían cortado. La que más llamó al modelo hizo 159 llamadas, menos que las 200 por omisión. [PROVEN: salida y base de la medición]
- **Un juicio que quedó viejo.** Los juicios de los extras se guardan por la clave del hecho, y la clave no cambia cuando cambia lo que el hecho dice. Los dos hechos que antes estaban mal y ahora están bien siguen juzgados como contenido equivocado, y por eso la precisión de todo lo juzgado da 0,98 y no 1,0. La estimación del sorteo no cambia: sus 60 se juzgaron sobre lo que dice esta medición. Para que no vuelva a pasar, cada juicio tendría que guardar la huella del contenido que se juzgó.

## Qué falta

- Que el responsable confirme la revisión: la fuente y la etiqueta de cada entrada, las correspondencias y los juicios de los dos sorteos, los 56 nuevos incluidos; y si los dos juicios viejos pasan a correctos (T20).
- Decidir, al confirmar la revisión, si la compra directa a ATOS se cuenta como de ARCE (T20).
- Que cada juicio de un extra guarde la huella del contenido que se juzgó, para que un juicio viejo no cuente sobre un hecho que cambió (T20).
- Fijar los valores del presupuesto con lo medido (T22).
- Un sorteo de análisis, para saber si dicen algo falso sin cifras (T21).
