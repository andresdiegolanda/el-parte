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
| Cobertura | En la medición contra el banco de pruebas, cuánto encontró El Parte de lo que estaba a su alcance: encontrados sobre encontrados, vistos y descartados y no encontrados ([banco-de-pruebas/metricas.md](../banco-de-pruebas/metricas.md)). |
| Columna | En el tablero, el estado de una tarjeta: Pendiente, Para empezar, En curso, Esperando o Hecho. |
| Contenedor | Paquete que lleva un programa con todo lo que necesita para correr, igual en cualquier máquina. |
| Corrida | Una ejecución completa de la noche: todos los exploradores, el cotejo, la refutación, el análisis y la edición. |
| Datos abiertos | Datos que un organismo publica para que cualquiera los use, con una licencia que lo permite. |
| Editor | Agente que aplica las reglas de redacción, arma el informe de cada cliente y manda a revisión humana lo dudoso. |
| Enmascarar | Tapar un dato personal con `[persona]` antes de que salga del explorador, o descartar el campo entero (ADR 0008). Ver [docs/fuentes/compras-estatales.md](../docs/fuentes/compras-estatales.md) para cómo se decide en ARCE. |
| Entidad | Aquello a lo que un cliente se suscribe: un organismo, una empresa (persona jurídica), un programa o una licitación. |
| Etapa | En el tablero, cada paso del ciclo fijo de un tipo de tarjeta (por ejemplo, las de un componente: contrato, código, pruebas, medición y página "cómo se hizo"). |
| Expediente | Todo lo que la base sabe de una entidad, para consultar cuando se quiera. |
| Explorador | Agente que lee una fuente y devuelve candidatos. Hay uno por fuente. |
| Falso positivo | Algo que el sistema presentó como hallazgo y no lo era: material viejo, un hecho repetido, una deducción presentada como hecho. |
| Flujo de trabajo | Sistema de agentes en el que el código fija el camino y los modelos se llaman en pasos predefinidos. Una noche de El Parte es un flujo de trabajo. |
| Fuera de alcance | Hallazgo del banco de pruebas que ninguna fuente de El Parte publica, como la prensa o el sitio del BPS ("fuera de El Parte"), o que publica una fuente que esa medición no leyó ("fuera de esta medición"). Se cuenta aparte y no baja la cobertura ([banco-de-pruebas/metricas.md](../banco-de-pruebas/metricas.md)). |
| GitHub Actions | El servicio de GitHub que corre tareas automáticas sobre el repositorio, descritas en archivos de `.github/workflows/`. En El Parte publica los tableros en cada push y corre las pruebas de noche ([ADR 0017](../adrs/0017-forma-de-trabajo-mas-liviana.md)). |
| GitHub Pages | El servicio de GitHub que publica páginas web desde un repositorio. En El Parte publica el kanban y el tablero de arquitectura en <https://andresdiegolanda.github.io/el-parte/>. |
| Grabación | Lo que una corrida leyó de afuera, de una fuente o del modelo, guardado ya enmascarado en `parte.grabaciones` para poder repetirla (ADR 0011). |
| Grado (de un documento) | Lo que lleva cada afirmación técnica de los documentos del proyecto: PROVEN con fuente, INFERRED o ASSUMED. Ver [como-se-trabaja.md](como-se-trabaja.md). |
| Grado (de un hecho) | Lo que pone el refutador a cada hecho: PROVEN (la fuente lo dice), INFERRED (se deduce de lo que dice la fuente) o NOT_MENTIONED (las fuentes no lo mencionan). |
| Huella | Resumen corto (hash) de un contenido. Si el contenido cambia, la huella cambia. Cada medición guarda la del registro del banco de pruebas con que se hizo, sin publicarlo. |
| IATI | Estándar internacional para publicar datos de proyectos de cooperación (*International Aid Transparency Initiative*). |
| Idempotencia | Propiedad de una operación que, repetida, deja todo igual que si se hubiera hecho una sola vez. Acá: procesar dos veces el mismo mensaje no crea dos hechos. |
| Identidad de un hecho | La regla que dice cuándo dos candidatos son el mismo hecho. Se define en `docs/contratos.md`. |
| INFERRED | Grado: se deduce de algo verificado, pero no se comprobó directamente. Vale para hechos y para documentos. |
| Integración continua | Correr las pruebas de forma automática sobre lo que está en el repositorio, sin depender de que alguien las corra. En El Parte, de noche y a demanda, con GitHub Actions. |
| Kafka | Sistema de mensajería con tópicos que guardan su historia y se pueden volver a leer. Es el que usa El Parte ([ADR 0003](../adrs/0003-eleccion-del-stack.md)). |
| Kanban | Forma de ver el trabajo como tarjetas que avanzan por columnas. En El Parte es una vista de solo lectura de `tablero.md`, publicada en <https://andresdiegolanda.github.io/el-parte/kanban/>. |
| Lámina | Diagrama de arquitectura en SVG, generado por un script desde su fuente. No se edita a mano. |
| Libro de la corrida | Fila de la base que lleva lo gastado y lo reservado de una corrida, contra su presupuesto ([ADR 0005](../adrs/0005-presupuesto-por-corrida.md)). |
| LTS | Versión con soporte largo (del inglés *long-term support*). Java 25 es la LTS más nueva. |
| Mailpit | Servidor de correo de prueba: captura los correos que manda la aplicación y los muestra en una página web. |
| Mermaid | Lenguaje de texto para escribir diagramas dentro de un Markdown. GitHub los dibuja al mostrar el archivo. |
| Micrometer | Biblioteca de métricas de Spring. Spring AI la usa para contar llamadas al modelo y tokens. |
| Migración | Un archivo SQL que lleva la base de una versión a la siguiente. Los aplica Flyway, en orden, al arrancar la aplicación; cada uno corre una sola vez. |
| Modelo de lenguaje | El sistema de inteligencia artificial que lee y escribe texto y que usan los agentes por dentro. |
| Modo banco | El modo de una corrida que repite una noche del banco de pruebas: su fecha objetivo es esa noche y sigue solo la entidad medida. Las corridas de una medición corren en orden, en un Docker Compose aparte con su propia base ([adrs/0014-medicion-contra-el-banco.md](../adrs/0014-medicion-contra-el-banco.md)). |
| NOT_MENTIONED | Grado de un hecho: las fuentes no respaldan lo que afirma el candidato. No se publica; queda en la sala de máquinas con lo que se buscó. |
| Observador | Agente que copia cada mensaje de cada tópico al registro de mensajes de la base, que es de donde salen la sala de máquinas y las mediciones: precisión por fuente, costo y duración por corrida y por agente, y fuentes bloqueadas. |
| OCDS | Estándar abierto de datos de contrataciones públicas (*Open Contracting Data Standard*). Es el formato en que publica ARCE. |
| Orquestación | La forma en que los agentes se reparten el trabajo y se pasan los resultados, con reglas fijas. |
| Página de la mañana | Lo que ve cada cliente al día siguiente: lo nuevo sobre sus suscripciones desde ayer, con la fuente de cada hecho, o un aviso claro de que no hubo nada. |
| PostgreSQL | Base de datos relacional de código abierto. Candidata para la base de afirmaciones. |
| Precisión | En la medición contra el banco de pruebas, cuánto de lo que El Parte publicó o mandó a revisión está bien: verdaderos sobre verdaderos y falsos positivos, por grado ([banco-de-pruebas/metricas.md](../banco-de-pruebas/metricas.md)). |
| Presupuesto por corrida | Límite de pasos, tiempo y costo de una corrida. Cuando se pasa, la corrida se corta y lo dice. |
| PROVEN | Grado: verificado. En un hecho, la fuente lo dice. En un documento, hay URL oficial o comprobación descrita, con fecha. |
| Publicable | Descarte del banco de pruebas que es un hecho real y nuevo de la entidad, y que la vigilancia anterior descartó porque no era de su tema. El Parte tendría que publicarlo, y se mide como un hallazgo. |
| Rebanada de punta a punta | Una versión chica pero completa del sistema, que atraviesa todas las capas: de la fuente a la página de la mañana. |
| Recorrido | En el tablero de arquitectura, un camino por el sistema parada por parada, por ejemplo una noche normal o una corrida repetida. |
| Refutador | Agente que intenta tirar abajo cada candidato y le pone un grado. |
| Reproducción | El modo de una corrida que repite otra leyendo solo sus grabaciones: sin red, sin costo y con los mismos mensajes. Ver [docs/contratos.md](../docs/contratos.md), "Repetir una corrida". |
| Revisión del banco | Lo que deciden las personas que leen el registro del banco de pruebas: qué fuente de El Parte publica cada entrada, qué etiqueta lleva cada descarte y qué hecho corresponde a qué entrada. Vive junto al registro y no se versiona ([banco-de-pruebas/formato.md](../banco-de-pruebas/formato.md)). |
| Revisión humana | Cola de hechos dudosos que revisa una persona. Mientras no se revisan, se muestran marcados. |
| robots.txt | Archivo que un sitio publica para decir qué partes no quiere que lean los programas automáticos. Se respeta. |
| Sala de máquinas | La parte de la aplicación que muestra, por cada corrida y cada hallazgo, qué agente hizo qué, qué se refutó y por qué, contra qué se cotejó, cuánto costó y cuánto tardó. |
| Salida estructurada | Respuesta del modelo en un formato fijo (un tipo Java con su esquema JSON) en lugar de texto libre. Spring AI la valida y la vuelve a pedir si no cumple. |
| Sincronización del kanban | El paso que llevaba los cambios del kanban editable a `tablero.md` ([ADR 0009](../adrs/0009-kanban-editable-sincronizado.md)). Ya no existe: el kanban es de solo lectura ([ADR 0017](../adrs/0017-forma-de-trabajo-mas-liviana.md)). |
| Sobre | Los campos comunes a todo mensaje entre agentes: identificador, tipo, versión, corrida, causa, agente y momento de emisión. |
| Spring AI | Proyecto del ecosistema Spring para usar modelos de lenguaje desde Java. Su estado actual se verifica en el ADR del stack. |
| Spring Boot | Forma estándar de armar aplicaciones Java con Spring, con configuración por defecto. |
| SVG | Formato de imagen vectorial escrito en texto. Se usa para las láminas. |
| Tablero | Archivo `tablero.md` con todo lo pendiente, en tarjetas. Es la única fuente del kanban. No confundir con el tablero de arquitectura. |
| Tablero de arquitectura | La página de `docs/tablero-de-arquitectura/` con el sistema objeto por objeto: refleja la arquitectura en todo momento, el verificador la compara con el código y los pedidos citan sus objetos (`arquitectura: <id>`). No es el tablero de tareas. Ver el ADR 0012. |
| Tarjeta | Un pendiente del tablero: un bloque `###` de `tablero.md` con tipo, dueño, notas, etapas, lista y registro. |
| Testcontainers | Biblioteca que levanta contenedores de Docker desde una prueba, para probar contra un PostgreSQL o un Kafka de verdad y no contra imitaciones. |
| Token | Unidad en la que un modelo de lenguaje mide el texto que lee y escribe. Los proveedores cobran por millón de tokens. |
| Tópico | Canal con nombre de un sistema de mensajería. Un agente publica mensajes en un tópico y otros los leen. Guardar los mensajes permite repetir una corrida y armar la sala de máquinas. |
