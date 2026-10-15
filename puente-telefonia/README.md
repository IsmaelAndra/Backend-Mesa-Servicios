# Puente de telefonía (Grandstream UCM)

Permite que el botón **Llamar** de la mesa de servicios llame de la extensión del
agente a la del solicitante a través de la central **Grandstream UCM**.

```
Agente pulsa "Llamar"
        │
        ▼
Mesa de servicios (AWS) ── deja la orden en la tabla "llamada"
        ▲
        │  HTTPS de SALIDA cada 2 s (como un navegador)
        │
Puente (servidor INTERNO) ──► API de la UCM (red interna, :8089)
                                   │
                                   ▼
                  Suena la extensión del agente → al contestar,
                  la central marca la extensión del solicitante
```

- **No se abre ningún puerto**, ni en el router ni en la UCM. El puente solo hace
  conexiones de salida: a la mesa (HTTPS) y a la UCM (red interna).
- Solo usa la acción `dialExtension` de la API de la UCM.
- La contraseña de la API de la UCM se queda en el servidor interno; nunca viaja a la mesa.
- El puente se identifica ante la mesa con un token propio (`PUENTE_TOKEN`).

## 1. Preparar la UCM

En la UCM (*Integrations → API Configuration*):

1. Activar **Enable API**.
2. Crear un usuario y una contraseña propios para la API (no el administrador).
3. En **Permitted IP(s)**, poner **solo la IP del servidor interno** donde correrá el puente.
4. Permisos de **Control de llamada**: marcar **solo `dialExtension`** (y opcionalmente `Hangup`).
   Todo lo demás (extensiones, archivos, CDR, `callbarge`…) sin marcar.
5. Guardar y **Apply Changes**.

## 2. Generar el token compartido

En cualquier equipo con OpenSSL (o Git Bash):

```bash
openssl rand -hex 32
```

El mismo valor va en dos sitios:

- En el servidor de la mesa (EC2), en `/opt/coac-ti/mesa-servicios/despliegue/.env`:
  `TELEFONIA_PUENTE_TOKEN=<token>` y reiniciar el backend
  (`docker compose -f docker-compose.prod.yml up -d backend`).
- En el `.env` del puente: `PUENTE_TOKEN=<token>`.

Sin `TELEFONIA_PUENTE_TOKEN` (o con menos de 32 caracteres) la telefonía queda
apagada y el botón Llamar no aparece.

## 3. Instalar el puente en el servidor interno

Copiar esta carpeta (`puente-telefonia/`) al servidor interno y crear el `.env`:

```bash
cp .env.example .env
# editar .env: PUENTE_TOKEN, UCM_URL, UCM_USUARIO, UCM_CLAVE
```

### Con Docker (Linux o Windows con Docker)

```bash
docker compose up -d --build
docker compose logs -f
```

### Sin Docker (Node.js 20 o superior)

```bash
node --env-file=.env index.mjs
```

En Windows se puede dejar como servicio con NSSM o el Programador de tareas
("Al iniciar el sistema", ejecutar `node --env-file=.env index.mjs` en esta carpeta).

En el log debe salir:

```
[puente] Iniciado. Mesa: https://... | UCM: https://192.168.1.235:8089/api | usuario UCM: ...
[puente] Sesión iniciada en la UCM
```

## 4. Asignar extensiones

En la mesa: **Administración → Usuarios → Editar**, campo **Extensión** (solo números).
El botón **Llamar a la ext.** de la ficha del ticket aparece cuando el agente y el
solicitante tienen extensión y el puente está configurado.

## Problemas frecuentes

| Mensaje en el log | Causa |
|---|---|
| `La mesa rechazó el token del puente` | `PUENTE_TOKEN` distinto de `TELEFONIA_PUENTE_TOKEN`, o el backend no se reinició |
| `La UCM rechazó el inicio de sesión (status -37)` o similar | Usuario/clave de la API incorrectos, o la IP del servidor no está en *Permitted IP(s)* |
| `La UCM respondió status ...` al llamar | Falta el permiso `dialExtension` o la extensión no existe |
| `La UCM no respondió a tiempo` | El servidor interno no llega a la UCM (IP/puerto) |
| En la mesa: "El puente de telefonía no la recogió a tiempo" | El puente está detenido o sin conexión a internet |
