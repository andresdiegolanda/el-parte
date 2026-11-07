# BPS: resoluciones de directorio

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: queda afuera de la versión 0. Sus términos prohíben la consulta automática** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md)).

El Banco de Previsión Social (BPS) es el organismo del banco de pruebas.

## Qué publica

- Las resoluciones de directorio, una por PDF, con título descriptivo: contrataciones, llamados, presupuesto, comisiones, personal. Hay carpetas por año desde 2005 y por mes desde marzo de 2005. [PROVEN: https://www.bps.gub.uy/3856/resoluciones-de-directorio.html, 2026-11-07]
- Los PDF tienen texto extraíble y la fecha de la resolución en el texto. [PROVEN: un PDF de muestra, 2026-11-07]

## Cómo se accede

- Solo páginas HTML y PDF. No hay API, RSS ni datos abiertos: el BPS no figura entre las organizaciones del catálogo nacional. [PROVEN: catálogo nacional, 2026-11-07]
- El listado se filtra por año y mes, pero no por día, y se ordena por título, no por fecha. [PROVEN: 2026-11-07]
- En la muestra, la resolución se publicó unas dos semanas después de dictada, y las publicaciones llegan en tandas. [INFERRED: de un caso y de las fechas del listado]

## Permiso de uso: prohibido

Los "Términos de Privacidad y Uso de Internet" del BPS, actualizados el 12/08/2026, prohíben usar cualquier mecanismo automático de consulta, recolección o extracción de datos, y piden que el acceso sea solo por las interfaces que provee el BPS. [PROVEN: https://www.bps.gub.uy/uso_internet, leído el 2026-11-07]

El sitio no tiene robots.txt (devuelve 404), pero eso no cambia nada: los términos lo prohíben de forma expresa. [PROVEN: 2026-11-07]

## Consulta por fecha

Por año y mes (`?idFiltro1=<año>&idFiltro2=<mes>`). [PROVEN: 2026-11-07] No importa para El Parte mientras rija la prohibición.

## Personas físicas

Sí: los PDF llevan al pie los nombres de quienes firman, y hay resoluciones de personal. [PROVEN para los firmantes, 2026-11-07]

## Qué falló en la verificación

La verificación hizo unas 13 consultas automáticas al sitio del BPS antes de leer sus términos. Cuando se leyeron, no se hizo ninguna más. Lección: al verificar una fuente, primero se leen sus términos y su robots.txt, y recién después se consulta. Quedó como práctica en [arranque/como-se-trabaja.md](../../arranque/como-se-trabaja.md).

## Qué queda para el banco de pruebas

Sin esta fuente, El Parte sigue al BPS por lo que otras fuentes dicen de él: sus compras en ARCE, las designaciones en la normativa de Presidencia y las operaciones de los bancos multilaterales en las que participe. [INFERRED: de lo que publica cada una de esas fuentes] Los hallazgos del banco de pruebas que solo estaban en el sitio del BPS se cuentan como "fuera de alcance" (D1). La alternativa es pedirle al BPS una autorización escrita.
