# Glosario

Los términos que usa el proyecto, en orden alfabético. Si un documento usa un término que no está acá ni se explica en el propio documento, se agrega.

| Término | Qué quiere decir |
|---|---|
| ADR | Registro de decisión de arquitectura (del inglés *Architecture Decision Record*). Un archivo corto en `adrs/` con la decisión, las razones, las alternativas rechazadas, los costos y qué la haría reversar. |
| ASSUMED | Grado de una afirmación: es un supuesto de trabajo, sin verificar. Si sostiene una decisión, tiene una tarjeta para verificarlo. |
| CHANGELOG | Archivo con cada cambio del repositorio, corto, con el enlace a su porqué. |
| Columna | En el tablero, el estado de una tarjeta: Pendiente, Para empezar, En curso, Esperando o Hecho. |
| Etapa | En el tablero, cada paso del ciclo fijo de un tipo de tarjeta. |
| GitHub Actions | El servicio de GitHub que corre tareas automáticas sobre el repositorio, descritas en archivos de `.github/workflows/`. Acá publica el kanban en cada push y corre las pruebas de noche. |
| GitHub Pages | El servicio de GitHub que publica páginas web estáticas desde un repositorio. Acá publica el kanban. |
| INFERRED | Grado de una afirmación: se deduce de algo verificado, pero no se comprobó directamente. |
| Integración continua | Correr las pruebas de forma automática sobre lo que está en el repositorio, sin depender de que alguien las corra. Acá, de noche y a demanda. |
| Kanban | Forma de ver el trabajo como tarjetas que avanzan por columnas. Acá es una vista de solo lectura de `tablero.md`. |
| Mermaid | Lenguaje de texto para escribir diagramas dentro del Markdown. |
| PROVEN | Grado de una afirmación: verificado, con su fuente (una URL o una comprobación descrita) y su fecha. |
| Tablero | El archivo `tablero.md`, con todo lo pendiente en tarjetas con dueño. Es lo que manda. |
| Tarjeta | Un pendiente del tablero: un bloque `###` con tipo, dueño, etapas, lista y registro. |
| Verificador | `herramientas/verificar_docs.py`: controla lo que se puede controlar de los documentos y el tablero, y falla si algo no cumple. |
