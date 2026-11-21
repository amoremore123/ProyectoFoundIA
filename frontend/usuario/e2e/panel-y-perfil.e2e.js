import { deflateSync } from 'node:zlib';
import { expect, test } from '@playwright/test';
import { ADMIN, PANEL, crearCuenta, entrarConSesion, sufijo } from './apoyo';

// PNG de 48 × 48 generado en memoria (sin archivos en el repositorio)
function pngPrueba() {
  const crc = (buf) => {
    let c = ~0;
    for (const b of buf) { c ^= b; for (let k = 0; k < 8; k += 1) c = (c >>> 1) ^ (0xedb88320 & -(c & 1)); }
    return ~c >>> 0;
  };
  const trozo = (tipo, datos) => {
    const t = Buffer.from(tipo);
    const largo = Buffer.alloc(4); largo.writeUInt32BE(datos.length);
    const control = Buffer.alloc(4); control.writeUInt32BE(crc(Buffer.concat([t, datos])));
    return Buffer.concat([largo, t, datos, control]);
  };
  const lado = 48;
  const cabecera = Buffer.alloc(13);
  cabecera.writeUInt32BE(lado, 0); cabecera.writeUInt32BE(lado, 4); cabecera[8] = 8; cabecera[9] = 2;
  const fila = Buffer.concat([Buffer.from([0]), Buffer.alloc(lado * 3, 0x55)]);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    trozo('IHDR', cabecera), trozo('IDAT', deflateSync(Buffer.concat(Array(lado).fill(fila)))), trozo('IEND', Buffer.alloc(0)),
  ]);
}

async function entrarAlPanel(browser, correo, password) {
  const contexto = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
  const panel = await contexto.newPage();
  await panel.goto(`${PANEL}/login`);
  await panel.getByLabel('Correo electrónico *').fill(correo);
  await panel.getByLabel('Contraseña *').fill(password);
  await panel.getByRole('button', { name: 'Entrar' }).click();
  return { contexto, panel };
}

test.describe('Panel de administración (Sprint 4)', () => {
  test('el administrador ve los respaldos con su huella y gestiona campañas', async ({ browser }) => {
    const { contexto, panel } = await entrarAlPanel(browser, ADMIN.correo, ADMIN.password);
    await expect(panel).not.toHaveURL(/\/login/);

    // H58/H59: respaldos
    await panel.getByRole('link', { name: 'Respaldos' }).click();
    await expect(panel.getByRole('heading', { name: 'Respaldos', level: 1 })).toBeVisible();
    await expect(panel.getByRole('region', { name: /Respaldo de la base/ })).toBeVisible();
    await expect(panel.getByRole('region', { name: /Respaldo de las fotos/ })).toBeVisible();
    await expect(panel.getByRole('region', { name: /cómo recuperar/ })).toContainText('recuperar_todo.sh');

    // H60: crear una campaña y ver su enlace con origen
    await panel.getByRole('link', { name: 'Campañas' }).click();
    await panel.getByRole('button', { name: /Nueva campaña/ }).click();
    const nombre = `Campaña e2e ${sufijo()}`;
    await panel.getByLabel('Nombre *').fill(nombre);
    const origen = await panel.getByLabel('Origen *').inputValue();
    expect(origen).toMatch(/^campana-e2e-[a-z0-9]+$/);
    const fin = new Date(Date.now() + 7 * 86400000).toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
    await panel.getByLabel('Fin *').fill(fin);
    await panel.getByRole('button', { name: 'Crear campaña' }).click();
    const fila = panel.getByRole('row').filter({ hasText: nombre });
    await expect(fila).toContainText('Activa');
    await expect(fila).toContainText(`?origen=${origen}`);
    await contexto.close();
  });

  test('una cuenta de usuario no puede entrar al panel', async ({ browser }) => {
    const cuenta = await crearCuenta('sinpanel');
    const { contexto, panel } = await entrarAlPanel(browser, cuenta.correo, cuenta.password);
    await expect(panel.getByRole('alert')).toBeVisible();
    await expect(panel).toHaveURL(/\/login/);
    await contexto.close();
  });
});

test.describe('Web: foto de perfil y páginas dentro de la app', () => {
  test('H64: sube la foto de perfil, la ve y la quita', async ({ page }) => {
    const cuenta = await crearCuenta('foto');
    await entrarConSesion(page, cuenta, '/perfil');
    await expect(page.getByRole('button', { name: /Subir foto/ })).toBeVisible();
    await page.locator('#foto-perfil-archivo').setInputFiles({ name: 'yo.png', mimeType: 'image/png', buffer: pngPrueba() });
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'Foto de perfil actualizada.' })).toBeVisible();

    // Recargar: la foto sigue ahí porque está guardada en el servidor
    await page.reload();
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toBeVisible();

    await page.getByRole('button', { name: 'Quitar foto' }).click();
    await page.getByRole('alertdialog', { name: '¿Quitar tu foto de perfil?' }).getByRole('button', { name: 'Quitar foto' }).click();
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Subir foto/ })).toBeVisible();
  });

  test('H64: un archivo que no es imagen se rechaza sin subirse', async ({ page }) => {
    const cuenta = await crearCuenta('fotomala');
    await entrarConSesion(page, cuenta, '/perfil');
    await page.locator('#foto-perfil-archivo').setInputFiles({ name: 'cv.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
    await expect(page.getByRole('alert')).toHaveText('La foto debe ser JPEG o PNG.');
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toHaveCount(0);
  });

  test('H73: con ?embebido=1 la página se ve sin menú y lo recuerda al navegar', async ({ page }) => {
    await page.goto('/terminos?embebido=1');
    await expect(page.getByRole('heading', { name: 'Términos de uso', level: 1 })).toBeVisible();
    await expect(page.locator('.navbar, .bottom-nav')).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Saltar al contenido' })).toHaveCount(0);
    await page.goto('/privacidad');
    await expect(page.locator('.navbar, .bottom-nav')).toHaveCount(0);
    await page.goto('/privacidad?embebido=0');
    await expect(page.getByRole('link', { name: 'Saltar al contenido' })).toHaveCount(1);
  });
});
