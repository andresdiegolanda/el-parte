# Tablero

Todo pendiente de El Parte vive acá, con dueño (regla 8). Este archivo es lo que manda. El kanban es una vista de solo lectura de este archivo: se genera con `python3 herramientas/generar_kanban.py` y se publica en <https://andresdiegolanda.github.io/el-parte/kanban/> en cada push ([ADR 0017](adrs/0017-forma-de-trabajo-mas-liviana.md)).

- Actualizado: 2026-11-18
- Nota: La primera rebanada (T08) anda de punta a punta y se mide contra el banco las 78 noches del registro (T20); los resultados están en docs/como-se-hizo/medicion-contra-el-banco.md, con la revisión confirmada: T08 y T20 están hechas. El analista (T21), el presupuesto (T22, con el tope de cada noche en el ADR 0005) y el explorador arreglado (T39) están hechos y medidos. La cola de revisión humana (T23) y el login por enlace (T25) están hechos: correr y revisar son solo del responsable. El Parte está publicado desde la máquina del responsable, con un túnel de Cloudflare y el correo por Resend (T40, ADR 0016). En su perfil, cada invitado elige qué organismos seguir y si quiere el aviso diario (T34); el aviso anda y espera que el responsable lo prenda publicado (T35). La forma de trabajo se alivianó con el ADR 0017 (T45). El sistema, objeto por objeto, está en el tablero de arquitectura (docs/tablero-de-arquitectura/, ADR 0012), que se cambia en el mismo momento que el sistema.
- Calendario: 2026-11-07, 2026-11-14, 2026-11-21, 2026-11-28
- Repositorio: https://github.com/andresdiegolanda/el-parte
- Proyecto: El Parte
- Objetivo: la versión 0

## Cómo se usa

Cada `##` con nombre de columna agrupa tarjetas, y cada `###` adentro es una tarjeta. Mover una tarjeta es mover su bloque a otra columna. Dentro de una columna, el orden es la prioridad: la primera de "Para empezar" es la próxima. En este archivo las columnas van en el orden en que conviene leerlas (lo que se está haciendo primero); el kanban las muestra en el orden de la tabla de columnas.

Después de cada cambio se corre el verificador, que controla el formato canónico de las tarjetas y estas reglas:

- Una tarjeta en "Para empezar" o "En curso" tiene todas sus dependencias en "Hecho".
- Una tarjeta en "Pendiente" tiene al menos una dependencia que no está hecha. Si no, va en "Para empezar".
- Una tarjeta en "Esperando" dice a quién o a qué espera.
- Una tarjeta en "Hecho" tiene todas sus etapas y su lista tildadas.
- Toda tarjeta tiene tipo, dueño válido y registro.

### Columnas

| Columna | Qué quiere decir |
|---|---|
| Pendiente | Espera otra tarjeta |
| Para empezar | Se puede hacer ya |
| En curso | Alguien la está haciendo |
| Esperando | Una decisión, otra persona o un material |
| Hecho | Terminada; no se borra |

### Tipos y etapas

Las etapas son el ciclo fijo de cada tipo, y bajan las reglas del proyecto a cada tarjeta. La lista, en cambio, es libre.

| Tipo | Etapas |
|---|---|
| Documento | Borrador; Grados y fuentes; Diagramas; Releído y verificado |
| ADR | Contexto y alternativas; Fuentes verificadas; Escrito; Aceptado |
| Componente | Contrato; Código; Pruebas; Medido contra el banco; Página cómo se hizo |
| Web | Diseño; Código; Pruebas; Página cómo se hizo |
| Herramienta | Escrita; Probada; Documentada |
| Verificación | (sin etapas) |
| Gestión | (sin etapas) |

### Dueños

| Dueño | Quién es |
|---|---|
| desarrollo | La sesión que esté trabajando en el repositorio, de una persona o de un asistente |
| Andrés | Responsable del proyecto: decisiones, accesos, claves, revisión humana y material que no está en el repositorio |

### Cómo se escribe una tarjeta

```markdown
### T99 · Título corto, con verbo
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T02, T05 a T07
- Espera a: (solo en Esperando)
- Fecha: AAAA-MM-DD (opcional)
- Detalle: [ruta/al/documento.md](ruta/al/documento.md)

Notas libres, en uno o más párrafos.

Etapas:
- [x] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Algo concreto para hacer

Registro:
- AAAA-MM-DD: Qué pasó.
```

Los campos van pegados al título, sin línea en blanco. Los que no aplican se omiten. Los bloques "Etapas:", "Lista:" y "Registro:" van después de las notas; "Etapas:" lleva todas las etapas de su tipo, en orden.

## En curso

### T24 · Sumar los exploradores del BID, el Banco Mundial y Presidencia
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/como-se-hizo/bid.md](docs/como-se-hizo/bid.md), [adrs/0006-fuentes-de-la-version-0.md](adrs/0006-fuentes-de-la-version-0.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md)

Las otras tres fuentes de la versión 0, cada una con sus descartes de datos personales.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [x] Explorador del BID
- [ ] Explorador del Banco Mundial (API v3, campos acotados)
- [ ] Explorador de la normativa de Presidencia, a ritmo bajo

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-17: Pasa a Para empezar: T08 está hecha. Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Pasa a En curso. El explorador del BID anda: las operaciones para Uruguay aprobadas cada noche que nombran a una entidad seguida, probado con un BID de mentira y con el filtro por fecha contra la API real. La operación del hallazgo del BID en el registro no está en la tabla de proyectos, así que por fecha no se puede ver (docs/como-se-hizo/bid.md). Siguen el Banco Mundial y Presidencia, que no tienen entradas en el registro, después del material para la demo; medir espera crédito en la API.

## Para empezar

### T47 · Leer el expediente con contexto largo
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T33
- Detalle: [docs/como-se-hizo/expediente.md](docs/como-se-hizo/expediente.md)

El expediente muestra cada hecho de una entidad a lo largo del tiempo, pero nadie lo lee entero. Con contexto largo, el modelo podría ver patrones que una publicación sola no muestra: compras que se repiten, prórrogas en cadena, proveedores que vuelven. Antes del código hace falta un ADR: qué puede escribir el modelo sobre muchos hechos juntos, cómo se gradúa una afirmación que sale de varios, y qué se guarda afuera del modelo, en la base y en el repositorio.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [ ] El ADR: qué puede escribir el modelo sobre un expediente y cómo se gradúa
- [ ] Una lectura del expediente con su costo en el presupuesto

Registro:
- 2026-11-17: Creada al construir el expediente sin el modelo (T33). Va a Para empezar porque T33 está hecha; no entra en la tanda autorizada el 2026-11-17.

## Esperando

### T42 · Comparar El Parte con una sesión de la Claude Console sobre el banco
- Tipo: Verificación
- Dueño: desarrollo
- Depende de: T20
- Espera a: que el responsable juzgue el sorteo de 20 hechos de la sesión (tmp/consola/sorteo-de-la-sesion.jsonl, en su máquina)
- Detalle: [docs/verificaciones/comparacion-con-una-sesion.md](docs/verificaciones/comparacion-con-una-sesion.md), [banco-de-pruebas/README.md](banco-de-pruebas/README.md), [adrs/0014-medicion-contra-el-banco.md](adrs/0014-medicion-contra-el-banco.md), [adrs/0004-grados-de-evidencia.md](adrs/0004-grados-de-evidencia.md)

Media hora con la Claude Console, con lo que ARCE publicó cada noche, puede dar un parte que se lee mejor que el de El Parte. Lo que no da es cuánto acierta, de dónde sale cada afirmación, la garantía de que no agrega cifras y lo que cuesta por noche. Esta comparación lo mide con el mismo banco y el mismo conteo.

Lo que la plataforma vuelve gratis con cada versión es la orquestación y la infraestructura. Lo que no trae es el banco con lo que pasó de verdad, la evidencia de cada afirmación y la medición en el dominio. Si la sesión de la Console sale igual o mejor, lo que vale de El Parte es la medición y no la infraestructura, y se recorta. Va después del material para la demo.

Lista:
- [x] Una muestra de 10 noches del registro, con lo que ARCE publicó del BPS en cada una
- [x] La misma consigna a la Console, noche por noche, con la salida en el formato del banco
- [x] El conteo con la misma herramienta, y el costo y el tiempo de cada lado
- [ ] La conclusión escrita: qué se mantiene de El Parte y qué se recorta

Registro:
- 2026-11-16: Creada. Se hace después del material para la demo.
- 2026-11-17: Pasa a Para empezar: T20 está hecha. Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: El material para la demo está hecho: dos videos con voz, uno de cómo funciona y otro de la arquitectura, armados con capturas de la aplicación publicada y los recorridos del tablero de arquitectura, fuera del repositorio. Pasa a Esperando: sin crédito en la API no hay sesión que comparar.
- 2026-11-18: Con crédito otra vez, la sesión corrió sobre diez noches con las mismas publicaciones que leyó El Parte. La cobertura empata; la sesión cuesta un tercio, tarda un quinto y cuenta de nuevo lo que ya contó, y nadie la refuta. Su precisión espera que una persona juzgue un sorteo de 20 de sus hechos: los juicios de El Parte guardan la huella de otra redacción. Ver docs/verificaciones/comparacion-con-una-sesion.md.

### T30 · Sumar el explorador de IMPO
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T08
- Espera a: crédito en la API: la clave llegó al tope de gasto de su espacio de trabajo, y sin el modelo no se puede medir
- Detalle: [docs/como-se-hizo/impo.md](docs/como-se-hizo/impo.md), [adrs/0010-impo-como-quinta-fuente.md](adrs/0010-impo-como-quinta-fuente.md), [docs/fuentes/impo.md](docs/fuentes/impo.md)

La quinta fuente de la versión 0: solo el JSON de las normas del Diario Oficial, sin avisos y con las personas tapadas.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [ ] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Índice de cada edición: números de las normas del día
- [x] JSON de cada norma, en ISO-8859-1
- [x] Firmantes descartados y nombres de personas tapados en el texto
- [x] Diez segundos entre pedidos, sin búsqueda ni avisos
- [x] Probar la cadena con leyes y resoluciones, no solo con decretos

Registro:
- 2026-11-14: Creada con el ADR 0010.
- 2026-11-17: Pasa a Para empezar: T08 está hecha. Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Pasa a En curso. El explorador anda contra un IMPO de mentira y contra muestras reales del índice y de las normas, bajadas a diez segundos entre pedidos después de volver a leer el robots.txt. Sigue la medición contra el banco, solo con IMPO.
- 2026-11-17: El explorador anda: probado con un IMPO de mentira y con las dos normas reales de los hallazgos; las resoluciones no se leen, a propósito. Contra IMPO de verdad, la medición 2026-11-17-1, de una noche y solo con IMPO, leyó los tres decretos del 24/09 y se quedó con el 223/026, el del hallazgo, pero se cortó sin gastar: la clave llegó al tope de gasto de su espacio de trabajo. Pasa a Esperando. Ver docs/como-se-hizo/impo.md.

### T35 · Mandar el aviso diario del parte
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T25
- Espera a: que el responsable prenda el aviso en la aplicación publicada (ELPARTE_AVISO_PRENDIDO=true en el .env)
- Detalle: [docs/como-se-hizo/aviso-diario.md](docs/como-se-hizo/aviso-diario.md), [adrs/0013-acceso-perfil-y-parte-diario.md](adrs/0013-acceso-perfil-y-parte-diario.md), [docs/contratos.md](docs/contratos.md)

Cada mañana, a cada invitado que lo quiere, un aviso corto con la noche, cuántos hechos hay y el enlace a su página. No manda el contenido de los hechos.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] El mensaje informes suma el modo de la corrida, y el aviso saltea lo que no es normal
- [x] Correo y preferencia con el rol de cuentas
- [x] Registro de envíos en cuentas.envios_del_parte, para no mandar dos veces
- [x] El aviso: asunto con la noche y cuántos hechos; cuerpo con pendientes, corrida cortada y el enlace
- [x] El correo saliente por SMTP; el servicio real se decide en T27
- [ ] Prenderlo en la aplicación publicada

Registro:
- 2026-11-14: Creada con el diseño del parte diario (ADR 0013).
- 2026-11-16: Pasa a Para empezar: T25 está hecha.
- 2026-11-16: El servicio de correo quedó decidido en el ADR 0016: Resend por SMTP, con la misma configuración que el enlace para entrar.
- 2026-11-17: Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: El aviso anda: escucha los informes de las corridas normales, que ahora traen el modo, y manda uno por invitado y noche, con el rol de cuentas solo. La etapa del banco se tilda sin medir: el banco no tiene invitados. Publicado viene apagado. Pasa a Esperando: falta que el responsable lo prenda. Ver docs/como-se-hizo/aviso-diario.md.

## Pendiente

## Hecho

### T33 · Armar el expediente de cada entidad
- Tipo: Web
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/como-se-hizo/expediente.md](docs/como-se-hizo/expediente.md), [docs/arquitectura.md](docs/arquitectura.md), [docs/base-de-afirmaciones.md](docs/base-de-afirmaciones.md)

Todo lo que la base sabe de una entidad: cada hecho y cada versión, en orden, para consultar cuando se quiera y no solo lo de anoche.

Es donde el sistema necesita contexto largo. Los agentes de la noche ven un candidato por vez, a propósito, para que el grado no se contamine con el resto (ADR 0004). El expediente mira a cada organismo a lo largo del tiempo, y ahí aparecen patrones que una publicación sola no muestra. El contexto largo no alcanza solo: lo que no se puede perder se escribe afuera del modelo, en la base y en el repositorio.

Etapas:
- [x] Diseño
- [x] Código
- [x] Pruebas
- [x] Página cómo se hizo

Registro:
- 2026-11-14: Creada: el tablero de arquitectura mostró que el expediente estaba diseñado en la arquitectura y en la base, pero ninguna tarjeta lo construía.
- 2026-11-16: Suma por qué el expediente es donde hace falta contexto largo, y los agentes de la noche, no.
- 2026-11-17: Pasa a Para empezar: T08 está hecha. Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Hecha, sin el modelo: /expediente lista las entidades y /expediente/<entidad> muestra cada hecho con todas sus versiones, ordenado por la noche en que apareció, con un resumen mes por mes. Leerlo con contexto largo pasa a T47, que necesita decidir qué puede escribir el modelo sobre muchos hechos juntos. Ver docs/como-se-hizo/expediente.md. Pasa a Hecho.

### T26 · Escribir la sección "Cómo funciona" y las páginas "cómo se hizo"
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/como-se-hizo/README.md](docs/como-se-hizo/README.md), [docs/arquitectura.md](docs/arquitectura.md)

La sección de la aplicación que enlaza a la documentación, y la página "cómo se hizo" de cada componente que todavía no la tenga (regla 6).

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-17: Pasa a Para empezar: T08 está hecha. Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Hecha. La aplicación tiene /como-funciona, en el menú: la noche agente por agente, los grados, el presupuesto y la medición, con enlaces al tablero de arquitectura publicado, a las páginas cómo se hizo y a los ADRs. Cada componente construido ya tenía su página; se suma su índice, docs/como-se-hizo/README.md. Los diagramas son los de cada página y el tablero de arquitectura, que la página enlaza. Una prueba comprueba que cada página de docs/como-se-hizo/ esté enlazada. Pasa a Hecho.

### T29 · Cambiar el modelo de cada agente sin reiniciar la aplicación
- Tipo: Web
- Dueño: desarrollo
- Depende de: T22, T25
- Detalle: [docs/como-se-hizo/modelos.md](docs/como-se-hizo/modelos.md), [adrs/0003-eleccion-del-stack.md](adrs/0003-eleccion-del-stack.md), [adrs/0005-presupuesto-por-corrida.md](adrs/0005-presupuesto-por-corrida.md), [docs/base-de-afirmaciones.md](docs/base-de-afirmaciones.md)

El modelo de cada agente y los precios viven en la base, con historia, y el responsable los cambia desde la sala de máquinas. Cada cambio vale desde la corrida siguiente.

Etapas:
- [x] Diseño
- [x] Código
- [x] Pruebas
- [x] Página cómo se hizo

Lista:
- [x] Tablas de modelos y precios, con los valores iniciales en una migración
- [x] El inicio de la corrida congela los modelos en corrida.iniciada
- [x] Cada agente pasa su modelo en las opciones de cada pedido
- [x] Rol de responsable en la lista de invitados
- [x] Pantalla del responsable, que no deja elegir un modelo sin precio
- [x] La sala de máquinas muestra qué modelo usó cada agente

Registro:
- 2026-11-14: Creada al aceptar el ADR 0003.
- 2026-11-17: Pasa a Para empezar: T22 y T25 están hechas.
- 2026-11-17: Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Hecha. Las tablas con historia, los valores iniciales, la congelación en corrida.iniciada, el modelo en cada pedido, el rol de responsable y la sala que muestra el modelo de cada agente ya estaban. Se suma /modelos: se ve sin entrar, y el responsable cambia el modelo de un agente, solo entre los que tienen precio, y carga precios con su fuente. Ver docs/como-se-hizo/modelos.md. Pasa a Hecho.

### T43 · Que la corrida de la noche se ponga al día
- Tipo: Componente
- Dueño: desarrollo
- Detalle: [docs/como-se-hizo/despliegue.md](docs/como-se-hizo/despliegue.md), [app/src/main/java/io/github/andresdiegolanda/elparte/corrida/NochePorReloj.java](app/src/main/java/io/github/andresdiegolanda/elparte/corrida/NochePorReloj.java)

La corrida de cada noche sale con una tarea programada a las 6 de Uruguay. En la máquina del responsable eso falla de dos maneras: si la máquina virtual de Docker estuvo en pausa, sale tarde, tanto como duró la pausa; y si la máquina está apagada o dormida a esa hora, no sale. Tiene que correr apenas pueda, sin duplicar una noche que ya corrió.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Revisar cada pocos minutos si ya pasó la hora y la noche de ayer no tiene corrida normal, y correrla
- [x] Que una noche no corra dos veces si la revisión y la corrida a mano coinciden
- [x] Probarlo con un reloj de prueba: la hora pasó con la aplicación apagada, y con la aplicación prendida

Registro:
- 2026-11-17: Creada: la primera corrida automática publicada no salió a las 6; el reloj monótono de la JVM iba unos 44 minutos atrás por una pausa de la máquina virtual de Docker (docs/como-se-hizo/despliegue.md).
- 2026-11-17: La corrida de la noche del 2026-11-16 arrancó a las 6:47 de Uruguay, 47 minutos tarde. Al reiniciar, la aplicación vuelve a calcular la próxima corrida con la hora de verdad, pero una pausa posterior la vuelve a atrasar. [INFERRED]
- 2026-11-17: Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Hecha. Cada cinco minutos la aplicación mira el reloj de pared y corre la noche de ayer si ya pasó la hora y no tiene corrida normal; la variable es ELPARTE_CORRIDA_HORA, en lugar de ELPARTE_CORRIDA_CRON. Cada inicio toma un candado de la base, así una noche no corre dos veces. Lo prueba un reloj fijo antes y después de la hora y cuatro revisiones a la vez. La etapa del banco se tilda sin medir: el banco corre sus noches de otra forma. El ADR 0016 no se reemplaza: la decisión, correr a las 6 en la máquina del responsable, sigue; cambia cómo se pone al día, que el ADR dejaba a mano. Ver docs/como-se-hizo/despliegue.md. Pasa a Hecho.

### T44 · Que el alta avise cuando un invitado no es un correo
- Tipo: Componente
- Dueño: desarrollo
- Detalle: [docs/como-se-hizo/despliegue.md](docs/como-se-hizo/despliegue.md), [app/src/main/java/io/github/andresdiegolanda/elparte/acceso/AltaDeInvitados.java](app/src/main/java/io/github/andresdiegolanda/elparte/acceso/AltaDeInvitados.java)

Al arrancar, el alta de invitados da de alta cada correo de la configuración. Si uno no es un correo, por ejemplo un texto de ejemplo que quedó en el .env, hoy se da de alta igual y nunca recibe el enlace, sin ningún aviso.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Validar cada correo de la lista de invitados al arrancar
- [x] Decidir si un correo inválido se anota en el registro o si la aplicación no arranca
- [x] Probarlo con un invitado que no es un correo

Registro:
- 2026-11-17: Creada: salió de la lista de mejoras de la forma de trabajo, pero es un arreglo de este proyecto (ADR 0017).
- 2026-11-17: Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: Hecha. Un invitado sin forma de correo no se da de alta y queda un aviso en el registro, con su número y sin el texto; la aplicación arranca igual, para no dejar sin El Parte a los demás. Si ya estaba dado de alta, queda desactivado. Lo prueba un tercer invitado de las pruebas con el texto de ejemplo. La etapa del banco se tilda sin medir: el banco no tiene invitados, y lo demás no cambia. Ver docs/como-se-hizo/despliegue.md y app/README.md. Pasa a Hecho.

### T08 · Construir la primera rebanada de punta a punta
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T02 a T07, T10, T14
- Detalle: [docs/como-se-hizo/primera-rebanada.md](docs/como-se-hizo/primera-rebanada.md), [app/README.md](app/README.md), [docs/fuentes/compras-estatales.md](docs/fuentes/compras-estatales.md)

Chica y completa le gana a grande y a medias: una fuente, del explorador a la página de la mañana, con la sala de máquinas de esa corrida.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Esqueleto: la aplicación, su imagen y Docker Compose
- [x] Explorador de ARCE, filtrando por el BPS como comprador antes de llamar al modelo
- [x] Zona horaria y publicaciones atrasadas de ARCE
- [x] Migraciones de la base a partir de docs/base-de-afirmaciones.md, con el reparto de permisos por esquema
- [x] Archivista
- [x] Refutador
- [x] Editor
- [x] Página de la mañana
- [x] Sala de máquinas de la corrida

Registro:
- 2026-11-07: Creada.
- 2026-11-07: La fuente es ARCE (ADR 0006).
- 2026-11-14: Pasa a Para empezar: T14 está hecha.
- 2026-11-14: La etapa "Medido contra el banco" se tilda con la medición de T20.
- 2026-11-14: Pasa a En curso: empieza por el esqueleto, la aplicación y Docker Compose.
- 2026-11-14: Esqueleto hecho: la aplicación arranca en Docker Compose con PostgreSQL, Kafka y Mailpit, y su estado muestra la base y Kafka sanos. Ver app/README.md.
- 2026-11-14: La rebanada anda de punta a punta: una noche de ARCE llega a la página de la mañana, con su traza en la sala de máquinas, y se repite sin red ni costo con los mismos mensajes. Diecisiete pruebas contra contenedores y una corrida en Docker Compose. Pasa a Esperando: falta medirla contra el banco (T20). La grabación de las corridas queda en un ADR propuesto, el 0011 (T31).
- 2026-11-17: Medida contra el banco con T20, que pasa a Hecho. Pasa a Hecho.

### T20 · Medir la primera rebanada contra el banco de pruebas
- Tipo: Verificación
- Dueño: desarrollo
- Depende de: T12
- Detalle: [adrs/0014-medicion-contra-el-banco.md](adrs/0014-medicion-contra-el-banco.md), [banco-de-pruebas/metricas.md](banco-de-pruebas/metricas.md), [banco-de-pruebas/formato.md](banco-de-pruebas/formato.md), [docs/plan.md](docs/plan.md)

La medición es parte de la versión 0. Corre la rebanada sobre cada noche del registro y compara lo encontrado con lo que encontró la vigilancia anterior (D1). No espera a que T08 cierre: alcanza con que la rebanada corra de punta a punta, y con esta medición T08 tilda su etapa "Medido contra el banco".

Lista:
- [x] Diseño del modo banco (ADR 0014)
- [x] compose.banco.yaml y el perfil banco: las noches en orden, una por vez, en un Compose aparte
- [x] La corrida en modo banco: fecha objetivo, solo la entidad medida, y la repetición que acepta una corrida del banco
- [x] Cuidar a ARCE: pausa entre pedidos y sin releer compradores ya leídos en la medición
- [x] El tope de gasto de toda la medición
- [x] El tipo de falla sinFecha en fuente.fallida (docs/contratos.md)
- [x] La salida al terminar, en banco-de-pruebas/mediciones/<medición>/
- [x] Tres noches reales primero, en la máquina del responsable, para conocer costo, duración y pedidos
- [x] Al tope de candidatos entran primero las compras de la fecha objetivo
- [x] Avisar al responsable para que suba el tope de gasto del espacio de trabajo y de la organización, antes de las 78 noches
- [x] Correr la rebanada sobre cada noche del registro
- [x] Revisar el registro: la fuente y la etiqueta de cada entrada, y después las correspondencias y los extras
- [x] Decidir cómo se juzgan los extras: en las 78 noches salieron 800 hechos PROVEN o INFERRED, y 789 no corresponden a nada del registro
- [x] Juzgar un sorteo de 60 extras: 57 bien y 3 con el contenido equivocado
- [x] Confirmar los juicios de los dos sorteos, los 56 nuevos incluidos
- [x] Confirmar la revisión: la fuente y la etiqueta de cada entrada y las correspondencias, que están en borrador; entre ellas, si la compra a ATOS se cuenta como de ARCE
- [x] Contar con las reglas de banco-de-pruebas/metricas.md; con la muestra tiene que dar banco-de-pruebas/muestra/medicion-esperada.json
- [x] Costo y duración por noche
- [x] Versionar las métricas con la huella del registro usado (ADR 0007)
- [x] Buscar la compra directa del BPS a ATOS, el único hallazgo que esta medición podía encontrar: no está en ninguna publicación del BPS que trajo el feed en el período, pero el feed no trae nada del 23 al 29 de octubre
- [x] Que el explorador avise cuando un día hábil viene sin publicaciones: el feed de ARCE no trae nada del 2026-10-23 al 2026-10-29 (pasa a T39, hecho)
- [x] Decidir qué hacer con el tope de 60 candidatos, que dejó 196 compras del BPS sin procesar (pasa a T22: se va, y corta el presupuesto)
- [x] Que la medición se detenga en la primera noche con errores del modelo: la 2026-11-16-1 se quedó sin crédito en la noche del 2026-09-24 y siguió con noches vacías
- [x] Medir de nuevo con el analista, el presupuesto y el explorador arreglado (2026-11-16-2), con un sorteo nuevo de 60 extras
- [x] Que cada juicio de un extra guarde la huella del contenido juzgado: dos juicios viejos siguen marcando mal hechos que ahora están bien

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-14: Deja de depender de T08, que no podía cerrar sin esta medición.
- 2026-11-14: Pasa a Para empezar: T12 está hecha. Suma la salida de las corridas y la revisión del registro, que pide el formato.
- 2026-11-15: Pasa a En curso. Diseño del modo banco en el ADR 0014, propuesto (T37): un Compose aparte por medición, las noches en orden, cuidado con ARCE y un tope de gasto. El código espera a que se acepte.
- 2026-11-15: Aceptado el ADR 0014. Sigue con el código del modo banco.
- 2026-11-15: Modo banco construido y probado: diecinueve pruebas contra contenedores y dos mediciones de prueba en Docker Compose (docs/como-se-hizo/medicion-contra-el-banco.md). Sigue con tres noches reales en la máquina del responsable, que tiene la clave.
- 2026-11-15: Tres noches reales, del 2026-11-11 al 13: 1,20 USD y 18 minutos, en banco-de-pruebas/mediciones/2026-11-15-1/. El tope de 60 candidatos se quedó corto en dos noches, y ahora entran primero las compras de la fecha objetivo. Las 78 noches costarían unos 31 USD: antes de correrlas hay que subir los topes de gasto. Sigue con el borrador de la revisión del registro.
- 2026-11-15: Borrador de la fuente y la etiqueta de las 84 entradas, en banco-de-pruebas/privado/revision/entradas.jsonl, fuera del repositorio, para que lo confirme el responsable. Valida sin errores. De ARCE, la única fuente que lee esta medición, son 8: 1 hallazgo, 5 publicables, 1 no-nuevo y 1 otra-entidad; las demás son de otras fuentes o de ninguna.
- 2026-11-16: Las 78 noches, en banco-de-pruebas/mediciones/2026-11-15-2/: 12,71 USD y 3 horas y 23 minutos, sin fallas. Con el borrador de la revisión y las correspondencias de seis entradas: 5 de 5 publicables encontrados, con 26 días de adelanto, y 0 de 1 hallazgo, la compra directa a ATOS. Faltan juzgar 789 extras. A la vista: el feed de ARCE no trae nada del 23 al 29 de octubre, y el tope de candidatos dejó 196 compras sin procesar (docs/como-se-hizo/medicion-contra-el-banco.md).
- 2026-11-16: Los extras se juzgan por sorteo (banco-de-pruebas/metricas.md). Un sorteo de 60 de los 789, con la semilla 20261116: 57 bien y 3 con el contenido equivocado, adjudicaciones repartidas entre varios proveedores. Precisión estimada de los extras, 0,95 (de 0,86 a 0,98), y de todo lo publicado, 0,95. El arreglo del explorador y el aviso de día vacío pasan a T39; el tope de candidatos, a T22. Falta que el responsable confirme la revisión y saber lo de ATOS.
- 2026-11-17: Las 78 noches con el analista, el presupuesto y el explorador arreglado, en banco-de-pruebas/mediciones/2026-11-16-2/: 19,11 USD, 0,245 por noche, ninguna cortada. 5 de 5 publicables y 0 de 1 hallazgo, como antes. Un sorteo nuevo de 60 de los 796 extras, con la semilla 20261117: los 60 bien, precisión estimada 1,0 (de 0,94 a 1,0). Los 56 juicios nuevos están en borrador, para que el responsable los confirme. Dos juicios del sorteo anterior quedaron viejos: los hechos cambiaron con T39 y ahora están bien (docs/como-se-hizo/medicion-contra-el-banco.md).
- 2026-11-17: El responsable confirmó los juicios de los dos sorteos, los 56 nuevos incluidos; el conteo no cambia. Los dos juicios viejos quedan como estaban. Siguen en borrador la fuente y la etiqueta de las entradas y las correspondencias, con lo de ATOS.
- 2026-11-17: Autorizada con commit y push al terminar; el gasto de la API de esta tanda, hasta 10 USD en total.
- 2026-11-17: El responsable confirmó la revisión entera: la fuente y la etiqueta de cada entrada y las correspondencias. La compra a ATOS cuenta como de ARCE, como en el borrador, así que el conteo no cambia.
- 2026-11-17: Cada hecho de la salida lleva la huella de su afirmación y cada juicio de un extra la del contenido juzgado; un juicio con otra huella cuenta como sin juzgar. Los 116 juicios recibieron la suya: 21 de los 60 primeros tienen otra redacción en la segunda medición (docs/como-se-hizo/medicion-contra-el-banco.md). Pasa a Hecho.

### T46 · Separar la metodología para usarla en otro proyecto
- Tipo: Documento
- Dueño: desarrollo
- Detalle: [metodologia/README.md](metodologia/README.md), [metodologia/plantillas/README.md](metodologia/plantillas/README.md)

La forma de trabajar de El Parte, escrita aparte y con plantillas, para que otro proyecto la adopte siguiendo pasos.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] metodologia/README.md: la idea, las piezas, los pasos para adoptarla y lo que se aprendió
- [x] metodologia/plantillas/: el README con las reglas, arranque/, el tablero, el CHANGELOG, los índices y el .gitignore
- [x] Que las herramientas se copien sin tocarlas: el verificador y el sitio saltean lo propio de El Parte, el kanban toma el nombre y el objetivo del tablero, y las pruebas de noche corren cada prueba de herramientas/pruebas/
- [x] Probar los pasos en un repositorio de prueba

Registro:
- 2026-11-17: Creada. Tamaño mediano. Sin autorización de commit: se pide al terminar.
- 2026-11-17: Escrita y probada: siguiendo los pasos en un repositorio de prueba en tmp/, el verificador, el kanban, el sitio y la prueba en Chrome pasaron. Pasa a Hecho.
- 2026-11-17: Commit y push pedidos al terminar.

### T45 · Aplicar el ADR 0017: una forma de trabajo más liviana
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [adrs/0017-forma-de-trabajo-mas-liviana.md](adrs/0017-forma-de-trabajo-mas-liviana.md), [arranque/como-se-trabaja.md](arranque/como-se-trabaja.md)

Un solo lugar por dato, un ritual según el tamaño del cambio, commits autorizados por tarea, el kanban de solo lectura, los tableros publicados en GitHub Pages en cada push, las pruebas de noche y a demanda en GitHub Actions, y controles visuales del tablero de arquitectura.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Lista:
- [x] El ADR 0017, aceptado con su alcance antes del código
- [x] El kanban de solo lectura, y la sincronización y la plantilla editable a obsoletos/
- [x] Las páginas generadas fuera del repositorio, y herramientas/armar_sitio.py con .github/workflows/publicar.yml
- [x] Las pruebas de noche y a demanda, en .github/workflows/pruebas.yml
- [x] Los controles de posición y las capturas del tablero de arquitectura
- [x] Un solo lugar por dato: el aviso del verificador y la limpieza de las cifras repetidas
- [x] El ritual según el tamaño y la autorización por tarea, en arranque/como-se-trabaja.md
- [x] GitHub Pages prendido y el sitio publicado
- [x] La página publicada en Claude, con un aviso que manda al kanban de GitHub Pages

Registro:
- 2026-11-17: Creada con el ADR 0017. Tamaño grande. Autorizada con commit y push al terminar. Pasa a En curso.
- 2026-11-17: Escrito, probado y documentado. El aviso de cifras repetidas deja cinco: tres de la presentación, que repite cifras de la medición 2026-11-15-2, y dos que coinciden por casualidad. Falta prender GitHub Pages y poner el aviso en la página publicada en Claude.
- 2026-11-17: GitHub Pages prendido, con GitHub Actions como fuente. El sitio está publicado en https://andresdiegolanda.github.io/el-parte/, con el kanban y el tablero de arquitectura, y la página publicada en Claude manda ahí. Las acciones de Pages avisan que apuntan a Node 20 y GitHub las corre en Node 24. Pasa a Hecho.

### T22 · Implementar el presupuesto que corta
- Tipo: Componente
- Dueño: desarrollo
- Detalle: [adrs/0005-presupuesto-por-corrida.md](adrs/0005-presupuesto-por-corrida.md), [docs/como-se-hizo/presupuesto.md](docs/como-se-hizo/presupuesto.md)

El advisor de presupuesto, el libro de la corrida y la tabla de precios del ADR 0005.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Advisor de presupuesto en todos los ChatClient
- [x] Libro de la corrida con actualización atómica
- [x] Tabla de precios en la base, con fuente y fecha, y sus valores iniciales en una migración
- [x] Corte por tiempo y mensaje corrida.cortada
- [x] Revisar el tope de 60 candidatos por corrida: en las 78 noches dejó 196 compras del BPS sin procesar (T20)
- [x] Fijar los valores del presupuesto con la medición: con 0,50 USD se habrían cortado 13 de las 78 noches; la más cara costó 1,14 USD y la que más llamó hizo 159 llamadas (docs/como-se-hizo/presupuesto.md)

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-15: Deja de depender de T08: el código de la rebanada ya está hecho, y a T08 solo le falta la medición (T20). Pasa a Para empezar.
- 2026-11-16: Hechos el contrato, el código, las pruebas y la página: un advisor en el ChatClient reserva el peor caso de cada llamada en el libro de la corrida, con un solo update que solo pasa si alcanza, y la rechaza si no; el rechazo se graba, y el cierre corta la corrida con el límite. La tabla de precios y el corte por tiempo ya estaban desde la primera rebanada. Se va el tope de 60 candidatos: los de la fecha objetivo pasan primero. Falta medirlo contra el banco y fijar sus valores. Pasa a En curso.
- 2026-11-16: La medición 2026-11-16-1, con 2 USD y 400 llamadas por noche, se quedó sin crédito de la API en la noche del 2026-09-24, después de 22 noches y 6,11 USD, y no se usa. Se mide de nuevo cuando haya crédito.
- 2026-11-17: Medido en la medición 2026-11-16-2, con 2 USD y 400 llamadas por noche: ninguna noche se cortó. Mediana 0,21 USD, el 90 % por debajo de 0,56, la más cara 1,14 USD, y como mucho 159 llamadas. Con los 0,50 USD por omisión se habrían cortado 13 de las 78. Falta que el responsable fije los valores.
- 2026-11-17: El responsable fijó el tope en 1,25 USD por noche; pasos y tiempo quedan en 200 llamadas y 45 minutos. Con 0,50 USD, El Parte publicado ya había cortado por costo las noches del 2026-11-11 y del 2026-11-16, con el BPS y el BCU seguidos. Valores en el ADR 0005, "Valores calibrados". Pasa a Hecho.

### T39 · Arreglar lo que la medición mostró del explorador de ARCE
- Tipo: Componente
- Dueño: desarrollo
- Detalle: [docs/como-se-hizo/medicion-contra-el-banco.md](docs/como-se-hizo/medicion-contra-el-banco.md)

La medición de las 78 noches dejó a la vista dos fallas del explorador de ARCE: no avisa cuando un día hábil viene sin publicaciones, y la afirmación de una adjudicación repartida entre varios proveedores nombra solo al primero.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Lista:
- [x] Un día hábil sin publicaciones en el feed es una fuente fallida, no una noche sin novedades
- [x] La afirmación de una adjudicación nombra a todos los proveedores de la publicación

Registro:
- 2026-11-16: Creada a partir de la medición de las 78 noches (T20). Pasa a En curso.
- 2026-11-16: Hecho el código: fuente.fallida de tipo sinPublicaciones cuando la fecha objetivo es un día hábil y el feed no trae nada de ese día, y el resumen de una adjudicación repartida nombra a todos los proveedores (docs/contratos.md, docs/como-se-hizo/primera-rebanada.md). La medición espera a la próxima corrida del banco, con T21.
- 2026-11-17: Medido en la medición 2026-11-16-2: las noches del 23 y del 26 al 29 de octubre terminaron con fuente.fallida sinPublicaciones, y el feed de ARCE confirma que no trae nada de esos días. Las adjudicaciones repartidas del sorteo nombran a todos los proveedores, y dos de las tres que estaban mal ahora están bien. Pasa a Hecho.

### T21 · Completar el analista
- Tipo: Componente
- Dueño: desarrollo
- Detalle: [docs/contratos.md](docs/contratos.md), [adrs/0015-que-puede-escribir-el-analista.md](adrs/0015-que-puede-escribir-el-analista.md), [docs/como-se-hizo/analista.md](docs/como-se-hizo/analista.md)

En la primera rebanada el analista solo pasa la afirmación. Acá escribe qué dice, por qué importa y qué mirar.

Etapas:
- [x] Contrato
- [x] Código
- [x] Pruebas
- [x] Medido contra el banco
- [x] Página cómo se hizo

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-15: Deja de depender de T08: el código de la rebanada ya está hecho, y a T08 solo le falta la medición (T20). Pasa a Para empezar.
- 2026-11-16: Hechos el contrato, el código, las pruebas y la página: el analista le pide al modelo qué dice, por qué importa y qué mirar, copia el grado del refutador y no usa un campo con una cifra que no está en la evidencia (ADR 0015). La página de la mañana muestra el análisis aparte. Falta medirlo contra el banco, junto con T39: se estima que la medición de las 78 noches cuesta unos 16 USD. Pasa a En curso.
- 2026-11-16: La medición 2026-11-16-1, con 2 USD y 400 llamadas por noche, se quedó sin crédito de la API en la noche del 2026-09-24, después de 22 noches y 6,11 USD, y no se usa. Se mide de nuevo cuando haya crédito.
- 2026-11-17: Medido en la medición 2026-11-16-2: 5,62 USD en 1.217 llamadas, 0,072 por noche. El control dejó sin análisis 7 "por qué importa" y 33 "qué mirar", y en 94 el "qué dice" quedó igual a la afirmación. Falta un sorteo de análisis, que el banco no juzga. Pasa a Hecho.

### T34 · Armar la página del perfil
- Tipo: Web
- Dueño: desarrollo
- Depende de: T25
- Detalle: [adrs/0013-acceso-perfil-y-parte-diario.md](adrs/0013-acceso-perfil-y-parte-diario.md), [docs/como-se-hizo/perfil.md](docs/como-se-hizo/perfil.md)

Donde cada invitado elige qué entidades seguir y si quiere el aviso diario por correo. Los cambios valen desde la corrida siguiente.

Etapas:
- [x] Diseño
- [x] Código
- [x] Pruebas
- [x] Página cómo se hizo

Lista:
- [x] Lo que sigue y el catálogo de lo que se puede seguir: las entidades con su identificador en alguna fuente
- [x] Seguir y dejar de seguir, en parte.suscripciones
- [x] Aviso diario sí o no, en cuentas.usuarios, con el rol de cuentas
- [x] La cuenta de demostración lo ve pero no lo cambia
- [x] Catálogo inicial: organismos con su código de comprador en ARCE, verificado

Registro:
- 2026-11-14: Creada con el diseño del acceso y el perfil (ADR 0013).
- 2026-11-16: Pasa a Para empezar: T25 está hecha.
- 2026-11-16: Hecha: el catálogo, con trece organismos y su código de comprador en ARCE, leído en una publicación de cada uno; seguir y dejar de seguir; el aviso diario sí o no, prendido de entrada; y el perfil de la demostración, que se ve sin entrar y no se cambia. Pasa a Hecho.

### T41 · Aceptar o corregir el ADR 0016
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/0016-despliegue-en-la-maquina-del-responsable.md](adrs/0016-despliegue-en-la-maquina-del-responsable.md)

Dónde se publica El Parte: la máquina del responsable, un túnel de Cloudflare, el correo por Resend, la salud fuera del túnel y una copia de la base cada 24 horas. Al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice.

Lista:
- [x] 0016: despliegue en la máquina del responsable

Registro:
- 2026-11-16: Creada con el ADR 0016.
- 2026-11-16: Aceptado el 0016, sin cambios, para esta etapa. Pasa a Hecho.

### T27 · Escribir el ADR de despliegue
- Tipo: ADR
- Dueño: desarrollo
- Depende de: T08
- Detalle: [adrs/0016-despliegue-en-la-maquina-del-responsable.md](adrs/0016-despliegue-en-la-maquina-del-responsable.md), [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md), [compose.publicado.yaml](compose.publicado.yaml), [app/README.md](app/README.md)

Dónde se publica el servicio para los usuarios invitados, con qué dominio y con qué servicio de correo (D9). Hoy todo corre en una máquina con Docker (`compose.yaml`); el ADR resuelve lo que falta para que los invitados lo usen cada mañana.

Etapas:
- [x] Contexto y alternativas
- [x] Fuentes verificadas
- [x] Escrito
- [x] Aceptado

Lista:
- [x] Una máquina siempre prendida, para la corrida de cada noche (`ELPARTE_CORRIDA_CRON`) y las páginas de cada mañana. La del responsable, con la corrida a las 6 de Uruguay y los servicios que vuelven solos; si está apagada, esa noche se corre a mano
- [x] Un dominio público con HTTPS, al que apuntan el enlace para entrar y el aviso diario. El subdominio del responsable, por un túnel de Cloudflare
- [x] Un servicio de correo real en lugar de Mailpit, con la configuración de envío que pida en el dominio, verificada en su documentación. Resend por SMTP, con SSL
- [x] La base y Kafka en almacenamiento persistente, y copias de seguridad de la base. Volúmenes de Docker y una copia de la base cada 24 horas, en respaldos/
- [x] Las claves del modelo y de la base en el servidor, fuera del repositorio. En el .env, con el token del túnel y la clave del correo
- [x] El tamaño de la máquina, con el costo y la duración por noche medidos en T20. 1,7 GB de memoria y unos 0,28 USD por noche; la duración no se midió aparte

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-14: Suma la lista de lo que el despliegue tiene que resolver.
- 2026-11-16: ADR 0016 escrito, propuesto: El Parte se publica desde la máquina del responsable, con un túnel de Cloudflare y el correo por Resend. Lo que lo levanta está en compose.publicado.yaml y se termina de probar con las claves reales en T40. Pasa a Esperando, hasta que se acepte (T41).
- 2026-11-16: ADR 0016 aceptado (T41). Pasa a Hecho.

### T40 · Publicar El Parte en la máquina del responsable
- Tipo: Herramienta
- Dueño: desarrollo
- Depende de: T25, T27
- Detalle: [compose.publicado.yaml](compose.publicado.yaml), [app/README.md](app/README.md), [docs/como-se-hizo/despliegue.md](docs/como-se-hizo/despliegue.md), [adrs/0016-despliegue-en-la-maquina-del-responsable.md](adrs/0016-despliegue-en-la-maquina-del-responsable.md)

Lo que el ADR 0016 decide, armado: el conector del túnel, el correo por Resend, la corrida de cada noche, los servicios que vuelven solos, la salud fuera del túnel y la copia diaria de la base. Probado en local con un correo de prueba que exige SSL y usuario, y en la máquina del responsable con el túnel y Resend reales.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Lista:
- [x] compose.publicado.yaml, que se suma a compose.yaml con su propio proyecto
- [x] Los puertos, solo en la máquina, también en local
- [x] La salud en un puerto al que el túnel no llega
- [x] La copia diaria de la base, y cómo se vuelve a una copia, probado
- [x] Las mediciones, sin los invitados del .env
- [x] El túnel creado en Cloudflare, con la ruta del subdominio a http://aplicacion:8080 (Andrés)
- [x] El dominio del correo verificado en Resend y su clave (Andrés)
- [x] Las claves, la dirección, el remitente y los invitados en el .env (Andrés)
- [x] Levantarla publicada y entrar desde afuera con el enlace de un correo real

Registro:
- 2026-11-16: Creada con el ADR 0016. Armada y probada en local; espera las claves del túnel y del correo.
- 2026-11-16: Levantada en la máquina del responsable con las claves reales: el túnel conecta y las páginas responden desde afuera. El correo pasa al puerto 2465 de Resend, porque el antivirus de la máquina interceptaba el 465.
- 2026-11-16: El responsable entró desde afuera con el enlace que le llegó por Resend. El enlace Estado del menú, que publicada daba error, se muestra solo en local. Pasa a Hecho.

### T25 · Armar el login por enlace, los cuatro usuarios y la cuenta de demostración
- Tipo: Web
- Dueño: desarrollo
- Detalle: [adrs/0013-acceso-perfil-y-parte-diario.md](adrs/0013-acceso-perfil-y-parte-diario.md), [adrs/0003-eleccion-del-stack.md](adrs/0003-eleccion-del-stack.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md), [docs/como-se-hizo/login.md](docs/como-se-hizo/login.md)

Login con token de un solo uso de Spring Security, correos en el esquema cuentas y lista de invitados desde la configuración. El diseño está en el ADR 0013 y en la vista del usuario del tablero de arquitectura.

Etapas:
- [x] Diseño
- [x] Código
- [x] Pruebas
- [x] Página cómo se hizo

Lista:
- [x] Alta de invitados desde elparte.invitados: cuentas.usuarios y parte.clientes con el mismo identificador
- [x] Dos fuentes de datos, una por rol, y un UserDetailsService que lee cuentas.usuarios
- [x] Entrar por enlace: tokens en cuentas.one_time_tokens y el enlace por el correo saliente
- [x] La misma respuesta para un correo no invitado
- [x] Sesión recordada 30 días en cuentas.persistent_logins, y probar que se combina con el login por token
- [x] La página de la mañana muestra el informe de quien entró; sin sesión, el de demostración
- [x] La cuenta de demostración, de solo lectura
- [x] La sala de máquinas y la cola de revisión, solo para el responsable del proyecto: hoy las puede usar cualquiera que llegue a la aplicación (D6, T23). Quedaron cerrados correr, repetir y la cola; la sala se mira sin entrar

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-14: Diseño hecho: alta de invitados, entrar por enlace y sesión recordada (ADR 0013, propuesto). El perfil queda en T34 y el aviso diario en T35.
- 2026-11-15: Deja de depender de T08: el código de la rebanada ya está hecho, y a T08 solo le falta la medición (T20). Pasa a Para empezar.
- 2026-11-16: Suma a la lista que la sala de máquinas y la cola de revisión las use solo el responsable: la cola de revisión (T23) ya escribe en la base y no tiene login.
- 2026-11-16: Hecha: el alta de invitados desde el entorno, el enlace de un solo uso con un botón para confirmarlo, el recordatorio de 30 días, y la página de la mañana de quien entró, con dos fuentes de datos, una por rol. Correr, repetir y la cola de revisión son solo del responsable; la sala de máquinas se mira sin entrar. Un invitado nuevo empieza siguiendo lo mismo que la demostración. El correo real y la dirección pública quedan para el despliegue (T27). Pasa a Hecho.

### T23 · Armar la cola de revisión humana
- Tipo: Web
- Dueño: desarrollo
- Detalle: [docs/contratos.md](docs/contratos.md), [docs/como-se-hizo/revision.md](docs/como-se-hizo/revision.md)

La cola de hechos INFERRED en la sala de máquinas, y la marca de pendiente en la página de la mañana (D6).

Etapas:
- [x] Diseño
- [x] Código
- [x] Pruebas
- [x] Página cómo se hizo

Registro:
- 2026-11-07: Creada con el plan (T07).
- 2026-11-15: Deja de depender de T08: el código de la rebanada ya está hecho, y a T08 solo le falta la medición (T20). Pasa a Para empezar.
- 2026-11-16: Hecha: la cola en /revision lista las versiones INFERRED pendientes con su razonamiento, su evidencia y su traza; quien revisa dice si la deducción se sostiene, y un no pide una nota. La página de la mañana muestra el resultado enseguida, y un hecho que no se sostiene sigue, tachado y con la nota (D6). Quién puede revisar queda para el login (T25). Pasa a Hecho.

### T38 · Armar una presentación para quien no conoce el proyecto
- Tipo: Documento
- Dueño: desarrollo
- Detalle: [docs/presentacion/README.md](docs/presentacion/README.md), [docs/presentacion/el-parte.md](docs/presentacion/el-parte.md)

Siete diapositivas que explican El Parte a alguien que nunca oyó hablar de él, versionadas en Marp para que se puedan comparar en GitHub como el resto de la documentación.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] El problema, qué hace y un ejemplo real del BPS
- [x] Los agentes, los grados y la medición contra el banco
- [x] Dónde está el proyecto
- [x] Los dibujos en SVG, generados por herramientas/generar_presentacion.py
- [x] De dónde sale cada cifra, en docs/presentacion/README.md

Registro:
- 2026-11-15: Creada y hecha. Se eligió Marp para que la presentación viva en el repositorio; lo que se resigna está en docs/presentacion/README.md.

### T37 · Aceptar o corregir el ADR 0014
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/0014-medicion-contra-el-banco.md](adrs/0014-medicion-contra-el-banco.md)

Cómo se producen las corridas que se miden contra el banco: un Docker Compose aparte por medición, las noches en orden sobre una base vacía, una pausa entre pedidos a ARCE y un tope de gasto para toda la medición. Al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice.

Lista:
- [x] 0014: medición contra el banco

Registro:
- 2026-11-15: Creada con el ADR 0014.
- 2026-11-15: Aceptado el 0014, sin cambios. Pasa a Hecho.

### T12 · Escribir el formato, la muestra sintética y las métricas del banco de pruebas
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T11
- Detalle: [banco-de-pruebas/README.md](banco-de-pruebas/README.md), [banco-de-pruebas/formato.md](banco-de-pruebas/formato.md), [banco-de-pruebas/metricas.md](banco-de-pruebas/metricas.md)

Lo que sí se versiona del banco de pruebas (D2), en `banco-de-pruebas/`.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] Formato del registro
- [x] Muestra sintética
- [x] Forma de las métricas
- [x] Cómo se cuentan los descartes del registro que El Parte sí publicaría

Registro:
- 2026-11-07: Creada.
- 2026-11-14: Pasa a Para empezar: T11 está hecha. Suma cómo contar los descartes, porque El Parte no filtra por tema.
- 2026-11-14: Pasa a En curso.
- 2026-11-14: Hecha. El formato del registro, de su revisión y de la salida de El Parte, con el control `herramientas/banco_de_pruebas.py`: el registro real lo cumple, con un aviso por una contradicción propia. La muestra sintética tiene un caso de cada resultado y su medición esperada, que controla el verificador. Las métricas cuentan cobertura, precisión, costo y duración; los descartes llevan una etiqueta de la revisión y no son falsos positivos por sí solos. T20 pasa a Para empezar.

### T36 · Aceptar o corregir el ADR 0013
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/0013-acceso-perfil-y-parte-diario.md](adrs/0013-acceso-perfil-y-parte-diario.md), [docs/tablero-de-arquitectura/README.md](docs/tablero-de-arquitectura/README.md)

Cómo entra cada usuario, dónde queda su perfil y cómo lee su parte cada mañana. Se ve en la vista del usuario del tablero de arquitectura, `docs/tablero-de-arquitectura/usuarios.html`. Al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice.

Lista:
- [x] 0013: acceso, perfil y parte diario

Registro:
- 2026-11-14: Creada con el ADR 0013.
- 2026-11-14: Aceptado el 0013, sin cambios. Pasa a Hecho.

### T32 · Armar el tablero de arquitectura
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [docs/tablero-de-arquitectura/README.md](docs/tablero-de-arquitectura/README.md), [adrs/0012-tablero-de-arquitectura.md](adrs/0012-tablero-de-arquitectura.md)

El sistema objeto por objeto, en una página: lo construido y lo diseñado, con sus flechas, sus recorridos y su historial. Es la referencia para pedir cambios, y el verificador lo compara con el código en cada cambio.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Lista:
- [x] Modelo del sistema en sistema.json
- [x] Página generada desde el modelo
- [x] Control contra el código en el verificador
- [x] Pruebas del control y de la página
- [x] La regla registrada en un ADR y en cómo se trabaja
- [x] Un README en cada carpeta de la raíz

Registro:
- 2026-11-14: Creada y en curso.
- 2026-11-14: Hecha: 56 objetos construidos y 16 diseñados, verificados contra el código; la regla queda en el ADR 0012 y en la regla 13 del README. Encontró que el expediente no tenía tarjeta: T33. Pasa a Hecho.

### T31 · Aceptar o corregir el ADR 0011
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/0011-grabacion-y-repeticion-de-corridas.md](adrs/0011-grabacion-y-repeticion-de-corridas.md)

La primera rebanada graba en la base todo lo que una corrida lee de afuera, fuentes y modelo, y la repite desde ahí. Reemplaza la sección "Respuestas grabadas del modelo" del ADR 0003. Al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice, y el 0003 suma que esa sección quedó reemplazada.

Lista:
- [x] 0011: grabación y repetición de corridas

Registro:
- 2026-11-14: Creada con la primera rebanada (T08).
- 2026-11-14: Aceptado el 0011, sin cambios. Pasa a Hecho.

### T18 · Pedir a IMPO que aclare si su JSON de normas se puede reutilizar
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [docs/fuentes/impo.md](docs/fuentes/impo.md)

Su página de datos abiertos ofrece el JSON con licencia abierta, pero sus condiciones de uso prohíben copiar. Con una aclaración por escrito a favor, IMPO entra como quinta fuente (ADR 0006).

Registro:
- 2026-11-07: Creada.
- 2026-11-14: No se envía el pedido: IMPO entra tomando como permiso su licencia de datos abiertos, solo para el JSON de las normas (ADR 0010). Pasa a Hecho.

### T11 · Entregar el registro completo del banco de pruebas
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

El seguimiento acumulado y las bitácoras por corrida van en `banco-de-pruebas/privado/`, que no se versiona (D2). Hay que confirmar cuántas noches cubre: la muestra sugiere unas cincuenta desde el 2026-09-17 y la definición del proyecto habla de unas setenta.

Registro:
- 2026-11-07: Creada.
- 2026-11-14: Entregado en `banco-de-pruebas/privado/`: 78 noches, del 2026-08-29 al 2026-11-14, en un documento acumulado y condensado (13 noches con detalle propio), más un índice, 84 hallazgos y descartes, y 52 consultas. No hay bitácoras por noche ni costos. Huella del zip entregado: sha256:9025e5720ba90ed78dc35c22105590c16c16d61e09c6de73171633f28c05fad5. Pasa a Hecho.

### T14 · Dejar la clave del proveedor de modelos fuera del repositorio
- Tipo: Gestión
- Dueño: Andrés
- Depende de: T05

Para las corridas reales (D4). Las pruebas automáticas no la necesitan: usan respuestas grabadas.

La clave vive solo en la máquina que corre las noches reales y graba respuestas, en un archivo `.env` en la raíz del clon, que `.gitignore` deja afuera, con la variable `ANTHROPIC_API_KEY`. No va en el chat ni en las variables del entorno en la nube: esas variables las puede leer cualquiera que use el entorno, y el proxy de las sesiones en la nube no inyecta secretos en `api.anthropic.com`. [PROVEN: https://code.claude.com/docs/en/cloud-environments.md, 2026-11-14] Las sesiones en la nube trabajan con respuestas grabadas.

Lista:
- [x] Un espacio de trabajo propio para El Parte en la consola de Claude
- [x] Tope de gasto mensual y aviso en ese espacio de trabajo
- [x] Clave creada en ese espacio de trabajo y guardada fuera del repositorio
- [x] Saldo de créditos de la API disponible

Registro:
- 2026-11-07: Creada.
- 2026-11-14: Pasa a Para empezar: T05 está hecha.
- 2026-11-14: Suma dónde vive la clave y los pasos para crearla.
- 2026-11-14: Hecha: espacio de trabajo el-parte con tope de 20 USD por mes y aviso a los 10, tope de la organización de 30 USD, saldo de créditos cargado y clave creada en ese espacio, guardada fuera del repositorio. Pasa a Hecho.

### T19 · Aceptar o corregir los ADRs propuestos
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/README.md](adrs/README.md)

Cada ADR propuesto se acepta, se corrige o se rechaza; al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice. El 0001 quedó reemplazado por el 0009.

Lista:
- [x] 0002: orquestación por mensajes
- [x] 0003: elección del stack
- [x] 0004: grados de evidencia
- [x] 0005: presupuesto por corrida
- [x] 0006: fuentes de la versión 0
- [x] 0007: registro del banco de pruebas fuera del repositorio
- [x] 0008: datos personales
- [x] 0009: kanban editable, sincronizado con el tablero

Registro:
- 2026-11-07: Creada.
- 2026-11-14: Aceptados el 0007 y el 0008.
- 2026-11-14: Aceptados del 0002 al 0005, con correcciones en el 0003, el 0004 y el 0005.
- 2026-11-14: Aceptados el 0006 y el 0009. Pasa a Hecho.
- 2026-11-14: El cambio hecho en el kanban no se aplicó porque la tarjeta también cambió en el repositorio.

### T05 · Escribir los primeros ADRs
- Tipo: ADR
- Dueño: desarrollo
- Depende de: T09
- Detalle: [adrs/0002-orquestacion-por-mensajes.md](adrs/0002-orquestacion-por-mensajes.md), [adrs/0003-eleccion-del-stack.md](adrs/0003-eleccion-del-stack.md), [adrs/0004-grados-de-evidencia.md](adrs/0004-grados-de-evidencia.md), [adrs/0005-presupuesto-por-corrida.md](adrs/0005-presupuesto-por-corrida.md)

Las cuatro decisiones de base del sistema. El del stack fija los modelos concretos, el sistema de mensajería, la interfaz web y el buzón de correo de prueba (D9).

Etapas:
- [x] Contexto y alternativas
- [x] Fuentes verificadas
- [x] Escrito
- [x] Aceptado

Lista:
- [x] Orquestación por mensajes
- [x] Elección del stack
- [x] Grados de evidencia
- [x] Presupuesto por corrida (D4)
- [x] Versión de Spring AI (2.0.x o 2.1) y de Java
- [x] Proyecto de mensajería compatible con Spring Boot 4
- [x] Cómo grabar y repetir respuestas del modelo en las pruebas (D4)

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a Para empezar: T09 está hecha. Suma tres puntos que la verificación dejó abiertos.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Escritos los ADRs 0002 a 0005, propuestos. Pasa a Esperando hasta que se acepten.
- 2026-11-14: Aceptados los ADRs 0002 a 0005, con dos correcciones: el grado NOT_MENTIONED en inglés (0004) y el modelo de cada agente configurable sin reiniciar (0003 y 0005, implementado en T29). Pasa a Hecho.

### T06 · Escribir los ADRs de las decisiones D2 y D3
- Tipo: ADR
- Dueño: desarrollo
- Detalle: [adrs/0007-registro-del-banco-fuera-del-repositorio.md](adrs/0007-registro-del-banco-fuera-del-repositorio.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md)

D2 deja el registro del banco de pruebas fuera del repositorio y D3 fija cómo se tratan los datos personales. Las dos son significativas y pasan a ADR (regla 3).

Etapas:
- [x] Contexto y alternativas
- [x] Fuentes verificadas
- [x] Escrito
- [x] Aceptado

Lista:
- [x] ADR de D2: registro fuera del repositorio
- [x] ADR de D3: datos personales

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Escritos los ADRs 0007 y 0008, propuestos. Pasa a Esperando hasta que se acepten.
- 2026-11-14: Aceptados los ADRs 0007 y 0008. Pasa a Hecho.

### T28 · Hacer editable el kanban y sincronizarlo con el tablero
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [adrs/0009-kanban-editable-sincronizado.md](adrs/0009-kanban-editable-sincronizado.md), [obsoletos/herramientas/sincronizar_tablero.py](obsoletos/herramientas/sincronizar_tablero.py), [obsoletos/kanban/plantilla.html](obsoletos/kanban/plantilla.html)

El kanban publicado se puede editar con las mismas reglas del tablero, y las sesiones de desarrollo lo sincronizan con este archivo. La prueba del kanban corre en Chrome con una base simulada; la de la sincronización, en una copia del repositorio.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Registro:
- 2026-11-14: Creada y hecha.
- 2026-11-14: La sincronización también lleva a la base los cambios de orden hechos en tablero.md, con una operación que solo escribe el orden.
- 2026-11-14: Después de un push, la sincronización escribe solo la huella nueva de las tarjetas que no cambiaron de contenido, en vez de la tarjeta entera.
- 2026-11-17: El kanban vuelve a ser de solo lectura y la sincronización pasa a obsoletos/ (ADR 0017, T45).

### T07 · Escribir docs/plan.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T02
- Detalle: [docs/plan.md](docs/plan.md)

El plan de los cuatro fines de semana del calendario (D10).

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] Qué se muestra al final de cada fin de semana
- [x] Que entre en unas seis horas de revisión por fin de semana

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a Para empezar: T02 está hecha.
- 2026-11-07: Hecha. Cada fin de semana tiene qué se muestra y cómo se comprueba; sus tareas están en T20 a T27.

### T04 · Escribir docs/base-de-afirmaciones.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T03
- Detalle: [docs/base-de-afirmaciones.md](docs/base-de-afirmaciones.md)

El modelo de datos de la base de afirmaciones, con diagrama.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] Modelo de datos con diagrama
- [x] Traza de qué agente produjo cada hecho
- [x] Tabla de usuarios aparte (D3)

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a Para empezar: T03 está hecha.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Hecha. Dos esquemas (parte y cuentas), 16 tablas; el DDL se probó contra PostgreSQL 18.6.

### T03 · Escribir docs/contratos.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T02
- Detalle: [docs/contratos.md](docs/contratos.md)

Los mensajes entre agentes. De esto depende que cada corrida se pueda repetir y que la sala de máquinas se arme desde los mensajes.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] Esquema y ejemplo de cada mensaje
- [x] Regla de identidad e idempotencia de cada hecho
- [x] Umbral de "demasiado INFERRED" (D6)

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a Para empezar: T02 está hecha.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Hecha. Once tipos de mensaje con esquema y ejemplo; en la versión 0, todo hecho INFERRED va a revisión.

### T15 · Comprobar en el verificador que los diagramas Mermaid se dibujan
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [herramientas/verificar_docs.py](herramientas/verificar_docs.py)

Los diagramas que había el 2026-11-07 se comprobaron a mano con mermaid-cli y Chrome sin ventana, y se dibujan todos. Falta que el verificador lo haga solo, y que se saltee con un aviso donde no haya navegador.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Hecha. El verificador dibuja cada diagrama con mermaid-cli, guarda en caché los que pasan y avisa sin fallar si no hay mmdc.

### T02 · Escribir docs/arquitectura.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T09
- Detalle: [docs/arquitectura.md](docs/arquitectura.md), [docs/laminas/arquitectura.json](docs/laminas/arquitectura.json), [herramientas/generar_laminas.py](herramientas/generar_laminas.py)

La visión general del sistema y la primera lámina en SVG, con su generador y el verificador que falla si la lámina no coincide (regla 5).

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Lista:
- [x] Visión general en Mermaid
- [x] Los agentes y sus razones
- [x] Secuencia de una noche
- [x] Lámina de arquitectura en SVG con su generador
- [x] Verificador de láminas

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a Para empezar: T09 está hecha.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Hecha. La lámina se genera desde su JSON y el verificador falla si el SVG no coincide.

### T10 · Verificar las fuentes candidatas
- Tipo: Verificación
- Dueño: desarrollo
- Detalle: [docs/fuentes/README.md](docs/fuentes/README.md), [adrs/0006-fuentes-de-la-version-0.md](adrs/0006-fuentes-de-la-version-0.md)

De cada fuente: permiso de uso, formato, si deja consultar por fecha y si nombra personas físicas. Cada una termina en su página en `docs/fuentes/`, con URL y fecha de verificación. Con eso se elige la fuente de la primera rebanada (D5).

Lista:
- [x] Compras estatales
- [x] Diario oficial (IMPO)
- [x] Resoluciones de directorio del organismo del banco de pruebas
- [x] Presidencia y novedades de gub.uy
- [x] BID
- [x] Banco Mundial
- [x] CAF
- [x] Elegir la fuente de la primera rebanada

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Hecha. Entran ARCE, BID, Banco Mundial y la normativa de Presidencia; ARCE es la fuente de la primera rebanada (ADR 0006, propuesto). El BPS queda afuera porque sus términos prohíben la consulta automática.

### T09 · Verificar Spring AI y sus patrones de orquestación
- Tipo: Verificación
- Dueño: desarrollo
- Detalle: [docs/verificaciones/spring-ai.md](docs/verificaciones/spring-ai.md)

Antes de apoyarse en Spring AI hay que saber qué ofrece hoy, verificado en su documentación oficial con URL y fecha. Destraba la arquitectura (T02) y el ADR del stack (T05).

Lista:
- [x] Versión vigente y fecha de publicación
- [x] Patrones de orquestación de agentes que documenta
- [x] Cómo abstrae el proveedor de modelos (D4)
- [x] Integración con mensajería en tópicos

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Pasa a En curso.
- 2026-11-07: Hecha. La ficha está en docs/verificaciones/spring-ai.md: Spring AI 2.0.2 sirve para cada agente por dentro, pero la orquestación por tópicos es del proyecto.

### T13 · Elegir la licencia del repositorio
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [LICENSE](LICENSE), [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md)

Se eligió MIT (D13). El texto está en `LICENSE`, en la raíz, y el README muestra la etiqueta.

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Hecha: licencia MIT.

### T17 · Dar acceso de escritura al repositorio a las sesiones en la nube
- Tipo: Gestión
- Dueño: Andrés

El push del primer commit desde una sesión en la nube devolvió HTTP 403: esas sesiones pueden leer el repositorio pero no escribir. Se resolvió sumando el repositorio a la app de GitHub de Claude, que ya estaba instalada en la cuenta con acceso a repositorios elegidos.

Registro:
- 2026-11-07: Creada, después del 403.
- 2026-11-07: Hecha: el push de los commits f3be363 y 375d044 anduvo.

### T16 · Armar el kanban generado desde el tablero
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [adrs/0001-tablero-fuente-unica-kanban-generado.md](adrs/0001-tablero-fuente-unica-kanban-generado.md), [kanban/plantilla.html](kanban/plantilla.html), [herramientas/generar_kanban.py](herramientas/generar_kanban.py)

Este archivo pasó de tablas a tarjetas, y el kanban se genera desde acá. El verificador controla que la página coincida con el tablero y las reglas de cada columna.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Registro:
- 2026-11-07: Creada y hecha.

### T01 · Armar la base documental
- Tipo: Documento
- Dueño: desarrollo
- Detalle: [arranque/README.md](arranque/README.md)

README con las reglas, CHANGELOG, tablero, la carpeta `arranque/`, `obsoletos/` y el verificador.

Etapas:
- [x] Borrador
- [x] Grados y fuentes
- [x] Diagramas
- [x] Releído y verificado

Registro:
- 2026-11-07: Hecha, en el commit f3be363.
