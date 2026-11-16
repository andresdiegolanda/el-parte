package io.github.andresdiegolanda.elparte.web;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.andresdiegolanda.elparte.acceso.AvisoDiario;
import io.github.andresdiegolanda.elparte.acceso.UsuarioDeElParte;

/**
 * El perfil (ADR 0013, T34): el catálogo de lo que se puede seguir, lo que sigue quien entró, y si quiere
 * el aviso diario. Sin sesión se ve el de la cuenta de demostración, sin botones: la demostración no se
 * cambia.
 */
@Controller
public class PerfilControlador {

    private final JdbcClient jdbc;
    private final Seguimientos seguimientos;
    private final AvisoDiario aviso;

    public PerfilControlador(JdbcClient jdbc, Seguimientos seguimientos, AvisoDiario aviso) {
        this.jdbc = jdbc;
        this.seguimientos = seguimientos;
        this.aviso = aviso;
    }

    /** Una entidad del catálogo, y si el cliente la sigue. */
    public record Entidad(String id, String nombre, boolean sigue) {
    }

    @GetMapping("/perfil")
    public String perfil(@AuthenticationPrincipal UsuarioDeElParte usuario, Model modelo) {
        UUID cliente = usuario != null ? usuario.clienteId()
                : jdbc.sql("select cliente_id from parte.clientes where es_demo order by alias limit 1")
                        .query(UUID.class).single();
        List<Entidad> catalogo = jdbc.sql("""
                select e.id, e.nombre, s.entidad_id is not null as sigue
                from parte.entidades e
                left join parte.suscripciones s on s.entidad_id = e.id and s.cliente_id = :cliente
                where e.identificadores -> 'arce' is not null
                order by e.nombre
                """).param("cliente", cliente)
                .query((f, n) -> new Entidad(f.getString(1), f.getString(2), f.getBoolean(3))).list();
        modelo.addAttribute("catalogo", catalogo);
        modelo.addAttribute("siguiendo", catalogo.stream().filter(Entidad::sigue).count());
        modelo.addAttribute("soloLectura", usuario == null);
        modelo.addAttribute("aviso", usuario != null && aviso.quiere(usuario.clienteId()));
        return "perfil";
    }

    @PostMapping("/perfil/seguir")
    public String seguir(@AuthenticationPrincipal UsuarioDeElParte usuario, @RequestParam String entidad) {
        if (usuario != null) {
            seguimientos.seguir(usuario.clienteId(), entidad);
        }
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/dejar")
    public String dejar(@AuthenticationPrincipal UsuarioDeElParte usuario, @RequestParam String entidad) {
        if (usuario != null) {
            seguimientos.dejar(usuario.clienteId(), entidad);
        }
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/aviso")
    public String aviso(@AuthenticationPrincipal UsuarioDeElParte usuario, @RequestParam boolean quiere) {
        if (usuario != null) {
            aviso.cambiar(usuario.clienteId(), quiere);
        }
        return "redirect:/perfil";
    }
}
