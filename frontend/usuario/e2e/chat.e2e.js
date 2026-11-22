import { expect, test } from '@playwright/test';
import { categoriaActiva, crearCuenta, entrarConSesion, ponerEnCustodia, publicar, sufijo } from './apoyo';

// C2 · H55, H56, H62 y H63: chat entre quien perdió y quien encontró, y con la biblioteca.
test.describe('Chat interno', () => {
  test('quien perdió y quien encontró conversan en dos navegadores; bloquear impide escribir', async ({ page, browser }) => {
    const marca = sufijo();
    const categoria = await categoriaActiva();
    const encontro = await crearCuenta('chatencontro');
    const perdio = await crearCuenta('chatperdio');
    const hallazgo = await publicar(encontro, {
      tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Billetera e2e ${marca}`, descripcion: `Billetera marrón con cierre, prueba ${marca}`,
    });
    const perdido = await publicar(perdio, {
      tipo: 'PERDIDO', categoriaId: categoria.id, nombre: `Billetera e2e ${marca}`, descripcion: `Perdí mi billetera marrón con cierre, prueba ${marca}`,
    });

    // Quien perdió contacta desde la coincidencia y abre el chat
    await entrarConSesion(page, perdio, `/objeto/${perdido.id}`);
    const tarjeta = page.getByRole('listitem').filter({ hasText: hallazgo.nombre });
    await tarjeta.getByRole('button', { name: 'Contactar' }).click();
    await tarjeta.getByRole('button', { name: 'Conversar' }).click();
    await expect(page).toHaveURL(/\/mensajes\/\d+$/);
    await expect(page.getByRole('heading', { level: 1, name: encontro.nombre })).toBeVisible();

    // Mensaje vacío: error junto al campo, sin enviar
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.getByRole('alert')).toHaveText('Escribe un mensaje.');
    await page.getByLabel('Mensaje', { exact: true }).fill('Hola, creo que es mi billetera. ¿Nos vemos en la biblioteca?');
    await page.getByRole('button', { name: 'Enviar' }).click();
    const lista = page.getByRole('log');
    await expect(lista.getByText('Hola, creo que es mi billetera.', { exact: false })).toBeVisible();

    // Quien encontró la ve en su lista, sin leer, y responde
    const otro = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima' });
    const pagina2 = await otro.newPage();
    await entrarConSesion(pagina2, encontro, '/mensajes');
    const item = pagina2.getByRole('link').filter({ hasText: perdio.nombre });
    await expect(item).toContainText('1 sin leer');
    await item.click();
    await expect(pagina2.getByRole('log').getByText('Hola, creo que es mi billetera.', { exact: false })).toBeVisible();
    await pagina2.getByLabel('Mensaje', { exact: true }).fill('Claro, mañana a las 10 en la biblioteca central.');
    await pagina2.getByRole('button', { name: 'Enviar' }).click();

    // Llega sin recargar (consulta periódica con el cursor)
    await expect(lista.getByText('Claro, mañana a las 10 en la biblioteca central.')).toBeVisible({ timeout: 15_000 });

    // Reportar un mensaje del otro
    await lista.getByRole('button', { name: /Reportar el mensaje/ }).click();
    await page.getByLabel(/Por qué reportas este mensaje/).fill('Prueba de reporte e2e');
    await page.getByRole('button', { name: 'Enviar reporte' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'quedó reportado' })).toBeVisible();

    // Bloquear: el campo de escritura desaparece con la explicación, y al otro tampoco lo deja escribir
    await page.getByRole('button', { name: 'Bloquear' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Bloquear' }).click();
    await expect(page.getByText('No se puede escribir porque hay un bloqueo')).toBeVisible();
    await expect(page.getByLabel('Mensaje', { exact: true })).toHaveCount(0);
    await pagina2.getByLabel('Mensaje', { exact: true }).fill('¿Sigues ahí?');
    await pagina2.getByRole('button', { name: 'Enviar' }).click();
    await expect(pagina2.getByRole('alert')).toContainText(/bloqueo/i);

    // Desbloquear lo restablece
    await page.getByRole('button', { name: 'Desbloquear' }).click();
    await expect(page.getByLabel('Mensaje', { exact: true })).toBeVisible();
    await otro.close();
  });

  test('quien reclamó un objeto en custodia escribe a la biblioteca', async ({ page }) => {
    const categoria = await categoriaActiva();
    const encontro = await crearCuenta('chatcustodia');
    const dueno = await crearCuenta('chatdueno');
    const objeto = await publicar(encontro, { tipo: 'ENCONTRADO', categoriaId: categoria.id, nombre: `Casco e2e ${sufijo()}`, descripcion: 'Casco de bicicleta' });
    await ponerEnCustodia(objeto.id);
    await entrarConSesion(page, dueno, `/objeto/${objeto.id}`);
    await page.getByLabel(/Qué detalle del objeto/).fill('Tiene una calcomanía de Tecsup');
    await page.getByRole('button', { name: 'Reclamar este objeto' }).click();
    await page.getByRole('button', { name: 'Sí, reclamar' }).click();
    await page.getByRole('button', { name: /Escribir a la biblioteca/ }).click();
    await expect(page).toHaveURL(/\/mensajes\/\d+$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Biblioteca' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Bloquear' })).toHaveCount(0);
    await page.getByLabel('Mensaje', { exact: true }).fill('Hola, ¿qué horario tienen para el retiro?');
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.getByRole('log').getByText('¿qué horario tienen para el retiro?', { exact: false })).toBeVisible();

    // H62: enviar una foto con vista previa (PNG de 1 × 1 generado en memoria)
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC', 'base64');
    await page.locator('#conv-foto').setInputFiles({ name: 'calcomania.png', mimeType: 'image/png', buffer: png });
    await expect(page.getByRole('img', { name: 'Vista previa de la foto que vas a enviar' })).toBeVisible();
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.getByRole('log').getByRole('img', { name: 'Foto enviada por ti' })).toBeVisible();
  });

  test('una conversación ajena no se puede abrir', async ({ page }) => {
    const cuenta = await crearCuenta('chatajeno');
    await entrarConSesion(page, cuenta, '/mensajes/999999');
    await expect(page.getByRole('heading', { name: 'Conversación no disponible' })).toBeVisible();
  });
});
