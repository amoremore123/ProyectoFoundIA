// H45.5 - Mide las 20 frases de prueba contra la API real del asistente y contra el
// motor local de la web, con el mismo resultado esperado.
// Uso (con la API Spring levantada):  node scripts/medir-frases-h45.mjs [--api http://localhost:8081]
// Escribe docs/pruebas/sprint3-evidencias/h45-frases.json. No usa sesión ni envía datos personales.
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CATEGORIAS_SEED, FRASES_H45, calificar, resumir } from '../frontend/usuario/src/qa/frasesAsistente.js';
import { interpretar } from '../frontend/usuario/src/utils/asistente.js';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const i = process.argv.indexOf('--api');
const API = (i > 0 ? process.argv[i + 1] : 'http://localhost:8081').replace(/\/+$/, '');
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

async function consultar(frase) {
  for (let intento = 0; intento < 3; intento += 1) {
    const inicio = Date.now();
    const res = await fetch(`${API}/api/asistente/interpretar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mensaje: frase }),
    });
    if (res.status === 429) { await esperar(Number(res.headers.get('retry-after') || 30) * 1000); continue; }
    if (!res.ok) return { status: res.status, ms: Date.now() - inicio, cuerpo: null };
    return { status: res.status, ms: Date.now() - inicio, cuerpo: await res.json() };
  }
  return { status: 429, ms: 0, cuerpo: null };
}

const filas = [];
for (const f of FRASES_H45) {
  const web = interpretar(f.frase, CATEGORIAS_SEED);
  const api = await consultar(f.frase);
  const cWeb = calificar(web, f.esperado);
  const cApi = calificar(api.cuerpo, f.esperado);
  filas.push({
    n: f.n, frase: f.frase, esperado: f.esperado,
    web: { intencion: web.intencion, tipoBuscado: web.tipoBuscado, categoria: web.categoria?.nombre ?? null, ubicacion: web.ubicacion, ...cWeb },
    api: {
      status: api.status, ms: api.ms, fuente: api.cuerpo?.fuente ?? null,
      intencion: api.cuerpo?.intencion ?? null, tipoBuscado: api.cuerpo?.tipoBuscado ?? null,
      categoria: api.cuerpo?.categoria?.nombre ?? null, ubicacion: api.cuerpo?.ubicacion ?? null, ...cApi,
    },
  });
  const marca = (c) => (c.acierto ? 'OK' : `NO (${c.fallos.join(', ')})`);
  console.log(`${String(f.n).padStart(2)} web ${marca(cWeb).padEnd(24)} api[${api.cuerpo?.fuente ?? api.status}] ${marca(cApi)}  ${f.frase}`);
  await esperar(3200); // respeta el límite de 20 consultas por minuto del asistente
}

const informe = {
  fecha: new Date().toISOString(),
  api: API,
  fuentes: [...new Set(filas.map((f) => f.api.fuente))],
  resumen: { web: resumir(filas.map((f) => f.web)), api: resumir(filas.map((f) => f.api)) },
  filas,
};
const salida = resolve(raiz, 'docs/pruebas/sprint3-evidencias/h45-frases.json');
writeFileSync(salida, `${JSON.stringify(informe, null, 2)}\n`);
console.log('\nResumen', JSON.stringify(informe.resumen), 'fuentes:', informe.fuentes.join(', '));
console.log('Evidencia:', salida);
