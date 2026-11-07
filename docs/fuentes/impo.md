# Diario Oficial (IMPO)

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: queda afuera de la versión 0 hasta que IMPO aclare por escrito el permiso de reutilización** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md); tablero, T18).

## Qué publica

- **La edición diaria del Diario Oficial**, en PDF: índice, documentos y avisos. `https://www.impo.com.uy/diariooficial` lleva a la última edición. [PROVEN: 2026-11-07]
- **El banco de normativa**, una página por norma, en `/bases/{tipo}/{número}-{año}`. [PROVEN: 2026-11-07]

## Cómo se accede

- Cada norma tiene una salida en JSON, sin clave, agregando `?json=true`. La respuesta trae la fecha de publicación, los artículos y los firmantes. [PROVEN: `https://www.impo.com.uy/bases/decretos/263-2026?json=true`, HTTP 200, y https://www.impo.com.uy/datos-abiertos/, 2026-11-07]
- El JSON viene en ISO-8859-1, y su esquema publicado no incluye el campo de firmantes que sí trae. [PROVEN: https://www.impo.com.uy/resources/basesIMPO.json, 2026-11-07]
- La edición diaria es solo PDF, y no hay listado ni búsqueda en JSON. [PROVEN: 2026-11-07]

## Permiso de uso: contradictorio

- **A favor:** la página de datos abiertos ofrece el JSON para que lo use libremente cualquier persona, con la Licencia de Datos Abiertos de Uruguay. [PROVEN: https://www.impo.com.uy/datos-abiertos/, 2026-11-07]
- **En contra:** las condiciones generales de uso del banco de datos prohíben su copia, reproducción o cesión a terceros, y el pie del sitio dice "Todos los derechos reservados". [PROVEN: https://www.impo.com.uy/politica-de-privacidad-y-seguridad/, 2026-11-07]
- No queda claro cuál de las dos manda para el JSON. [INFERRED] Hasta que IMPO lo aclare por escrito, El Parte no la usa.
- El robots.txt pide 10 segundos entre pedidos y prohíbe la búsqueda y avisos puntuales, pero no las normas ni los índices diarios. [PROVEN: https://www.impo.com.uy/robots.txt, 2026-11-07]

## Consulta por fecha

Sí, por la URL de cada edición (`/diariooficial/AAAA/MM/DD/indice.pdf`). Para repetir una noche: índice del día, número de cada norma, JSON de la norma y control de su fecha de publicación. La cadena funcionó para un decreto; para otros tipos de norma no se probó. [PROVEN para el decreto, 2026-11-07] Un día sin edición devuelve HTTP 200 con un texto de error, no un 404. [PROVEN: 2026-11-07]

## Personas físicas

Sí, y muchas: los firmantes de cada norma, y avisos sobre particulares (sucesiones, sociedades conyugales, edictos matrimoniales). [PROVEN: 2026-11-07] Es la fuente con más datos personales de las verificadas.

## Riesgos

- El permiso, como se explica arriba.
- Leer el índice obliga a procesar un PDF a dos columnas.
- IMPO se reserva cambiar las condiciones de acceso. [PROVEN: 2026-11-07]
