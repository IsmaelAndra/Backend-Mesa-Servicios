// =============================================================================
// Puente de telefonía de la Mesa de Servicios COAC
// =============================================================================
// Corre en un servidor INTERNO de la cooperativa. Cada 2 segundos pregunta a
// la mesa de servicios (conexión de SALIDA por HTTPS, como un navegador) si hay
// llamadas pendientes, y las ejecuta con la API de la central Grandstream UCM,
// que solo es accesible desde la red interna.
//
//   Mesa (AWS) <── HTTPS de salida ── este puente ──> UCM (red interna)
//
// No abre ningún puerto ni expone la UCM. Solo usa la acción dialExtension.
//
// Configuración: solo MESA_URL y PUENTE_TOKEN (archivo .env junto a este).
// La URL, el usuario y la clave de la API de la UCM se configuran en la mesa
// (Administración → Telefonía) y el puente los descarga; los cambios se aplican
// solos, sin reiniciar.
//
// Sin dependencias externas: Node.js 20 o superior.
// =============================================================================

import https from 'node:https';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const VERSION = '1.2.0';
const CARPETA = path.dirname(fileURLToPath(import.meta.url));

cargarEnv(path.join(CARPETA, '.env'));

const mesaUrl = requerida('MESA_URL').replace(/\/+$/, '');   // https://mesa-servicios.coacmagisterio.fin.ec/api
const token = requerida('PUENTE_TOKEN');                      // generado en Administración → Telefonía
if (token.length < 32) salir('PUENTE_TOKEN no es válido: genéralo en Administración → Telefonía.');

/** Configuración de la UCM descargada de la mesa. */
let ucm = { enabled: false, ucmUrl: null, ucmUsuario: null, ucmClave: null, intervaloMs: 2000 };
let firmaConfig = '';
let configEn = 0;
let cookieUcm = null;
/** Último uso correcto de la sesión con la UCM (ms). Una sesión parada caduca sola. */
let ultimoUsoUcm = 0;
/** Sin uso durante este tiempo se abre una sesión nueva antes de marcar. */
const SESION_UCM_MAX_INACTIVA_MS = 4 * 60 * 1000;
/**
 * Códigos de la UCM que indican una sesión vieja o un fallo pasajero: se abre
 * otra sesión y se reintenta una vez. -8 (tiempo agotado) salía en la primera
 * llamada tras un rato sin usar la central; las siguientes ya funcionaban.
 */
const STATUS_REINTENTABLES = new Set([-5, -6, -7, -8]);
/** Significado de los códigos más habituales de la API de la UCM. */
const STATUS_UCM = {
  '-1': 'parámetros no válidos',
  '-5': 'la sesión requiere autenticación',
  '-6': 'la sesión caducó',
  '-7': 'la central cerró la conexión',
  '-8': 'la central no respondió a tiempo',
  '-9': 'error interno de la central',
  '-15': 'valor no válido (¿existe la extensión?)',
  '-16': 'la extensión no existe',
  '-37': 'usuario o clave de la API incorrectos',
  '-47': 'el usuario de la API no tiene permiso',
  '-68': 'inicio de sesión restringido (IP no permitida)',
  '-69': 'inicio de sesión bloqueado por intentos fallidos',
};
function textoStatus(status) {
  const t = STATUS_UCM[String(status)];
  return t ? `status ${status}: ${t}` : `status ${status}`;
}
/** "ok" o el último error con la UCM; viaja a la mesa en la cabecera X-Puente-Ucm. */
let estadoUcm = 'sin probar';
let activo = true;
let fallosSeguidos = 0;

// ----------------------------------------------------------------------------- utilidades

/** Lee un .env sencillo (CLAVE=valor). No pisa variables ya definidas en el sistema. */
function cargarEnv(archivo) {
  if (!fs.existsSync(archivo)) return;
  for (const linea of fs.readFileSync(archivo, 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (!m || linea.trim().startsWith('#')) continue;
    if (process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
}

function requerida(nombre) {
  const v = (process.env[nombre] || '').trim();
  if (!v) salir(`Falta ${nombre} en el archivo .env (ver .env.example).`);
  return v;
}

function salir(mensaje) {
  console.error(`[puente] ${mensaje}`);
  process.exit(1);
}

function log(...partes) {
  console.log(new Date().toISOString(), '[puente]', ...partes);
}

const esperar = (ms) => new Promise((ok) => setTimeout(ok, ms));

// ----------------------------------------------------------------------------- API de la mesa

async function pedirMesa(ruta, opciones = {}) {
  const res = await fetch(`${mesaUrl}${ruta}`, {
    ...opciones,
    headers: {
      'X-Puente-Token': token,
      'X-Puente-Version': VERSION,
      'X-Puente-Ucm': String(estadoUcm).slice(0, 300),
      'Content-Type': 'application/json',
      ...(opciones.headers || {}),
    },
    signal: AbortSignal.timeout(15_000),
  });
  if (res.status === 401 || res.status === 403) {
    throw new Error('La mesa rechazó el token del puente (genera uno nuevo en Administración → Telefonía y ponlo en .env)');
  }
  if (!res.ok) throw new Error(`La mesa respondió HTTP ${res.status}`);
  return res.status === 204 ? null : res.json();
}

/** Descarga la configuración de la UCM cada 30 s; si cambió, cierra la sesión anterior. */
async function actualizarConfig(forzar = false) {
  if (!forzar && Date.now() - configEn < 30_000) return;
  const c = await pedirMesa('/telefonia/puente/config');
  configEn = Date.now();
  const firma = JSON.stringify([c.enabled, c.ucmUrl, c.ucmUsuario, c.ucmClave]);
  if (firma !== firmaConfig) {
    firmaConfig = firma;
    ucm = { ...c, intervaloMs: c.intervaloMs || 2000 };
    cookieUcm = null;
    log(`Configuración recibida: ${c.enabled ? 'activa' : 'DESACTIVADA'} | UCM ${c.ucmUrl || '(sin URL)'} | usuario ${c.ucmUsuario || '(sin usuario)'}`);
    if (c.enabled) {
      await iniciarSesionUcm().catch((e) => log(`No se pudo iniciar sesión en la UCM: ${e.message}`));
    }
  }
}

async function informar(id, ok, error) {
  await pedirMesa(`/telefonia/puente/llamadas/${id}/resultado`, {
    method: 'POST',
    body: JSON.stringify({ ok, error: error || null }),
  });
}

// ----------------------------------------------------------------------------- API de la UCM

/**
 * POST JSON a la UCM. Su certificado es autofirmado, así que se acepta SOLO para
 * esta conexión (red interna); las llamadas a la mesa validan el certificado.
 */
function pedirUcm(request) {
  if (!ucm.ucmUrl) return Promise.reject(new Error('Falta la URL de la UCM en Administración → Telefonía'));
  const cuerpo = JSON.stringify({ request });
  const url = new URL(ucm.ucmUrl);
  return new Promise((ok, mal) => {
    const req = https.request(
      {
        hostname: url.hostname,
        port: url.port || 443,
        path: url.pathname,
        method: 'POST',
        rejectUnauthorized: false,
        timeout: 10_000,
        headers: { 'Content-Type': 'application/json;charset=UTF-8', 'Content-Length': Buffer.byteLength(cuerpo) },
      },
      (res) => {
        let datos = '';
        res.setEncoding('utf8');
        res.on('data', (c) => (datos += c));
        res.on('end', () => {
          try {
            ok(JSON.parse(datos));
          } catch {
            mal(new Error(`Respuesta no válida de la UCM (HTTP ${res.statusCode})`));
          }
        });
      },
    );
    req.on('timeout', () => req.destroy(new Error('La UCM no respondió a tiempo')));
    req.on('error', (e) => mal(new Error(`No se pudo conectar con la UCM: ${e.message}`)));
    req.end(cuerpo);
  });
}

async function iniciarSesionUcm() {
  try {
    const reto = await pedirUcm({ action: 'challenge', user: ucm.ucmUsuario, version: '1.0' });
    const challenge = reto?.response?.challenge;
    if (!challenge) throw new Error(`La UCM no devolvió el reto (status ${reto?.status})`);
    const md5 = crypto.createHash('md5').update(challenge + (ucm.ucmClave || '')).digest('hex');
    const login = await pedirUcm({ action: 'login', user: ucm.ucmUsuario, token: md5 });
    if (login?.status !== 0 || !login?.response?.cookie) {
      throw new Error(`La UCM rechazó el inicio de sesión (status ${login?.status}): revisa usuario, clave e IP permitida`);
    }
    cookieUcm = login.response.cookie;
    ultimoUsoUcm = Date.now();
    estadoUcm = 'ok';
    log('Sesión iniciada en la UCM');
  } catch (e) {
    cookieUcm = null;
    estadoUcm = e.message;
    throw e;
  }
}

/** Ordena la llamada; si la sesión caducó o la central tardó, inicia otra y reintenta una vez. */
async function marcar(origen, destino) {
  // Una sesión que lleva un rato sin usarse se renueva antes: la central la
  // descarta por su cuenta y la primera orden fallaba.
  if (cookieUcm && Date.now() - ultimoUsoUcm > SESION_UCM_MAX_INACTIVA_MS) {
    cookieUcm = null;
  }
  for (let intento = 0; intento < 2; intento++) {
    if (!cookieUcm) await iniciarSesionUcm();
    const r = await pedirUcm({ action: 'dialExtension', cookie: cookieUcm, caller: origen, callee: destino });
    if (r?.status === 0) {
      ultimoUsoUcm = Date.now();
      estadoUcm = 'ok';
      return;
    }
    if (STATUS_REINTENTABLES.has(r?.status) && intento === 0) {
      log(`La UCM respondió ${textoStatus(r?.status)}; nueva sesión y otro intento`);
      cookieUcm = null;
      continue;
    }
    throw new Error(`La UCM respondió ${textoStatus(r?.status)}`);
  }
}

// ----------------------------------------------------------------------------- bucle

async function vuelta() {
  await actualizarConfig();
  const pendientes = await pedirMesa('/telefonia/puente/pendientes');
  for (const ll of pendientes || []) {
    try {
      await marcar(ll.origen, ll.destino);
      log(`Llamada ${ll.id.slice(0, 8)}: ext. ${ll.origen} -> ext. ${ll.destino} ordenada`);
      await informar(ll.id, true);
    } catch (e) {
      log(`Llamada ${ll.id.slice(0, 8)} falló: ${e.message}`);
      await informar(ll.id, false, e.message).catch(() => undefined);
    }
  }
}

async function principal() {
  log(`Puente de telefonía ${VERSION}. Mesa: ${mesaUrl}`);
  while (activo) {
    try {
      await vuelta();
      if (fallosSeguidos > 0) log('Conexión con la mesa recuperada');
      fallosSeguidos = 0;
      await esperar(ucm.intervaloMs || 2000);
    } catch (e) {
      fallosSeguidos++;
      // Espera creciente para no insistir contra un servidor caído (máximo 1 min).
      const pausa = Math.min(60_000, 2000 * 2 ** Math.min(fallosSeguidos, 5));
      if (fallosSeguidos <= 3 || fallosSeguidos % 10 === 0) log(`Error: ${e.message}. Reintento en ${pausa / 1000} s`);
      await esperar(pausa);
    }
  }
}

for (const senal of ['SIGINT', 'SIGTERM']) {
  process.on(senal, async () => {
    activo = false;
    if (cookieUcm) await pedirUcm({ action: 'logout', cookie: cookieUcm }).catch(() => undefined);
    log('Detenido');
    process.exit(0);
  });
}

principal();
