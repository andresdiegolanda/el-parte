package io.github.andresdiegolanda.elparte.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Informe;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * Arma el informe de la mañana de cada cliente al cerrar la corrida: los hechos de esta noche sobre
 * las entidades que sigue, o "sin novedades" si no hubo ninguno (docs/contratos.md).
 *
 * <p>El informe sale de los mensajes de la corrida, así una reproducción emite el mismo. Solo una
 * corrida normal lo guarda en la base, apuntando a las versiones que escribió el editor.
 */
@Component
public class ArmadoDeInformes {

    private final JdbcClient jdbc;
    private final RegistroDeMensajes registro;
    private final Corridas corridas;
    private final Emisor emisor;

    public ArmadoDeInformes(JdbcClient jdbc, RegistroDeMensajes registro, Corridas corridas, Emisor emisor) {
        this.jdbc = jdbc;
        this.registro = registro;
        this.corridas = corridas;
        this.emisor = emisor;
    }

    @Transactional
    public void armar(String corridaId, boolean cortada) {
        CorridaIniciada corrida = corridas.contexto(corridaId);
        Map<String, Set<String>> entidadesPorCandidato = new HashMap<>();
        for (Sobre sobre : registro.deCorridaYTipo(corridaId, Tipos.CANDIDATOS)) {
            Candidato candidato = registro.contenido(sobre, Candidato.class);
            Set<String> entidades = new HashSet<>();
            for (EntidadEnCandidato entidad : candidato.entidades()) {
                if (entidad.entidadId() != null) {
                    entidades.add(entidad.entidadId());
                }
            }
            entidadesPorCandidato.put(candidato.candidatoId(), entidades);
        }
        List<HechoAnalizado> analizados = registro.deCorridaYTipo(corridaId, Tipos.HECHOS_ANALIZADOS).stream()
                .map(sobre -> registro.contenido(sobre, HechoAnalizado.class))
                .sorted((a, b) -> a.candidatoId().compareTo(b.candidatoId()))
                .toList();

        List<UUID> clientes = jdbc.sql("select cliente_id from parte.clientes order by cliente_id").query(UUID.class).list();
        int posicion = 0;
        for (UUID cliente : clientes) {
            Set<String> suscripciones = new HashSet<>(jdbc.sql(
                    "select entidad_id from parte.suscripciones where cliente_id = :c").param("c", cliente)
                    .query(String.class).list());
            if (suscripciones.isEmpty()) {
                continue;
            }
            Set<String> claves = new LinkedHashSet<>();
            int pendientes = 0;
            for (HechoAnalizado hecho : analizados) {
                Set<String> entidades = entidadesPorCandidato.getOrDefault(hecho.candidatoId(), Set.of());
                if (entidades.stream().anyMatch(suscripciones::contains) && claves.add(hecho.claveDeHecho())
                        && "INFERRED".equals(hecho.grado())) {
                    pendientes++;
                }
            }
            Informe informe = new Informe(cliente.toString(), corrida.fechaObjetivo(), new ArrayList<>(claves),
                    claves.isEmpty(), pendientes, cortada, corrida.modo());
            emisor.emitir(corridaId, Tipos.INFORMES, Editor.AGENTE, Corridas.idDeInicio(corridaId), ++posicion, informe);
            if (!"reproduccion".equals(corrida.modo())) {
                guardar(corridaId, cliente, corrida.fechaObjetivo(), informe);
            }
        }
    }

    private void guardar(String corridaId, UUID cliente, String fecha, Informe informe) {
        Long informeId = jdbc.sql("""
                insert into parte.informes (cliente_id, fecha_objetivo, corrida_id, sin_novedades, corrida_cortada)
                values (:cliente, cast(:fecha as date), :corrida, :sin, :cortada)
                on conflict (cliente_id, fecha_objetivo, corrida_id) do nothing
                returning id
                """)
                .param("cliente", cliente)
                .param("fecha", fecha)
                .param("corrida", corridaId)
                .param("sin", informe.sinNovedades())
                .param("cortada", informe.corridaCortada())
                .query(Long.class).optional().orElse(null);
        if (informeId == null || informe.hechos().isEmpty()) {
            return;
        }
        jdbc.sql("""
                insert into parte.informe_hechos (informe_id, version_id)
                select :informe, v.id
                from parte.versiones_de_hecho v
                join parte.hechos h on h.id = v.hecho_id
                where v.corrida_id = :corrida and h.clave_de_hecho in (:claves)
                on conflict do nothing
                """)
                .param("informe", informeId)
                .param("corrida", corridaId)
                .param("claves", informe.hechos())
                .update();
    }
}
