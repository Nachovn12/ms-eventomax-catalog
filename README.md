# EventoMax Catalog

Microservicio de dominio de **EventoMax** responsable de la gestiÃƒÂ³n de servicios, equipos, inventario y tarifas.

## TecnologÃƒÂ­as

- Java 25 LTS
- Spring Boot
- Spring Data JPA
- Hibernate
- Flyway
- PostgreSQL
- Maven
- OpenAPI / Swagger

## Responsabilidades

ms-eventomax-catalog debe:

- Administrar servicios disponibles para eventos.
- Administrar equipos y recursos de montaje.
- Gestionar inventario y disponibilidad.
- Gestionar tarifas asociadas a servicios y equipos.
- Validar disponibilidad antes de reservar inventario.
- Evitar la doble reserva de equipos.
- Persistir la informaciÃƒÂ³n propia del dominio de catÃƒÂ¡logo.
- Exponer operaciones bajo /api/catalog/*.

## Alcance actual implementado

El microservicio ms-eventomax-catalog actualmente implementa:

- CRUD de **servicios**.
- GestiÃƒÂ³n de **equipos** e **inventario**.
- **Reservas transaccionales de inventario**, mediante control de concurrencia de base de datos.
- PrevenciÃƒÂ³n backend de **sobre-reserva / doble consumo** mediante la implementaciÃƒÂ³n actual.
- Persistencia con PostgreSQL mediante Spring Data JPA / Hibernate.
- Versionado de esquema con Flyway.
- Despliegue cloud en AWS EC2 conectado a Amazon RDS PostgreSQL.
- IntegraciÃƒÂ³n protegida mediante API Gateway -> BFF -> Catalog.

## Arquitectura

El microservicio forma parte del flujo seguro de EventoMax:

Angular -> Microsoft Entra ID -> AWS API Gateway -> ms-eventomax-bff -> ms-eventomax-catalog -> Amazon RDS PostgreSQL

El frontend no accede directamente a este servicio ni a su base de datos.

## Persistencia

El microservicio utilizarÃƒÂ¡ PostgreSQL mediante:

- Spring Data JPA
- Hibernate
- Flyway

En cloud se utilizarÃƒÂ¡ Amazon RDS for PostgreSQL.

El servicio es propietario de sus propios datos y no debe realizar consultas SQL directas sobre datos internos de otros microservicios.

## Seguridad

La autenticaciÃƒÂ³n y autorizaciÃƒÂ³n se realizan mediante Microsoft Entra ID, AWS API Gateway y ms-eventomax-bff.

ms-eventomax-catalog permanece como microservicio interno de dominio y recibe las solicitudes autorizadas desde el BFF a travÃƒÂ©s de la red Docker eventomax-net.

No se deben almacenar en este repositorio:

- Client Secrets
- Access Tokens
- Credenciales AWS
- Credenciales PostgreSQL
- Archivos .env reales
- Passwords o claves privadas

## Estrategia de ramas

- main: versiÃƒÂ³n estable y preparada para entrega.
- develop: rama de integraciÃƒÂ³n.
- eature/*: desarrollo de historias de usuario.
- ix/*: correcciones.
- chore/*: configuraciÃƒÂ³n e infraestructura.

Flujo de integraciÃƒÂ³n:

eature/* -> Pull Request -> develop -> pruebas -> Pull Request -> main

## DESARROLLO LOCAL

Para desarrollo local, se debe utilizar Docker y las variables de entorno.

1. Copiar: .env.example -> .env
2. Usar: docker-compose.yml
3. Ejecutar:

``bash
# Iniciar los contenedores
docker compose up -d --build

# Ver los logs
docker compose logs -f
``

Localmente Catalog estÃƒÂ¡ disponible en: http://localhost:8081

### Pruebas

Para ejecutar la suite de pruebas unitarias y empaquetar la aplicaciÃƒÂ³n:

En Windows:
``bash
.\mvnw.cmd clean test
.\mvnw.cmd package -DskipTests
``

En Linux:
``bash
./mvnw clean test
./mvnw package -DskipTests
``

## PRODUCCIÃƒâ€œN / DEMO

La demo oficial NO depende de localhost.
Catalog se ejecuta en AWS EC2 mediante Docker Compose.

Flujo de la arquitectura:
Angular -> Microsoft Entra ID -> AWS API Gateway -> ms-eventomax-bff -> ms-eventomax-catalog -> Amazon RDS PostgreSQL

Aclaraciones de seguridad y despliegue:
- **Catalog NO es pÃƒÂºblico**: Angular nunca consume Catalog directamente.
- El BFF accede a http://ms-eventomax-catalog:8080 de manera interna, dentro de la red eventomax-net.
- En cloud no se depende de localhost.
- DB_URL, DB_USER y DB_PASSWORD deben inyectarse mediante variables de entorno o un mecanismo seguro en el host EC2. Nunca se deben documentar secretos reales.

ProducciÃƒÂ³n utiliza el archivo: docker-compose.prod.yml

Para ejecutar en producciÃƒÂ³n:
``bash
docker compose -f docker-compose.prod.yml up -d --build
``

**Importante:** docker-compose.prod.yml NO publica Catalog al host (EC2) para mantener la seguridad. El servicio expone ÃƒÂºnicamente su puerto 8080 interno a la red de Docker eventomax-net.

Diferencia de puertos:
- localhost:8081 = entorno local (para pruebas de desarrollo con port mapping).
- 8080 interno = contenedor / red Docker cloud (cerrado).

## Endpoints ÃƒÂºtiles (Entorno Local)

- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **Actuator Health**: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- **Actuator Info**: [http://localhost:8081/actuator/info](http://localhost:8081/actuator/info)

## Proyecto acadÃƒÂ©mico

**Asignatura:** DSY1107 - Desarrollo Cloud Native I
**Caso:** Caso 8 - EventoMax
