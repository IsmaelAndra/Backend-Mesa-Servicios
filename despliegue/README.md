# Mesa de Servicios COAC: construir, publicar y desplegar con Docker

Las imágenes se **construyen en tu PC** con Docker Desktop, se **publican en GitHub Container Registry (GHCR)** y el **servidor EC2 solo las descarga** y las levanta. En el servidor no se compila nada.

```
Tu PC (Docker Desktop)                     GHCR                         EC2
docker compose build  ──push──►  ghcr.io/ismaelandra/...  ◄──pull──  docker compose up -d

En el EC2:
navegador ─HTTPS─► Nginx del servidor (80/443, ya existe)
                       │  red de Docker compartida (PROXY_NETWORK)
                       ▼
                   frontend:8080 (Nginx + Angular) ──/api──► backend:8080 ──► db:5432 (red interna)
```

- Ningún contenedor publica puertos en el servidor. Tu Nginx llega al frontend por una red de Docker compartida, con el nombre `mesa-servicios-frontend`.
- PostgreSQL está en una red interna sin salida a internet, y solo el backend llega a él.
- Los datos (base de datos y adjuntos) viven en volúmenes de Docker y no se pierden al actualizar.

## 1. Archivos

| Archivo | Para qué sirve |
|---|---|
| `Backend-Mesa-Servicios/Dockerfile` | Imagen del backend: compila el JAR con Gradle y lo ejecuta con Java 17, sin root, con las fuentes que necesitan los PDF y un healthcheck en `/api/actuator/health`. |
| `Backend-Mesa-Servicios/.dockerignore` | Deja fuera de la imagen `.env`, `uploads/`, `build/`, `.git/` y esta carpeta. |
| `Frontend-Mesa-Servicios/Dockerfile` | Imagen del frontend: compila Angular (`dist/mesa-ayuda-frontend`) y lo sirve con Nginx sin root en el puerto 8080. |
| `Frontend-Mesa-Servicios/.dockerignore` | Deja fuera `node_modules/`, `dist/`, `.angular/` y `.git/`. |
| `Frontend-Mesa-Servicios/nginx.conf` | Sirve Angular, reenvía `/api` al backend (y `/api/events`, el SSE, sin búfer), pone las cabeceras de seguridad y obtiene la IP real del usuario. |
| `despliegue/docker-compose.yml` | **En tu PC.** Construye las dos imágenes con su nombre de GHCR y permite probarlas en `http://localhost:8088`. |
| `despliegue/docker-compose.prod.yml` | **En el servidor.** Sin `build`: descarga las imágenes de GHCR y las levanta sin publicar puertos. |
| `despliegue/.env.example` | Variables necesarias, con valores de ejemplo. Se copia a `.env`. |
| `despliegue/respaldo.sh` | Copia de seguridad de la base de datos y de los adjuntos. |

## 2. Antes de empezar (una sola vez)

### 2.1 Token de GitHub para GHCR

En GitHub → *Settings* → *Developer settings* → *Personal access tokens (classic)* → *Generate new token*:

- **En tu PC** (para publicar): permisos `write:packages` y `read:packages`.
- **En el servidor** (para descargar): un token aparte con solo `read:packages`.

Guárdalos en un gestor de contraseñas. No se escriben en ningún archivo del proyecto.

### 2.2 Arquitectura del EC2

```bash
uname -m
```

`x86_64` es la arquitectura que construye Docker Desktop en Windows por defecto. Si sale `aarch64` (instancias Graviton), mira el punto 3.3.

### 2.3 Red de Docker del Nginx del servidor

El frontend tiene que estar en la misma red de Docker que tu Nginx. Mira cómo se llama:

```bash
docker network ls
docker inspect <contenedor-del-nginx> --format '{{json .NetworkSettings.Networks}}'
```

Ese nombre va en `PROXY_NETWORK` del `.env` del servidor. Si tu Nginx todavía no está en ninguna red propia, crea una y conéctalo. Esto añade una red, no cambia su configuración:

```bash
docker network create proxy
docker network connect proxy <contenedor-del-nginx>
```

Mira también su rango de IP y ponlo en `REAL_IP_FROM` si no está dentro de `172.16.0.0/12`:

```bash
docker network inspect proxy --format '{{(index .IPAM.Config 0).Subnet}}'
```

## 3. Construir y publicar las imágenes (PowerShell, en tu PC)

### 3.1 Primera vez: el `.env` local

```powershell
cd C:\Users\IsmaelAndrade\Documents\GitHub\Backend-Mesa-Servicios\despliegue
Copy-Item .env.example .env
```

Para construir basta con `GHCR_OWNER` y `VERSION`. Para probar en local rellena también `DB_PASSWORD`, `JWT_SECRET` y `GRAPH_ENCRYPTION_KEY` con valores de prueba, y las URL con `http://localhost:8088`.

### 3.2 Construir, probar y publicar

```powershell
cd C:\Users\IsmaelAndrade\Documents\GitHub\Backend-Mesa-Servicios\despliegue

# 1. Versión que vas a publicar (cámbiala en cada entrega)
$env:VERSION = "1.1.0"

# 2. Comprobar el archivo y construir las dos imágenes
docker compose config --quiet
docker compose build

# 3. (Opcional) Probar en tu PC: http://localhost:8088
docker compose up -d
docker compose ps
docker compose down          # sin -v: conserva los datos de prueba

# 4. Iniciar sesión en GHCR (pega el token de "write:packages" cuando lo pida)
docker login ghcr.io -u IsmaelAndra

# 5. Subir las dos imágenes
docker compose push backend frontend
```

Las imágenes quedan como `ghcr.io/ismaelandra/mesa-servicios-backend:1.1.0` y `ghcr.io/ismaelandra/mesa-servicios-frontend:1.1.0`. Se ven en GitHub → tu perfil → *Packages*. Por defecto son **privadas**, así que el servidor necesita su propio `docker login`.

### 3.3 Si el EC2 es ARM (Graviton)

En lugar de los pasos 2 y 5, construye y sube las dos arquitecturas a la vez:

```powershell
docker buildx create --use --name multi 2>$null
docker buildx build --platform linux/amd64,linux/arm64 --build-arg VERSION=$env:VERSION `
  -t ghcr.io/ismaelandra/mesa-servicios-backend:$env:VERSION --push ..
docker buildx build --platform linux/amd64,linux/arm64 --build-arg VERSION=$env:VERSION `
  -t ghcr.io/ismaelandra/mesa-servicios-frontend:$env:VERSION --push ..\..\Frontend-Mesa-Servicios
```

## 4. Instalar en el servidor EC2 (primera vez)

```bash
# 1. Carpeta del despliegue con los tres archivos que necesita
sudo mkdir -p /opt/mesa-servicios && sudo chown "$USER" /opt/mesa-servicios
cd /opt/mesa-servicios
# Cópialos desde tu PC (scp) o descárgalos del repositorio:
#   despliegue/docker-compose.prod.yml, despliegue/.env.example, despliegue/respaldo.sh
chmod +x respaldo.sh

# 2. Variables de producción
cp .env.example .env
chmod 600 .env
nano .env
```

En el `.env` del servidor:

- Descomenta `COMPOSE_FILE=docker-compose.prod.yml`. Así `docker compose` y `respaldo.sh` usan el archivo de producción sin poner `-f`.
- `GHCR_OWNER` y `VERSION`: la versión que publicaste.
- `PROXY_NETWORK` y `REAL_IP_FROM`: las del punto 2.3.
- Genera las claves y pégalas:
  ```bash
  echo "DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')"
  echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')"
  echo "GRAPH_ENCRYPTION_KEY=$(openssl rand -base64 48 | tr -d '\n')"
  ```
- `CORS_ORIGINS`, `APP_PUBLIC_BASE_URL` y `APP_FRONTEND_BASE_URL` con el dominio real, en `https://`.
- `ADMIN_SEED_PASSWORD`: contraseña temporal para crear `superadmin@` y `admin@`.

```bash
# 3. Descargar y levantar
echo "<token read:packages>" | docker login ghcr.io -u IsmaelAndra --password-stdin
docker compose config --quiet     # valida el archivo y el .env
docker compose pull
docker compose up -d

# 4. Comprobar (los tres deben acabar en "healthy"; la primera vez Flyway crea las tablas)
docker compose ps
docker compose logs -f backend    # Ctrl+C para salir; buscar "Started MesaAyudaApplication"
```

> **Guarda `GRAPH_ENCRYPTION_KEY` y `DB_PASSWORD`.** Si se pierde la primera, hay que volver a escribir el secreto de Microsoft Graph y la api key de la IA. Si se pierde la segunda, solo se recupera entrando al contenedor de la base.

### 4.1 Primer ingreso

1. Entra con `superadmin@coacmagisterio.fin.ec` y la `ADMIN_SEED_PASSWORD`, y cambia la contraseña. Es la única cuenta que se siembra: desde **Admin → Usuarios** crea después los administradores, agentes y jefes de área.
2. Vacía `ADMIN_SEED_PASSWORD` en el `.env` y aplica con `docker compose up -d`.
3. En **Admin → Microsoft Graph** configura las credenciales y la cuenta remitente, y activa el correo. Desde ese momento **los correos son reales**.
4. Revisa que en **Admin → Cola de correo** el envío figure como activo.

## 5. Configuración mínima del Nginx del servidor

**No se aplica automáticamente.** Es lo que tu Nginx necesita para llegar a la aplicación: tu Nginx ya es contenedor y está en la red `PROXY_NETWORK`. Añádelo en tu configuración y recárgalo tú (`nginx -t` y después `nginx -s reload`).

```nginx
server {
    listen 443 ssl http2;
    server_name soporte.ejemplo.fin.ec;

    ssl_certificate     /ruta/al/fullchain.pem;
    ssl_certificate_key /ruta/al/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000" always;

    # Igual que el límite de adjuntos de la aplicación (50 MB por petición).
    client_max_body_size 50m;

    location / {
        # El DNS de Docker resuelve el alias del frontend en la red compartida.
        resolver 127.0.0.11 valid=10s;
        set $mesa http://mesa-servicios-frontend:8080;
        proxy_pass $mesa;

        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Connection        "";

        # Eventos en vivo (SSE en /api/events): sin búfer y con conexión larga.
        proxy_buffering    off;
        proxy_read_timeout 1h;
    }
}

server {
    listen 80;
    server_name soporte.ejemplo.fin.ec;
    return 301 https://$host$request_uri;
}
```

- **`X-Forwarded-Proto https`** es lo que hace que los enlaces y la cookie de sesión funcionen en HTTPS.
- **`X-Forwarded-For`**: el frontend toma la IP real del usuario (para los límites de intentos de login) solo si la petición llega desde `REAL_IP_FROM`.
- Con `resolver` y la variable `$mesa`, tu Nginx arranca aunque la aplicación esté parada, y sigue funcionando cuando el contenedor se recrea con otra IP.

> ¿Tu Nginx corre en el propio servidor y no en un contenedor? Entonces no puede usar la red de Docker. En `docker-compose.prod.yml`, añade al servicio `frontend` `ports: ["127.0.0.1:8088:8080"]` y usa `proxy_pass http://127.0.0.1:8088;` en lugar de las líneas `resolver`, `set` y `proxy_pass`. En el `.env`, pon en `REAL_IP_FROM` la red de Docker por la que entra esa conexión (normalmente `172.16.0.0/12`).

## 6. Actualizar a una versión nueva

Las migraciones de base de datos (Flyway) se aplican **solas** al arrancar el backend nuevo. Por eso siempre se hace antes una copia de seguridad.

1. En tu PC: construye y publica la versión nueva (punto 3.2) con otro número, por ejemplo `1.1.1`.
2. En el servidor:

```bash
cd /opt/mesa-servicios
./respaldo.sh                      # 1. copia de seguridad
nano .env                          # 2. VERSION=1.1.1
docker compose pull                # 3. descarga la versión nueva
docker compose up -d               # 4. recrea solo lo que cambió
docker compose ps                  # 5. comprobar
docker compose logs --tail=100 backend
docker image prune                 # 6. (opcional) borra las imágenes viejas
```

Mientras el backend se reinicia (uno o dos minutos), la aplicación devuelve errores al llamar a la API. Para no molestar, actualiza fuera del horario de atención o activa antes el **modo mantenimiento** en *Admin → Páginas del sistema*.

### Volver a la versión anterior

- **La versión nueva no traía migraciones** (archivos `V<n>__….sql` nuevos en `src/main/resources/db/migration`): pon la `VERSION` anterior en el `.env` y ejecuta `docker compose up -d`.
- **Sí traía migraciones:** la base ya tiene el esquema nuevo y el backend antiguo no arranca sobre ella (Flyway lo detecta). Restaura la copia del paso 1 (punto 7.2) y después vuelve a la `VERSION` anterior.

## 7. Copias de seguridad

Hay que guardar dos cosas: la **base de datos** y el volumen **uploads** (adjuntos de los tickets, logo, imágenes).

### 7.1 Hacer una copia

```bash
cd /opt/mesa-servicios
./respaldo.sh                          # deja las copias en ~/respaldos
DIAS=30 DESTINO=/mnt/backups ./respaldo.sh
```

Para hacerlo cada noche a las 02:30, añade esta línea con `crontab -e`:

```
30 2 * * * cd /opt/mesa-servicios && ./respaldo.sh >> ~/respaldos/respaldo.log 2>&1
```

Copia `~/respaldos` **fuera del servidor**, por ejemplo a S3.

### 7.2 Restaurar

```bash
cd /opt/mesa-servicios
docker compose stop backend frontend

docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < ~/respaldos/bd-AAAAMMDD-HHMM.dump

docker run --rm -v mesa-servicios_uploads:/datos -v ~/respaldos:/respaldo alpine \
  sh -c 'rm -rf /datos/* && tar xzf /respaldo/uploads-AAAAMMDD-HHMM.tar.gz -C /datos'

docker compose up -d
```

### 7.3 Traer los datos de una instalación sin Docker

En el servidor antiguo:

```bash
pg_dump -U postgres -d mesa_ayuda_coac -Fc > mesa.dump
tar czf uploads.tar.gz -C /ruta/de/UPLOAD_DIR .
```

En el nuevo, con los contenedores ya creados, restaura como en el punto 7.2. Al arrancar, Flyway aplica las migraciones que falten.

### 7.4 Copiar producción a una base de pruebas

Sirve para reproducir un problema con datos reales sin tocar producción. La copia
tiene **datos personales reales**: trátala como producción y bórrala al terminar.

**1. En el EC2, sacar la copia** (base y archivos subidos):

```bash
mkdir -p ~/copias && cd ~/copias
docker exec mesa-servicios-db-1 sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > mesa-$(date +%F).dump
docker run --rm -v mesa-servicios_uploads:/datos:ro -v ~/copias:/c alpine tar czf /c/uploads-$(date +%F).tar.gz -C /datos .
```

Bájala a tu PC (WinSCP, `scp` o AnyDesk).

**2. En tu PC, restaurar en una base aparte** (no en la de desarrollo de siempre).
Con el PostgreSQL local y Docker Desktop, en PowerShell, en la carpeta donde está el `.dump`:

```powershell
docker run --rm -e PGPASSWORD=TU_CLAVE_LOCAL postgres:16-alpine psql -h host.docker.internal -U postgres -c "CREATE DATABASE mesa_pruebas"
docker run --rm -e PGPASSWORD=TU_CLAVE_LOCAL -v "${PWD}:/d" postgres:16-alpine pg_restore -h host.docker.internal -U postgres -d mesa_pruebas --no-owner --no-privileges /d/mesa-AAAA-MM-DD.dump
```

**3. Antes de arrancar nada, apagar lo que sale hacia fuera** (correo, Microsoft Graph,
telefonía y automatizaciones) con `preparar-copia-pruebas.sql`, en esta misma carpeta:

```powershell
docker run --rm -e PGPASSWORD=TU_CLAVE_LOCAL -v "${PWD}:/d" postgres:16-alpine psql -h host.docker.internal -U postgres -d mesa_pruebas -f /d/preparar-copia-pruebas.sql
```

La comprobación del final debe salir en `false` / `0`. **Sin este paso, la copia
envía correos reales a los usuarios** y procesa la cola de correo pendiente.

**4. Arrancar el backend contra la copia.** En el `.env` del backend pon
`DB_DATABASE=mesa_pruebas` y arranca como siempre (`./gradlew.bat bootRun`). Para ver
fotos y logos, descomprime `uploads-AAAA-MM-DD.tar.gz` en la carpeta `uploads/` del backend.

Tenlo en cuenta al probar:

- Las contraseñas son las de producción.
- Los secretos guardados (Microsoft Graph, IA, clave de la UCM) no se pueden leer con tu
  `SECRET_ENCRYPTION_KEY` local. Es lo esperado: la copia no debe conectarse a nada real.
- Al terminar, vuelve a poner tu base en `DB_DATABASE` y borra la copia y el `.dump`:
  `DROP DATABASE mesa_pruebas;`

## 8. Validar que todo funciona

```bash
docker compose config --quiet && echo "compose OK"
docker compose ps                                     # db, backend y frontend en "healthy"
docker compose exec backend curl -fsS http://localhost:8080/api/actuator/health   # {"status":"UP"}
# Desde la red del proxy, como lo verá tu Nginx:
docker run --rm --network "$(grep ^PROXY_NETWORK .env | cut -d= -f2)" curlimages/curl -fsS \
  http://mesa-servicios-frontend:8080/api/actuator/health
curl -I https://soporte.ejemplo.fin.ec                 # 200, con Content-Security-Policy
```

## 9. Operación diaria

| Para… | Comando (desde `/opt/mesa-servicios`) |
|---|---|
| Ver estado | `docker compose ps` |
| Logs en vivo | `docker compose logs -f backend` (o `frontend`, `db`) |
| Log de la aplicación | `docker compose exec backend tail -n 200 logs/mesa-ayuda.log` |
| Reiniciar el backend | `docker compose restart backend` |
| Aplicar cambios del `.env` | `docker compose up -d` |
| Consola SQL | `docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` |
| Espacio usado | `docker system df` |

> **Nunca** `docker compose down -v`: la `-v` borra los volúmenes, es decir, **la base de datos y todos los adjuntos**.

## 10. Migraciones que se aplican solas

Al arrancar, el backend aplica las que falten, en orden. Desde la 1.0.0:

- **V10** `cola_de_correo`: cola de envío de correos (`mail_outbox`).
- **V11** `activos`: tablas `asset` y `asset_history` (activos de TI: código único, persona, área y sede).
- **V12** `historial_activos`: el historial guarda quién tenía el activo, quién lo recibió y quién hizo el cambio.

Activos: lo gestionan `SUPER_ADMIN`, `ADMIN` y el jefe de área (`AREA_MANAGER`, solo los de su área); eliminar es solo de `SUPER_ADMIN` y `ADMIN`. Cada usuario ve los activos que tiene asignados. Los activos de ejemplo solo se crean con el perfil `dev`, nunca en producción.

## 11. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `Falta … en .env` | No existe `.env` junto al compose, o esa variable está vacía. |
| `pull access denied` / `denied` | Falta `docker login ghcr.io` en el servidor, o el token no tiene `read:packages`. |
| `network proxy declared as external, but could not be found` | `PROXY_NETWORK` no coincide con la red de tu Nginx (punto 2.3). |
| `exec format error` al arrancar | Imagen amd64 en un EC2 ARM (o al revés): punto 3.3. |
| El backend se reinicia en bucle | `docker compose logs backend`. Lo habitual: falta `JWT_SECRET`, o falló una migración (el log dice cuál). |
| 502 en tu Nginx | El frontend aún arranca o no está en `PROXY_NETWORK`. Valida como en el punto 8. |
| Al recargar la página pide login otra vez | No llega `X-Forwarded-Proto: https`, o `CORS_ORIGINS` no coincide con el dominio. |
| Todos los usuarios bloqueados a la vez por "demasiados intentos" | `REAL_IP_FROM` no cubre la red de tu Nginx y todos comparten su IP. |
| Los correos tienen enlaces a localhost | `APP_FRONTEND_BASE_URL` mal puesto. Corrígelo y ejecuta `docker compose up -d`. |
