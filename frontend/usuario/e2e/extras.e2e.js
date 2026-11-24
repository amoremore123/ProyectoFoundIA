import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { ADMIN, API, PANEL, categoriaActiva, crearCuenta, entrarConSesion, ponerEnCustodia, publicar, sufijo } from './apoyo';

// Extras: etiqueta QR, reconocimiento, coincidencias coherentes, cierre al devolver, comparación
// por imagen (sin gastar la IA) y, en el panel, impacto y mapa de pérdidas. Servicios reales.
const FOTO = fileURLToPath(new URL('../../../database/demo/fotos/casco-encontrado.jpg', import.meta.url));
const auth = (c) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${c.token}` });

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

test.describe('Extras: ideas para después del reparto', () => {
  test('Etiqueta QR: quien encuentra escanea, avisa por el chat y el dueño puede desactivarla', async ({ page, browser }) => {
    const marca = sufijo();
    const dueno = await crearCuenta('etiqueta');
    const quienEncuentra = await crearCuenta('escanea');

    // El dueño crea la etiqueta desde su perfil y ve el QR
    await entrarConSesion(page, dueno, '/perfil');
    await page.getByLabel('¿Qué cosa es?').fill(`Calculadora e2e ${marca}`);
    await page.getByLabel('Cómo reconocerla (opcional)').fill('Funda negra con un sticker azul');
    await page.getByRole('button', { name: 'Crear etiqueta' }).click();
    await expect(page.getByAltText(`Código QR de Calculadora e2e ${marca}`)).toBeVisible();
    const [etiqueta] = await fetch(`${API}/api/etiquetas`, { headers: auth(dueno) }).then((r) => r.json());

    // Quien la encuentra escanea sin sesión: ve el nombre, no los datos del dueño, y debe iniciar sesión
    const otro = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
    const escaner = await otro.newPage();
    await escaner.goto(`/etiqueta/${etiqueta.codigo}`);
    await expect(escaner.getByRole('heading', { name: `Calculadora e2e ${marca}` })).toBeVisible();
    await expect(escaner.getByText(dueno.correo)).toHaveCount(0);
    await expect(escaner.getByRole('link', { name: /Inicia sesión para avisar/ })).toBeVisible();

    // Con sesión le escribe y queda en el chat
    await entrarConSesion(escaner, quienEncuentra, `/etiqueta/${etiqueta.codigo}`);
    await escaner.getByLabel('Mensaje para su dueño').fill(`La encontré en el laboratorio ${marca}`);
    await escaner.getByRole('button', { name: /Avisar a su dueño/ }).click();
    await expect(escaner).toHaveURL(/\/mensajes\/\d+$/);
    await expect(escaner.getByText(`La encontré en el laboratorio ${marca}`)).toBeVisible();
    await expect(escaner.getByText(`Etiqueta QR · Calculadora e2e ${marca}`)).toBeVisible();

    // El dueño recibe el aviso
    const avisos = await fetch(`${API}/api/notificaciones`, { headers: auth(dueno) }).then((r) => r.json());
    expect(JSON.stringify(avisos)).toContain(`Encontraron tu Calculadora e2e ${marca}`);

    // Al desactivarla, el QR deja de funcionar
    await page.reload();
    await page.getByRole('button', { name: 'Desactivar' }).click();
    await page.getByRole('button', { name: 'Sí, desactivar' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'ya no funciona' })).toBeVisible();
    await escaner.goto(`/etiqueta/${etiqueta.codigo}`);
    await expect(escaner.getByRole('heading', { name: 'Esta etiqueta ya no está activa' })).toBeVisible();
    await otro.close();
  });

  test('Reconocimiento: al entregar un objeto en la biblioteca aparece la insignia en el perfil', async ({ page }) => {
    const cuenta = await crearCuenta('reconoce');
    await entrarConSesion(page, cuenta, '/perfil');
    const tarjeta = page.getByRole('region', { name: 'Tu aporte a la comunidad' });
    await expect(tarjeta).toContainText('Te falta 1 entrega para «Primera entrega»');

    const categoria = await categoriaActiva();
    const hallazgo = await publicar(cuenta, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Casco e2e ${sufijo()}`, descripcion: 'Casco amarillo' });
    await ponerEnCustodia(hallazgo.id);
    await page.reload();
    await expect(tarjeta).toContainText('Primera entrega');
    await expect(tarjeta).toContainText('Entregaste 1 objeto en la biblioteca');
  });

  test('Coincidencias: solo objetos del mismo tipo y el hallazgo se cierra al devolverlo', async ({ page, browser }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const encuentra = await crearCuenta('devuelve');
    const pierde = await crearCuenta('pierde');
    const billetera = await publicar(encuentra, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Billetera e2e ${marca}`, descripcion: 'Billetera marrón' });
    const lentes = await publicar(encuentra, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Lentes e2e ${marca}`, descripcion: 'Lentes negros' });
    const perdida = await publicar(pierde, { tipo: 'PERDIDO', categoriaId: categoria.id, nombre: `Billetera e2e ${marca}`, descripcion: 'Billetera marrón' });

    // Misma categoría, lugar y fecha, pero los lentes no son una billetera
    const sugeridas = await fetch(`${API}/api/objetos/${perdida.id}/coincidencias`).then((r) => r.json());
    expect(sugeridas.map((s) => s.objetoId)).toContain(billetera.id);
    expect(sugeridas.map((s) => s.objetoId)).not.toContain(lentes.id);
    await entrarConSesion(page, pierde, `/objeto/${perdida.id}`);
    await expect(page.getByRole('heading', { name: `Billetera e2e ${marca}`, level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: `Lentes e2e ${marca}` })).toHaveCount(0);

    // Quien la encontró la devuelve y la cierra
    const otro = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
    const finder = await otro.newPage();
    await entrarConSesion(finder, encuentra, `/objeto/${billetera.id}`);
    await expect(finder.getByText('Buscando a su dueño')).toBeVisible();
    await finder.getByRole('button', { name: 'Ya lo devolví' }).click();
    await finder.getByRole('button', { name: 'Sí, ya lo devolví' }).click();
    await expect(finder.getByText('Devuelto · caso cerrado')).toBeVisible();
    await otro.close();

    // Ya nadie la puede reclamar
    await page.goto(`/objeto/${billetera.id}`);
    await expect(page.getByText('Este objeto ya volvió a su dueño')).toBeVisible();
    await expect(page.getByRole('button', { name: /Reclamar este objeto/ })).toHaveCount(0);
  });

  test('Comparación por imagen: con foto propia se ofrece comparar con IA (sin llamarla)', async ({ page }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const pierde = await crearCuenta('foto');
    const encuentra = await crearCuenta('fotodos');
    const perdida = await publicar(pierde, { tipo: 'PERDIDO', categoriaId: categoria.id, nombre: `Casco e2e ${marca}`, descripcion: 'Casco amarillo' });
    await publicar(encuentra, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Casco e2e ${marca}`, descripcion: 'Casco amarillo' });

    await entrarConSesion(page, pierde, `/objeto/${perdida.id}`);
    await expect(page.getByRole('heading', { name: `Casco e2e ${marca}`, level: 3 }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Comparar fotos con IA/ })).toHaveCount(0);

    const formulario = new FormData();
    formulario.append('archivo', new Blob([readFileSync(FOTO)], { type: 'image/jpeg' }), 'casco.jpg');
    const subida = await fetch(`${API}/api/objetos/${perdida.id}/fotos`, { method: 'POST', headers: { Authorization: `Bearer ${pierde.token}` }, body: formulario });
    expect(subida.status).toBe(201);
    await page.reload();
    await expect(page.getByRole('button', { name: /Comparar fotos con IA/ })).toBeVisible();
  });

  test('Panel: el Dashboard muestra el impacto y Estadísticas el mapa de la sede', async ({ browser }) => {
    const { contexto, panel } = await entrarAlPanel(browser);
    const impacto = panel.getByRole('region', { name: 'Impacto' });
    await expect(impacto).toBeVisible();
    await expect(impacto.getByRole('list', { name: /Devueltos por mes/ })).toBeVisible();

    await panel.getByRole('link', { name: 'Estadísticas', exact: true }).click();
    const mapa = panel.getByRole('region', { name: 'Mapa de pérdidas' });
    await expect(mapa).toBeVisible();
    await expect(mapa.locator('.leaflet-container')).toBeVisible();
    await expect(mapa.getByRole('button', { name: 'Ver la sede completa' })).toBeVisible();
    await mapa.getByRole('combobox', { name: 'Qué objetos mostrar en el mapa' }).selectOption('PERDIDO');
    await expect(mapa.locator('.leaflet-container')).toBeVisible();
    await contexto.close();
  });
});
