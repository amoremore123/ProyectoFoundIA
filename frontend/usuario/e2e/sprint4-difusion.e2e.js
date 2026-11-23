import { deflateSync } from 'node:zlib';
import { expect, test } from '@playwright/test';
import {
  ADMIN, ADMIN_API, API, PANEL, categoriaActiva, codigoDeVerificacion, crearCuenta, entrarConSesion, letras, passwordPrueba,
  publicar, sesionAdminDjango, sufijo,
} from './apoyo';

// C2 · H66, H67, H69 y H64 (foto en el chat) con los servicios reales.
async function entrarAlPanel(browser) {
  const contexto = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
  const panel = await contexto.newPage();
  await panel.goto(`${PANEL}/login`);
  await panel.getByLabel('Correo electrónico *').fill(ADMIN.correo);
  await panel.getByLabel('Contraseña *').fill(ADMIN.password);
  await panel.getByRole('button', { name: 'Entrar' }).click();
  await expect(panel).not.toHaveURL(/\/login/);
  return { contexto, panel };
}

async function crearCampana(nombre) {
  const token = await sesionAdminDjango();
  const hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
  const fin = new Date(Date.now() + 7 * 86400000).toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
  const origen = `e2e-${sufijo()}`;
  const r = await fetch(`${ADMIN_API}/api/admin/campanas/`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ nombre, origen, destino: '/', fechaInicio: hoy, fechaFin: fin }),
  });
  if (!r.ok) throw new Error(`Campaña: ${r.status} ${await r.text()}`);
  return r.json();
}

function png() {
  const crc = (b) => { let c = ~0; for (const x of b) { c ^= x; for (let k = 0; k < 8; k += 1) c = (c >>> 1) ^ (0xedb88320 & -(c & 1)); } return ~c >>> 0; };
  const t = (tipo, d) => { const n = Buffer.from(tipo); const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const c = Buffer.alloc(4); c.writeUInt32BE(crc(Buffer.concat([n, d]))); return Buffer.concat([l, n, d, c]); };
  const lado = 32; const h = Buffer.alloc(13); h.writeUInt32BE(lado, 0); h.writeUInt32BE(lado, 4); h[8] = 8; h[9] = 2;
  const fila = Buffer.concat([Buffer.from([0]), Buffer.alloc(lado * 3, 0x33)]);
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), t('IHDR', h), t('IDAT', deflateSync(Buffer.concat(Array(lado).fill(fila)))), t('IEND', Buffer.alloc(0))]);
}

test.describe('Sprint 4: difusión y foto en el chat', () => {
  test('H67: una visita y un registro por el enlace de la campaña aparecen en el tablero del panel', async ({ page, browser }) => {
    const campana = await crearCampana(`Alcance e2e ${sufijo()}`);
    await page.goto(`/?origen=${campana.origen}`);
    // Una recarga en la misma pestaña no vuelve a contar
    await page.reload();
    const correo = `e2e-alcance-${sufijo()}@prueba.invalid`;
    const password = passwordPrueba();
    await page.goto('/registro');
    await page.getByLabel('Nombre *').fill(`Prueba${letras(4)}`);
    await page.getByLabel('Apellido *').fill('Eedos');
    await page.getByLabel('Correo electrónico *').fill(correo);
    await page.getByLabel('Contraseña *', { exact: true }).fill(password);
    await page.getByLabel('Confirmar contraseña *').fill(password);
    await page.getByRole('checkbox', { name: /He leído y acepto los Términos/ }).check();
    await page.getByRole('button', { name: 'Registrarme' }).click();
    await expect(page).toHaveURL(/\/verificar/);
    await codigoDeVerificacion(correo);

    const token = await sesionAdminDjango();
    const api = await fetch(`${ADMIN_API}/api/admin/campanas/${campana.id}/alcance/`, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.json());
    expect(api.visitas).toBe(1);
    expect(api.registros).toBe(1);

    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Campañas' }).click();
    const fila = panel.getByRole('region', { name: /Alcance de las campañas/ }).getByRole('row').filter({ hasText: campana.nombre });
    await expect(fila).toContainText('1');
    await expect(fila).toContainText('100%');
    await contexto.close();
  });

  test('H66: el panel prepara el texto para una red y registra lo publicado', async ({ browser }) => {
    const campana = await crearCampana(`Promo e2e ${sufijo()}`);
    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Campañas' }).click();
    await panel.getByRole('button', { name: `Promocionar ${campana.nombre} en redes` }).click();
    await panel.getByRole('combobox', { name: 'Red' }).selectOption('X');
    await panel.getByRole('button', { name: /Preparar texto/ }).click();
    const texto = panel.getByLabel('Texto de la publicación *');
    await expect(texto).toHaveValue(new RegExp(`origen=${campana.origen}`));
    await expect(panel.getByText(/\/280 caracteres para X/)).toBeVisible();
    await panel.getByLabel(/Dirección de la publicación/).fill('https://x.com/foundia/status/123');
    await panel.getByRole('button', { name: 'Registrar publicación' }).click();
    await expect(panel.getByRole('status').filter({ hasText: 'Publicación en X (Twitter) registrada' })).toBeVisible();
    await expect(panel.getByRole('listitem').filter({ hasText: 'X (Twitter)' })).toBeVisible();
    await contexto.close();
  });

  test('H69: un aviso masivo para el personal de biblioteca se envía tras la vista previa y llega a su bandeja', async ({ browser }) => {
    const titulo = `Aviso masivo e2e ${sufijo()}`;
    // El grupo necesita al menos una cuenta: se crea una y se le da el rol BIBLIOTECA (H36)
    const bibliotecaria = await crearCuenta('masivobib');
    const token = await sesionAdminDjango();
    const cambio = await fetch(`${ADMIN_API}/api/admin/usuarios/${bibliotecaria.usuario.id}/`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ rol: 'BIBLIOTECA', confirmar: true, motivo: 'Cuenta de prueba e2e para H69' }),
    });
    expect(cambio.status).toBe(200);
    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Avisos masivos' }).click();
    await panel.getByLabel('Título *').fill(titulo);
    await panel.getByLabel('Mensaje *').fill('Prueba de envío masivo para el personal de biblioteca.');
    await panel.getByLabel('Para *').selectOption('BIBLIOTECA');
    await panel.getByRole('button', { name: 'Vista previa' }).click();
    await expect(panel.getByText(/Lo recibirán \d+ cuentas/)).toBeVisible();
    await panel.getByRole('button', { name: /Enviar a \d+ cuentas/ }).click();
    await panel.getByRole('button', { name: 'Sí, enviar' }).click();
    const fila = panel.getByRole('row').filter({ hasText: titulo });
    await expect(fila).toContainText('Solo el personal de biblioteca');
    await fila.getByRole('button', { name: /Ver destinatarios/ }).click();
    await expect(panel.getByRole('region', { name: /Destinatarios de/ }).getByText('Sin leer').first()).toBeVisible();
    await contexto.close();
    // Llega a la bandeja de la cuenta de biblioteca
    const sesion = await fetch(`${API}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correo: bibliotecaria.correo, password: bibliotecaria.password }) }).then((r) => r.json());
    const avisos = await fetch(`${API}/api/notificaciones`, { headers: { Authorization: `Bearer ${sesion.token}` } }).then((r) => r.json());
    expect(JSON.stringify(avisos)).toContain(titulo);
  });

  test('H64: en el chat se ve la foto de perfil de la otra persona', async ({ page }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const conFoto = await crearCuenta('confoto');
    const otra = await crearCuenta('verfoto');
    const formulario = new FormData();
    formulario.append('archivo', new Blob([png()], { type: 'image/png' }), 'yo.png');
    const subida = await fetch(`${API}/api/perfil/foto`, { method: 'POST', headers: { Authorization: `Bearer ${conFoto.token}` }, body: formulario });
    expect(subida.status).toBe(200);
    const h = await publicar(conFoto, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Gorra e2e ${marca}`, descripcion: `Gorra roja ${marca}` });
    const p = await publicar(otra, { tipo: 'PERDIDO', categoriaId: categoria.id, nombre: `Gorra e2e ${marca}`, descripcion: `Gorra roja ${marca}` });
    const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${otra.token}` };
    const contacto = await fetch(`${API}/api/contactos`, { method: 'POST', headers: auth, body: JSON.stringify({ objetoPerdidoId: p.id, objetoEncontradoId: h.id }) }).then((r) => r.json());
    const conv = await fetch(`${API}/api/conversaciones`, { method: 'POST', headers: auth, body: JSON.stringify({ contactoId: contacto.id }) }).then((r) => r.json());

    await entrarConSesion(page, otra, `/mensajes/${conv.id}`);
    const avatar = page.locator('.conv-cabecera-avatar img');
    await expect(avatar).toBeVisible();
    expect(await avatar.getAttribute('src')).toMatch(/^blob:/);
    await page.goto('/mensajes');
    await expect(page.locator('.conv-lista-avatar img').first()).toBeVisible();
  });
});
