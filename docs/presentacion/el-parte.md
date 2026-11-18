---
marp: true
theme: el-parte
paginate: true
lang: es
title: El Parte, en ocho diapositivas
---

<!-- _class: oscura portada -->
<!-- _paginate: false -->
<!-- _footer: Solo en compras estatales se publicaron entre 550 y 670 documentos por día hábil en octubre de 2026. -->

![bg right:42% 82%](imagenes/portada.svg)

### EL PARTE

# Cada día el Estado publica cientos de documentos. Nadie los lee todos.

El Parte los lee cada noche y a la mañana cuenta qué hay de nuevo, con la prueba de cada cosa.

<!--
El Parte es un proyecto didáctico. La idea entra en una frase: el Estado uruguayo publica muchísimo, en muchos sitios distintos, y nadie tiene tiempo de leerlo todo cada día. El Parte lo lee de noche y a la mañana dice qué hay de nuevo sobre lo que a uno le interesa. Lo que lo distingue es que cada cosa que dice lleva la prueba: el enlace a la fuente y un grado que dice cuán seguro está. El dato de abajo da la escala: solo el sitio de compras estatales publica entre 550 y 670 documentos por día hábil.
-->

---

## Qué hace El Parte

![w:1130](imagenes/que-hace.svg)

**Las cinco fuentes, todas andando:** compras estatales (ARCE), Diario Oficial (IMPO), normativa de Presidencia, BID y Banco Mundial. A la mañana, además de la página, llega un aviso por correo.

<!--
Tres pasos. Primero, cada persona elige qué entidades le interesan: un organismo como el BPS, una empresa, un programa. Segundo, de noche, El Parte lee lo que se publicó ese día en las fuentes públicas. Solo usa fuentes que permiten el uso de sus datos: datos abiertos, APIs o descargas oficiales; nada de sitios que prohíben la lectura automática. Tercero, a la mañana, cada persona ve lo nuevo sobre lo que sigue, con la fuente de cada cosa, y si no hubo nada nuevo la página lo dice en lugar de inventar; además le llega un aviso corto por correo. Son cinco fuentes: compras estatales, el Diario Oficial, la normativa de Presidencia, el BID y el Banco Mundial. Las cinco andan.
-->

---

<!-- _class: ejemplo -->
<!-- _footer: Esa noche leyó 571 publicaciones, se quedó con las 25 del BPS y verificarlas costó 0,22 USD. -->

## Un ejemplo real: una compra del BPS

```text
Lo que publicó compras estatales

buyer.name
  Banco de Previsión Social
tender.title
  Concurso de Precios 9807/2026
tender.description
  Contratación de servicio de mantenimiento
  integral de equipos aire acondicionado
  instalados en la sucursal del B.P.S. en la
  ciudad de Maldonado.
tender.tenderPeriod.endDate
  2026-09-14T11:00:00Z
```

> **Lo que dice El Parte a la mañana**
>
> “Banco de Previsión Social publicó el llamado Concurso de Precios 9807/2026 (Contratación de servicio de mantenimiento integral de equipos aire acondicionado instalados en la sucursal del B.P.S. en la ciudad de Maldonado.), con 1 ítem. Recibe ofertas hasta el 2026-09-14 11:00.”
>
> **PROVEN:** la fuente lo dice tal cual.

Fuente: [comprasestatales.gub.uy](https://www.comprasestatales.gub.uy/ocds/release/llamado-1367309), publicado el 28 de agosto de 2026 a las 11:20.

<!--
Un caso real, de la medición contra el banco de pruebas. A la izquierda, lo que publica el sitio de compras estatales: datos estructurados, en un formato abierto. A la derecha, cómo queda en la página de la mañana: una frase que se entiende, con el grado PROVEN porque la fuente dice exactamente eso, y el enlace para comprobarlo. Abajo, el embudo de esa noche: de 571 publicaciones, el explorador se quedó con las 25 que eran del BPS antes de llamar al modelo de lenguaje, y verificar esas 25 costó 22 centavos de dólar.
-->

---

## Por dentro: agentes con un solo trabajo cada uno

Un agente es un programa que usa un modelo de lenguaje para una tarea acotada. Se pasan el trabajo con mensajes, y cada mensaje queda guardado.

![w:1130](imagenes/agentes.svg)

<!--
En lugar de un solo programa que hace todo, son varios agentes con un trabajo cada uno, que se pasan el trabajo como en una línea de montaje. Los exploradores buscan en las fuentes: hay cinco, uno por fuente, porque cada fuente tiene su formato y sus fallas. El archivista es el único que recuerda: compara con lo que ya se vio para no anunciar dos veces lo mismo. El refutador, en naranja, es la pieza clave: intenta demostrar que cada hecho es falso, o viejo, o una deducción. El analista explica para qué sirve lo que sobrevive, y el editor arma la página de cada persona. Arriba, el observador mide todo: cuánto cuesta y cuánto tarda cada paso. Cada mensaje queda guardado, así que una noche se puede repetir entera sin costo para revisarla.
-->

---

<!-- _class: oscura -->
<!-- _footer: La noche del 29 de agosto: 25 compras del BPS, 22 PROVEN, 1 INFERRED y 2 NOT_MENTIONED. -->

## Por qué se le puede creer

El refutador intenta tirar abajo cada hecho y le pone uno de tres grados. El grado decide qué pasa con él.

![w:980](imagenes/grados.svg)

| Grado | Qué quiere decir | Qué pasa |
|---|---|---|
| PROVEN | La fuente lo dice tal cual. | Se publica con su enlace. |
| INFERRED | Se deduce de la fuente, pero no está escrito. | Una persona lo revisa antes. |
| NOT_MENTIONED | La fuente no lo dice. | No se publica. |

<!--
Esta es la regla que hace que el sistema no mienta. Ningún hecho llega a nadie sin que un agente haya intentado probar que es falso: que la fecha sea real y no material viejo, que no sea el mismo hecho de otro día, que la fuente diga eso y no otra cosa. Sale con uno de tres grados. PROVEN, la fuente lo dice tal cual: se publica con su enlace. INFERRED, se deduce pero no está escrito: en esta versión, todo lo inferido pasa por una persona antes de mostrarse como seguro. NOT_MENTIONED, la fuente no lo dice: no se publica. Abajo, cómo salió una noche real.
-->

---

## Cómo sabemos si funciona

Otra vigilancia siguió al BPS durante 78 noches. El Parte repite esas noches, con sus cinco fuentes, y se compara.

![w:1000](imagenes/medicion.svg)

| 5 de 5 | 60 de 60 | 2 de 4 | 0,25 USD |
|---|---|---|---|
| compras del BPS que la vigilancia descartó y El Parte publicó, 26 días antes | hechos de un sorteo, correctos: precisión de 0,94 a 1 | hallazgos de la vigilancia al alcance de las cinco fuentes, de 14 a 27 días antes | por noche, con las cinco fuentes |

<!--
Para saber si El Parte sirve hace falta compararlo con algo. Entre el 29 de agosto y el 14 de noviembre, otra vigilancia siguió al BPS todas las noches con un solo agente y anotó lo que encontró y lo que descartó: 84 entradas, de las que 19 son hallazgos. El Parte vuelve a correr esas mismas 78 noches, una por una, pidiéndole a cada fuente lo que publicó esa fecha, y después se cuenta: cuánto de lo que encontró la vigilancia encontró también El Parte, cuánto de lo que publica está bien, y cuánto cuesta. En compras estatales, El Parte publicó las cinco compras del BPS que la vigilancia había visto y descartado porque no eran de su tema, una mediana de 26 días antes. De lo que publicó y la vigilancia no tenía, una persona juzgó un sorteo de 60 hechos: los 60 estaban bien. De los 19 hallazgos, 15 estaban en sitios que El Parte no lee; de los 4 a su alcance encontró 2, una ley y un decreto del Diario Oficial, entre 14 y 27 días antes. Los otros dos son una compra directa que no está entre lo que compras estatales publicó esas noches y una operación que no está en la tabla de proyectos del BID. Una noche con las cinco fuentes cuesta unos 25 centavos de dólar, y casi todo es compras estatales.
-->

---

## ¿No alcanza con preguntarle al modelo?

Diez noches del BPS, las mismas publicaciones: una sesión con el modelo, sin agentes, contra El Parte.

| | Una sesión con el modelo | El Parte |
|---|---|---|
| Compras importantes encontradas | 5 de 5 | 5 de 5 |
| Precisión juzgada por una persona | 20 de 20 | 35 de 36 |
| Hechos, con lo que ya había contado | 310 | 175 |
| Una cifra que no está en la fuente | nada lo impide | no se publica |
| Costo de las diez noches | 1,53 USD | 4,11 USD |

Lo que agrega El Parte no es encontrar más: es no repetir, no dejar pasar una cifra sin evidencia y medirse noche por noche.

<!--
La pregunta honesta es si hace falta todo esto, o si alcanza con darle al modelo lo que se publicó cada noche y pedirle el parte. Se probó con diez noches del BPS y las mismas publicaciones. La sesión encontró lo mismo, con la misma precisión, por un tercio del costo y en un quinto del tiempo. Lo que El Parte agrega es otra cosa: el archivista se acuerda de lo que ya contó, así que no repite, 175 hechos contra 310; una cifra que no está en la evidencia no se publica, y en la sesión nada lo impide; y se mide contra el banco noche por noche. La comparación también mostró algo incómodo: el refutador casi no refutó, y casi todo lo que parecía refutación eran respuestas cortadas, que se arreglaron. Está escrito así en el repositorio.
-->

---

## Dónde estamos

![w:1130](imagenes/donde.svg)

La versión 0 está completa y publicada: cinco fuentes, usuarios invitados, un aviso por correo cada mañana, el expediente de cada entidad y, a pedido, una lectura del expediente con el modelo. Cada decisión, cada prueba y cada error quedan escritos en el repositorio: [github.com/andresdiegolanda/el-parte](https://github.com/andresdiegolanda/el-parte)

<!--
El plan de la versión 0 eran cuatro fines de semana, hasta el 29 de noviembre, y se terminó el 18. Primero la documentación: antes de escribir código se decidió qué fuentes entran, cómo se hablan los agentes y cómo se guarda cada hecho. Después la primera rebanada, chica pero completa: una fuente, de la red a la página de la mañana. Después la medición contra la vigilancia anterior, y las otras cuatro fuentes con los usuarios. Hoy está publicado, con un aviso por correo cada mañana y el expediente de cada entidad: todos sus hechos a lo largo del tiempo. Y a pedido, el modelo lee el expediente entero y propone observaciones que citan hechos, que el refutador también gradúa. Es un proyecto didáctico: todo queda documentado en el repositorio, incluido lo que salió mal, porque mostrar cómo se hizo es tan importante como el resultado.
-->
