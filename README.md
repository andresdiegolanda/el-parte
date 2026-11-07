# El Parte

El Parte es un servicio web. Un grupo de agentes orquestados lee todas las noches lo que publica el Estado uruguayo en sus fuentes públicas, extrae hechos (quién, qué, cuándo, con qué fuente) y los guarda en una base común. Cada cliente tiene una página donde se suscribe a entidades: organismos, empresas, programas, licitaciones. Cada mañana su página muestra lo que se encontró la noche anterior sobre sus suscripciones, con la fuente de cada hecho, y dice con claridad cuándo no hubo nada nuevo.

Es un proyecto didáctico y de portafolio. Su objetivo es mostrar cómo se construye un sistema de agentes orquestados que corre solo y no miente. Mostrar cómo se hizo es tan importante como el resultado: cualquiera que entre al repositorio o a la aplicación tiene que poder entender, sin hablar con nadie, qué hace cada pieza, por qué está, cómo se construyó y qué falló en el camino. No es un negocio: no hay pagos, planes ni soporte.

## Por dónde empezar

Si llegás por primera vez, seas persona o asistente, leé la carpeta [`arranque/`](arranque/README.md). Alcanza con eso para entender el proyecto y ponerte a trabajar.

## Estado

Fase de documentación: todavía no hay código. Lo que está en curso y lo que falta está en el [tablero](tablero.md), que también se ve como kanban en [`kanban/index.html`](kanban/index.html) (se abre en cualquier navegador). Lo que cambió, en el [CHANGELOG](CHANGELOG.md).

## Las reglas del proyecto

1. **Documentación primero.** Antes del código van la carpeta de arranque, la arquitectura, los contratos entre agentes, el modelo de la base y el plan. El código sigue a los documentos, y los documentos se corrigen cuando el código enseña algo.
2. **Toda afirmación técnica lleva su grado:** PROVEN con fuente, INFERRED o ASSUMED. Nada sobre un producto, una API o una fuente se escribe de memoria: se contrasta con la documentación oficial y se anota la URL y la fecha.
3. **Toda decisión significativa es un ADR:** decisión, razones, alternativas rechazadas, costos y qué la haría reversar.
4. **Todo cambio va al CHANGELOG**, corto: qué cambió, dónde, y el enlace al documento que tiene el porqué.
5. **Diagramas en todo lo que se explique:** Mermaid dentro del Markdown para flujos, secuencias y estados; SVG para las láminas de arquitectura, generadas por script desde su fuente y nunca editadas a mano, con un verificador que falle si una lámina no coincide con su generador.
6. **Cada componente tiene su página "cómo se hizo":** qué hace, por qué existe, cómo se construyó, qué se probó, qué falló y qué se cambió. La bitácora de lo que falló es contenido de primera, no algo que se esconde.
7. **Español rioplatense, sin guiones largos en ningún artefacto.** Los términos técnicos se explican la primera vez o remiten al [glosario](arranque/glosario.md).
8. **Todo pendiente tiene dueño y vive en el [tablero](tablero.md)**, en Markdown. El kanban se genera desde ese archivo.
9. **Nada se borra:** lo que sobra se mueve a [`obsoletos/`](obsoletos/README.md).
10. **El repositorio es el único handoff:** una sesión nueva, de una persona o de un asistente, arranca desde la carpeta [`arranque/`](arranque/README.md) sin necesitar nada más.
11. **Sin datos personales en la base ni en la demo;** solo fuentes públicas y datos sintéticos para las pruebas. El proyecto es independiente de cualquier trabajo para clientes y no incluye material de otros proyectos.
12. **Después de cada escritura se relee el archivo y se comprueba el cambio.** Nada se da por hecho sin verlo.

Cómo se cumple cada regla en la práctica está en [`arranque/como-se-trabaja.md`](arranque/como-se-trabaja.md).

## Licencia

Pendiente de decisión (ver el [tablero](tablero.md)). Mientras no haya licencia, rigen los derechos de autor por defecto.
