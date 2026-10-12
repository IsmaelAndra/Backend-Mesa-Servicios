# Mesa de Servicios COAC: instalación y actualización en producción

Despliegue con Docker Compose: **PostgreSQL + backend (Spring Boot) + frontend (Nginx con Angular)** en un solo servidor Linux.

```
navegador ──HTTPS──► proxy con certificado ──HTTP──► frontend (Nginx :HTTP_PORT)
                                                         │  /        → Angular
                                                         └─ /api/…   → backend:8080 ──► db:5432
```

Solo el contenedor `frontend` publica un puerto. El backend y la base de datos no se ven desde fuera.

---

## 1. Requisitos

| Qué | Mínimo |
|---|---|
| Sistema | Linux x86_64 (Ubuntu 22.04/24.04, Debian 12, RHEL 9…) |
| Docker Engine | 24 o superior, con el plugin `docker compose` (v2) |
| Memoria | 4 GB (backend ~1,5 GB, PostgreSQL ~0,5 GB, el resto para el sistema) |
| Disco | 20 GB libres, más lo que ocupen los adjuntos |
| Red | Salida a internet hacia `login.microsoftonline.com` y `graph.microsoft.com` (correo y usuarios). Para compilar, también a Docker Hub, Maven Central y npm |
| HTTPS | Un certificado para el dominio, en un proxy delante. Ver el punto 5 |

Comprobar:

```bash
docker --version
docker compose version
```

## 2. Instalación por primera vez

### 2.1 Clonar los dos repositorios, uno al lado del otro

```bash
sudo mkdir -p /opt/mesa-servicios && sudo chown "$USER" /opt/mesa-servicios
cd /opt/mesa-servicios
git clone <url>/Backend-Mesa-Servicios.git
git clone <url>/Frontend-Mesa-Servicios.git
```

Queda así:

```
/opt/mesa-servicios/
├── Backend-Mesa-Servicios/
│   └── despliegue/          ← docker-compose.yml, .env, esta guía
└── Frontend-Mesa-Servicios/
```

### 2.2 Crear el `.env`

```bash
cd /opt/mesa-servicios/Backend-Mesa-Servicios/despliegue
cp .env.example .env
chmod 600 .env
```

Genera las claves y pégalas en el `.env`:

```bash
echo "DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')"
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')"
echo "GRAPH_ENCRYPTION_KEY=$(openssl rand -base64 48 | tr -d '\n')"
```

Rellena además:

- `CORS_ORIGINS`, `APP_PUBLIC_BASE_URL` y `APP_FRONTEND_BASE_URL` con el dominio real (`https://…`). De `APP_FRONTEND_BASE_URL` salen los enlaces de los correos.
- `ADMIN_SEED_PASSWORD`: contraseña temporal para crear `superadmin@…` y `admin@…`. Las dos cuentas piden cambiarla en el primer login.
- `HTTP_PORT` y `HTTP_BIND`: dónde escucha la aplicación. Ver el punto 5.

> **Guarda `GRAPH_ENCRYPTION_KEY` y `DB_PASSWORD` en un gestor de contraseñas.** Si se pierde `GRAPH_ENCRYPTION_KEY`, hay que volver a escribir el secreto de Microsoft Graph y la api key de la IA. Si se pierde `DB_PASSWORD`, solo se recupera entrando al contenedor de la base.

### 2.3 Construir y arrancar

```bash
docker compose up -d --build
```

La primera vez tarda varios minutos: compila los dos proyectos y Flyway crea todas las tablas. Seguir el arranque:

```bash
docker compose ps                 # los tres deben acabar en "healthy"
docker compose logs -f backend    # Ctrl+C para salir; buscar "Started MesaAyudaApplication"
```

### 2.4 Primer ingreso

1. Abre `https://tu-dominio`, entra con `superadmin@coacmagisterio.fin.ec` y la `ADMIN_SEED_PASSWORD`, y cambia la contraseña.
2. Haz lo mismo con `admin@…`.
3. **Vacía `ADMIN_SEED_PASSWORD` en el `.env`** y aplica el cambio con `docker compose up -d`. Las cuentas ya existen y la variable no vuelve a usarse, pero no conviene dejar una contraseña escrita en el servidor.
4. En **Admin → Microsoft Graph**: credenciales, cuenta remitente (`mail_from_account`) y activar el correo. Desde ese momento **los correos son reales**.
5. Revisa **Admin → Cola de correo**. El estado debe decir que el envío está activo.

## 3. Actualizar a una versión nueva

Las migraciones de base de datos (Flyway) se aplican **solas** al arrancar el backend nuevo. Por eso siempre se hace primero una copia de seguridad.

```bash
cd /opt/mesa-servicios/Backend-Mesa-Servicios/despliegue

# 1. Copia de seguridad (punto 4)
./respaldo.sh   # o los comandos del punto 4.1

# 2. Etiquetar la versión actual, para poder volver atrás
docker tag mesa-servicios-backend:latest  mesa-servicios-backend:anterior
docker tag mesa-servicios-frontend:latest mesa-servicios-frontend:anterior

# 3. Traer el código nuevo de los dos repos
git -C .. pull
git -C ../../Frontend-Mesa-Servicios pull

# 4. Compilar y reemplazar los contenedores
docker compose build
docker compose up -d

# 5. Comprobar
docker compose ps
docker compose logs --tail=100 backend
```

Mientras el backend se reinicia (uno o dos minutos), la página muestra error al llamar a la API. Conviene actualizar fuera del horario de atención, o activar antes el **modo mantenimiento** en *Admin → Páginas del sistema*.

### Volver a la versión anterior

Si la versión nueva **no** trae migraciones nuevas (`V<n>__….sql` en `src/main/resources/db/migration`):

```bash
docker tag mesa-servicios-backend:anterior  mesa-servicios-backend:latest
docker tag mesa-servicios-frontend:anterior mesa-servicios-frontend:latest
docker compose up -d --no-build
```

Si **sí** las trae, la base ya tiene el esquema nuevo y el backend antiguo no arranca sobre ella (Flyway lo detecta). Hay que restaurar la copia del paso 1 (punto 4.2) y después volver a las imágenes `anterior`.

## 4. Copias de seguridad

Hay que guardar dos cosas: la **base de datos** y el volumen **uploads** (adjuntos de tickets, logo, imágenes).

### 4.1 Hacer una copia

```bash
cd /opt/mesa-servicios/Backend-Mesa-Servicios/despliegue
mkdir -p ~/respaldos && F=$(date +%Y%m%d-%H%M)

# Base de datos (formato comprimido de pg_dump)
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ~/respaldos/bd-$F.dump

# Archivos subidos
docker run --rm -v mesa-servicios_uploads:/datos:ro -v ~/respaldos:/respaldo alpine \
  tar czf /respaldo/uploads-$F.tar.gz -C /datos .
```

Para automatizarlo, pon esos comandos en un script con `cron` (por ejemplo cada noche) y copia `~/respaldos` **fuera del servidor**.

### 4.2 Restaurar

```bash
docker compose stop backend frontend

# Base de datos (borra lo que haya y carga la copia)
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < ~/respaldos/bd-AAAAMMDD-HHMM.dump

# Archivos subidos
docker run --rm -v mesa-servicios_uploads:/datos -v ~/respaldos:/respaldo alpine \
  sh -c 'rm -rf /datos/* && tar xzf /respaldo/uploads-AAAAMMDD-HHMM.tar.gz -C /datos'

docker compose up -d
```

### 4.3 Traer los datos de una instalación existente (sin Docker)

En el servidor antiguo:

```bash
pg_dump -U postgres -d mesa_ayuda_coac -Fc > mesa.dump
tar czf uploads.tar.gz -C /ruta/de/UPLOAD_DIR .
```

En el nuevo, con los contenedores creados (`docker compose up -d`), restaura como en el punto 4.2. Al arrancar, Flyway aplica las migraciones que falten.

## 5. HTTPS y el proxy de delante

El contenedor `frontend` habla **HTTP**. El certificado lo pone lo que tenga delante. Dos casos:

**A) Otro proxy en el mismo servidor** (Nginx, Caddy, Apache o Traefik con el certificado). En el `.env`:

```
HTTP_BIND=127.0.0.1
HTTP_PORT=8088
```

Ese proxy reenvía `https://tu-dominio` a `http://127.0.0.1:8088` y manda `X-Forwarded-Proto: https`. Ejemplo con Nginx en el host:

```nginx
server {
    listen 443 ssl http2;
    server_name soporte.ejemplo.fin.ec;
    ssl_certificate     /etc/ssl/…/fullchain.pem;
    ssl_certificate_key /etc/ssl/…/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000" always;
    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_buffering off;          # eventos en vivo (SSE)
        proxy_read_timeout 1h;
    }
}
```

**B) Un balanceador o proxy en otra máquina** (F5, IIS ARR, nube). Deja `HTTP_BIND=0.0.0.0` y **cierra con el firewall** el `HTTP_PORT` para todo el que no sea ese balanceador.

**En los dos casos**, para que los límites de intentos de login vean la IP real del usuario y no la del proxy, edita `Frontend-Mesa-Servicios/nginx.conf`. Descomenta `set_real_ip_from` con la IP del proxy (en el caso A, la del host en la red de Docker, normalmente `172.16.0.0/12`) y reconstruye con `docker compose up -d --build frontend`.

> Sin HTTPS la sesión no se mantiene al recargar: la cookie de refresco es `Secure` (`APP_SECURE_COOKIE=true`). No la pongas en `false` en producción.

## 6. Usar un PostgreSQL que ya existe

Si la base va en un servidor de base de datos aparte:

1. En `docker-compose.yml`, borra el servicio `db`, el bloque `depends_on` del backend y el volumen `pgdata`.
2. En el bloque `environment` del backend, cambia `DB_HOST: db` por el host real (y `DB_PORT` si no es 5432).
3. El usuario de `DB_USERNAME` necesita permiso para crear tablas en esa base. La primera migración ejecuta `CREATE EXTENSION IF NOT EXISTS pg_trgm`: o el usuario puede crearla, o un DBA la crea antes.

## 7. Operación diaria

| Para… | Comando (desde `despliegue/`) |
|---|---|
| Ver estado | `docker compose ps` |
| Logs en vivo | `docker compose logs -f backend` (o `frontend`, `db`) |
| Log de la aplicación | `docker compose exec backend tail -n 200 logs/mesa-ayuda.log` |
| Reiniciar el backend | `docker compose restart backend` |
| Aplicar cambios del `.env` | `docker compose up -d` (recrea lo que cambió) |
| Parar todo | `docker compose stop` |
| Consola SQL | `docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` |
| Espacio usado | `docker system df` |
| Limpiar imágenes viejas | `docker image prune` |

> **Nunca** `docker compose down -v`: la `-v` borra los volúmenes, es decir, **la base de datos y todos los adjuntos**.

## 8. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `Falta DB_PASSWORD en .env` al arrancar | No existe `despliegue/.env` o la variable está vacía |
| El backend se reinicia en bucle | `docker compose logs backend`. Lo habitual: falta `JWT_SECRET`, o una migración falló (el log dice cuál) |
| La página carga pero dice "error de conexión" | El backend aún arranca (espera a `healthy`) o se cayó; ver sus logs |
| Al recargar la página pide login otra vez | Se entra por HTTP en vez de HTTPS (cookie `Secure`), o `CORS_ORIGINS` no coincide con el dominio |
| Los correos tienen enlaces vacíos o a localhost | `APP_FRONTEND_BASE_URL` mal puesto. Corrígelo y `docker compose up -d` |
| No llegan correos | *Admin → Cola de correo*: el estado del envío y el último error de cada correo |
| Los PDF de reportes salen sin texto | Imagen antigua sin fuentes; reconstruir con `docker compose build --no-cache backend` |
| Subir un adjunto da error 413 | Supera los 50 MB por petición (límite de Nginx y de Spring) |
