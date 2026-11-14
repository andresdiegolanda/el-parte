# Glosario

Los términos que usa el proyecto, en orden alfabético. Si un documento usa un término que no está acá ni se explica en el propio documento, se agrega.

| Término | Qué quiere decir en El Parte |
|---|---|
| ADR | Registro de decisión de arquitectura (del inglés *Architecture Decision Record*). Un archivo corto en `adrs/` con la decisión, las razones, las alternativas rechazadas, los costos y qué la haría reversar. |
| Advisor | En Spring AI, un interceptor que envuelve cada llamada al modelo. Se encadenan en orden y con ellos funcionan las herramientas, la validación de la salida y la observación. |
| Agente | Programa que usa un modelo de lenguaje para cumplir un rol acotado (explorar una fuente, refutar, analizar). Recibe mensajes y produce mensajes. |
| Analista | Agente que, para cada hecho que sobrevivió al refutador, escribe qué dice, por qué importa y qué mirar. |
| API | Interfaz para que un programa le pida datos a otro de forma estructurada, sin leer páginas pensadas para personas. |
| Archivista | Agente dueño de la memoria. Coteja cada candidato contra la base de afirmaciones y lo marca como conocido, actualización o nuevo. |
| ASSUMED | Grado de una afirmación de los documentos del proyecto: supuesto de trabajo, sin verificar. |
| Banco de pruebas | Registro de una vigilancia anterior hecha con un solo agente, contra el que se mide El Parte. No se versiona. |
| Base de afirmaciones | La base de datos de hechos: entidad, estado, fecha, grado, fuente y la traza de qué agente los produjo. |
| Candidato | Lo que devuelve un explorador: un posible hecho con su evidencia cruda (URL, texto, fecha detectada). Todavía no es un hecho. |
| CC BY 4.0 | Licencia Creative Commons Atribución 4.0: permite reutilizar los datos para cualquier fin, citando la fuente. La usan el BID y el Banco Mundial. |
| CHANGELOG | Archivo con cada cambio del repositorio, corto, con enlace al documento que tiene el porqué. |
| ChatClient | En Spring AI, la API de uso diario para hablar con un modelo. Por dentro usa un ChatModel. |
| ChatModel | En Spring AI, la interfaz común a todos los proveedores de modelos. Cambiar de proveedor pide cambios mínimos de código. |
| Clave de hecho | La identidad de un hecho: dice cuándo dos candidatos son el mismo. Se arma con la fuente, su identificador estable y el tipo de hecho ([docs/contratos.md](../docs/contratos.md)). |
| Columna | En el tablero, el estado de una tarjeta: Pendiente, Para empezar, En curso, Esperando o Hecho. |
| Contenedor | Paquete que lleva un programa con todo lo que necesita para correr, igual en cualquier máquina. |
| Corrida | Una ejecución completa de la noche: todos los exploradores, el cotejo, la refutación, el análisis y la edición. |
| Datos abiertos | Datos que un organismo publica para que cualquiera los use, con una licencia que lo permite. |
| Editor | Agente que aplica las reglas de redacción, arma el informe de cada cliente y manda a revisión humana lo dudoso. |
| Entidad | Aquello a lo que un cliente se suscribe: un organismo, una empresa (persona jurídica), un programa o una licitación. |
| Etapa | En el tablero, cada paso del ciclo fijo de un tipo de tarjeta (por ejemplo, las de un componente: contrato, código, pruebas, medición y página "cómo se hizo"). |
| Expediente | Todo lo que la base sabe de una entidad, para consultar cuando se quiera. |
| Explorador | Agente que lee una fuente y devuelve candidatos. Hay uno por fuente. |
| Falso positivo | Algo que el sistema presentó como hallazgo y no lo era: material viejo, un hecho repetido, una deducción presentada como hecho. |
| Flujo de trabajo | Sistema de agentes en el que el código fija el camino y los modelos se llaman en pasos predefinidos. Una noche de El Parte es un flujo de trabajo. |
| Fuera de alcance | Hallazgo del banco de pruebas que vino de un lugar que El Parte no lee (prensa, buscadores). Se cuenta aparte en la medición. |
| Grado (de un documento) | Lo que lleva cada afirmación técnica de los documentos del proyecto: PROVEN con fuente, INFERRED o ASSUMED. Ver [como-se-trabaja.md](como-se-trabaja.md). |
| Grado (de un hecho) | Lo que pone el refutador a cada hecho: PROVEN (la fuente lo dice), INFERRED (se deduce de lo que dice la fuente) o NOT_MENTIONED (las fuentes no lo mencionan). |
| Huella | Resumen corto (hash) de un contenido. Si el contenido cambia, la huella cambia. La sincronización del kanban la usa para saber si una tarjeta cambió en GitHub. |
| IATI | Estándar internacional para publicar datos de proyectos de cooperación (*International Aid Transparency Initiative*). |
| Idempotencia | Propiedad de una operación que, repetida, deja todo igual que si se hubiera hecho una sola vez. Acá: procesar dos veces el mismo mensaje no crea dos hechos. |
| Identidad de un hecho | La regla que dice cuándo dos candidatos son el mismo hecho. Se define en `docs/contratos.md`. |
| INFERRED | Grado: se deduce de algo verificado, pero no se comprobó directamente. Vale para hechos y para documentos. |
| Kafka | Sistema de mensajería con tópicos que guardan su historia y se pueden volver a leer. Es el que usa El Parte ([ADR 0003](../adrs/0003-eleccion-del-stack.md)). |
| Kanban | Forma de ver el trabajo como tarjetas que avanzan por columnas. En El Parte, la página publicada se puede editar y se sincroniza con `tablero.md`; `kanban/index.html` es su copia sin conexión. |
| Lámina | Diagrama de arquitectura en SVG, generado por un script desde su fuente. No se edita a mano. |
| Libro de la corrida | Fila de la base que lleva lo gastado y lo reservado de una corrida, contra su presupuesto ([ADR 0005](../adrs/0005-presupuesto-por-corrida.md)). |
| LTS | Versión con soporte largo (del inglés *long-term support*). Java 25 es la LTS más nueva. |
| Mailpit | Servidor de correo de prueba: captura los correos que manda la aplicación y los muestra en una página web. |
| Mermaid | Lenguaje de texto para escribir diagramas dentro de un Markdown. GitHub los dibuja al mostrar el archivo. |
| Micrometer | Biblioteca de métricas de Spring. Spring AI la usa para contar llamadas al modelo y tokens. |
| Modelo de lenguaje | El sistema de inteligencia artificial que lee y escribe texto y que usan los agentes por dentro. |
| NOT_MENTIONED | Grado de un hecho: las fuentes no respaldan lo que afirma el candidato. No se publica; queda en la sala de máquinas con lo que se buscó. |
| Observador | Agente que mide precisión por fuente, costo y duración por corrida y por agente, y fuentes bloqueadas. |
| OCDS | Estándar abierto de datos de contrataciones públicas (*Open Contracting Data Standard*). Es el formato en que publica ARCE. |
| Orquestación | La forma en que los agentes se reparten el trabajo y se pasan los resultados, con reglas fijas. |
| Página de la mañana | Lo que ve cada cliente al día siguiente: lo nuevo sobre sus suscripciones desde ayer, con la fuente de cada hecho, o un aviso claro de que no hubo nada. |
| PostgreSQL | Base de datos relacional de código abierto. Candidata para la base de afirmaciones. |
| Presupuesto por corrida | Límite de pasos, tiempo y costo de una corrida. Cuando se pasa, la corrida se corta y lo dice. |
| PROVEN | Grado: verificado. En un hecho, la fuente lo dice. En un documento, hay URL oficial o comprobación descrita, con fecha. |
| Rebanada de punta a punta | Una versión chica pero completa del sistema, que atraviesa todas las capas: de la fuente a la página de la mañana. |
| Refutador | Agente que intenta tirar abajo cada candidato y le pone un grado. |
| Revisión humana | Cola de hechos dudosos que revisa una persona. Mientras no se revisan, se muestran marcados. |
| robots.txt | Archivo que un sitio publica para decir qué partes no quiere que lean los programas automáticos. Se respeta. |
| Sala de máquinas | La parte de la aplicación que muestra, por cada corrida y cada hallazgo, qué agente hizo qué, qué se refutó y por qué, contra qué se cotejó, cuánto costó y cuánto tardó. |
| Salida estructurada | Respuesta del modelo en un formato fijo (un tipo Java con su esquema JSON) en lugar de texto libre. Spring AI la valida y la vuelve a pedir si no cumple. |
| Sincronización del kanban | El paso, hecho por una sesión de desarrollo, que lleva los cambios del kanban publicado a `tablero.md` y el estado de `tablero.md` al kanban ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md)). |
| Sobre | Los campos comunes a todo mensaje entre agentes: identificador, tipo, versión, corrida, causa, agente y momento de emisión. |
| Spring AI | Proyecto del ecosistema Spring para usar modelos de lenguaje desde Java. Su estado actual se verifica en el ADR del stack. |
| Spring Boot | Forma estándar de armar aplicaciones Java con Spring, con configuración por defecto. |
| SVG | Formato de imagen vectorial escrito en texto. Se usa para las láminas. |
| Tablero | Archivo `tablero.md` con todo lo pendiente, en tarjetas. Es la única fuente del kanban. |
| Tarjeta | Un pendiente del tablero: un bloque `###` de `tablero.md` con tipo, dueño, notas, etapas, lista y registro. |
| Token | Unidad en la que un modelo de lenguaje mide el texto que lee y escribe. Los proveedores cobran por millón de tokens. |
| Tópico | Canal con nombre de un sistema de mensajería. Un agente publica mensajes en un tópico y otros los leen. Guardar los mensajes permite repetir una corrida y armar la sala de máquinas. |
