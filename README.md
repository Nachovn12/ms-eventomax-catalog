# EventoMax Catalog

Microservicio de dominio de **EventoMax** responsable de la gestión de servicios, equipos, inventario y tarifas.

## Tecnologías

- Java 25 LTS
- Spring Boot
- Spring Data JPA
- Hibernate
- Flyway
- PostgreSQL
- Maven
- OpenAPI / Swagger

## Responsabilidades

`ms-eventomax-catalog` debe:

- Administrar servicios disponibles para eventos.
- Administrar equipos y recursos de montaje.
- Gestionar inventario y disponibilidad.
- Gestionar tarifas asociadas a servicios y equipos.
- Validar disponibilidad antes de reservar inventario.
- Evitar la doble reserva de equipos.
- Persistir la información propia del dominio de catálogo.
- Exponer operaciones bajo `/api/catalog/*`.

## Alcance implementado en EP1

Para EP1, `ms-eventomax-catalog` implementa el vertical mínimo de servicios:

- `GET /api/catalog/services`
- `GET /api/catalog/services/{id}`
- `POST /api/catalog/services`
- `PUT /api/catalog/services/{id}`
- persistencia con PostgreSQL mediante Spring Data JPA / Hibernate;
- versionado de esquema con Flyway;
- despliegue cloud en AWS EC2 conectado a Amazon RDS PostgreSQL;
- integración protegida mediante API Gateway -> BFF -> Catalog.

La gestión completa de equipos, inventario, disponibilidad y prevención de doble reserva corresponde al alcance semestral posterior.

## Inventario

EventoMax debe impedir que un mismo equipo sea reservado simultáneamente para eventos incompatibles.

La disponibilidad y reserva de inventario debe validarse en backend.

Cuando corresponda, se utilizarán:

- transacciones;
- control de concurrencia;
- validación de disponibilidad;
- operaciones atómicas sobre reservas.

La lógica de inventario no debe depender de validaciones realizadas únicamente en el frontend.

## Arquitectura

El microservicio forma parte del flujo seguro de EventoMax:

`Angular → Microsoft Entra ID → JWT → AWS API Gateway → ms-eventomax-bff → ms-eventomax-catalog → PostgreSQL`

El frontend no accede directamente a este servicio ni a su base de datos.

## Persistencia

El microservicio utilizará PostgreSQL mediante:

- Spring Data JPA
- Hibernate
- Flyway

En cloud se utilizará Amazon RDS for PostgreSQL.

El servicio es propietario de sus propios datos y no debe realizar consultas SQL directas sobre datos internos de otros microservicios.

## Seguridad

La autenticación y autorización se realizan mediante Microsoft Entra ID, AWS API Gateway y `ms-eventomax-bff`.

`ms-eventomax-catalog` permanece como microservicio interno de dominio y recibe las solicitudes autorizadas desde el BFF a través de la red Docker `eventomax-net`.

No se deben almacenar en este repositorio:

- Client Secrets
- Access Tokens
- credenciales AWS
- credenciales PostgreSQL
- archivos `.env` reales
- passwords o claves privadas

## Estrategia de ramas

- `main`: versión estable y preparada para entrega.
- `develop`: rama de integración.
- `feature/*`: desarrollo de historias de usuario.
- `fix/*`: correcciones.
- `chore/*`: configuración e infraestructura.

Flujo de integración:

`feature/* → Pull Request → develop → pruebas → Pull Request → main`

## DESARROLLO LOCAL

Para desarrollo local, se debe utilizar Docker y las variables de entorno.

1. Copiar: `.env.example` -> `.env`
2. Usar: `docker-compose.yml`
3. Ejecutar:

```bash
# Iniciar los contenedores
docker compose up -d --build

# Ver los logs
docker compose logs -f
```

Localmente Catalog está disponible en: `http://localhost:8081`

### Pruebas

Para ejecutar la suite de pruebas unitarias y empaquetar la aplicación:

En Windows:
```bash
.\mvnw.cmd clean test
.\mvnw.cmd package -DskipTests
```

En Linux:
```bash
./mvnw clean test
./mvnw package -DskipTests
```

## PRODUCCIÓN / DEMO

La demo oficial NO depende de localhost ni del PC del instituto.
Catalog se ejecuta en AWS EC2 mediante Docker.

Flujo de la arquitectura:
Microsoft Entra ID -> AWS API Gateway -> ms-eventomax-bff -> ms-eventomax-catalog -> Amazon RDS PostgreSQL

Catalog NO es accedido directamente por Angular.

Producción utiliza el archivo: `docker-compose.prod.yml`
y requiere las siguientes variables de entorno inyectadas de forma segura (sin hardcodear valores reales):
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`

Debe existir la red externa `eventomax-net` para que los contenedores se comuniquen. Ejemplo seguro (solo si no existe):
```bash
docker network create eventomax-net
```

Para ejecutar en producción:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

**Importante:** `docker-compose.prod.yml` NO publica Catalog al host (EC2) para mantener la seguridad. El servicio expone únicamente su puerto 8080 interno, y el BFF lo consume a través de la red de Docker `eventomax-net`.

Diferencia de puertos:
- `localhost:8081` = entorno local (para pruebas de desarrollo).
- `puerto 8080 interno` = contenedor / red Docker cloud.

## Endpoints Útiles

- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **Actuator Health**: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- **Actuator Info**: [http://localhost:8081/actuator/info](http://localhost:8081/actuator/info)

## Proyecto académico

**Asignatura:** DSY1107 – Desarrollo Cloud Native I  
**Caso:** Caso 8 – EventoMax
