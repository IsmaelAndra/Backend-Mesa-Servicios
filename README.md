# Mesa de Ayuda COAC — Backend

API REST de la mesa de servicios de la COAC Magisterio de Pichincha: tickets con SLA, chat en tiempo real (SSE), notificaciones por Microsoft Graph, reportes PDF (JasperReports), asistente NEXA y un motor de automatizaciones (workflows).

El frontend vive en [Frontend-Mesa-Servicios](https://github.com/IsmaelAndra/Frontend-Mesa-Servicios).

## Requisitos

- Java 17
- PostgreSQL 14 o superior (con la extensión `pg_trgm`, incluida en la distribución estándar)
- Docker solo si se quieren ejecutar en local las pruebas `*IT` (en CI ya corren)

## Puesta en marcha (desarrollo)

```bash
cp .env.example .env          # completar DB_PASSWORD, JWT_SECRET y ADMIN_SEED_PASSWORD
./gradlew bootRun --args="--spring.profiles.active=dev"
```

- `bootRun` carga el `.env` de la raíz (solo esa tarea; las pruebas no lo leen).
- `JWT_SECRET`: `openssl rand -base64 64`.
- Con `ADMIN_SEED_PASSWORD` definido, el primer arranque crea `superadmin@` y `admin@` con cambio de contraseña obligatorio.
- El perfil `dev` añade los usuarios de prueba `agente@` / `usuario@` (`Default123!`), activa Swagger (`http://localhost:8080/api/swagger-ui.html`) y el log SQL.

## Base de datos y migraciones (Flyway)

El esquema lo gestiona **Flyway** al arrancar:

| Archivo | Qué es |
| --- | --- |
| `db/migration/V1__esquema_base.sql` | Esquema completo (idempotente) |
| `db/migration/V2__...`, `V3__...` | Cambios posteriores, uno por archivo |
| `db/migration/R__datos_base.sql` | Catálogos y textos iniciales; se reaplica cuando cambia |
| `db/dev/R__datos_dev.sql` | Usuarios de prueba, solo con el perfil `dev` |

Reglas:

- **Un cambio de esquema nuevo = una migración nueva** (`V3__descripcion_en_minusculas.sql`). Nunca se edita una `V` ya aplicada: Flyway valida su checksum y la aplicación no arrancaría.
- Las sentencias usan comillas simples, no `$$` (lo comprueba `SqlScriptsValidationTest`).
- Una base que existía antes de Flyway se marca en la versión 0 y recibe `V1` encima; como `V1` es idempotente, solo completa lo que falte.

## Pruebas

```bash
./gradlew test                                            # todas
./gradlew test --tests com.mesaayuda.service.TicketServiceTest
```

- Las pruebas unitarias usan JUnit 5 + Mockito y no necesitan base de datos.
- Las `*IT` (`integracion/`) levantan un PostgreSQL real con Testcontainers: validan las migraciones, el mapeo JPA (`ddl-auto=validate`) y el JPQL de los repositorios. **Se saltan solas si Docker no está en marcha**; en CI siempre corren.
- Cobertura: `./gradlew jacocoTestReport` → `build/reports/jacoco/test/html`.

## Producción

**Instalación y actualización con Docker:** [`despliegue/INSTALACION.md`](despliegue/INSTALACION.md) (`docker-compose.yml` con PostgreSQL, este backend y el frontend con Nginx; `.env.example` de producción; `respaldo.sh` para copias de seguridad).

- `SPRING_PROFILES_ACTIVE=prod`: apaga Swagger y el DEBUG, fuerza la cookie `Secure` y expone solo `health`.
- `APP_SECURE_COOKIE=true` detrás de HTTPS.
- `APP_TRUST_FORWARDED_HEADERS=true` **solo** si todo el tráfico entra por un proxy.
- Varias instancias: las tareas programadas usan **ShedLock** (tabla `shedlock`), así que cada una se ejecuta en una sola instancia. El worker de workflows reparte la cola entre todas.

## Integración continua

`.github/workflows/ci.yml`: pruebas (con las `*IT` sobre el Docker del runner de GitHub) e informe de cobertura en cada push y pull request.

## Estructura

```
src/main/java/com/mesaayuda/
  config/      seguridad, CORS, auditoría, ShedLock, semillas
  controller/  endpoints REST
  service/     lógica de negocio
  workflow/    motor de automatizaciones (+ handler/)
  scheduler/   tareas programadas (SLA, notificaciones, Graph…)
  entity/ dto/ mapper/ repository/
src/main/resources/
  db/migration/  migraciones Flyway
  reports/       plantillas JasperReports
```

Las convenciones del proyecto (seguridad, motor de workflows, búsqueda, nombres de columnas) están documentadas en el `AGENTS.md` de la carpeta de trabajo. El código, los comentarios y los textos están en español.

## Módulo de Activos de TI

- CRUD de activos con código único, tipo, marca, modelo, serie, estado, fechas, proveedor, costo, garantía.
- Asignación/desasignación a usuario, área y sede.
- Historial de cambios (sset_history).
- Permisos: SUPER_ADMIN|ADMIN|AREA_MANAGER gestionan; SUPER_ADMIN|ADMIN eliminan.
- Integrado a búsqueda global y catálogos de área/sucursal.
- Seed de ejemplo solo en perfil dev.
