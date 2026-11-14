# docs

La documentación técnica de El Parte: cómo está hecho el sistema, por qué, y qué se sabe de cada fuente. Lo que lee una sesión nueva para empezar está en [`arranque/`](../arranque/README.md); las decisiones, en [`adrs/`](../adrs/README.md).

## Qué hay

| Ruta | Qué es |
|---|---|
| [`tablero-de-arquitectura/`](tablero-de-arquitectura/README.md) | El sistema objeto por objeto, en una página: lo construido y lo diseñado, con sus conexiones y sus recorridos. Refleja la arquitectura en todo momento y es la referencia para pedir cambios ([ADR 0012](../adrs/0012-tablero-de-arquitectura.md)). |
| [`arquitectura.md`](arquitectura.md) | La arquitectura de la versión 0 explicada: los agentes y sus razones, una noche paso a paso, qué pasa cuando algo falla y el despliegue. |
| [`contratos.md`](contratos.md) | Los mensajes entre agentes, con su esquema y un ejemplo; la identidad de un hecho, la idempotencia, cómo termina y cómo se repite una corrida. |
| [`base-de-afirmaciones.md`](base-de-afirmaciones.md) | El modelo de datos: dos esquemas, el DDL que es la primera migración, y de dónde sale cada vista. |
| [`plan.md`](plan.md) | Los cuatro fines de semana: qué se hace, qué se muestra y cómo se comprueba cada uno. |
| [`fuentes/`](fuentes/README.md) | Una página por fuente del Estado: qué publica, cómo se accede, con qué permiso y qué riesgos tiene. |
| [`verificaciones/`](verificaciones/spring-ai.md) | Lo que se verificó de una tecnología antes de apoyarse en ella, con URL y fecha. |
| [`laminas/`](laminas/arquitectura.json) | Las láminas de arquitectura: la fuente en JSON y el SVG que genera `herramientas/generar_laminas.py`. El SVG no se edita a mano. |
| [`como-se-hizo/`](como-se-hizo/primera-rebanada.md) | Una página por componente: qué hace, por qué existe, cómo se construyó, qué se probó, qué falló y qué se cambió (regla 6). |

## Cuál manda

Para saber qué existe y cómo se conecta cada pieza, manda el [tablero de arquitectura](tablero-de-arquitectura/README.md), que el verificador compara con el código. Para saber por qué se hizo así, los ADRs y estos documentos. Si dos se contradicen, se corrige el que está mal en el mismo cambio.
