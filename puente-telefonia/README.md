# Puente de telefonía (Grandstream UCM)

Permite que el botón **Llamar a la ext.** de la mesa de servicios llame de la
extensión del agente a la del solicitante a través de la central **Grandstream UCM**.

```
Agente pulsa "Llamar a la ext. 181"
        │
        ▼
Mesa de servicios (AWS) ── deja la orden
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
  conexiones de salida.
- Solo usa la acción `dialExtension` de la API de la UCM.
- **Todo se configura en la mesa: Administración → Telefonía** (URL, usuario y clave
  de la API de la UCM, activar/desactivar, token del puente, estado y últimas
  llamadas). El puente descarga esa configuración; los cambios se aplican solos.
- En el servidor interno solo hay dos datos: la URL de la mesa y el token del puente.
- **Sin Docker**: Node.js 20 o superior, como tarea de Windows o servicio de Linux.

## 1. Preparar la UCM

En la UCM (*Integrations → API Configuration*):

1. Activar **Enable API**.
2. Crear un usuario y una contraseña propios para la API (no el administrador).
3. En **Permitted IP(s)**, poner **solo la IP del servidor interno** donde correrá el puente.
4. En **Control de llamada**, marcar **solo `dialExtension`**. Todo lo demás sin marcar.
5. Guardar y **Apply Changes**.

## 2. Configurar en la mesa (Administración → Telefonía)

1. Escribir la **URL de la API** (`https://192.168.1.235:8089/api`), el **usuario** y la **clave**.
2. **Generar token del puente** y copiarlo: se muestra una sola vez.
3. **Activar** la telefonía y guardar.

## 3. Instalar el puente en el servidor interno

Copiar esta carpeta al servidor (por ejemplo `C:\MesaPuenteTelefonia`) y crear el
archivo `.env` a partir de `.env.example`:

```
MESA_URL=https://mesa-servicios.coacmagisterio.fin.ec/api
PUENTE_TOKEN=<el token generado en Administración>
```

### Windows

1. Instalar **Node.js 20 LTS** o superior (https://nodejs.org).
2. Abrir **PowerShell como Administrador** en la carpeta y ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\instalar-windows.ps1
```

Crea la tarea **"Mesa - Puente de telefonia"**: arranca con Windows aunque nadie
inicie sesión, se reinicia sola si se cae y escribe en `puente.log`.
Para quitarla: `.\desinstalar-windows.ps1`.

### Linux

Seguir las instrucciones del encabezado de `mesa-puente-telefonia.service`
(servicio systemd con un usuario propio sin privilegios).

### Prueba manual (sin instalar)

```bash
node index.mjs
```

En unos segundos, **Administración → Telefonía** debe mostrar el puente **En línea**
y la UCM **Conectada**.

## 4. Asignar extensiones

En la mesa: **Administración → Usuarios → Editar**, campo **Extensión telefónica**.
El botón **Llamar a la ext.** de la ficha del ticket aparece cuando la telefonía
está activa y el solicitante tiene extensión.

## Problemas frecuentes

Administración → Telefonía muestra el estado del puente y el último error con la UCM.

| Mensaje | Causa |
|---|---|
| Puente **Desconectado** | La tarea o el servicio no está corriendo, o el servidor no sale a internet |
| `La mesa rechazó el token del puente` (en `puente.log`) | El token del `.env` no es el último generado en Administración |
| `La UCM rechazó el inicio de sesión` | Usuario o clave de la API incorrectos, o la IP del servidor no está en *Permitted IP(s)* |
| `No se pudo conectar con la UCM` | El servidor interno no llega a la URL de la UCM (IP o puerto) |
| `La UCM respondió status ...` al llamar | Falta el permiso `dialExtension` o la extensión no existe |
| En la ficha: "El puente de telefonía no la recogió a tiempo" | El puente está detenido |
