import { expect, test } from '@playwright/test';
import { API, categoriaActiva, crearCuenta, entrarConSesion, hoyLima, publicar, sufijo } from './apoyo';

// C2 · Publicar un objeto perdido, ver la coincidencia con un hallazgo y contactar a quien lo encontró.
test.describe('Publicación, coincidencia y contacto', () => {
  test('quien perdió algo lo publica, ve el hallazgo parecido y contacta sin compartir su correo', async ({ page }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const encontro = await crearCuenta('encontro');
    const perdio = await crearCuenta('perdio');
    const hallazgo = await publicar(encontro, {
      tipo: 'ENCONTRADO', categoriaId: categoria.id,
      nombre: `Mochila azul e2e ${marca}`, descripcion: `Mochila azul con llavero de estrella, prueba ${marca}`,
    });

    await entrarConSesion(page, perdio, '/publicar');
    await expect(page.getByRole('button', { name: /Perdí un objeto/ })).toHaveAttribute('aria-pressed', 'true');
    await page.getByLabel('Nombre *').fill(`Mochila azul e2e ${marca}`);
    await page.getByLabel('Descripción *').fill(`Perdí mi mochila azul con un llavero de estrella, prueba ${marca}`);
    await page.getByLabel('Categoría *').selectOption(String(categoria.id));
    await page.getByLabel('Ubicación *').fill('Biblioteca central');
    await page.getByLabel('Fecha *').fill(hoyLima());
    await page.getByRole('button', { name: 'PUBLICAR OBJETO' }).click();

    // Queda publicada y se abre su detalle con las coincidencias
    await expect(page).toHaveURL(/\/objeto\/\d+$/);
    await expect(page.getByRole('heading', { level: 1, name: `Mochila azul e2e ${marca}` })).toBeVisible();
    const tarjeta = page.getByRole('listitem').filter({ hasText: hallazgo.nombre });
    await expect(tarjeta).toBeVisible();

    await tarjeta.getByRole('button', { name: 'Contactar' }).click();
    await expect(tarjeta.getByRole('status')).toHaveText('Solicitud de contacto enviada.');

    // Quien encontró el objeto recibe el aviso, sin el correo de quien lo perdió
    const respuesta = await fetch(`${API}/api/notificaciones`, { headers: { Authorization: `Bearer ${encontro.token}` } });
    const avisos = await respuesta.json();
    const lista = Array.isArray(avisos) ? avisos : avisos.content || avisos.results || [];
    expect(lista.length).toBeGreaterThan(0);
    expect(JSON.stringify(lista)).not.toContain(perdio.correo);
  });

  test('sin sesión no se puede publicar: lleva al inicio de sesión', async ({ page }) => {
    await page.goto('/publicar');
    await expect(page).toHaveURL(/\/login/);
  });

  test('el detalle de un objeto en custodia no muestra coincidencias ni errores', async ({ page }) => {
    // Corrección M1 del Sprint 4 (web), comprobada con la API real
    const { ponerEnCustodia } = await import('./apoyo');
    const categoria = await categoriaActiva();
    const encontro = await crearCuenta('custodia');
    const objeto = await publicar(encontro, {
      tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Casaca e2e ${sufijo()}`, descripcion: 'Casaca negra de prueba',
    });
    await ponerEnCustodia(objeto.id);
    const errores = [];
    page.on('response', (r) => { if (r.url().includes('/coincidencias') && r.status() >= 400) errores.push(r.status()); });
    await page.goto(`/objeto/${objeto.id}`);
    await expect(page.getByText('En custodia', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Coincidencias sugeridas' })).toHaveCount(0);
    expect(errores).toEqual([]);
  });
});
