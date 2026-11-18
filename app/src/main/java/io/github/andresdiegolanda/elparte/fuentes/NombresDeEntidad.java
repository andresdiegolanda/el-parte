package io.github.andresdiegolanda.elparte.fuentes;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cómo se reconoce a una entidad en el texto de una publicación, como una norma de IMPO o un proyecto del
 * BID: por su nombre, sin importar tildes ni mayúsculas, y por su sigla, solo en mayúsculas y como palabra
 * entera. El nombre sale de {@code parte.entidades}: "Administración Nacional de Usinas y Trasmisiones
 * Eléctricas (UTE)" da el nombre sin la sigla y la sigla. Si la entidad tiene nombres para la fuente en
 * sus identificadores, se usan esos.
 */
public final class NombresDeEntidad {

    private static final Pattern SIGLA_FINAL = Pattern.compile("^(.*?)\\s*\\(([A-ZÁÉÍÓÚÑ]{2,10})\\)\\s*$");
    private static final Pattern SIGLA = Pattern.compile("^[A-ZÁÉÍÓÚÑ]{2,10}$");

    private final String entidadId;
    private final String nombre;
    private final List<Patron> patrones = new ArrayList<>();
    private final List<String> nombres;

    /** Un nombre normalizado, que se busca en el texto normalizado, o una sigla, que se busca tal cual. */
    private record Patron(Pattern patron, boolean sigla) {
    }

    public NombresDeEntidad(String entidadId, String nombre, List<String> nombresEnLaFuente) {
        this.entidadId = entidadId;
        this.nombre = nombre;
        List<String> nombres = new ArrayList<>(nombresEnLaFuente);
        if (nombres.isEmpty()) {
            Matcher m = SIGLA_FINAL.matcher(nombre);
            if (m.matches()) {
                nombres.add(m.group(1));
                nombres.add(m.group(2));
            } else {
                nombres.add(nombre);
            }
        }
        for (String n : nombres) {
            boolean sigla = SIGLA.matcher(n).matches();
            patrones.add(new Patron(Pattern.compile("\\b" + Pattern.quote(sigla ? n : normalizar(n)) + "\\b"), sigla));
        }
        this.nombres = List.copyOf(nombres);
    }

    public String entidadId() {
        return entidadId;
    }

    public String nombre() {
        return nombre;
    }

    /** Los nombres con los que se la busca: los de la fuente o, si no hay, los que salen de su nombre. */
    public List<String> nombresEnLaFuente() {
        return nombres;
    }

    /** Dónde aparece la entidad en el texto, o -1. */
    public int dondeAparece(String texto) {
        if (texto == null || texto.isEmpty()) {
            return -1;
        }
        String normal = normalizar(texto);
        int primero = -1;
        for (Patron patron : patrones) {
            Matcher m = patron.patron().matcher(patron.sigla() ? texto : normal);
            if (m.find() && (primero < 0 || m.start() < primero)) {
                primero = m.start();
            }
        }
        return primero;
    }

    public boolean aparece(String texto) {
        return dondeAparece(texto) >= 0;
    }

    /** Minúsculas y sin tildes, del mismo largo que el texto, así las posiciones coinciden. */
    static String normalizar(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            salida.append(base.isEmpty() ? c : base.charAt(0));
        }
        return salida.toString().toLowerCase(Locale.ROOT);
    }
}
