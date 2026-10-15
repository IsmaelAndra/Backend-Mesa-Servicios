// =============================================================================
// Puente de telefonía de la Mesa de Servicios COAC
// =============================================================================
// Corre en un servidor INTERNO de la cooperativa. Cada pocos segundos pregunta
// a la mesa de servicios (conexión de SALIDA por HTTPS, como un navegador) si
// hay llamadas pendientes, y las ejecuta con la API de la central Grandstream
// UCM, que solo es accesible desde la red interna.
//
//   Mesa (AWS) <── HTTPS de salida ── este puente ──> UCM (red interna)
//
// No abre ningún puerto ni expone la UCM. Solo usa la acción dialExtension:
// suena la extensión del agente y, al contestar, la central marca la del
// solicitante.
//
// Sin dependencias externas: Node.js 20 o superior.
// =============================================================================

import https from 'node:https';
import crypto from 'node:crypto';

const cfg = {
  mesaUrl: requerida('MESA_URL').replace(/\/+$/, ''),        // https://mesa-servicios.coacmagisterio.fin.ec/api
  token: requerida('PUENTE_TOKEN'),                          // el mismo TELEFONIA_PUENTE_TOKEN del backend
  ucmUrl: requerida('UCM_URL').replace(/\/+$/, ''),          // https://192.168.1.235:8089/api
  ucmUsuario: requerida('UCM_USUARIO'),                      // usuario creado en la UCM → API Configuration
  ucmClave: requerida('UCM_CLAVE'),
  intervaloMs: Number(process.env.INTERVALO_MS || 2000),
};

if (cfg.token.length < 32) {
  salir('PUENTE_TOKEN debe tener al menos 32 caracteres (igual que TELEFONIA_PUENTE_TOKEN del backend).');
}

let cookieUcm = null;
let activo = true;
let fallosSeguidos = 0;

// ----------------------------------------------------------------------------- utilidades

function requerida(nombre) {
  const v = (process.env[nombre] || '').trim();
  if (!v) salir(`Falta la variable ${nombre} (ver .env.example).`);
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

// ----------------------------------------------------------------------------- API de la UCM

/**
 * POST JSON a la UCM. Su certificado es autofirmado, así que se acepta SOLO para
 * esta conexión (red interna); las llamadas a la mesa validan el certificado.
 */
function pedirUcm(request) {
  const cuerpo = JSON.stringify({ request });
  const url = new URL(cfg.ucmUrl);
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
    req.on('error', mal);
    req.end(cuerpo);
  });
}

async function iniciarSesionUcm() {
  const reto = await pedirUcm({ action: 'challenge', user: cfg.ucmUsuario, version: '1.0' });
  const challenge = reto?.response?.challenge;
  if (!challenge) throw new Error(`La UCM no devolvió el reto (status ${reto?.status})`);
  const token = crypto.createHash('md5').update(challenge + cfg.ucmClave).digest('hex');
  const login = await pedirUcm({ action: 'login', user: cfg.ucmUsuario, token });
  if (login?.status !== 0 || !login?.response?.cookie) {
    throw new Error(`La UCM rechazó el inicio de sesión (status ${login?.status})`);
  }
  cookieUcm = login.response.cookie;
  log('Sesión iniciada en la UCM');
}

/** Ordena la llamada; si la sesión caducó, inicia otra y reintenta una vez. */
async function marcar(origen, destino) {
  for (let intento = 0; intento < 2; intento++) {
    if (!cookieUcm) await iniciarSesionUcm();
    const r = await pedirUcm({ action: 'dialExtension', cookie: cookieUcm, caller: origen, callee: destino });
    if (r?.status === 0) return;
    // -6: cookie caducada o inválida → nueva sesión y otro intento.
    if (r?.status === -6 && intento === 0) {
      cookieUcm = null;
      continue;
    }
    throw new Error(`La UCM respondió status ${r?.status}`);
  }
}

// ----------------------------------------------------------------------------- API de la mesa

async function pedirMesa(ruta, opciones = {}) {
  const res = await fetch(`${cfg.mesaUrl}${ruta}`, {
    ...opciones,
    headers: { 'X-Puente-Token': cfg.token, 'Content-Type': 'application/json', ...(opciones.headers || {}) },
    signal: AbortSignal.timeout(15_000),
  });
  if (res.status === 401 || res.status === 403) {
    throw new Error('La mesa rechazó el token del puente (revisa PUENTE_TOKEN y TELEFONIA_PUENTE_TOKEN)');
  }
  if (!res.ok) throw new Error(`La mesa respondió HTTP ${res.status}`);
  return res.status === 204 ? null : res.json();
}

async function informar(id, ok, error) {
  await pedirMesa(`/telefonia/puente/llamadas/${id}/resultado`, {
    method: 'POST',
    body: JSON.stringify({ ok, error: error || null }),
  });
}

// ----------------------------------------------------------------------------- bucle

async function vuelta() {
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
  log(`Iniciado. Mesa: ${cfg.mesaUrl} | UCM: ${cfg.ucmUrl} | usuario UCM: ${cfg.ucmUsuario}`);
  try {
    await iniciarSesionUcm();
  } catch (e) {
    log(`Aviso: no se pudo iniciar sesión en la UCM todavía (${e.message}). Se reintentará con la primera llamada.`);
  }
  while (activo) {
    try {
      await vuelta();
      if (fallosSeguidos > 0) log('Conexión recuperada');
      fallosSeguidos = 0;
      await esperar(cfg.intervaloMs);
    } catch (e) {
      fallosSeguidos++;
      // Espera creciente para no insistir contra un servidor caído (máximo 1 min).
      const pausa = Math.min(60_000, cfg.intervaloMs * 2 ** Math.min(fallosSeguidos, 5));
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
