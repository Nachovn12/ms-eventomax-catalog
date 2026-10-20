package cl.duoc.eventomax.catalog.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationV3IntegrationTest {

    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    @Test
    @DisplayName("Flyway V3 migration: successfully applies over pre-existing V2 data without loss or lock issues")
    void testFlywayV3MigrationOverPreExistingV2Data() throws Exception {
        DB.start();
        try {
            String jdbcUrl = DB.getJdbcUrl();
            String username = DB.getUsername();
            String password = DB.getPassword();

            // 1. Ejecutar Flyway hasta V2 exclusivamente
            Flyway flywayV2 = Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .locations("classpath:db/migration")
                    .target("2")
                    .load();
            var migrateResultV2 = flywayV2.migrate();
            assertTrue(migrateResultV2.success, "Migración inicial V1 y V2 debe ser exitosa");
            assertEquals("2", flywayV2.info().current().getVersion().getVersion());

            // 2. Insertar datos previos en el esquema V2
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO service (name, description, rate, active) VALUES ('Iluminación Conciertos', 'Focos móviles', 45000.00, true)");
                    stmt.execute("INSERT INTO equipment (name, description, active) VALUES ('Foco Beam 230W', 'Foco 230W Beam', true)");
                    stmt.execute("INSERT INTO inventory (equipment_id, available_qty, reserved_qty, version) VALUES (1, 8, 2, 0)");
                    stmt.execute("INSERT INTO production_reservation (production_id, equipment_id, quantity, status) " +
                            "VALUES (5001, 1, 2, 'RESERVED')");
                }
            }

            // 3. Ejecutar migración Flyway V3 sobre la base de datos con datos preexistentes
            Flyway flywayV3 = Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .locations("classpath:db/migration")
                    .target("3")
                    .load();
            var migrateResultV3 = flywayV3.migrate();
            assertTrue(migrateResultV3.success, "Migración V3 debe ser exitosa sobre datos previos");
            assertEquals("3", flywayV3.info().current().getVersion().getVersion());

            // 4. Verificar integridad de datos previos y existencia de la nueva estructura V3
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                // Verificar que los datos preexistentes de V2 continúan intactos
                try (Statement stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM service")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM equipment")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                    try (ResultSet rs = stmt.executeQuery("SELECT available_qty, reserved_qty FROM inventory WHERE equipment_id = 1")) {
                        assertTrue(rs.next());
                        assertEquals(8, rs.getInt("available_qty"));
                        assertEquals(2, rs.getInt("reserved_qty"));
                    }
                    try (ResultSet rs = stmt.executeQuery("SELECT status FROM production_reservation WHERE production_id = 5001")) {
                        assertTrue(rs.next());
                        assertEquals("RESERVED", rs.getString("status"));
                    }
                }

                // Verificar que reservation_generations existe y funciona
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reservation_generations (production_id, generation, closed) VALUES (?, ?, ?)")) {
                    ps.setLong(1, 5001L);
                    ps.setLong(2, 1L);
                    ps.setBoolean(3, false);
                    int inserted = ps.executeUpdate();
                    assertEquals(1, inserted);
                }

                // Verificar restricción CHECK (generation > 0)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reservation_generations (production_id, generation, closed) VALUES (?, ?, ?)")) {
                    ps.setLong(1, 5002L);
                    ps.setLong(2, 0L); // 0 debe violar CHECK (generation > 0)
                    ps.setBoolean(3, false);
                    assertThrows(SQLException.class, ps::executeUpdate, "Generación 0 debe ser rechazada por el CHECK");
                }
            }
        } finally {
            DB.stop();
        }
    }
}

