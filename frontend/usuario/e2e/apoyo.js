// Apoyo de las pruebas de punta a punta (C2). Habla con los servicios reales: Spring, Django y Mailpit.
// Nada de esto se simula: si un servicio no responde, la prueba falla y lo dice.
import { randomBytes } from 'node:crypto';

export const API = process.env.E2E_API || 'http://localhost:8081';
export const ADMIN_API = process.env.E2E_ADMIN_API || 'http://localhost:8000';
export const MAILPIT = process.env.E2E_MAILPIT || 'http://localhost:8025';
export const PANEL = process.env.E2E_PANEL || 'http://localhost:5174';

// Cuenta de administrador de database/seed.sql (documentada solo para desarrollo).
// Se puede cambiar con E2E_ADMIN_CORREO y E2E_ADMIN_PASSWORD.
export const ADMIN = {
  correo: process.env.E2E_ADMIN_CORREO || 'admin@foundia.dev',
  password: process.env.E2E_ADMIN_PASSWORD || 'Admin123!',
};

// Cada ejecución usa datos nuevos: un sufijo al azar evita choques con ejecuciones anteriores
export function sufijo() {
  return randomBytes(4).toString('hex');
}

// Letras para nombres (la API solo acepta letras en nombre y apellido)
export function letras(n = 6) {
  const abc = 'abcdefghijklmnopqrstuvwxyz';
  return Array.from(randomBytes(n), (b) => abc[b % abc.length]).join('');
}

// Contraseña de prueba generada en cada ejecución; cumple las reglas de H11 y no se guarda
export function passwordPrueba() {
  return `E2e-${randomBytes(6).toString('base64url')}9a!`;
}

export function hoyLima() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
}

async function json(respuesta, contexto) {
  const texto = await respuesta.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { datos = texto; }
  if (!respuesta.ok) throw new Error(`${contexto}: ${respuesta.status} ${typeof datos === 'string' ? datos : JSON.stringify(datos)}`);
  return datos;
}

export async function versionLegal() {
  const datos = await json(await fetch(`${API}/api/legal/documentos`), 'Documentos legales');
  return datos.version;
}

// Espera el correo de verificación en Mailpit y devuelve el código de 6 dígitos
export async function codigoDeVerificacion(correo, { intentos = 30 } = {}) {
  for (let i = 0; i < intentos; i += 1) {
    const busqueda = await json(await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${correo}"`)}`), 'Mailpit');
    const mensaje = busqueda?.messages?.[0];
    if (mensaje) {
      const detalle = await json(await fetch(`${MAILPIT}/api/v1/message/${mensaje.ID}`), 'Mailpit');
      const codigo = /\b(\d{6})\b/.exec(`${detalle.Text || ''} ${detalle.HTML || ''}`)?.[1];
      if (codigo) return codigo;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`No llegó el código de verificación para ${correo} a Mailpit`);
}

// Crea y verifica una cuenta por la API (para preparar datos; el registro por pantalla se prueba aparte)
export async function crearCuenta(prefijo = 'usuario') {
  const correo = `e2e-${prefijo}-${sufijo()}@prueba.invalid`;
  const password = passwordPrueba();
  const nombre = `Prueba${letras(4)}`;
  await json(await fetch(`${API}/api/auth/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nombre, apellido: 'Eedos', correo, password, aceptaTerminos: true, versionLegal: await versionLegal() }),
  }), 'Registro');
  const codigo = await codigoDeVerificacion(correo);
  await json(await fetch(`${API}/api/auth/verificar`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correo, codigo }),
  }), 'Verificación');
  const sesion = await json(await fetch(`${API}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correo, password }),
  }), 'Inicio de sesión');
  return { correo, password, nombre, token: sesion.token, usuario: sesion.usuario };
}

export async function categoriaActiva() {
  const lista = await json(await fetch(`${API}/api/categorias`), 'Categorías');
  const categorias = Array.isArray(lista) ? lista : lista?.content || lista?.results || [];
  const activa = categorias.find((c) => c.estado !== false && !/QA/i.test(c.nombre)) || categorias[0];
  if (!activa) throw new Error('No hay categorías activas para publicar');
  return activa;
}

export async function publicar(cuenta, datos) {
  return json(await fetch(`${API}/api/objetos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${cuenta.token}` },
    body: JSON.stringify({ fechaObjeto: hoyLima(), ubicacion: 'Biblioteca central', ...datos }),
  }), 'Publicar');
}

export async function sesionAdminDjango() {
  const datos = await json(await fetch(`${ADMIN_API}/api/admin/auth/login/`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(ADMIN),
  }), 'Inicio de sesión del panel');
  return datos.token || datos.access;
}

// La biblioteca recibe el objeto: queda EN_CUSTODIA con su código y su etiqueta
export async function ponerEnCustodia(objetoId) {
  const token = await sesionAdminDjango();
  return json(await fetch(`${ADMIN_API}/api/admin/custodias/`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ objetoId, observaciones: 'Recibido en la prueba e2e' }),
  }), 'Recepción en custodia');
}

// Abre la web con la sesión ya iniciada (igual que tras pasar por el formulario de ingreso)
export async function entrarConSesion(page, cuenta, ruta = '/') {
  await page.goto('/cookies');
  await page.evaluate(({ token, usuario }) => {
    localStorage.setItem('foundia-almacenamiento', 'esencial');
    localStorage.setItem('token', token);
    localStorage.setItem('usuario', JSON.stringify(usuario));
  }, cuenta);
  await page.goto(ruta);
}
