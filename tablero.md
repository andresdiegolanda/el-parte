# Tablero

Todo pendiente de El Parte vive acá, con dueño (regla 8). Este archivo es lo que manda. El kanban publicado es editable y se sincroniza con este archivo cada vez que una sesión de desarrollo lee o escribe el repositorio, o cuando se le pide; su copia sin conexión, [`kanban/index.html`](kanban/index.html), se genera desde acá con `python3 herramientas/generar_kanban.py` y no se edita a mano ([ADR 0009](adrs/0009-kanban-editable-sincronizado.md)).

- Actualizado: 2026-11-14
- Nota: El kanban publicado ahora se puede editar, y cada sesión lo sincroniza con este archivo (T28, ADR 0009). La primera rebanada (T08) sigue esperando la revisión de los ADRs (T19), la clave (T14) y el registro del banco (T11).
- Calendario: 2026-11-07, 2026-11-14, 2026-11-21, 2026-11-28
- Repositorio: https://github.com/andresdiegolanda/el-parte

## Cómo se usa

Cada `##` con nombre de columna agrupa tarjetas, y cada `###` adentro es una tarjeta. Mover una tarjeta es mover su bloque a otra columna. Dentro de una columna, el orden es la prioridad: la primera de "Para empezar" es la próxima. En este archivo las columnas van en el orden en que conviene leerlas (lo que se está haciendo primero); el kanban las muestra en el orden de la tabla de columnas.

Después de cada cambio se vuelve a generar el kanban y se corre el verificador, que controla el formato canónico de las tarjetas y estas reglas, las mismas que aplica el kanban al editarlo:

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

## Para empezar

### T19 · Aceptar o corregir los ADRs propuestos
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [adrs/README.md](adrs/README.md)

Los ADRs 0002 a 0009 están propuestos; el 0001 quedó reemplazado por el 0009. Cada uno se acepta, se corrige o se rechaza; al aceptarlo, su estado pasa a "aceptado" en el archivo y en el índice.

Lista:
- [ ] 0002: orquestación por mensajes
- [ ] 0003: elección del stack
- [ ] 0004: grados de evidencia
- [ ] 0005: presupuesto por corrida
- [ ] 0006: fuentes de la versión 0
- [ ] 0007: registro del banco de pruebas fuera del repositorio
- [ ] 0008: datos personales
- [ ] 0009: kanban editable, sincronizado con el tablero

Registro:
- 2026-11-07: Creada.

### T18 · Pedir a IMPO que aclare si su JSON de normas se puede reutilizar
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [docs/fuentes/impo.md](docs/fuentes/impo.md)

Su página de datos abiertos ofrece el JSON con licencia abierta, pero sus condiciones de uso prohíben copiar. Con una aclaración por escrito a favor, IMPO entra como quinta fuente (ADR 0006).

Registro:
- 2026-11-07: Creada.

### T11 · Entregar el registro completo del banco de pruebas
- Tipo: Gestión
- Dueño: Andrés
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

El seguimiento acumulado y las bitácoras por corrida van en `banco-de-pruebas/privado/`, que no se versiona (D2). Hay que confirmar cuántas noches cubre: la muestra sugiere unas cincuenta desde el 2026-09-17 y la definición del proyecto habla de unas setenta.

Registro:
- 2026-11-07: Creada.

## Esperando

### T06 · Escribir los ADRs de las decisiones D2 y D3
- Tipo: ADR
- Dueño: desarrollo
- Espera a: Andrés, que acepte o corrija los ADRs 0007 y 0008 (T19)
- Detalle: [adrs/0007-registro-del-banco-fuera-del-repositorio.md](adrs/0007-registro-del-banco-fuera-del-repositorio.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md)

D2 deja el registro del banco de pruebas fuera del repositorio y D3 fija cómo se tratan los datos personales. Las dos son significativas y pasan a ADR (regla 3).

Etapas:
- [x] Contexto y alternativas
- [x] Fuentes verificadas
- [x] Escrito
- [ ] Aceptado

Lista:
- [x] ADR de D2: registro fuera del repositorio
- [x] ADR de D3: datos personales

Registro:
- 2026-11-07: Creada.
- 2026-11-07: Escritos los ADRs 0007 y 0008, propuestos. Pasa a Esperando hasta que se acepten.

### T05 · Escribir los primeros ADRs
- Tipo: ADR
- Dueño: desarrollo
- Depende de: T09
- Espera a: Andrés, que acepte o corrija los ADRs 0002 a 0005 (T19)
- Detalle: [adrs/0002-orquestacion-por-mensajes.md](adrs/0002-orquestacion-por-mensajes.md), [adrs/0003-eleccion-del-stack.md](adrs/0003-eleccion-del-stack.md), [adrs/0004-grados-de-evidencia.md](adrs/0004-grados-de-evidencia.md), [adrs/0005-presupuesto-por-corrida.md](adrs/0005-presupuesto-por-corrida.md)

Las cuatro decisiones de base del sistema. El del stack fija los modelos concretos, el sistema de mensajería, la interfaz web y el buzón de correo de prueba (D9).

Etapas:
- [x] Contexto y alternativas
- [x] Fuentes verificadas
- [x] Escrito
- [ ] Aceptado

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

## Pendiente

### T08 · Construir la primera rebanada de punta a punta
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T02 a T07, T10, T14
- Detalle: [arranque/proyecto.md](arranque/proyecto.md), [docs/fuentes/compras-estatales.md](docs/fuentes/compras-estatales.md)

Chica y completa le gana a grande y a medias: una fuente, del explorador a la página de la mañana, con la sala de máquinas de esa corrida.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [ ] Explorador de ARCE, filtrando por el BPS como comprador antes de llamar al modelo
- [ ] Zona horaria y publicaciones atrasadas de ARCE
- [ ] Migraciones de la base a partir de docs/base-de-afirmaciones.md, con el reparto de permisos por esquema
- [ ] Archivista
- [ ] Refutador
- [ ] Editor
- [ ] Página de la mañana
- [ ] Sala de máquinas de la corrida

Registro:
- 2026-11-07: Creada.
- 2026-11-07: La fuente es ARCE (ADR 0006).

### T12 · Escribir el formato, la muestra sintética y las métricas del banco de pruebas
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T11

Lo que sí se versiona del banco de pruebas (D2), en `banco-de-pruebas/`.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Formato del registro
- [ ] Muestra sintética
- [ ] Forma de las métricas

Registro:
- 2026-11-07: Creada.

### T14 · Dejar la clave del proveedor de modelos fuera del repositorio
- Tipo: Gestión
- Dueño: Andrés
- Depende de: T05

Para las corridas reales (D4). Las pruebas automáticas no la necesitan: usan respuestas grabadas.

Registro:
- 2026-11-07: Creada.

### T20 · Medir la primera rebanada contra el banco de pruebas
- Tipo: Verificación
- Dueño: desarrollo
- Depende de: T08, T12
- Detalle: [docs/plan.md](docs/plan.md)

La medición es parte de la versión 0. Corre la rebanada sobre cada noche del registro y compara lo encontrado con lo que encontró la vigilancia anterior (D1).

Lista:
- [ ] Correr la rebanada sobre cada noche del registro
- [ ] Contar encontrados, no encontrados, fuera de alcance y falsos positivos
- [ ] Costo y duración por noche
- [ ] Versionar las métricas con la huella del registro usado (ADR 0007)

Registro:
- 2026-11-07: Creada con el plan (T07).

### T21 · Completar el analista
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/contratos.md](docs/contratos.md)

En la primera rebanada el analista solo pasa la afirmación. Acá escribe qué dice, por qué importa y qué mirar.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Registro:
- 2026-11-07: Creada con el plan (T07).

### T22 · Implementar el presupuesto que corta
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T08
- Detalle: [adrs/0005-presupuesto-por-corrida.md](adrs/0005-presupuesto-por-corrida.md)

El advisor de presupuesto, el libro de la corrida y la tabla de precios del ADR 0005.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [ ] Advisor de presupuesto en todos los ChatClient
- [ ] Libro de la corrida con actualización atómica
- [ ] Tabla de precios con fuente y fecha
- [ ] Corte por tiempo y mensaje corrida.cortada

Registro:
- 2026-11-07: Creada con el plan (T07).

### T23 · Armar la cola de revisión humana
- Tipo: Web
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/contratos.md](docs/contratos.md)

La cola de hechos INFERRED en la sala de máquinas, y la marca de pendiente en la página de la mañana (D6).

Etapas:
- [ ] Diseño
- [ ] Código
- [ ] Pruebas
- [ ] Página cómo se hizo

Registro:
- 2026-11-07: Creada con el plan (T07).

### T24 · Sumar los exploradores del BID, el Banco Mundial y Presidencia
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T08
- Detalle: [adrs/0006-fuentes-de-la-version-0.md](adrs/0006-fuentes-de-la-version-0.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md)

Las otras tres fuentes de la versión 0, cada una con sus descartes de datos personales.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [ ] Explorador del BID
- [ ] Explorador del Banco Mundial (API v3, campos acotados)
- [ ] Explorador de la normativa de Presidencia, a ritmo bajo

Registro:
- 2026-11-07: Creada con el plan (T07).

### T25 · Armar el login por enlace, los cuatro usuarios y la cuenta de demostración
- Tipo: Web
- Dueño: desarrollo
- Depende de: T08
- Detalle: [adrs/0003-eleccion-del-stack.md](adrs/0003-eleccion-del-stack.md), [adrs/0008-datos-personales.md](adrs/0008-datos-personales.md)

Login con token de un solo uso de Spring Security, correos en el esquema cuentas y lista de invitados desde la configuración.

Etapas:
- [ ] Diseño
- [ ] Código
- [ ] Pruebas
- [ ] Página cómo se hizo

Registro:
- 2026-11-07: Creada con el plan (T07).

### T26 · Escribir la sección "Cómo funciona" y las páginas "cómo se hizo"
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T08
- Detalle: [docs/arquitectura.md](docs/arquitectura.md)

La sección de la aplicación que enlaza a la documentación, y la página "cómo se hizo" de cada componente que todavía no la tenga (regla 6).

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Registro:
- 2026-11-07: Creada con el plan (T07).

### T27 · Escribir el ADR de despliegue
- Tipo: ADR
- Dueño: desarrollo
- Depende de: T08
- Detalle: [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md)

Dónde se publica el servicio para los usuarios invitados, con qué dominio y con qué servicio de correo (D9).

Etapas:
- [ ] Contexto y alternativas
- [ ] Fuentes verificadas
- [ ] Escrito
- [ ] Aceptado

Registro:
- 2026-11-07: Creada con el plan (T07).

## Hecho

### T28 · Hacer editable el kanban y sincronizarlo con el tablero
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [adrs/0009-kanban-editable-sincronizado.md](adrs/0009-kanban-editable-sincronizado.md), [herramientas/sincronizar_tablero.py](herramientas/sincronizar_tablero.py), [kanban/plantilla.html](kanban/plantilla.html)

El kanban publicado se puede editar con las mismas reglas del tablero, y las sesiones de desarrollo lo sincronizan con este archivo. La prueba del kanban corre en Chrome con una base simulada; la de la sincronización, en una copia del repositorio.

Etapas:
- [x] Escrita
- [x] Probada
- [x] Documentada

Registro:
- 2026-11-14: Creada y hecha.

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
