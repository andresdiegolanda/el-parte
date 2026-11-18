package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tapa los nombres de personas en los títulos y resúmenes de las resoluciones de Presidencia antes de que
 * los vea nadie (ADR 0008). Es texto libre, así que los nombres hay que detectarlos, sin el modelo:
 *
 * <ul>
 * <li>Una tira de dos o más palabras con mayúscula que no son de una institución, un cargo o un lugar se
 * cambia por {@code [persona]}: "Se designa en misión oficial a [persona]". Las palabras de instituciones
 * están en {@link #INSTITUCIONALES}, y las de las entidades seguidas se suman en cada llamada.</li>
 * <li>Una sola palabra con mayúscula también, si va después de un título o un cargo ("Subsecretario
 * Olesker", "Dr. Pérez"), y el título se tapa con ella.</li>
 * <li>Una sola palabra con mayúscula pegada a unos puntos suspensivos, como queda un nombre en un resumen
 * cortado ("formulada por Lorena de...").</li>
 * </ul>
 *
 * <p>Prefiere tapar de más: un nombre de empresa o de proyecto que no está en la lista también queda como
 * {@code [persona]}. Un apellido solo, sin título ni cargo delante, no se ve: es el límite de tapar por
 * reglas, y se mide en docs/como-se-hizo/presidencia.md.
 */
public final class EnmascaradorPresidencia {

    public static final String PERSONA = "[persona]";

    /** Palabras de instituciones, cargos, documentos y lugares que no son nombres de personas. */
    static final Set<String> INSTITUCIONALES = Set.of(("Resolución Resoluciones Decreto Ley Leyes Poder Ejecutivo Legislativo Judicial "
            + "Presidencia Presidente Presidenta República Oriental Uruguay Uruguaya Uruguayo Uruguayos Uruguayas "
            + "Ministerio Ministro Ministra Ministros Subsecretario Subsecretaria Subsecretaría Secretaría Secretario Secretaria "
            + "Prosecretaría Prosecretario Prosecretaria Dirección Director Directora Directores Directorio Directivo General "
            + "Nacional Nacionales Regional Departamental Internacional Internacionales Interior Exterior Exteriores Economía "
            + "Finanzas Trabajo Seguridad Social Desarrollo Educación Cultura Salud Pública Públicas Público Públicos Defensa "
            + "Industria Energía Minería Transporte Obras Ganadería Agricultura Pesca Turismo Vivienda Ordenamiento Territorial "
            + "Ambiente Relaciones Deporte Banco Previsión Hipotecario Central Seguros Junta Juntas Consejo Consejos Consultivo "
            + "Comisión Comisiones Honoraria Honorario Asesora Asesor Administradora Administración Agencia Oficina Instituto "
            + "Universidad Universitario Centro Centros Sede Unidad Unidades Dependientes Servicio Servicios Civil Registro Fondo "
            + "Fideicomiso Programa Plan Proyecto Proyectos Convenio Convenios Contrato Contratos Préstamo Préstamos Licitación "
            + "Abreviada Contratación Directa Compra Compras Contrataciones Estatales Estado Interés Valor Agregado Impuesto "
            + "Impuestos Tribunal Cuentas Corte Fiscalía Intendencia Intendencias Intendentes Congreso Departamento Sección "
            + "Catastral Padrón Escalafón Grado Especialista Serie Abogado Licenciado Licenciada Profesional Corporación Empresa "
            + "Empresas Sociedad Asociación Club Federación Confederación Unión Cooperativa Cooperativismo Fundación Atlético "
            + "Atlética S SA S.A S.A. SRL S.R.L. Ltda Inc Misión Oficial Activo Embajada Embajador Embajadora Extraordinario "
            + "Extraordinaria Plenipotenciario Plenipotenciaria Consejero Consejera Cónsul Consulado Representante Delegación "
            + "Mixta Comitiva Cartera Designación Asimilación Renovación Sustitución Modificación Creación Excepción Aprobación "
            + "Autorización Asignación Adjudicación Adquisición Disposición Desafectación Transposición Transformación "
            + "Suscripción Prórroga Cese Incremento Declárase Convocatoria Edición Premios Premio Concursables Beca Becas Primera "
            + "Primer Segunda Segundas Experiencia Laboral Feria Empleo Jornadas Reunión Técnica Técnicas Técnico Ministerial "
            + "Considerando Numeral Artículo Anexo Capítulo Inciso Literal Deportivo Promovido Promovidos Política Económica "
            + "Estadística Estadísticas Investigación Agropecuaria Rehabilitación Drogas Aduanas Casinos Granja Granjera "
            + "Emergencia Ciencias Sociales Medicina Operacional Diplomatura Museo Casa Gobierno Parque Pista Atletismo "
            + "Selecciones Selección Cuenca Río Plata Litoral Norte Sur Este Oeste Radiodifusión Telecomunicaciones Licencias "
            + "Clase Canteras Recursos Derecho Derechos Protección Restitución Identidad Origen Integración Urbana Niño "
            + "Adolescente Terciaria Privada Antártico Mercosur MERCOSUR Unidas Naciones Organización Estados Americanos "
            + "Interamericano Mundial Europea Puertos Personas Función Montevideo Canelones Colonia Paysandú Durazno Tacuarembó "
            + "Salto Florida BPS ANCAP UTE ANTEL OSE ASSE INAU INAC INE ONSC MEF MTSS MIDES MSP MEC MIEM MTOP MGAP MVOT MRREE "
            + "MINTUR MDN RNU I II III IV V VI VII VIII IX X XI XII XIII XIV XV A B C D E F N").split(" "));

    /** Títulos y cargos que van delante de un nombre. Se tapan con el nombre que les sigue. */
    static final Set<String> TITULOS = Set.of(("Sr. Sra. Srta. Sres. Sras. Dr. Dra. Dres. Lic. Lie. Esc. Cr. Cra. Ing. Agr. Arq. Prof. "
            + "Mtro. Mtra. Mag. Ec. Psic. Méd. Vet. Tec. Téc. Asist. Cnel. Tte. Cap. Gral. Embajador Embajadora Subsecretario "
            + "Subsecretaria Consejero Consejera Ministro Ministra Comisario Comisaria Contralmirante Almirante Coronel General "
            + "Director Directora Presidente Presidenta Intendente Intendenta Senador Senadora Diputado Diputada Señor Señora")
            .split(" "));

    private static final String PALABRA = "[A-ZÁÉÍÓÚÜÑ][\\p{L}\\p{N}'’´-]*\\.?";
    private static final String CONECTOR = "(?:de las|de los|de la|del|de|da|do|dos|y|e)";
    private static final Pattern TIRA = Pattern.compile("(?<![\\p{L}\\p{N}])" + PALABRA + "(?:\\s+(?:" + CONECTOR + "\\s+)?" + PALABRA + ")*");
    private static final Pattern CORTADO = Pattern.compile("(?<![\\p{L}\\p{N}\\[])(" + PALABRA + ")(\\s+" + CONECTOR + ")?(\\s*(?:\\.\\.\\.|…))");

    private EnmascaradorPresidencia() {
    }

    public record Tapado(String texto, int tapados) {
    }

    /** Tapa los nombres de un texto. Las palabras de {@code entidades} nunca se tapan. */
    public static Tapado tapar(String texto, Collection<String> entidades) {
        if (texto == null || texto.isEmpty()) {
            return new Tapado(texto, 0);
        }
        Set<String> institucionales = new HashSet<>(INSTITUCIONALES);
        for (String nombre : entidades) {
            for (String palabra : nombre.split("[\\s()]+")) {
                if (!palabra.isEmpty()) {
                    institucionales.add(palabra);
                    institucionales.add(sinTildes(palabra));
                }
            }
        }
        int[] tapados = {0};
        StringBuilder salida = new StringBuilder();
        Matcher tira = TIRA.matcher(texto);
        int desde = 0;
        while (tira.find()) {
            salida.append(texto, desde, tira.start()).append(taparTira(tira.group(), institucionales, tapados));
            desde = tira.end();
        }
        salida.append(texto.substring(desde));

        StringBuilder sinCortes = new StringBuilder();
        Matcher cortado = CORTADO.matcher(salida);
        while (cortado.find()) {
            String palabra = cortado.group(1);
            boolean nombre = !esInstitucional(palabra, institucionales) && !TITULOS.contains(palabra);
            cortado.appendReplacement(sinCortes, Matcher.quoteReplacement(nombre ? PERSONA + cortado.group(3) : cortado.group()));
            if (nombre) {
                tapados[0]++;
            }
        }
        cortado.appendTail(sinCortes);
        return new Tapado(sinCortes.toString(), tapados[0]);
    }

    /** Una tira de palabras con mayúscula: se tapan los grupos que parecen nombres. */
    private static String taparTira(String tira, Set<String> institucionales, int[] tapados) {
        List<String> salida = new ArrayList<>();
        List<String> grupo = new ArrayList<>();
        for (String palabra : tira.split("\\s+")) {
            boolean mayuscula = Character.isUpperCase(palabra.codePointAt(0));
            if (!mayuscula && (palabra.equals("y") || palabra.equals("e"))) {
                // "y" separa a dos personas: "a [persona] y [persona]".
                cerrar(grupo, salida, tapados);
                salida.add(palabra);
            } else if (mayuscula && (TITULOS.contains(palabra) || !esInstitucional(palabra, institucionales))) {
                grupo.add(palabra);
            } else if (!mayuscula && grupo.stream().anyMatch(p -> Character.isUpperCase(p.codePointAt(0)))) {
                grupo.add(palabra);
            } else {
                cerrar(grupo, salida, tapados);
                salida.add(palabra);
            }
        }
        cerrar(grupo, salida, tapados);
        return String.join(" ", salida);
    }

    private static void cerrar(List<String> grupo, List<String> salida, int[] tapados) {
        List<String> colgados = new ArrayList<>();
        while (!grupo.isEmpty() && !Character.isUpperCase(grupo.getLast().codePointAt(0))) {
            colgados.addFirst(grupo.removeLast());
        }
        if (!grupo.isEmpty()) {
            long titulos = grupo.stream().filter(TITULOS::contains).count();
            long nombres = grupo.stream().filter(p -> Character.isUpperCase(p.codePointAt(0)) && !TITULOS.contains(p)).count();
            if (nombres >= 2 || (nombres == 1 && titulos >= 1)) {
                String ultima = grupo.getLast();
                salida.add(PERSONA + (ultima.endsWith(".") && !TITULOS.contains(ultima) ? "." : ""));
                tapados[0]++;
            } else {
                salida.addAll(grupo);
            }
        }
        salida.addAll(colgados);
        grupo.clear();
    }

    private static boolean esInstitucional(String palabra, Set<String> institucionales) {
        String sinPunto = palabra.endsWith(".") ? palabra.substring(0, palabra.length() - 1) : palabra;
        return institucionales.contains(palabra) || institucionales.contains(sinPunto)
                || institucionales.contains(sinTildes(sinPunto));
    }

    private static String sinTildes(String palabra) {
        return Normalizer.normalize(palabra, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
