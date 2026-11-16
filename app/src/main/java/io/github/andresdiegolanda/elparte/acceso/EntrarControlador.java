package io.github.andresdiegolanda.elparte.acceso;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Las páginas para entrar: pedir el enlace, el aviso de que salió, y la que confirma el enlace. La
 * confirmación es un botón, no un ingreso directo: algunos programas de correo abren los enlaces para
 * revisarlos, y así no gastan el token.
 */
@Controller
public class EntrarControlador {

    @GetMapping("/entrar")
    public String entrar(@RequestParam(required = false) String vencido, Model modelo) {
        modelo.addAttribute("vencido", vencido != null);
        return "entrar";
    }

    @GetMapping("/entrar/enviado")
    public String enviado() {
        return "entrar-enviado";
    }

    @GetMapping("/login/ott")
    public String confirmar(@RequestParam(required = false) String token, Model modelo) {
        modelo.addAttribute("token", token);
        return "entrar-confirmar";
    }
}
