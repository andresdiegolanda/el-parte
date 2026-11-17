# Tablero

Todo pendiente de {proyecto} vive acá, con dueño (regla 8). Este archivo es lo que manda. El kanban es una vista de solo lectura de este archivo: se genera con `python3 herramientas/generar_kanban.py` y se publica en <{sitio}kanban/> en cada push.

- Actualizado: {fecha}
- Nota: {Dónde está el proyecto y dónde mirar, en pocas frases y sin cifras: las cifras viven en su documento (regla 13).}
- Calendario: {fecha}
- Repositorio: {repositorio}
- Proyecto: {proyecto}
- Objetivo: la primera versión

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
| Componente | Contrato; Código; Pruebas; Página cómo se hizo |
| Herramienta | Escrita; Probada; Documentada |
| Verificación | (sin etapas) |
| Gestión | (sin etapas) |

### Dueños

| Dueño | Quién es |
|---|---|
| desarrollo | La sesión que esté trabajando en el repositorio, de una persona o de un asistente |
| {responsable} | Responsable del proyecto: decisiones, accesos, claves y material que no está en el repositorio |

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

### T01 · Escribir qué es el proyecto
- Tipo: Documento
- Dueño: desarrollo
- Detalle: [arranque/proyecto.md](arranque/proyecto.md)

Qué es, para quién, qué entra en la primera versión, qué no entra y cómo se sabe que anda.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Registro:
- {fecha}: Creada al arrancar el repositorio.

### T02 · Escribir las decisiones iniciales
- Tipo: Documento
- Dueño: desarrollo
- Detalle: [arranque/decisiones-iniciales.md](arranque/decisiones-iniciales.md)

Las preguntas que la definición del proyecto no resuelve, y lo que se decide para cada una.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Registro:
- {fecha}: Creada al arrancar el repositorio.

## Esperando

## Pendiente

### T03 · Escribir el plan de la primera versión
- Tipo: Documento
- Dueño: desarrollo
- Depende de: T01, T02

En qué orden se construye la primera versión, de punta a punta, y cómo se mide.

Etapas:
- [ ] Borrador
- [ ] Grados y fuentes
- [ ] Diagramas
- [ ] Releído y verificado

Registro:
- {fecha}: Creada al arrancar el repositorio.

## Hecho

### T04 · Arrancar el repositorio con la metodología
- Tipo: Gestión
- Dueño: desarrollo

Las plantillas y las herramientas de la metodología de El Parte, copiadas y adaptadas.

Registro:
- {fecha}: Hecho.
