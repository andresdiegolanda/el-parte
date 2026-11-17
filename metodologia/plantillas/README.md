# {proyecto}

{Qué es el proyecto, para quién y qué problema resuelve, en dos o tres frases.}

## Por dónde empezar

Si llegás por primera vez, seas persona o asistente, leé la carpeta [`arranque/`](arranque/README.md). Alcanza con eso para entender el proyecto y ponerte a trabajar.

## Estado

{En qué fase está, en una frase.} Lo que está en curso y lo que falta está en el [tablero](tablero.md), que también se ve como kanban en <{sitio}kanban/>. Lo que cambió, en el [CHANGELOG](CHANGELOG.md).

## Las reglas del proyecto

1. **Documentación primero.** Antes del código van la carpeta de arranque, el diseño y el plan. El código sigue a los documentos, y los documentos se corrigen cuando el código enseña algo.
2. **Toda afirmación técnica lleva su grado:** PROVEN con fuente, INFERRED o ASSUMED. Nada sobre un producto, una API o una fuente se escribe de memoria: se contrasta con la documentación oficial y se anota la URL y la fecha.
3. **Toda decisión significativa es un ADR:** decisión, razones, alternativas rechazadas, costos y qué la haría reversar.
4. **Todo cambio va al CHANGELOG**, corto: qué cambió, dónde, y el enlace al documento que tiene el porqué.
5. **Diagramas en todo lo que se explique**, en Mermaid dentro del Markdown. Lo que se genera desde una fuente no se edita a mano.
6. **Cada componente tiene su página "cómo se hizo":** qué hace, por qué existe, cómo se construyó, qué se probó, qué falló y qué se cambió. Lo que falló es contenido de primera.
7. **Un idioma y un estilo:** {idioma}, sin guiones largos en ningún archivo. Los términos técnicos se explican la primera vez o remiten al [glosario](arranque/glosario.md).
8. **Todo pendiente tiene dueño y vive en el [tablero](tablero.md)**, en Markdown. El kanban se genera desde ese archivo.
9. **Nada se borra:** lo que sobra se mueve a [`obsoletos/`](obsoletos/README.md).
10. **El repositorio es el único handoff:** una sesión nueva, de una persona o de un asistente, arranca desde la carpeta [`arranque/`](arranque/README.md) sin necesitar nada más.
11. **Lo sensible no entra al repositorio:** claves, datos personales y material de otros proyectos quedan afuera.
12. **Después de cada escritura se relee el archivo y se comprueba el cambio.** Nada se da por hecho sin verlo.
13. **Un solo lugar por dato:** cada cifra, valor o decisión vive en un documento, y los demás lo enlazan.
14. **Lo temporal va en `tmp/`,** en la raíz del clon, que no se versiona. Lo que vale la pena guardar pasa al repositorio.

Cómo se cumple cada regla en la práctica está en [`arranque/como-se-trabaja.md`](arranque/como-se-trabaja.md).
