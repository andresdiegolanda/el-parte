# ADRs

Cada decisión significativa del proyecto es un ADR, un registro de decisión de arquitectura (regla 3). La plantilla y las reglas para escribirlos están en [arranque/como-se-trabaja.md](../arranque/como-se-trabaja.md). Las decisiones de arranque que todavía no tienen ADR están en [arranque/decisiones-iniciales.md](../arranque/decisiones-iniciales.md).

## Índice

| Número | Decisión | Estado | Fecha |
|---|---|---|---|
| [0001](0001-tablero-fuente-unica-kanban-generado.md) | El tablero en Markdown es la fuente única y el kanban se genera desde él | reemplazado por 0009 | 2026-11-07 |
| [0002](0002-orquestacion-por-mensajes.md) | Los agentes se coordinan por mensajes en tópicos | aceptado | 2026-11-07 |
| [0003](0003-eleccion-del-stack.md) | Elección del stack | aceptado; respuestas grabadas, reemplazado por 0011 | 2026-11-07 |
| [0004](0004-grados-de-evidencia.md) | Grados de evidencia | aceptado | 2026-11-07 |
| [0005](0005-presupuesto-por-corrida.md) | Presupuesto por corrida | aceptado; valores calibrados el 2026-11-17 | 2026-11-07 |
| [0006](0006-fuentes-de-la-version-0.md) | Fuentes de la versión 0 y de la primera rebanada | aceptado; IMPO, reemplazado por 0010 | 2026-11-07 |
| [0007](0007-registro-del-banco-fuera-del-repositorio.md) | El registro del banco de pruebas no entra al repositorio | aceptado | 2026-11-07 |
| [0008](0008-datos-personales.md) | Datos personales | aceptado | 2026-11-07 |
| [0009](0009-kanban-editable-sincronizado.md) | Kanban editable, sincronizado con el tablero | reemplazado por 0017 | 2026-11-14 |
| [0010](0010-impo-como-quinta-fuente.md) | IMPO entra como quinta fuente de la versión 0 | aceptado | 2026-11-14 |
| [0011](0011-grabacion-y-repeticion-de-corridas.md) | Las corridas se graban en la base y se repiten desde ahí; reemplaza las respuestas grabadas del 0003 | aceptado | 2026-11-14 |
| [0012](0012-tablero-de-arquitectura.md) | El tablero de arquitectura refleja el sistema y es la referencia de los pedidos | aceptado | 2026-11-14 |
| [0013](0013-acceso-perfil-y-parte-diario.md) | Cómo entra cada usuario, dónde queda su perfil y cómo lee su parte cada mañana | aceptado | 2026-11-14 |
| [0014](0014-medicion-contra-el-banco.md) | La medición contra el banco corre en un Compose aparte, noche por noche | aceptado | 2026-11-15 |
| [0015](0015-que-puede-escribir-el-analista.md) | Qué puede escribir el analista | aceptado | 2026-11-16 |
| [0016](0016-despliegue-en-la-maquina-del-responsable.md) | El Parte se publica desde la máquina del responsable, con un túnel de Cloudflare y el correo por Resend | aceptado | 2026-11-16 |
| [0017](0017-forma-de-trabajo-mas-liviana.md) | Una forma de trabajo más liviana: un lugar por dato, ritual según el tamaño, integración continua y tableros publicados; reemplaza al 0009 | aceptado | 2026-11-17 |
| [0018](0018-lectura-del-expediente-con-contexto-largo.md) | Qué puede escribir el modelo cuando lee un expediente entero: observaciones que citan hechos, graduadas por el refutador | propuesto | 2026-11-18 |
