# BID: proyectos para Uruguay

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: entra en la versión 0** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md)).

## Qué publica

En su portal de datos abiertos, el Banco Interamericano de Desarrollo publica:

- **Proyectos** en preparación y aprobados desde 1960, con número, nombre, fechas de aprobación y de firma, estado, tipo, sector, montos y objetivo. Uruguay tiene 856 operaciones; la más reciente se aprobó el 2026-11-04. [PROVEN: https://data.iadb.org/dataset/idb-projects-dataset y consultas a su API, 2026-11-07]
- **Avisos de adquisiciones y adjudicaciones.** El último de Uruguay es del 2026-11-05. [PROVEN: consulta a la API, 2026-11-07]
- **Contratos adjudicados.** Vienen atrasados: el último de Uruguay se firmó el 2025-12-31. [PROVEN: consulta a la API, 2026-11-07]

El código de Uruguay en estos datos es **UR**, no UY. [PROVEN: con UY no devuelve nada, 2026-11-07]

## Cómo se accede

El portal es un CKAN, una plataforma de datos abiertos con API documentada. [PROVEN: https://docs.ckan.org/en/2.11/api/, enlazada desde el robots.txt del portal, 2026-11-07] Ejemplo probado, con respuesta HTTP 200 y 856 resultados:

```sh
curl -G https://data.iadb.org/api/3/action/datastore_search \
  --data-urlencode resource_id=814b7b54-477a-4c25-b3bf-6be05412069d \
  --data-urlencode 'filters={"cntry_cd":"UR"}' \
  --data-urlencode 'sort=apprvl_dt desc' --data-urlencode limit=8
```

La frecuencia de actualización no está declarada, pero el archivo se modificó el 2026-11-06 e incluye la aprobación del 2026-11-04. [INFERRED: actualización casi diaria]

## Permiso de uso

- Licencia Creative Commons Atribución 4.0 (CC BY 4.0): se puede reutilizar citando la fuente. [PROVEN: `license_id: cc-by` en `package_show`, verificado de nuevo el 2026-11-07]
- El robots.txt del portal permite la API y las descargas (`Allow: /*api/*action/`, `Allow: /*dataset/*/resource/*/download/`) y prohíbe las páginas de búsqueda con parámetros. [PROVEN: https://data.iadb.org/robots.txt, 2026-11-07]
- El sitio principal, www.iadb.org, devolvió un bloqueo 403 de Cloudflare incluso para su robots.txt, así que sus términos generales no se pudieron leer. No se intentó saltear el bloqueo. [PROVEN: 2026-11-07]

## Consulta por fecha

Sí, por fecha de aprobación de proyectos y por fecha de publicación de avisos, con filtros o con SQL sobre la tabla. [PROVEN: consultas de prueba, 2026-11-07]

Límite: no hay fecha de última modificación por proyecto ni historia de estados. Repetir una noche devuelve lo que hoy se sabe de esa fecha, no lo que se veía esa noche. [PROVEN por la lista de campos, 2026-11-07]

## Personas físicas

- Los datos de proyectos no tienen campos de personas. [PROVEN: lista de campos, 2026-11-07]
- Las adjudicaciones nombran al adjudicatario, y en Uruguay hay más de 2.700 contratos de consultores individuales, que son personas. [INFERRED: del tipo de contrato] El explorador no lee los adjudicatarios de consultorías individuales (D3).

## Riesgos

- El portal es de 2025, y los identificadores de sus archivos pueden cambiar. [INFERRED]
- Las fechas se guardan como texto.
