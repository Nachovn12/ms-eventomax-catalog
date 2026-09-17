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

El acceso protegido llegará a través de:

`AWS API Gateway → ms-eventomax-bff → ms-eventomax-catalog`

La autenticación y autorización se basan en JWT emitidos por Microsoft Entra ID.

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

## Ejecución local

### Perfil Local

Para desarrollo local, se puede ejecutar la aplicación con el perfil `local`. Este perfil utiliza la configuración de `src/main/resources/application-local.yaml` para conectarse a una base de datos PostgreSQL local.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Docker Compose

Para levantar el entorno completo (aplicación y base de datos) usando Docker:

```bash
# Iniciar los contenedores
docker-compose up -d --build

# Ver los logs
docker-compose logs -f
```

La configuración en `docker-compose.yml` utiliza las variables de entorno de un archivo `.env` (basado en `.env.example`).
**Nota**: Las variables reales para la conexión a la base de datos en producción (`DB_URL`, `DB_USER`, `DB_PASSWORD`) vienen inyectadas desde el entorno cloud.

## Endpoints Útiles

- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **Actuator Health**: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- **Actuator Info**: [http://localhost:8081/actuator/info](http://localhost:8081/actuator/info)

## Proyecto académico

**Asignatura:** DSY1107 – Desarrollo Cloud Native I  
**Caso:** Caso 8 – EventoMax
