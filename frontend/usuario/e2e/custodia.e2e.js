import { expect, test } from '@playwright/test';
import { ADMIN, PANEL, categoriaActiva, crearCuenta, entrarConSesion, ponerEnCustodia, publicar, sufijo } from './apoyo';

// C2 · Objeto en custodia: la etiqueta se verifica, el dueño reclama, la biblioteca acepta en el
// panel y el dueño recibe su QR de retiro. Una tercera persona ya no puede reclamarlo.
test.describe('Custodia: etiqueta, reclamo, aceptación en el panel y QR de retiro', () => {
  test('recorrido completo con la web y el panel', async ({ page, browser }) => {
    const marca = sufijo();
    const nombre = `Audífonos e2e ${marca}`;
    const categoria = await categoriaActiva();
    const encontro = await crearCuenta('hallazgo');
    const dueno = await crearCuenta('dueno');
    const tercero = await crearCuenta('tercero');
    const objeto = await publicar(encontro, {
      tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre, descripcion: 'Audífonos blancos con estuche',
    });
    const custodia = await ponerEnCustodia(objeto.id);

    // 1. Quien escanea la etiqueta ve el objeto, sin datos personales
    await page.goto(`/custodia/verificar?t=${encodeURIComponent(custodia.tokenQr)}`);
    await expect(page.getByRole('heading', { name: 'Objeto en custodia en la biblioteca' })).toBeVisible();
    await expect(page.getByText(`Etiqueta ${custodia.codigo}`)).toBeVisible();
    await expect(page.getByRole('heading', { name: nombre })).toBeVisible();
    await expect(page.locator('main')).not.toContainText(encontro.correo);

    // 2. El dueño lo reclama con una respuesta de verificación
    await entrarConSesion(page, dueno, `/objeto/${objeto.id}`);
    await page.getByLabel(/Qué detalle del objeto/).fill('Tienen mis iniciales grabadas en el estuche');
    await page.getByRole('button', { name: 'Reclamar este objeto' }).click();
    await page.getByRole('button', { name: 'Sí, reclamar' }).click();
    await expect(page.getByRole('heading', { name: 'Reclamo en revisión' })).toBeVisible();

    // 3. La biblioteca lo acepta en el panel de administración
    const contextoPanel = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
    const panel = await contextoPanel.newPage();
    await panel.goto(`${PANEL}/login`);
    await panel.getByLabel('Correo electrónico *').fill(ADMIN.correo);
    await panel.getByLabel('Contraseña *').fill(ADMIN.password);
    await panel.getByRole('button', { name: 'Entrar' }).click();
    await expect(panel).not.toHaveURL(/\/login/);
    await panel.goto(`${PANEL}/reclamos`);
    await panel.getByLabel('Buscar por objeto o reclamante').fill(marca);
    await panel.getByRole('button', { name: /Buscar/ }).click();
    const fila = panel.getByRole('row').filter({ hasText: nombre });
    await expect(fila).toBeVisible();
    await fila.getByRole('button', { name: /Resolver reclamo/ }).click();
    await panel.getByPlaceholder(/Documento de identidad/).fill('DNI y detalle del estuche verificados');
    await panel.getByRole('button', { name: 'Continuar' }).click();
    await panel.getByRole('button', { name: 'Sí, aceptar' }).click();
    await expect(panel.getByRole('status').filter({ hasText: /aceptad|QR/i }).first()).toBeVisible();
    await contextoPanel.close();

    // 4. El dueño ve que fue aceptado y su QR de retiro
    await page.reload();
    await expect(page.getByRole('heading', { name: '¡Reclamo aceptado!' })).toBeVisible();
    await expect(page.getByRole('img', { name: `QR de retiro de ${nombre}` })).toBeVisible();
    await expect(page.getByText(/Válido hasta el/)).toBeVisible();

    // 5. Otra persona ya no ve el formulario: el objeto tiene dueño confirmado
    await entrarConSesion(page, tercero, `/objeto/${objeto.id}`);
    await expect(page.getByRole('heading', { name: 'Este objeto ya tiene dueño confirmado' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reclamar este objeto' })).toHaveCount(0);
  });

  test('una etiqueta falsa se rechaza', async ({ page }) => {
    await page.goto('/custodia/verificar?t=no.es.valida');
    await expect(page.getByRole('alert')).toContainText('Etiqueta no válida');
  });
});
