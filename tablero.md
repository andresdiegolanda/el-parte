# Tablero

Todo pendiente del proyecto vive acá, con dueño (regla 8). Cuando se termina una tarea, pasa a "Hecho" con su fecha; no se borra.

**Dueños posibles**

- `Andrés`: responsable del proyecto. Decisiones, accesos, claves, revisión humana y entrega de material que no está en el repositorio.
- `desarrollo`: la sesión que esté trabajando en el repositorio, de una persona o de un asistente.

**Estados:** pendiente, en curso, bloqueado, hecho.

## En curso

Nada en curso. La próxima tarea es T02.

## Pendiente

| ID | Tarea | Dueño | Estado | Depende de | Enlace |
|---|---|---|---|---|---|
| T02 | Escribir `docs/arquitectura.md`: visión general en Mermaid, los agentes y sus razones, la secuencia de una noche, y la lámina de arquitectura en SVG con su generador y su verificador. | desarrollo | pendiente | T09 | [proyecto.md](arranque/proyecto.md) |
| T03 | Escribir `docs/contratos.md`: cada mensaje entre agentes con esquema y ejemplo, la regla de identidad e idempotencia de cada hecho y el umbral de "demasiado INFERRED". | desarrollo | pendiente | T02 | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T04 | Escribir `docs/base-de-afirmaciones.md`: modelo de datos con diagrama, incluida la tabla aparte de usuarios (D3). | desarrollo | pendiente | T03 | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T05 | Primeros ADRs: orquestación por mensajes, elección del stack (incluye modelos, sistema de mensajería, interfaz web y buzón de correo de prueba), grados de evidencia, presupuesto por corrida (D4). | desarrollo | pendiente | T09 | [como-se-trabaja.md](arranque/como-se-trabaja.md) |
| T06 | ADRs de las decisiones de arranque D2 (registro fuera del repositorio) y D3 (datos personales). | desarrollo | pendiente | | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T07 | Escribir `docs/plan.md`: los cuatro fines de semana (D10) y qué se muestra al final de cada uno. | desarrollo | pendiente | T02 | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T08 | Primera rebanada de punta a punta: una fuente, un explorador, el archivista, el refutador, el editor, una página de la mañana y la sala de máquinas de esa corrida, con su página "cómo se hizo". | desarrollo | pendiente | T02 a T07, T10, T14 | [proyecto.md](arranque/proyecto.md) |
| T09 | Verificar en la documentación oficial el estado actual de Spring AI y de sus patrones de orquestación de agentes, con URL y fecha. | desarrollo | pendiente | | [proyecto.md](arranque/proyecto.md) |
| T10 | Verificar cada fuente candidata (permiso de uso, formato, si deja consultar por fecha, si nombra personas) y escribir su página en `docs/fuentes/`. Elegir con eso la fuente de la primera rebanada (D5). | desarrollo | pendiente | | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T11 | Entregar el registro completo del banco de pruebas (seguimiento acumulado y bitácoras por corrida) en `banco-de-pruebas/privado/`, y confirmar cuántas noches cubre: la muestra sugiere unas cincuenta desde el 2026-09-17 y la definición habla de unas setenta. | Andrés | pendiente | | [proyecto.md](arranque/proyecto.md) |
| T12 | Escribir el formato del registro del banco de pruebas, una muestra sintética y la forma de las métricas en `banco-de-pruebas/`. | desarrollo | pendiente | T11 | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T13 | Elegir la licencia del repositorio. | Andrés | pendiente | | [README.md](README.md) |
| T14 | Dejar la clave del proveedor de modelos disponible fuera del repositorio para las corridas reales (D4). | Andrés | pendiente | T05 | [decisiones-iniciales.md](arranque/decisiones-iniciales.md) |
| T15 | Agregar al verificador una comprobación de que los diagramas Mermaid se pueden dibujar. | desarrollo | pendiente | | [herramientas/verificar_docs.py](herramientas/verificar_docs.py) |

## Bloqueado

Nada por ahora.

## Hecho

| ID | Tarea | Dueño | Estado | Fecha | Enlace |
|---|---|---|---|---|---|
| T01 | Base documental: `README.md` con las reglas, `CHANGELOG.md`, este tablero, la carpeta `arranque/`, `obsoletos/` y el verificador. | desarrollo | hecho | 2026-11-07 | [arranque/README.md](arranque/README.md) |
