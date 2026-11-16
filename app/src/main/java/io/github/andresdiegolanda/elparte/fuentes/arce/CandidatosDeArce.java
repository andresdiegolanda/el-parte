package io.github.andresdiegolanda.elparte.fuentes.arce;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Evidencia;
import tools.jackson.databind.JsonNode;

/**
 * Arma un candidato a partir de una publicación de ARCE ya enmascarada.
 *
 * <p>La publicación OCDS es estructurada, así que el explorador de ARCE no necesita el modelo: el
 * resumen se escribe con los campos de la publicación, y la evidencia es la lista de esos campos con
 * su valor. Así es más barato y siempre da lo mismo para la misma publicación.
 */
public final class CandidatosDeArce {

    private static final int MAXIMO_ITEMS_EN_RESUMEN = 3;

    private CandidatosDeArce() {
    }

    /** El tipo de hecho de docs/contratos.md según la etiqueta OCDS de la publicación. */
    public static String tipoDeHecho(JsonNode publicacion) {
        String etiqueta = publicacion.path("tag").path(0).asString("");
        return switch (etiqueta) {
            case "tender" -> "llamado";
            case "tenderAmendment", "tenderUpdate" -> "aclaracion";
            case "award", "awardUpdate" -> "adjudicacion";
            case "tenderCancellation" -> "cancelacion";
            default -> "aviso";
        };
    }

    public static Candidato armar(String candidatoId, JsonNode publicacion, String entidadDelComprador, int tapados,
            String hashContenido) {
        String id = publicacion.path("id").asString("");
        String ocid = publicacion.path("ocid").asString("");
        String compra = ocid.substring(ocid.lastIndexOf('-') + 1);
        String tipo = tipoDeHecho(publicacion);
        String comprador = publicacion.path("buyer").path("name").asString("El comprador");
        JsonNode llamado = publicacion.path("tender");
        Campos campos = new Campos();
        campos.agregar("buyer.name", comprador);
        String resumen;
        switch (tipo) {
            case "llamado" -> {
                String titulo = campos.agregar("tender.title", llamado.path("title").asString(""));
                String descripcion = campos.agregar("tender.description", llamado.path("description").asString(""));
                int items = llamado.path("items").size();
                campos.agregar("tender.items", String.valueOf(items));
                String cierre = campos.agregar("tender.tenderPeriod.endDate", llamado.path("tenderPeriod").path("endDate").asString(""));
                resumen = comprador + " publicó el llamado " + titulo
                        + (descripcion.isBlank() ? "" : " (" + descripcion + ")")
                        + ", con " + items + (items == 1 ? " ítem" : " ítems") + "."
                        + (cierre.isBlank() ? "" : " Recibe ofertas hasta el " + fechaLegible(cierre) + ".");
            }
            case "aclaracion" -> {
                campos.agregar("tender.id", llamado.path("id").asString(compra));
                if (id.startsWith("ajuste_")) {
                    String detalle = campos.agregar("tender.submissionMethodDetails",
                            llamado.path("submissionMethodDetails").asString(""));
                    String cierre = campos.agregar("tender.tenderPeriod.endDate",
                            llamado.path("tenderPeriod").path("endDate").asString(""));
                    resumen = comprador + " ajustó el llamado " + compra
                            + (cierre.isBlank() ? "" : ": recibe ofertas hasta el " + fechaLegible(cierre))
                            + (detalle.isBlank() || !cierre.isBlank() ? "" : ": " + detalle) + ".";
                } else {
                    String descripcion = campos.agregar("tender.documents[0].description",
                            llamado.path("documents").path(0).path("description").asString(""));
                    resumen = comprador + " publicó una aclaración del llamado " + compra
                            + (descripcion.isBlank() ? "." : ": " + descripcion);
                }
            }
            case "adjudicacion" -> {
                // Una compra puede estar repartida en varias adjudicaciones, cada una a otros proveedores:
                // el resumen los nombra a todos, y no solo a los de la primera (T39).
                JsonNode adjudicaciones = publicacion.path("awards");
                List<String> proveedores = new ArrayList<>();
                List<String> items = new ArrayList<>();
                int totalDeItems = 0;
                for (int a = 0; a < adjudicaciones.size(); a++) {
                    JsonNode adjudicacion = adjudicaciones.path(a);
                    int n = 0;
                    for (JsonNode proveedor : adjudicacion.path("suppliers")) {
                        String nombre = proveedor.path("name").asString("");
                        String campo = "awards[" + a + "].suppliers[" + n++ + "].name";
                        if (!nombre.isBlank() && !proveedores.contains(nombre)) {
                            proveedores.add(campos.agregar(campo, nombre));
                        }
                    }
                    n = 0;
                    for (JsonNode item : adjudicacion.path("items")) {
                        totalDeItems++;
                        if (items.size() < MAXIMO_ITEMS_EN_RESUMEN) {
                            items.add(campos.agregar("awards[" + a + "].items[" + n + "].description",
                                    item.path("description").asString("")));
                        }
                        n++;
                    }
                }
                String monto = "";
                JsonNode unica = adjudicaciones.path(0);
                if (adjudicaciones.size() == 1 && unica.path("value").has("amount")) {
                    monto = campos.agregar("awards[0].value.amount", unica.path("value").path("amount").asString(""))
                            + " " + campos.agregar("awards[0].value.currency", unica.path("value").path("currency").asString(""));
                }
                resumen = comprador + " adjudicó la compra " + compra
                        + (adjudicaciones.size() > 1 ? " en " + adjudicaciones.size() + " adjudicaciones" : "")
                        + (proveedores.isEmpty() ? "" : (adjudicaciones.size() > 1 ? ", a " : " a ") + String.join(", ", proveedores))
                        + (items.isEmpty() ? "" : ": " + String.join("; ", items)
                                + (totalDeItems > items.size() ? " y otros ítems" : ""))
                        + (monto.isBlank() ? "" : ", por " + monto.trim())
                        + ".";
            }
            case "cancelacion" -> {
                campos.agregar("tender.status", llamado.path("status").asString(""));
                resumen = comprador + " canceló el llamado " + compra + ".";
            }
            default -> resumen = comprador + " publicó una novedad sobre la compra " + compra + ".";
        }

        List<EntidadEnCandidato> entidades = new ArrayList<>();
        entidades.add(new EntidadEnCandidato("comprador", entidadDelComprador, comprador));
        for (JsonNode adjudicacion : publicacion.path("awards")) {
            for (JsonNode proveedor : adjudicacion.path("suppliers")) {
                String nombre = proveedor.path("name").asString("");
                String rut = proveedor.path("id").asString("");
                boolean tapado = Enmascarador.PERSONA.equals(nombre);
                EntidadEnCandidato entidad = new EntidadEnCandidato("proveedor",
                        tapado || rut.isBlank() ? null : "persona_juridica:uy-rut-" + rut.replaceFirst("^R", ""),
                        nombre);
                if (!entidades.contains(entidad)) {
                    entidades.add(entidad);
                }
            }
        }
        String fecha = publicacion.path("date").asString("").replace("Z", "");
        return new Candidato(candidatoId, "arce", id, ocid, LectorArce.urlPublica(id), fecha, tipo, entidades,
                resumen.replaceAll("\\s+", " ").trim(), new Evidencia(campos.toString(), hashContenido), tapados);
    }

    /** {@code 2026-11-17T15:30:00Z} se lee como {@code 2026-11-17 15:30}, hora de Uruguay. */
    static String fechaLegible(String fechaOcds) {
        String sinZona = fechaOcds.replace("Z", "");
        return sinZona.length() >= 16 ? sinZona.substring(0, 10) + " " + sinZona.substring(11, 16) : sinZona;
    }

    /** Los campos que respaldan el resumen, en el orden en que se usaron. */
    private static final class Campos {
        private final StringJoiner texto = new StringJoiner("; ");

        String agregar(String campo, String valor) {
            if (valor != null && !valor.isBlank()) {
                texto.add(campo + " = " + valor);
            }
            return valor == null ? "" : valor;
        }

        @Override
        public String toString() {
            return texto.toString();
        }
    }
}
