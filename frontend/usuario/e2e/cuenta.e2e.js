import { expect, test } from '@playwright/test';
import { codigoDeVerificacion, crearCuenta, letras, passwordPrueba, sufijo } from './apoyo';

// C2 · H11 y H12: registrarse, verificar el correo con el código real (Mailpit) e iniciar sesión.
test.describe('Cuenta: registro, verificación e inicio de sesión', () => {
  test('una persona nueva se registra, verifica su correo y entra a FoundIA', async ({ page }) => {
    const correo = `e2e-registro-${sufijo()}@prueba.invalid`;
    const password = passwordPrueba();

    await page.goto('/registro');
    await page.getByLabel('Nombre *').fill(`Prueba${letras(4)}`);
    await page.getByLabel('Apellido *').fill('Eedos');
    await page.getByLabel('Correo electrónico *').fill(correo);
    await page.getByLabel('Contraseña *', { exact: true }).fill(password);
    await page.getByLabel('Confirmar contraseña *').fill(password);

    // Sin aceptar los términos no se crea la cuenta
    await page.getByRole('button', { name: 'Registrarme' }).click();
    await expect(page.getByText('Debes aceptar los términos y la política de privacidad')).toBeVisible();

    await page.getByRole('checkbox', { name: /He leído y acepto los Términos/ }).check();
    await page.getByRole('button', { name: 'Registrarme' }).click();

    await expect(page).toHaveURL(/\/verificar\?correo=/);
    const codigo = await codigoDeVerificacion(correo);
    await page.getByLabel('Código de verificación *').fill(codigo);
    await page.getByRole('button', { name: /Verificar/ }).click();

    // Verificada, entra con la sesión iniciada
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/perfil');
    await expect(page.getByRole('heading', { name: 'Mi perfil' })).toBeVisible();
    await expect(page.getByText(correo)).toBeVisible();
  });

  test('el mismo correo no se puede registrar dos veces', async ({ page }) => {
    const cuenta = await crearCuenta('repetido');
    await page.goto('/registro');
    await page.getByLabel('Nombre *').fill('Otra');
    await page.getByLabel('Apellido *').fill('Persona');
    await page.getByLabel('Correo electrónico *').fill(cuenta.correo);
    const password = passwordPrueba();
    await page.getByLabel('Contraseña *', { exact: true }).fill(password);
    await page.getByLabel('Confirmar contraseña *').fill(password);
    await page.getByRole('checkbox', { name: /He leído y acepto los Términos/ }).check();
    await page.getByRole('button', { name: 'Registrarme' }).click();
    // El aviso general y el error junto al campo de correo
    await expect(page.getByRole('alert')).toContainText('Ese correo ya está registrado');
    await expect(page.getByRole('textbox', { name: /Correo electrónico/ })).toHaveAttribute('aria-invalid', 'true');
    await expect(page).toHaveURL(/\/registro/);
  });

  test('inicio de sesión: contraseña incorrecta muestra el error y la correcta entra', async ({ page }) => {
    const cuenta = await crearCuenta('ingreso');
    await page.goto('/login');
    await page.getByLabel('Correo electrónico *').fill(cuenta.correo);
    await page.getByLabel('Contraseña *').fill(`${cuenta.password}x`);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);

    await page.getByLabel('Contraseña *').fill(cuenta.password);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page).toHaveURL(/\/$/);
    expect(await page.evaluate(() => Boolean(localStorage.getItem('token')))).toBe(true);
  });
});
