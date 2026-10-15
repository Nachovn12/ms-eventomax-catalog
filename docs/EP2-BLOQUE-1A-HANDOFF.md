# Handoff Técnico - EventoMax EP2: Bloque 1A (Catalog)

- **Repositorio:** `ms-eventomax-catalog`
- **Rama:** `feature/EMX-bloque-1a-catalog`
- **Fecha:** Octubre 2026
- **Estado del bloque:** Implementado y verificado localmente.

---

## 1. Alcance Implementado

En este bloque se resolvió la gestión de concurrencia y consistencia en reservas de equipamiento:

1. **Prevención de Deadlocks y Sobreventa:**
   - Bloqueo pesimista (`LockModeType.PESSIMISTIC_WRITE`) sobre `Equipment` ordenado de forma determinista y ascendente por `equipmentId` (`ORDER BY e.equipmentId ASC`).
   - Validación estricta de stock disponible (`availableStock >= quantityRequested`). Si falta stock, la transacción se aborta con excepción y no se descuenta ningún ítem.

2. **Replay Idempotente y Detección de Conflictos:**
   - Si se reintenta una reserva para una misma producción (`productionId` existente):
     - Si los ítems y cantidades coinciden exactamente con la reserva previa, se retorna la reserva existente (`200 OK`) sin volver a descontar stock.
     - Si los ítems o cantidades difieren, se rechaza inmediatamente con `409 Conflict` (discrepancia de payload idempotente).

3. **Endpoints de Compensación y Consulta:**
   - `POST /api/catalog/reservations/production/{productionId}/compensate`: Endpoint de compensación técnica que cancela la reserva y restituye el stock de forma atómica.
   - `GET /api/catalog/reservations/production/{productionId}`: Endpoint de consulta para resolver incertidumbre tras timeouts de red en servicios consumidores (`ms-eventomax-productions` y BFF).

---

## 2. Evidencia de Pruebas Locales

- **Pruebas unitarias e integración de repositorio:** 43/43 PASS (`mvn clean test`).
- **Prueba de concurrencia multi-hilo:** `InventoryConcurrencyIntegrationTest.java` ejecutada contra contenedor real PostgreSQL 15 en Docker Desktop. 10 hilos concurrentes compitiendo por las últimas unidades de stock demostraron 0 sobreventas y rechazo consistente por falta de inventario sin bloqueos mutuos.

---

## 3. Limitaciones Conocidas y Trabajo Pendiente

> [!WARNING]
> No se declara 100% verificado ni listo para producción. Quedan pendientes pruebas de integración distribuida (E2E) con los demás microservicios en red compartida y despliegue real en infraestructura cloud/AWS.

- Integración con colas asíncronas para eventos de stock mínimo/agotado (Bloques posteriores si aplica).
- Pruebas de integración E2E bajo API Gateway / Spring Cloud Gateway.

---

## 4. Instrucciones para Continuar en el Laboratorio

### Requisitos previos:
- JDK 21 o superior.
- Docker Desktop (opcional, para ejecutar la prueba de integración con PostgreSQL real).
- Maven 3.9+ o script `./mvnw`.

### Comandos de clonado y ejecución:
```bash
git clone https://github.com/Nachovn12/ms-eventomax-catalog.git
cd ms-eventomax-catalog
git checkout feature/EMX-bloque-1a-catalog

# Compilar y ejecutar pruebas unitarias
./mvnw clean test
```
