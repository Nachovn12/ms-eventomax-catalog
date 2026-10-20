# Handoff Técnico - EventoMax EP2: Bloque 1A y Estabilización P1 (Catalog)

- **Repositorio:** `ms-eventomax-catalog`
- **Rama:** `fix/catalog-p1-stabilization` (hacia `develop`)
- **Fecha:** Octubre 2026
- **Estado del bloque:** Implementado y verificado localmente con Testcontainers PostgreSQL 15.

---

## 1. Alcance Implementado

En este bloque se resolvió la gestión de concurrencia, consistencia en reservas y mantenimiento de equipamiento:

1. **Prevención de Deadlocks y Sobreventa:**
   - Bloqueo pesimista (`LockModeType.PESSIMISTIC_WRITE`) sobre `Inventory` ordenado de forma determinista y ascendente por `equipmentId`.
   - Validación estricta de stock disponible (`availableQty >= quantityRequested`). Si falta stock, la transacción se aborta con `InsufficientInventoryException` (`HTTP 409 Conflict`) sin descontar ningún ítem.

2. **Replay Idempotente y Detección de Conflictos:**
   - Si se reintenta una reserva para una misma producción (`productionId` existente):
     - Si los ítems y cantidades coinciden exactamente con la reserva previa, se retorna la reserva existente (`200 OK`) sin volver a descontar stock.
     - Si los ítems o cantidades difieren, se rechaza inmediatamente con `409 Conflict` (discrepancia de payload idempotente).

3. **Protocolo Fencing de Generaciones y Tombstones (Flyway V3):**
   - Tabla `reservation_generations(production_id, generation, closed)` con `CHECK (generation > 0)`.
   - Cabecera `X-Reservation-Generation` enviada por `ms-eventomax-productions`: rechaza peticiones obsoletas o de generaciones ya cerradas por compensación.
   - Peticiones legacy sin cabecera son bloqueadas si la producción ya cuenta con registros de generación o reservas activas.

4. **Endpoints de Compensación y Consulta:**
   - `POST /api/catalog/reservations/production/{productionId}/compensate`: Compensación técnica atómica que cancela la reserva (`COMPENSATED`), cierra la generación (tombstone) y restituye el stock disponible.
   - `GET /api/catalog/reservations/production/{productionId}`: Consulta de reservas por producción para reconciliación tras timeouts.

5. **Mantenimiento y Reabastecimiento de Equipos (P1):**
   - `PUT /api/catalog/equipment/{id}`: Permite al rol `Admin` actualizar nombre, descripción, estado activo (desactivación lógica) y stock disponible (`availableQty`).
   - **Garantía de Integridad:** Se adquiere lock pesimista sobre `Inventory`; `reservedQty` nunca se altera directamente, preservando intactas las reservas de producciones activas concurrentes.

---

## 2. Evidencia de Pruebas

- **Java Version:** Eclipse Temurin JDK 25 LTS.
- **Pruebas de concurrencia multi-hilo:** `InventoryConcurrencyIntegrationTest.java` ejecutada contra contenedor real PostgreSQL 15 en Docker Desktop / Testcontainers.
  - Verificación de 0 sobreventas compitiendo por stock.
  - Verificación de actualización concurrente de stock simultánea con confirmación de reserva y compensación técnica.
  - Verificación de bloqueo de reservas sobre equipos desactivados lógicamente (`active: false`).
- **Pruebas de migración Flyway V3:** `FlywayMigrationV3IntegrationTest.java` (PASS).
- **Controladores y servicios:** `EquipmentControllerTest.java`, `ReservationControllerTest.java`, `ServiceControllerTest.java` (PASS).

---

## 3. Instrucciones de Ejecución

### Requisitos previos:
- Eclipse Temurin JDK 25.
- Docker Desktop activo (para PostgreSQL 15 en Testcontainers).
- Maven 3.9+ o script `./mvnw`.

### Comandos de ejecución:
```bash
# Compilar y ejecutar suite completa de pruebas
./mvnw clean verify

# Construir contenedor Docker
docker build -t ms-eventomax-catalog:latest .
```
