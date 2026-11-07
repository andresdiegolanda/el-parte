# 0008. Datos personales

- Estado: propuesto
- Fecha: 2026-11-07

## Contexto

La regla 11 prohíbe datos personales en la base y en la demo. Pero hay dos lugares por donde entran igual:

- **Las fuentes.** Las cuatro fuentes de la versión 0 nombran personas: contactos y adjudicatarios en ARCE, consultores individuales en el BID, jefes de equipo y contactos en el Banco Mundial, designaciones y misiones oficiales en Presidencia. [PROVEN: [docs/fuentes/](../docs/fuentes/README.md), 2026-11-07]
- **El login.** El login por enlace de correo obliga a guardar el correo de cada usuario.

Además, los términos de las API del Banco Mundial prohíben juntar o guardar información personal. [PROVEN: https://www.worldbank.org/ext/en/legal/terms-conditions, 2026-11-07]

La decisión D3 resolvió esto de forma provisoria. Este ADR la formaliza y la baja a cada fuente.

## Decisión

### 1. Solo se sigue a entidades que no son personas

Un cliente se puede suscribir a organismos, personas jurídicas, programas, licitaciones y proyectos. Nunca a una persona física.

### 2. Los datos personales se tapan en el explorador, antes de todo lo demás

El explorador de cada fuente descarta o tapa los datos personales **antes** de llamar al modelo y antes de publicar el candidato. Ningún mensaje, ningún hecho y ninguna llamada al modelo llevan esos datos. En su lugar va `[persona]`, y el candidato cuenta cuántos se taparon (`datosPersonalesEnmascarados`, en [docs/contratos.md](../docs/contratos.md)). La URL de la fuente queda, así cualquiera puede ir a ver el original.

| Fuente | Qué se descarta o se tapa |
|---|---|
| ARCE | Se descarta el campo de contacto del comprador (nombre, correo, teléfono). Se tapa el nombre del adjudicatario cuando es una persona física. |
| BID | No se leen los adjudicatarios de contratos de consultores individuales. |
| Banco Mundial | Se piden solo los campos necesarios, sin el jefe de equipo. Se descartan los datos de contacto de los avisos. |
| Presidencia | Se tapan los nombres de personas en títulos y textos de resoluciones. |

En las fuentes estructuradas (ARCE, BID, Banco Mundial) el descarte es por campo, con código común. En el texto libre de Presidencia los nombres hay que detectarlos, y eso puede fallar. [INFERRED] Por eso el editor hace una segunda pasada antes de escribir un hecho, y todo nombre que se haya escapado se registra como falla en la sala de máquinas.

### 3. Los correos viven aparte

- Los correos de los usuarios están en una tabla de un esquema propio de la base (`cuentas`), separado de la base de afirmaciones. Solo los lee el módulo de login.
- En todo lo demás (mensajes, informes, sala de máquinas) un cliente es un identificador opaco, `clienteId`.
- La lista de invitados se carga desde la configuración del despliegue, nunca desde el repositorio.
- La cuenta de demostración no tiene correo.

### 4. Tampoco en los registros de la aplicación

Los registros (*logs*) de la aplicación no escriben correos ni textos de fuentes sin tapar. Las pruebas usan solo datos sintéticos.

## Razones

- Tapar antes del modelo y del mensaje hace que el dato no entre a ningún lado. Taparlo después obligaría a limpiar mensajes, base y registros, y algo quedaría. [INFERRED]
- Conservar la URL mantiene la verificabilidad: El Parte no esconde el original, solo no lo copia.
- Separar los correos en su propio esquema hace que la base de afirmaciones y la demo queden libres de datos personales, como pide la regla 11.
- Cumple los términos del Banco Mundial sin un tratamiento especial.

## Alternativas rechazadas

- **Guardar los nombres tal como los publica la fuente.** Va contra la regla 11 y contra los términos del Banco Mundial.
- **Descartar todo hecho que mencione a una persona.** Se perderían hechos útiles, como una adjudicación a un unipersonal o una designación en un organismo.
- **Guardar el correo cifrado o como huella.** El login tiene que mandar el enlace a esa dirección, así que el correo hace falta en claro. Separarlo protege igual.

## Costos

- Se pierde información: quién firmó una resolución o a quién se adjudicó una consultoría individual.
- Detectar nombres en texto libre puede fallar, y cada falla es un dato personal que entró. La segunda pasada y el registro de fallas lo acotan, pero no lo eliminan.
- Hay que mantener la tabla de descartes de cada fuente cuando cambia su formato.

## Qué la haría reversar

- Que tapar nombres haga que dos hechos distintos queden iguales.
- Que las fallas de detección en Presidencia sean frecuentes: esa fuente sale de la versión 0 hasta resolverlo.
