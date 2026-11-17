// H51 - Auditoría de accesibilidad con Lighthouse en las pantallas principales de la web,
// en modo claro y oscuro, con y sin sesión. Segunda opinión frente a axe (src/qa/accesibilidad.js).
//
// Requisitos: web en http://localhost:5173, API Spring en :8081 y Lighthouse instalado aparte
// (no es dependencia del proyecto). Las credenciales se leen del entorno; usar una cuenta de seed.sql:
//   LH_DIR=<carpeta con node_modules/lighthouse> LH_CORREO=... LH_PASSWORD=... node scripts/lighthouse-h51.mjs
// Escribe docs/pruebas/sprint3-evidencias/lighthouse-h51.json (sin token ni datos de la cuenta).
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LH_DIR = process.env.LH_DIR;
if (!LH_DIR) throw new Error('Define LH_DIR con la carpeta donde se instaló lighthouse');
const requerir = createRequire(join(LH_DIR, 'package.json'));
const cargar = async (m) => import(pathToFileURL(requerir.resolve(m)).href);
const lighthouse = (await cargar('lighthouse')).default;
const chromeLauncher = await cargar('chrome-launcher');
const puppeteer = (await cargar('puppeteer-core')).default;

const WEB = process.env.LH_WEB || 'http://localhost:5173';
const API = process.env.LH_API || 'http://localhost:8081';
const CHROME = process.env.LH_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PUBLICAS = ['/', '/login', '/registro', '/explorar', '/buscar', '/privacidad', '/recuperar-password'];
const CON_SESION = ['/perfil', '/notificaciones', '/publicar', '/mis-publicaciones', '/objeto/1'];

async function iniciarSesion() {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ correo: process.env.LH_CORREO, password: process.env.LH_PASSWORD }),
  });
  if (!res.ok) throw new Error(`No se pudo iniciar sesión (${res.status})`);
  return res.json();
}

const chrome = await chromeLauncher.launch({ chromePath: CHROME, chromeFlags: ['--headless=new', '--no-first-run'] });
const navegador = await puppeteer.connect({ browserURL: `http://127.0.0.1:${chrome.port}` });
const pagina = await navegador.newPage();
await pagina.goto(WEB, { waitUntil: 'networkidle0' });

const sesion = await iniciarSesion();
const filas = [];
try {
  for (const tema of ['claro', 'oscuro']) {
    for (const ruta of [...PUBLICAS, ...CON_SESION]) {
      const conSesion = CON_SESION.includes(ruta);
      await pagina.goto(WEB, { waitUntil: 'networkidle0' });
      await pagina.evaluate((t, s) => {
        localStorage.setItem('foundia-tema', t);
        if (s) { localStorage.setItem('token', s.token); localStorage.setItem('usuario', JSON.stringify(s.usuario)); }
        else { localStorage.removeItem('token'); localStorage.removeItem('usuario'); }
      }, tema, conSesion ? sesion : null);
      const { lhr } = await lighthouse(`${WEB}${ruta}`, {
        port: chrome.port, output: 'json', logLevel: 'error',
        onlyCategories: ['accessibility'], disableStorageReset: true,
      });
      const fallas = Object.values(lhr.audits)
        .filter((a) => a.score !== null && a.score < 1 && a.scoreDisplayMode === 'binary')
        .map((a) => ({
          id: a.id, titulo: a.title,
          nodos: (a.details?.items || []).map((it) => it.node?.selector || it.node?.snippet || '').slice(0, 8),
        }));
      const manuales = Object.values(lhr.audits).filter((a) => a.scoreDisplayMode === 'manual').length;
      const fila = { ruta, tema, conSesion, puntaje: Math.round(lhr.categories.accessibility.score * 100), fallas, manuales };
      filas.push(fila);
      console.log(`${tema.padEnd(6)} ${ruta.padEnd(20)} ${String(fila.puntaje).padStart(3)}  ${fallas.map((f) => `${f.id}(${f.nodos.length})`).join(' ')}`);
    }
  }
} finally {
  await navegador.disconnect();
  await chrome.kill();
}

const informe = {
  fecha: new Date().toISOString(),
  lighthouse: JSON.parse(readFileSync(join(LH_DIR, 'node_modules/lighthouse/package.json'), 'utf8')).version,
  dispositivo: 'móvil emulado (configuración por defecto de Lighthouse)', web: WEB, filas,
};
const salida = resolve(raiz, process.env.LH_SALIDA || 'docs/pruebas/sprint3-evidencias/lighthouse-h51.json');
writeFileSync(salida, `${JSON.stringify(informe, null, 2)}\n`);
console.log('Evidencia:', salida);
