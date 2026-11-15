# Cómo se hizo: la medición contra el banco

La parte de El Parte que produce lo que se mide contra el banco de pruebas: las corridas en modo banco, una por cada noche del registro, en un Docker Compose aparte, y la salida que después cuenta [`herramientas/banco_de_pruebas.py`](../../herramientas/banco_de_pruebas.py) (tablero, T20). El diseño está en [adrs/0014-medicion-contra-el-banco.md](../../adrs/0014-medicion-contra-el-banco.md), y el código, en `app/src/main/java/io/github/andresdiegolanda/elparte/banco/`.

## Qué hace

```mermaid
sequenceDiagram
    participant A as Arranque del banco
    participant M as Medición
    participant I as Inicio de corrida
    participant N as La noche
    participant B as Base de la medición
    A->>M: medir del desde al hasta
    loop cada noche, en orden
        M->>B: ¿ya terminó esta noche?
        alt ya terminó
            M->>M: la saltea
        else no
            M->>B: lo gastado en el modelo
            M->>I: iniciar en modo banco
            I->>N: corrida.iniciada
            N->>B: candidatos, grados, hechos, mensajes
            M->>B: espera a que termine
        end
    end
    M->>B: corridas y mensajes del banco
    M->>M: escribe la salida en la carpeta de la medición
```

- **Arranca solo.** Con el perfil `banco`, que activa [`compose.banco.yaml`](../../compose.banco.yaml), la aplicación lee la medición y sus noches de las variables de entorno y mide en un hilo aparte. Mientras tanto, su sala de máquinas se puede mirar en el puerto 8081. Si falta una variable, lo dice en el registro y no mide.
- **Una noche por vez, en orden.** Inicia una corrida en modo banco, que sigue solo la entidad medida, y espera a que termine antes de la siguiente. Saltea las noches que ya terminaron en esa base, así una medición cortada se retoma levantándola de nuevo.
- **Tope de gasto.** Antes de cada noche suma lo gastado en el modelo por todas las corridas del banco; si llegó al tope, no inicia más y lo anota.
- **Cuida a ARCE.** El lector espera 250 ms antes de cada pedido en el perfil `banco`, y el explorador no vuelve a pedir lo que la corrida de la noche anterior ya leyó de una publicación: lo toma de su grabación y lo graba igual en la corrida actual, para que se pueda repetir.
- **La salida.** Al terminar escribe `salida-de-el-parte.json` en `banco-de-pruebas/mediciones/<medición>/`, con la forma de [banco-de-pruebas/formato.md](../../banco-de-pruebas/formato.md).

Cómo se corre está en [app/README.md](../../app/README.md), "Medir contra el banco".

## Por qué existe

La medición contra el banco es parte de la versión 0 y es lo que dice si El Parte sirve ([docs/plan.md](../plan.md)). Las reglas de cómo se cuenta ya estaban escritas y probadas (T12); faltaba producir lo que se cuenta sin mezclarlo con lo que ven los invitados, con la memoria que El Parte tendría noche a noche, y sin castigar a ARCE ni al presupuesto.

## Cómo se construyó

- **Sin código para separar.** La separación la da el nombre de proyecto de Compose: otra base, otro Kafka, otros volúmenes. `compose.banco.yaml` cierra los puertos de la base, de Kafka y del correo con `!reset`, y publica la aplicación en el 8081.
- **El modo banco es casi el normal.** Los agentes solo miran si una corrida es de reproducción, así que una del banco hace todo lo que hace una normal, sobre su propia base. Lo nuevo en el inicio es la fecha del registro y la entidad medida en lugar de las suscripciones. La repetición acepta como original una corrida del banco.
- **Reusar sin perder la repetición.** Lo que la noche anterior ya leyó se busca por la clave de la grabación (`arce:comprador:<id>` y `arce:publicacion:<id>`) en la corrida del banco de la fecha anterior, que sale con una sola consulta sobre la clave primaria. Una falla grabada no se reusa: se vuelve a intentar. Lo reusado pasa por la grabación de la corrida actual, así repetirla no necesita la anterior.
- **La salida desde la base.** Las corridas salen de `parte.corridas`; el costo y la duración, de `corrida.terminada`; las fuentes no repetibles y las lecturas fallidas, de `fuente.fallida`; y de cada clave de hecho, la primera noche en que salió PROVEN o INFERRED, o si nunca, la primera en que salió NOT_MENTIONED.

## Qué se probó

- **`MedicionContraElBancoTest`**, contra un PostgreSQL y un Kafka en contenedores, con tres noches de octubre del ARCE de mentira. [PROVEN: `mvn test`, 19 pruebas, 2026-11-15]
  - Las tres noches corren en orden, con tres llamadas al modelo y 0,012 USD.
  - ARCE recibe 14 pedidos; sin reusar lo de la noche anterior serían 17.
  - La salida tiene la forma del formato, y `herramientas/banco_de_pruebas.py` la valida y la cuenta.
  - Medir otra vez saltea las tres noches y no pide nada.
  - Una fuente que no deja pedir la fecha sale como no repetible y no como lectura fallida.
  - Con lo gastado en el tope, no se inicia ninguna noche más.
  - Una noche del banco se repite sin red ni costo, con los mismos mensajes.
- **`LectorArceTest`** controla que el lector espere la pausa antes de cada pedido.
- **En Docker Compose**, en un espacio en la nube, con `compose.banco.yaml`. [PROVEN: dos mediciones de prueba, 2026-11-15]
  - Sin las variables, la aplicación lo dice y no mide.
  - Con ellas, corre dos noches en orden y escribe la salida en la carpeta montada.
  - Al levantarla otra vez, saltea lo hecho.
  - `docker compose down -v` borra todo sin pedir las variables.
  - Ahí ARCE no se alcanza desde los contenedores, así que las noches terminaron con la fuente fallida: esa prueba cubre el armado, no lo que encuentra El Parte.

## Qué falló

- **Las dos pruebas grandes comparten la base.** La de la primera rebanada contaba todos los hechos e informes de la base, y la medición agrega los suyos. Se cambió para que cuente solo lo de su noche de noviembre, y la medición usa noches de octubre con compras distintas. Las dos pasan en cualquier orden. [PROVEN: corridas en orden alfabético e inverso, 2026-11-15]
- **Un bloque de texto de Java se comió un espacio.** Los bloques de texto borran los espacios al final de cada línea, y una consulta armada con uno quedó `andh.clave...`. Se resolvió con `\s`.
- **El control de datos personales vio una URL de prueba.** La prueba agregaba a mano un `fuente.fallida` con una URL de `ejemplo.invalid`, que es justo lo que busca el control de la primera rebanada. Se usó una URL de ARCE.
- **La aplicación no podía escribir la salida en Linux.** La imagen corre con un usuario sin privilegios, y la carpeta montada era de otro usuario. La aplicación de la medición corre como root; la de todos los días sigue sin privilegios.
- **Compose pedía las variables hasta para bajar.** Con `${VARIABLE:?}`, `docker compose down` fallaba si no estaban. Las variables pasaron a ser opcionales para Compose, y la aplicación las controla.

## Qué falta

- Correr tres noches reales en la máquina del responsable, con la clave, para conocer el costo, la duración y la cantidad de pedidos por noche, y con eso decidir si se corren las 78 (T20).
- La revisión del registro, que hacen personas leyéndolo, para que el conteo pueda decir algo (T20).
