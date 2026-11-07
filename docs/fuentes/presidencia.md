# Presidencia y novedades de gub.uy

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: entra en la versión 0 solo la normativa de Presidencia, leída a ritmo bajo; las noticias quedan afuera** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md)).

## Qué publica

- **Normativa de Presidencia:** leyes, decretos y resoluciones. Cada resolución es una página con título, fecha y resumen, y un enlace al PDF. [PROVEN: https://www.gub.uy/presidencia/institucional/normativa?field_tipo_de_norma_target_id=276, 2026-11-07]
- **Noticias:** https://www.gub.uy/presidencia/comunicacion/noticias. [PROVEN: 2026-11-07]
- Cada organismo tiene su propio sitio dentro de gub.uy con la misma estructura. [INFERRED: patrón de las URLs] No hay un listado de noticias de todo el portal: `https://www.gub.uy/noticias` da 404. [PROVEN: 2026-11-07]

## Cómo se accede

- Solo páginas HTML y PDF. No hay RSS ni API pública: las rutas probadas devuelven 404 o redirigen a HTML. [PROVEN: 2026-11-07]
- Hay un mapa del sitio en XML con la fecha de modificación de cada URL, pero el de Presidencia tenía nueve días de atraso. [PROVEN: https://www.gub.uy/presidencia/sitemap.xml, 2026-11-07]
- En el catálogo de datos abiertos, Presidencia tiene un solo conjunto, que no es de normativa ni de noticias. [PROVEN: catálogo nacional, 2026-11-07]
- El PDF de muestra es un escaneo con texto reconocido por OCR, y su fecha no se encontró en el texto. [PROVEN: 2026-11-07]

## Permiso de uso

- Los términos de uso cuentan como usuario también a quien entra "a través de una aplicación informática", consideran pública la información del portal y prohíben sobrecargar el sitio. [PROVEN: https://www.gub.uy/presidencia/terminos-condiciones-uso, 2026-11-07]
- No dan una licencia explícita de reutilización. Que se pueda leer con un programa a ritmo moderado, citando la fuente, se deduce de esas cláusulas. [INFERRED]
- El robots.txt de gub.uy prohíbe las búsquedas y los listados por filtro, pero no la normativa, las noticias ni el mapa del sitio. [PROVEN: https://www.gub.uy/robots.txt, 2026-11-07]

Es la fuente con el permiso más débil de las que entran: no prohíbe, pero tampoco da una licencia. Por eso se lee a ritmo bajo y se cita siempre.

## Consulta por fecha

- **Normativa: sí**, por año y mes, con fecha en cada ítem. El selector llega por lo menos hasta 2006. [PROVEN: listado de resoluciones de octubre de 2026, 2026-11-07]
- **Noticias: no.** El filtro por fecha no cambia el listado; solo se puede retroceder por páginas. [PROVEN: 2026-11-07]

## Personas físicas

Sí. Los títulos de resoluciones nombran personas (misiones oficiales, designaciones) y las noticias nombran autoridades. [PROVEN: 2026-11-07] Es la fuente donde más pesa el enmascarado de nombres (D3).

## Riesgos

- La extracción depende del HTML del sitio, que puede cambiar.
- El reconocimiento de texto de los PDF puede fallar, como en la muestra.
