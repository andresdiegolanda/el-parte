# Diario Oficial (IMPO)

Verificada el 2026-11-07 (tarjeta T10). **Veredicto: entra en la versión 0, solo el JSON de las normas, sin avisos y con las personas tapadas** ([ADR 0010](../../adrs/0010-impo-como-quinta-fuente.md)). Hasta el 2026-11-14 quedaba afuera, a la espera de una aclaración de IMPO ([ADR 0006](../../adrs/0006-fuentes-de-la-version-0.md); tablero, T18).

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
- No queda claro cuál de las dos manda para el JSON. [INFERRED] El Parte toma como permiso la licencia de datos abiertos, solo para el JSON de las normas ([ADR 0010](../../adrs/0010-impo-como-quinta-fuente.md)).
- El robots.txt pide 10 segundos entre pedidos y prohíbe la búsqueda y avisos puntuales, pero no las normas ni los índices diarios. [PROVEN: https://www.impo.com.uy/robots.txt, 2026-11-07]

## Consulta por fecha

Sí, por la URL de cada edición (`/diariooficial/AAAA/MM/DD/indice.pdf`). Para repetir una noche: índice del día, número de cada norma, JSON de la norma y control de su fecha de publicación. La cadena funcionó para un decreto; para otros tipos de norma no se probó. [PROVEN para el decreto, 2026-11-07] Un día sin edición devuelve HTTP 200 con un texto de error, no un 404. [PROVEN: 2026-11-07]

## Personas físicas

Sí, y muchas: los firmantes de cada norma, y avisos sobre particulares (sucesiones, sociedades conyugales, edictos matrimoniales). [PROVEN: 2026-11-07] Es la fuente con más datos personales de las verificadas.

## Lo que se aprendió al construir el explorador

Del 2026-11-17, con el robots.txt leído de nuevo ese día (sigue pidiendo 10 segundos entre pedidos y no prohíbe las normas ni los índices) y tres ediciones reales, a diez segundos entre pedidos: la del 16/09/2026, la del 24/09/2026 y la del 16/10/2026. [PROVEN: pedidos a https://www.impo.com.uy/diariooficial/2026/09/24/indice.pdf y siguientes, 2026-11-17]

- **El índice se lee con PDFBox sin ordenar por posición.** Así cada documento sale en una línea que empieza con su número de orden (`7 - Decreto 223/026.- Reglaméntase ...`) y su sumario sigue hasta `Pág. N`, aunque el PDF esté a dos columnas.
- **Del índice salen las leyes y los decretos.** La edición del 24/09 trae tres decretos; la del 16/10, la Ley 20.531; la del 16/09, ninguno: sus documentos son resoluciones de organismos, casi todas designaciones de personas con nombre y apellido en el mismo índice.
- **Las leyes están en `leyes-originales`** (`/bases/leyes-originales/20531-2026`), y los decretos, en `decretos`. El número de una ley no trae el año: es el de la edición.
- **La fecha de publicación no es la de promulgación.** El Decreto 223/026 se promulgó el 16/09 y salió en la edición del 24/09; su JSON trae las dos. El hecho va con la de publicación.
- **Un fin de semana, el índice viene vacío.** Pedido el 2026-11-17, el índice del sábado 29/08/2026 y el del domingo 30/08/2026 respondieron 200 sin nada, ni PDF ni texto de error. Para el explorador es lo mismo que un día sin edición. [PROVEN: medición `2026-11-17-2`, 2026-11-17]
- **`urlVerImagen` no lleva a la norma.** `/diariooficial/2026/09/24/8` es el visor de la página 8 de la edición, en HTML, también con `?json=true`.
- **Las leyes firman adentro del texto.** Además del campo de firmantes, el último artículo de una ley trae la firma de quien preside la cámara y de su secretario, en mayúsculas y con títulos encadenados (`Dr. Mag.`). El tapado los cubre; un nombre sin título, sin tratamiento y fuera de una firma no se ve.
- **Una ley grande es grande.** El JSON de la Rendición de Cuentas 2025 pesa 393 KB, con 312 artículos; 8 nombran al BPS. El refutador le muestra al modelo solo esos.

## Riesgos

- El permiso, como se explica arriba.
- Leer el índice obliga a procesar un PDF a dos columnas; si IMPO cambia el formato del índice, el explorador no encuentra normas y la noche sale sin nada de IMPO, sin error. [INFERRED]
- El tapado de personas es por reglas, sin el modelo.
- IMPO se reserva cambiar las condiciones de acceso. [PROVEN: 2026-11-07]
