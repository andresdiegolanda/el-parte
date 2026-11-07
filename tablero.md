# Tablero

Todo pendiente de El Parte vive acá, con dueño (regla 8). Este archivo es la única fuente del kanban: [`kanban/index.html`](kanban/index.html) se genera desde acá con `python3 herramientas/generar_kanban.py` y no se edita a mano ([ADR 0001](adrs/0001-tablero-fuente-unica-kanban-generado.md)).

- Actualizado: 2026-11-07
- Nota: Licencia MIT (T13) y etiquetas en el README. Lo próximo es T09, que destraba la arquitectura y el ADR del stack.
- Calendario: 2026-11-07, 2026-11-14, 2026-11-21, 2026-11-28
- Repositorio: https://github.com/andresdiegolanda/el-parte

## Cómo se usa

Cada `##` con nombre de columna agrupa tarjetas, y cada `###` adentro es una tarjeta. Mover una tarjeta es mover su bloque a otra columna. Dentro de una columna, el orden es la prioridad: la primera de "Para empezar" es la próxima. En este archivo las columnas van en el orden en que conviene leerlas (lo que se está haciendo primero); el kanban las muestra en el orden de la tabla de columnas.

Después de cada cambio se vuelve a generar el kanban y se corre el verificador, que controla el formato y estas reglas:

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

### T09 · Verificar Spring AI y sus patrones de orquestación
- Tipo: Verificación
- Dueño: desarrollo
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

Antes de apoyarse en Spring AI hay que saber qué ofrece hoy, verificado en su documentación oficial con URL y fecha. Destraba la arquitectura (T02) y el ADR del stack (T05).

Lista:
- [ ] Versión vigente y fecha de publicación
- [ ] Patrones de orquestación de agentes que documenta
- [ ] Cómo abstrae el proveedor de modelos (D4)
- [ ] Integración con mensajería en tópicos

Registro:
- 2026-11-07: Creada.

### T10 · Verificar las fuentes candidatas
- Tipo: Verificación
- Dueño: desarrollo
- Detalle: [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md)

De cada fuente: permiso de uso, formato, si deja consultar por fecha y si nombra personas físicas. Cada una termina en su página en `docs/fuentes/`, con URL y fecha de verificación. Con eso se elige la fuente de la primera rebanada (D5).

Lista:
- [ ] Compras estatales
- [ ] Diario oficial (IMPO)
- [ ] Resoluciones de directorio del organismo del banco de pruebas
- [ ] Presidencia y novedades de gub.uy
- [ ] BID
- [ ] Banco Mundial
- [ ] CAF
- [ ] Elegir la fuente de la primera rebanada

Registro:
- 2026-11-07: Creada.

### T06 · Escribir los ADRs de las decisiones D2 y D3
- Tipo: ADR
- Dueño: desarrollo
- Detalle: [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md)

D2 deja el registro del banco de pruebas fuera del repositorio y D3 fija cómo se tratan los datos personales. Las dos son significativas y pasan a ADR (regla 3).

Etapas:
- [ ] Contexto y alternativas
- [ ] Fuentes verificadas
- [ ] Escrito
- [ ] Aceptado

Lista:
- [ ] ADR de D2: registro fuera del repositorio
- [ ] ADR de D3: datos personales

Registro:
- 2026-11-07: Creada.

### T15 · Comprobar en el verificador que los diagramas Mermaid se dibujan
- Tipo: Herramienta
- Dueño: desarrollo
- Detalle: [herramientas/verificar_docs.py](herramientas/verificar_docs.py)

Los diagramas que había el 2026-11-07 se comprobaron a mano con mermaid-cli y Chrome sin ventana, y se dibujan todos. Falta que el verificador lo haga solo, y que se saltee con un aviso donde no haya navegador.

Etapas:
- [ ] Escrita
- [ ] Probada
- [ ] Documentada

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

## Pendiente

### T02 · Escribir docs/arquitectura.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T09
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

La visión general del sistema y la primera lámina en SVG, con su generador y el verificador que falla si la lámina no coincide (regla 5).

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Visión general en Mermaid
- [ ] Los agentes y sus razones
- [ ] Secuencia de una noche
- [ ] Lámina de arquitectura en SVG con su generador
- [ ] Verificador de láminas

Registro:
- 2026-11-07: Creada.

### T03 · Escribir docs/contratos.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T02

Los mensajes entre agentes. De esto depende que cada corrida se pueda repetir y que la sala de máquinas se arme desde los mensajes.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Esquema y ejemplo de cada mensaje
- [ ] Regla de identidad e idempotencia de cada hecho
- [ ] Umbral de "demasiado INFERRED" (D6)

Registro:
- 2026-11-07: Creada.

### T04 · Escribir docs/base-de-afirmaciones.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T03

El modelo de datos de la base de afirmaciones, con diagrama.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Modelo de datos con diagrama
- [ ] Traza de qué agente produjo cada hecho
- [ ] Tabla de usuarios aparte (D3)

Registro:
- 2026-11-07: Creada.

### T05 · Escribir los primeros ADRs
- Tipo: ADR
- Dueño: desarrollo
- Depende de: T09
- Detalle: [arranque/como-se-trabaja.md](arranque/como-se-trabaja.md)

Las cuatro decisiones de base del sistema. El del stack fija los modelos concretos, el sistema de mensajería, la interfaz web y el buzón de correo de prueba (D9).

Etapas:
- [ ] Contexto y alternativas
- [ ] Fuentes verificadas
- [ ] Escrito
- [ ] Aceptado

Lista:
- [ ] Orquestación por mensajes
- [ ] Elección del stack
- [ ] Grados de evidencia
- [ ] Presupuesto por corrida (D4)

Registro:
- 2026-11-07: Creada.

### T07 · Escribir docs/plan.md
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T02

El plan de los cuatro fines de semana del calendario (D10).

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Lista:
- [ ] Qué se muestra al final de cada fin de semana
- [ ] Que entre en unas seis horas de revisión por fin de semana

Registro:
- 2026-11-07: Creada.

### T08 · Construir la primera rebanada de punta a punta
- Tipo: Componente
- Dueño: desarrollo
- Depende de: T02 a T07, T10, T14
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

Chica y completa le gana a grande y a medias: una fuente, del explorador a la página de la mañana, con la sala de máquinas de esa corrida.

Etapas:
- [ ] Contrato
- [ ] Código
- [ ] Pruebas
- [ ] Medido contra el banco
- [ ] Página cómo se hizo

Lista:
- [ ] Una fuente y su explorador
- [ ] Archivista
- [ ] Refutador
- [ ] Editor
- [ ] Página de la mañana
- [ ] Sala de máquinas de la corrida

Registro:
- 2026-11-07: Creada.

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

## Hecho

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
