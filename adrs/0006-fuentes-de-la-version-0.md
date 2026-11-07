# 0006. Fuentes de la versión 0 y de la primera rebanada

- Estado: propuesto
- Fecha: 2026-11-07

## Contexto

La versión 0 lleva entre cuatro y seis fuentes públicas con permiso de uso. La decisión D5 propuso siete candidatas, sujetas a verificar sus términos y formatos, y pidió que la primera rebanada use una fuente que se pueda consultar por fecha, para medirla contra el banco de pruebas. La verificación de las siete está en [docs/fuentes/](../docs/fuentes/README.md).

## Decisión

| Fuente | Decisión |
|---|---|
| Compras estatales (ARCE) | Entra. Es la fuente de la primera rebanada. |
| BID | Entra. |
| Banco Mundial | Entra, como fuente secundaria. |
| Normativa de Presidencia | Entra, leída a ritmo bajo y citando siempre la fuente. |
| Diario Oficial (IMPO) | Queda afuera hasta que IMPO aclare por escrito si su JSON se puede reutilizar (T18). |
| Resoluciones de directorio del BPS | Queda afuera: sus términos prohíben la consulta automática. |
| CAF | Queda afuera: su sitio bloquea robots y su archivo abierto se actualiza cada pocos meses. |
| Noticias de gub.uy | Quedan afuera: no se pueden consultar por fecha. |

La primera rebanada lee ARCE filtrando por el BPS como comprador, porque el BPS es el organismo del banco de pruebas. Que el BPS aparezca como comprador en los datos de ARCE se verifica al escribir el explorador. [ASSUMED]

**El explorador de ARCE filtra por organismo antes de llamar al modelo.** ARCE publica entre 550 y 670 publicaciones por día hábil. [PROVEN: [docs/fuentes/compras-estatales.md](../docs/fuentes/compras-estatales.md), 2026-11-07] Pasarlas todas por un modelo rompería el presupuesto de 0,50 USD por corrida ([ADR 0005](0005-presupuesto-por-corrida.md)). [INFERRED] El filtro por comprador es código común, sin modelo.

## Razones

- **ARCE tiene el mejor permiso:** una licencia de datos abiertos explícita, enlazada en cada dato. [PROVEN: [docs/fuentes/compras-estatales.md](../docs/fuentes/compras-estatales.md), 2026-11-07]
- **Se puede repetir una noche:** cada publicación tiene su fecha, y el feed mensual cubre desde diciembre de 2017, con todas las noches del banco de pruebas adentro. [PROVEN: misma página, 2026-11-07]
- **Los datos vienen estructurados (OCDS)**, así que el explorador es simple y la primera rebanada puede poner el esfuerzo en el archivista y el refutador, que son el corazón del proyecto. [INFERRED]
- **La vigilancia anterior buscaba sobre todo licitaciones**, así que debería haber hallazgos del banco de pruebas en ARCE. [INFERRED: de la noche de muestra; se confirma con el registro completo (T11)]
- **Cuatro fuentes cumplen el mínimo de la versión 0**, y las cuatro tienen permiso de uso: dos con licencia abierta explícita (ARCE, BID), una con CC BY y API sin documentar (Banco Mundial) y una con términos que admiten el acceso por aplicaciones (Presidencia). [PROVEN: páginas de cada fuente, 2026-11-07]

## Alternativas rechazadas

- **El BID para la primera rebanada.** Tiene buen permiso y consulta por fecha, pero devuelve el estado de hoy de lo aprobado en una fecha, no lo que se veía esa noche, y tiene poco volumen para Uruguay.
- **La normativa de Presidencia para la primera rebanada.** Obliga a leer HTML y PDF escaneados, y su permiso es el más débil de los que entran.
- **IMPO ahora.** Técnicamente sirve, pero su permiso es contradictorio. Usarla sin aclararlo iría contra la regla de no usar fuentes sin permiso.
- **Pedir al BPS una autorización escrita antes de arrancar.** Puede hacerse en paralelo, pero la versión 0 no puede depender de una respuesta que no se sabe cuándo llega.

## Costos

- Sin el sitio del BPS, los hallazgos del banco de pruebas que solo estaban ahí se cuentan como "fuera de alcance" (D1), y la medición cubre menos.
- La API v3 del Banco Mundial no está documentada y puede cambiar sin aviso.
- ARCE tiene dos puntos por resolver antes de la primera rebanada: la zona horaria de sus fechas y las publicaciones atrasadas (T08).

## Qué la haría reversar

- Que IMPO aclare que su JSON se puede reutilizar: entra como quinta fuente.
- Que el BPS autorice por escrito leer sus resoluciones.
- Que la zona horaria o los atrasos de ARCE hagan imposible repetir noches, o que el banco de pruebas casi no tenga hallazgos en ARCE.
