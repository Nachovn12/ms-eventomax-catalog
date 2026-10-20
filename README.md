# ms-eventomax-catalog — Microservicio de Catálogo de Servicios, Equipamiento y Reservas Transaccionales (EP2)

**Asignatura:** Desarrollo Cloud Native I (`DSY1107`)
**Caso de Estudio:** Caso 8 — Plataforma de Producción y Montaje de Eventos (**EventoMax**)
**Equipo:** Ignacio Valeria, Benjamín Flores, Benjamín Espinoza

---

## 1. Propósito del Repositorio y Rol en EventoMax

`ms-eventomax-catalog` gestiona el catálogo maestro de servicios contratables, el inventario físico de equipamiento técnico (sonido, iluminación, escenarios, video) y las reservas atómicas e idempotentes por producción con control de concurrencia pesimista (`PESSIMISTIC_WRITE`) y *generation fencing* (`reservation_generation`).

En **EventoMax EP2**, además de soportar la confirmación transaccional de producciones desde `ms-eventomax-productions`, provee información referencial en tiempo real a `ms-eventomax-notify` (`CatalogEquipmentClient`) para enriquecer el **Acta de Cierre en PDF** con la disponibilidad operativa del catálogo.

---

## 2. Arquitectura del Módulo y Diagrama

```mermaid
flowchart LR
    BFF["ms-eventomax-bff"] -->|/api/catalog/**| CAT["ms-eventomax-catalog (:8080)"]
    PROD["ms-eventomax-productions"] -->|POST /api/catalog/reservations\nPOST /api/catalog/reservations/compensations| CAT
    NOTIFY["ms-eventomax-notify"] -->|GET /api/catalog/equipment\n(Enriquecimiento Acta PDF)| CAT
    CAT <-->|JPA + Flyway V1..V5\nPESSIMISTIC_WRITE en escrituras\nSin bloqueo en lecturas| PG_CAT[("PostgreSQL\ncatalog_services\nequipment\ninventory\nproduction_reservations")]
```

---

## 3. Stack Tecnológico y Versiones Reales (`pom.xml`)

| Componente | Tecnología / Dependencia | Versión |
|---|---|---|
| Lenguaje | Java (Eclipse Temurin) | `25` |
| Framework Base | Spring Boot (`spring-boot-starter-parent`) | `4.1.1` |
| Persistencia | Spring Data JPA / Hibernate + PostgreSQL Driver | Boot `4.1.1` |
| Migraciones DB | Flyway Core + `flyway-database-postgresql` | Boot `4.1.1` (`V1` a `V5`) |
| Observabilidad | `spring-boot-starter-actuator` + OpenAPI (`springdoc`) | `3.0.3` |
| Testing | JUnit 5, Testcontainers (`2.0.5`), MockMvc, H2 | Boot `4.1.1` |

---

## 4. Cumplimiento de Rúbrica EP2 (Matriz Caso 8)

| Criterio de Evaluación EP2 | Implementación en `ms-eventomax-catalog` | Estado |
|---|---|---|
| **Consistencia de Inventario y Reservas** | Bloqueo pesimista ordenado por `equipmentId` ascendente (`findByEquipmentIdForUpdate`) para evitar deadlocks en reservas multi-ítem e idempotencia por `idempotencyKey`. | **CUMPLIDO** |
| **Consultas Read-Only sin Contención de Locks** | Separación estricta entre `findByEquipmentIdWithoutLock` (lecturas de catálogo y enriquecimiento PDF de Notify) y `findByEquipmentIdForUpdate` (mutaciones de stock). | **CUMPLIDO** |
| **Compensación Transaccional (Saga)** | Endpoint `POST /api/catalog/reservations/compensations` con soporte de `generation` fencing para liberar stock ante fallos posteriores en Productions. | **CUMPLIDO** |

---

## 5. Endpoints Expuestos

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/catalog/services` | Lista servicios activos contratables (`?activeOnly=true`). |
| `GET` | `/api/catalog/services/{id}` | Obtiene detalle de un servicio por ID. |
| `POST` | `/api/catalog/services` | Crea un nuevo servicio en el catálogo. |
| `PUT` | `/api/catalog/services/{id}` | Actualiza nombre, descripción, precio o estado activo de un servicio. |
| `GET` | `/api/catalog/equipment` | Lista equipamiento junto con `availableQty` y `reservedQty` (sin adquirir bloqueos de fila). |
| `POST` | `/api/catalog/equipment` | Crea nuevo equipo e inicializa su inventario. |
| `PUT` | `/api/catalog/equipment/{id}` | Actualiza metadatos y ajusta stock total validando que no sea menor al stock reservado. |
| `POST` | `/api/catalog/reservations` | Reserva equipamiento de forma atómica e idempotente para una producción. |
| `POST` | `/api/catalog/reservations/compensations` | Compensa/libera reservas activas de una producción validando generación. |
| `GET` | `/api/catalog/reservations/productions/{productionId}` | Consulta las reservas asociadas a una producción. |
| `GET` | `/actuator/health`, `/actuator/info` | Estado de salud del microservicio. |

---

## 6. Seguridad y Aislamiento de Red

`ms-eventomax-catalog` se despliega en la red privada `eventomax-net` (`expose: ["8080"]`, sin puertos publicados al host). Todo acceso externo es autenticado y autorizado por **AWS API Gateway** y **`ms-eventomax-bff`** (permitiendo mutaciones de catálogo únicamente al rol `Admin` y reservas a `Admin`/`Productor`).

---

## 7. Base de Datos y Migraciones Flyway

| Versión | Archivo | Descripción |
|---|---|---|
| `V1` | `V1__init_catalog.sql` | Crea tablas `catalog_services`, `equipment` e `inventory`. |
| `V2` | `V2__seed_initial_catalog.sql` | Datos semilla de servicios y equipamiento técnico. |
| `V3` | `V3__create_production_reservations.sql` | Crea tablas `production_reservations` y `production_reservation_items`. |
| `V4` | `V4__add_generation_to_production_reservations.sql` | Añade columna `generation` para fencing de compensaciones. |
| `V5` | `V5__fix_active_reservation_unique_index.sql` | Índice único parcial sobre reservas en estado `ACTIVE`. |

---

## 8. Variables de Entorno Requeridas

| Variable | Ejemplo Placeholder | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/eventomax_catalog` | URL JDBC PostgreSQL |
| `DB_USER` | `catalog_user` | Usuario PostgreSQL |
| `DB_PASSWORD` | `<SECRET_DB_PASSWORD>` | Contraseña PostgreSQL |
| `JAVA_TOOL_OPTIONS` | `-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=65.0` | Límites de memoria JVM para EC2 `t3.large` |

---

## 9. Ejecución Local y Pruebas Automatizadas

```bash
# Ejecutar suite completa de pruebas unitarias y de concurrencia con Testcontainers (67 tests)
mvn clean test
```

**Resultado certificado en EP2:** `Tests run: 67, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`.

---

## 10. Construcción y Despliegue Docker

```bash
docker build -t ms-eventomax-catalog:ep2 .
docker compose -f docker-compose.prod.yml up -d --build
```

---

## 11. Estado Actual de Certificación EP2 y Trazabilidad Jira

- **Pruebas Automatizadas:** `67/67` aprobadas (`InventoryConcurrencyIntegrationTest`, `InventoryReservationServiceTest`, `EquipmentControllerTest`, `EquipmentServiceTest`, `CatalogControllerTest`).
- **Historias Jira:** `EMX-Bloque-1A` (Reservas atómicas, compensación y edición de inventario), `fix/catalog-inventory-readonly-lock` (Eliminación de lock pesimista en lecturas).
