import { expect, test } from '@playwright/test';
import { ADMIN, API, PANEL, categoriaActiva, crearCuenta, entrarConSesion, publicar, sufijo } from './apoyo';

// C2 · H71, H68, H56 y H70 con los servicios reales (Spring, Django y MySQL).
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

test.describe('Sprint 4: avisos, canales, reportes del chat y versiones', () => {
  test('H71: un aviso creado en el panel aparece en Inicio y desaparece al retirarlo', async ({ page, browser }) => {
    const titulo = `Aviso e2e ${sufijo()}`;
    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Avisos' }).click();
    await panel.getByRole('button', { name: /Nuevo aviso/ }).click();
    await panel.getByLabel('Título *').fill(titulo);
    await panel.getByLabel('Texto *').fill('La biblioteca atiende hasta las 6 p. m. esta semana.');
    await panel.getByLabel('Abre una página de FoundIA').check();
    await panel.getByLabel('Página *').fill('/explorar');
    const fin = new Date(Date.now() + 3 * 86400000).toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
    await panel.getByLabel('Hasta *').fill(fin);
    await panel.getByRole('button', { name: 'Crear aviso' }).click();
    await expect(panel.getByRole('row').filter({ hasText: titulo })).toContainText('Vigente');

    await page.goto('/');
    const aviso = page.getByRole('region', { name: 'Avisos destacados' }).getByRole('article').filter({ hasText: titulo });
    await expect(aviso).toBeVisible();
    await aviso.getByRole('link', { name: 'Ver más' }).click();
    await expect(page).toHaveURL(/\/explorar$/);

    await panel.getByRole('button', { name: `Retirar ${titulo}` }).click();
    await panel.getByRole('button', { name: 'Sí, retirar' }).click();
    await expect(panel.getByRole('row').filter({ hasText: titulo })).toContainText('Retirado');
    await contexto.close();
    await page.goto('/');
    await expect(page.getByText(titulo)).toHaveCount(0);
  });

  test('H68: elegir recibir los avisos por correo se guarda en la cuenta', async ({ page }) => {
    const cuenta = await crearCuenta('canal');
    await entrarConSesion(page, cuenta, '/notificaciones');
    await page.getByText('Preferencias de avisos').click();
    const correo = page.getByRole('checkbox', { name: /Correo electrónico/ });
    await expect(correo).not.toBeChecked();
    await correo.check();
    await page.getByRole('button', { name: 'Guardar preferencias' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Preferencias guardadas' })).toBeVisible();
    const guardadas = await fetch(`${API}/api/notificaciones/preferencias`, { headers: { Authorization: `Bearer ${cuenta.token}` } }).then((r) => r.json());
    expect(guardadas).toMatchObject({ correo: true, push: true });
  });

  test('H56: un mensaje reportado en el chat se resuelve en el panel y el autor recibe la advertencia', async ({ page, browser }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const autor = await crearCuenta('autorchat');
    const reporta = await crearCuenta('reportachat');
    const h = await publicar(autor, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Lentes e2e ${marca}`, descripcion: `Lentes negros ${marca}` });
    const p = await publicar(reporta, { tipo: 'PERDIDO', categoriaId: categoria.id, nombre: `Lentes e2e ${marca}`, descripcion: `Lentes negros ${marca}` });
    const json = (r) => r.json();
    const auth = (c) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${c.token}` });
    const contacto = await fetch(`${API}/api/contactos`, { method: 'POST', headers: auth(reporta), body: JSON.stringify({ objetoPerdidoId: p.id, objetoEncontradoId: h.id }) }).then(json);
    const conv = await fetch(`${API}/api/conversaciones`, { method: 'POST', headers: auth(reporta), body: JSON.stringify({ contactoId: contacto.id }) }).then(json);
    await fetch(`${API}/api/conversaciones/${conv.id}/mensajes`, { method: 'POST', headers: auth(autor), body: JSON.stringify({ texto: `Pásame tu número ${marca}` }) });

    // Quien recibió el mensaje lo reporta desde la web
    await entrarConSesion(page, reporta, `/mensajes/${conv.id}`);
    await page.getByRole('button', { name: /Reportar el mensaje/ }).click();
    await page.getByLabel(/Por qué reportas este mensaje/).fill('Me pide datos personales');
    await page.getByRole('button', { name: 'Enviar reporte' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'quedó reportado' })).toBeVisible();

    // El administrador lo ve y advierte al autor
    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Reportes del chat' }).click();
    const fila = panel.getByRole('row').filter({ hasText: `Pásame tu número ${marca}` });
    await expect(fila).toContainText(reporta.nombre);
    await fila.getByRole('button', { name: /Resolver reporte/ }).click();
    await panel.getByLabel(/Motivo de la decisión/).fill('No pidas datos personales en el chat');
    await panel.getByRole('button', { name: 'Continuar' }).click();
    await panel.getByRole('button', { name: 'Sí, advertir' }).click();
    await expect(panel.getByRole('row').filter({ hasText: `Pásame tu número ${marca}` })).toHaveCount(0);
    await contexto.close();

    const avisos = await fetch(`${API}/api/notificaciones`, { headers: auth(autor) }).then(json);
    const lista = Array.isArray(avisos) ? avisos : avisos.content || avisos.results || [];
    const texto = JSON.stringify(lista);
    expect(texto).toContain('No pidas datos personales en el chat');
    expect(texto).not.toContain(reporta.nombre);
  });

  test('H70: el panel muestra las versiones restaurables y cómo ensayarlas, sin restaurar desde la web', async ({ browser }) => {
    const { contexto, panel } = await entrarAlPanel(browser);
    await panel.getByRole('link', { name: 'Respaldos' }).click();
    const seccion = panel.getByRole('region', { name: 'Versiones a las que se puede volver' });
    await expect(seccion.getByRole('table')).toBeVisible();
    await seccion.getByRole('button', { name: /Ver cómo restaurar la versión/ }).first().click();
    await expect(panel.getByRole('region', { name: /Cómo restaurar la versión/ })).toContainText('restaurar_version.sh');
    await expect(panel.getByRole('button', { name: /^Restaurar$/ })).toHaveCount(0);
    await contexto.close();
  });
});
