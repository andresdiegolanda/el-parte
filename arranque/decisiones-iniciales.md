# Decisiones iniciales

Al arrancar el proyecto quedaron abiertas diez preguntas que la definición no resolvía (D1 a D10). Después se sumaron la forma de esta carpeta (D11), el nombre del proyecto (D12) y la licencia (D13). Estas son las decisiones que se tomaron. Son provisorias: están vigentes hasta que un ADR las confirme o las cambie, y el [tablero](../tablero.md) dice cuáles tienen que pasar a ADR.

Cada decisión dice qué se decidió, por qué, qué cuesta y qué la haría cambiar.

## D1. Qué trae el banco de pruebas y cómo se repite una noche

- **Decisión.** La medición repite cada noche del registro pidiéndole a cada fuente lo que publicó en esa fecha. Si una fuente no deja consultar por fecha, la noche se marca "no repetible para esa fuente". Los hallazgos del registro que vienen de lugares que El Parte no lee (prensa, buscadores web) se cuentan aparte, como "fuera de alcance".
- **Por qué.** La bitácora de muestra (corrida del 2026-11-06) trae las búsquedas y un resumen de sus resultados, pero no el contenido crudo de lo leído. [INFERRED: una sola noche de muestra; el análisis está en [proyecto.md](proyecto.md)]
- **Costo.** No todas las noches se van a poder repetir para todas las fuentes, y la comparación nunca va a ser exacta.
- **Qué la haría cambiar.** Que el registro completo traiga el contenido crudo de cada noche.
- **Estado.** Provisoria. Falta el registro completo (tablero, T11).

## D2. El registro del banco de pruebas no entra al repositorio

- **Decisión.** El registro no se versiona. Se lee desde una ruta configurable, que por defecto es `banco-de-pruebas/privado/` y está excluida en `.gitignore`. En el repositorio van su formato, las métricas de cada medición y una muestra sintética, para que la prueba automática corra igual.
- **Por qué.** El registro es material de otro proyecto (regla 11).
- **Costo.** Una sesión que no tenga el registro no puede repetir la medición real, solo la muestra sintética. Es una excepción acotada a la regla 10, y queda escrita como tal.
- **Qué la haría cambiar.** Una versión depurada del registro que se pueda publicar.
- **Estado.** Provisoria. Pasa a ADR (tablero, T06).

## D3. Datos personales

- **Decisión.**
  - Solo se puede suscribir a organismos, personas jurídicas, programas y licitaciones. No a personas físicas.
  - Los nombres de personas físicas que aparecen en los textos de las fuentes se enmascaran al guardar. La URL de la fuente queda, así cualquiera puede ir a ver el original.
  - Los correos de los usuarios viven en una tabla aparte, fuera de la base de afirmaciones. Nunca entran a la demo ni al repositorio.
- **Por qué.** La regla 11 prohíbe datos personales en la base y en la demo, pero el login por enlace obliga a guardar correos, y es esperable que las fuentes oficiales nombren personas en designaciones, adjudicaciones a personas físicas o empresas unipersonales. [ASSUMED: se verifica fuente por fuente al escribir cada explorador (tablero, T10)]
- **Costo.** Hay hechos que pierden información, y enmascarar exige detectar nombres, cosa que puede fallar.
- **Qué la haría cambiar.** Que el enmascarado haga que dos hechos distintos queden iguales, o que una fuente importante sea casi toda sobre personas.
- **Estado.** Provisoria. Pasa a ADR (tablero, T06).

## D4. Modelo de lenguaje y tope de gasto

- **Decisión.**
  - Proveedor Anthropic, usado desde Spring AI. Un modelo chico para los exploradores y el archivista y uno más grande para el refutador y el analista. Los modelos concretos se eligen en el ADR del stack, verificados en la documentación oficial.
  - Tope de 0,50 USD por corrida: una pasada completa del banco de pruebas, de unas setenta noches, queda en 35 USD como máximo.
  - Las pruebas automáticas usan respuestas grabadas del modelo y no gastan.
  - La clave del proveedor la pone el responsable del proyecto, fuera del repositorio.
- **Por qué.** El presupuesto que corta necesita números desde el principio. Las respuestas grabadas hacen que las pruebas sean reproducibles y gratis.
- **Costo.** Atarse a un proveedor. Spring AI abstrae el proveedor, pero eso se verifica en el ADR. [ASSUMED]
- **Qué la haría cambiar.** Que el costo medido por corrida quede muy lejos del tope, por arriba o por abajo.
- **Estado.** Provisoria. Pasa al ADR de presupuesto por corrida (tablero, T05).

## D5. Las fuentes de la versión 0

- **Decisión.** Entran la fuente oficial del organismo del banco de pruebas, compras estatales, IMPO, el BID y el Banco Mundial. La CAF y Presidencia o gub.uy entran solo si sus términos de uso y sus formatos lo permiten. La primera rebanada usa una fuente del banco de pruebas que deje consultar por fecha, para poder medirla desde el primer día.
- **Por qué.** Son las fuentes con más chance de tener datos abiertos, API o descarga oficial [ASSUMED: se verifica cada una (tablero, T10)], y la primera tiene que poder compararse con el banco de pruebas.
- **Costo.** Algunas fuentes interesantes pueden quedar afuera por sus términos.
- **Qué la haría cambiar.** Lo que diga la verificación de permisos y formatos de cada fuente.
- **Estado.** Provisoria.

## D6. Revisión humana

- **Decisión.** Revisa el responsable del proyecto, desde una cola en la sala de máquinas. Mientras nadie revisa, el hecho se muestra con su grado y la marca "pendiente de revisión". Nada desaparece de la página sin que la página lo diga. El umbral de "demasiado INFERRED" se define en `docs/contratos.md`.
- **Por qué.** La página sale todas las mañanas, haya revisión o no, y el sistema no puede esconder lo que no sabe.
- **Costo.** El cliente puede ver hechos no revisados, aunque marcados.
- **Qué la haría cambiar.** Que los hechos pendientes de revisión resulten falsos seguido.
- **Estado.** Provisoria.

## D7. Idioma del código

- **Decisión.** Todo en español: clases, tablas, tópicos, mensajes de commit. Los nombres van sin tildes ni eñes (`Afirmacion`, `explorador.candidatos`), salvo lo que imponga cada framework.
- **Por qué.** La regla 7 pide español en todos los artefactos, y sin tildes en los nombres se evitan problemas de codificación entre herramientas.
- **Costo.** Mezcla inevitable con el inglés de los frameworks.
- **Qué la haría cambiar.** Que la mezcla haga el código difícil de leer.
- **Estado.** Vigente.

## D8. Dónde vive el repositorio

- **Decisión.** En <https://github.com/andresdiegolanda/el-parte>, rama `main`. No se hacen commits ni push sin un pedido explícito.
- **Por qué.** Un lugar único y público para el handoff (regla 10). [PROVEN: la API de GitHub devuelve `private: false` y rama por defecto `main`, 2026-11-07]
- **Costo.** Ninguno relevante.
- **Qué la haría cambiar.** Nada previsto.
- **Estado.** Vigente. La licencia está en D13.

## D9. Dónde corre el servicio

- **Decisión.** La versión 0 se levanta en una máquina local con un solo comando, y los correos del login caen en un buzón de prueba local. Dónde se publica el servicio, con qué dominio y con qué servicio de correo, se decide con un ADR en el cuarto fin de semana.
- **Por qué.** Publicar no aporta nada a las primeras rebanadas, y elegir hosting antes de medir costos es adivinar.
- **Costo.** Los usuarios invitados recién pueden entrar al final.
- **Qué la haría cambiar.** Necesitar mostrar el servicio en línea antes.
- **Estado.** Provisoria.

## D10. Calendario

- **Decisión.** Cuatro fines de semana: 7 y 8, 14 y 15, 21 y 22, y 28 y 29 de noviembre de 2026. El responsable del proyecto dedica unas seis horas por fin de semana a revisar y probar. El plan detallado, con qué se muestra al final de cada uno, va en `docs/plan.md`.
- **Por qué.** El plan tiene que entrar en horas reales.
- **Costo.** Poco margen para imprevistos.
- **Qué la haría cambiar.** Una semana con menos horas.
- **Estado.** Provisoria (tablero, T07).

## D11. La guía de arranque es una carpeta

- **Decisión.** La guía para sesiones nuevas es la carpeta `arranque/` en la raíz del repositorio, en lugar del archivo `docs/arranque.md` que figuraba en la definición. Una sesión nueva solo tiene que leer esta carpeta.
- **Por qué.** Un punto de entrada único y visible, separado de la documentación técnica que va a crecer en `docs/`, y que puede tener varios archivos cortos en lugar de uno largo.
- **Costo.** Una carpeta más en la raíz.
- **Qué la haría cambiar.** Nada previsto.
- **Estado.** Vigente.

## D12. El nombre es El Parte

- **Decisión.** El proyecto se llama El Parte. Deja de ser un nombre provisorio.
- **Por qué.** Un parte es el informe breve de novedades, y eso es lo que entrega el servicio cada mañana.
- **Costo.** Ninguno relevante.
- **Qué la haría cambiar.** Nada previsto.
- **Estado.** Vigente.

## D13. Licencia MIT

- **Decisión.** El repositorio se publica con la licencia MIT, en [`LICENSE`](../LICENSE), a nombre de Andrés Landa.
- **Por qué.** El proyecto es didáctico y de portafolio: cualquiera puede usar, copiar, modificar y redistribuir el código y su documentación, con la condición de conservar el aviso de copyright y el de la licencia. [PROVEN: texto de la licencia MIT de choosealicense.com, https://github.com/github/choosealicense.com/blob/gh-pages/_licenses/mit.txt, 2026-11-07]
- **Costo.** No se puede exigir que quien reutilice el código publique sus cambios.
- **Qué la haría cambiar.** Nada previsto. Lo ya publicado con MIT sigue bajo MIT aunque después se cambie la licencia. [ASSUMED]
- **Estado.** Vigente.
