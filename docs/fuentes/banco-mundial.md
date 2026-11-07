# Banco Mundial: proyectos para Uruguay

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: entra en la versión 0, como fuente secundaria** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md)).

## Qué publica

- **Proyectos y operaciones** desde 1947: estado, fechas de aprobación, cierre y divulgación, montos, prestatario, agencia ejecutora, sectores y transacciones. Uruguay tiene 134 proyectos y 5 activos; el último se aprobó el 2026-09-10 y el anterior el 2024-09-30. [PROVEN: https://projects.worldbank.org/en/projects-operations/projects-list?countrycode_exact=UY y consultas a la API, 2026-11-07]
- **Avisos de adquisiciones** y adjudicaciones: Uruguay tiene 1.222, y el último es del 2026-10-28. [PROVEN: consulta a la API, 2026-11-07]

Uruguay tiene muy poco movimiento: una aprobación en todo 2026. Casi todas las noches esta fuente no va a traer nada. [INFERRED: de las fechas de aprobación]

## Cómo se accede

- La documentación oficial apunta a la versión 2 de la API, que está congelada: lo más nuevo que tiene es de enero de 2025. [PROVEN: https://datahelpdesk.worldbank.org/knowledgebase/articles/889386-developer-information-overview y consultas, 2026-11-07]
- La versión vigente es la 3, que usa el propio sitio de proyectos, pero no tiene documentación publicada. [PROVEN que la usa el sitio; no se encontró documentación, 2026-11-07]

Ejemplo probado con la versión 3 (verificado de nuevo el 2026-11-07, devuelve un proyecto):

```sh
curl "https://search.worldbank.org/api/v3/projects?format=json&countrycode_exact=UY&fl=id,boardapprovaldate,status&rows=10&strdate=2026-09-10&enddate=2026-09-10"
```

Los avisos siguen en la versión 2: `https://search.worldbank.org/api/v2/procnotices?format=json&project_ctry_code=UY`. [PROVEN: 2026-11-07]

## Permiso de uso

- Los datos de proyectos y de avisos tienen licencia CC BY 4.0. [PROVEN: https://datacatalog.worldbank.org/search/dataset/0037800 y ficha 0037795, 2026-11-07]
- Los términos de las API piden no exceder un volumen razonable de pedidos y prohíben juntar o guardar información personal. [PROVEN: https://www.worldbank.org/ext/en/legal/terms-conditions, 2026-11-07]
- El host de la API no tiene robots.txt; el del sitio de proyectos no restringe las rutas de proyectos. [PROVEN: 2026-11-07]

## Consulta por fecha

Sí, por fecha de aprobación del directorio (`strdate` y `enddate`). Los avisos no se filtran por fecha, pero se pueden ordenar por fecha y filtrar del lado del explorador. Igual que en el BID, se obtiene el estado actual, no el de esa noche. [PROVEN: consultas de prueba, 2026-11-07]

## Personas físicas

- Cada proyecto trae el nombre del jefe de equipo del banco. Se excluye pidiendo solo los campos necesarios con `fl=`. [PROVEN: 2026-11-07]
- Algunos avisos traen datos de contacto. [INFERRED: patrones en el texto] El explorador no los guarda (D3, y los términos de las API).

## Riesgos

- La versión 3 no está documentada y puede cambiar sin aviso. [INFERRED]
- Hay Cloudflare delante, y la API del catálogo respondió 429 (límite de pedidos) en la primera llamada. [PROVEN: 2026-11-07]
