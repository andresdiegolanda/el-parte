# CAF: proyectos en Uruguay

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: queda afuera de la versión 0** ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md)).

## Qué publica y cómo se accede

- **El sitio no se puede usar.** www.caf.com responde con un desafío contra robots (Imperva Incapsula) en las páginas de Uruguay, de proyectos y de términos. [PROVEN: 2026-11-07] Además, su certificado TLS viene incompleto, sin el certificado intermedio. [PROVEN: 2026-11-07]
- **El único canal abierto es IATI** (*International Aid Transparency Initiative*, un estándar para publicar datos de cooperación). CAF publica un archivo XML IATI 2.03 con 286 actividades, generado el 2026-09-09. Uruguay tiene 7 actividades, y no hay adquisiciones. [PROVEN: https://iatiregistry.org/publisher/caf y el archivo publicado, 2026-11-07]

## Permiso de uso

El registro IATI declara licencia ODC-BY, que permite copiar, distribuir y usar la base. [PROVEN: https://iatiregistry.org/publisher/caf y https://opendatacommons.org/licenses/by/summary/, 2026-11-07] Los términos de caf.com no se pudieron leer por el desafío contra robots.

## Consulta por fecha

No. El archivo IATI es una sola foto, con la misma fecha de actualización para todas las actividades, y se publica de forma irregular. Lo aprobado ayer recién aparece en la próxima publicación, meses después. [PROVEN para la foto única; INFERRED para la frecuencia, 2026-11-07]

## Personas físicas

El archivo IATI no trae nombres de personas, solo un correo genérico de la organización. [PROVEN: 2026-11-07]

## Por qué queda afuera

El explorador nocturno no puede leer el sitio, y el archivo abierto se actualiza cada pocos meses sin consulta por fecha. Siete actividades no justifican un explorador. Se reconsidera si CAF publica datos con más frecuencia.
