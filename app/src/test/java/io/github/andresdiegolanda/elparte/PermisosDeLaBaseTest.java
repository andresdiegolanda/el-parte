package io.github.andresdiegolanda.elparte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * El reparto de permisos por esquema (docs/base-de-afirmaciones.md, ADR 0008): el rol de los agentes
 * y la web no ve las cuentas, el del login no ve la base de afirmaciones, y ninguno cambia el esquema.
 */
class PermisosDeLaBaseTest extends ConServicios {

    @Test
    void losAgentesNoVenLasCuentas() throws SQLException {
        try (Connection agentes = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "elparte_agentes", "agentes-local")) {
            assertThat(contar(agentes, "select count(*) from parte.fuentes")).isEqualTo(1);
            assertThatThrownBy(() -> contar(agentes, "select count(*) from cuentas.usuarios"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> ejecutar(agentes, "create table parte.intrusa (id int)"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void elLoginNoVeLaBaseDeAfirmaciones() throws SQLException {
        try (Connection cuentas = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "elparte_cuentas", "cuentas-local")) {
            assertThat(contar(cuentas, "select count(*) from cuentas.usuarios")).isZero();
            assertThatThrownBy(() -> contar(cuentas, "select count(*) from parte.hechos"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> ejecutar(cuentas, "create table cuentas.intrusa (id int)"))
                    .hasMessageContaining("permission denied");
        }
    }

    private static long contar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement(); ResultSet fila = sentencia.executeQuery(sql)) {
            fila.next();
            return fila.getLong(1);
        }
    }

    private static void ejecutar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }
}
