// Prueba real de H12: no cambia relojes, base de datos ni el correo de la pantalla.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const API = 'http://localhost:18081';
const WEB = 'http://localhost:15173';
const MAIL = 'http://localhost:18368';
const salida = path.resolve(process.env.QA_EVIDENCIAS || 'tmp/evidencias-bloqueo-natural');
fs.mkdirSync(salida, { recursive: true });
const pausa = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function enviar(ruta, datos) {
  return fetch(API + ruta, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos) });
}
(async () => {
  let navegador;
  const resultado = {};
  try {
    const correo = `qa-bloqueo-natural-${Date.now()}@prueba.invalid`;
    assert.equal((await enviar('/api/auth/register', { nombre: 'Prueba', apellido: 'Bloqueo', correo, password: 'Segura123!' })).status, 201);
    let codigo;
    for (let i = 0; i < 40 && !codigo; i++) {
      const lista = await (await fetch(MAIL + '/api/v1/messages')).json();
      const mensaje = lista.messages.find((m) => m.To?.some((to) => to.Address === correo));
      if (mensaje) codigo = (await (await fetch(MAIL + '/api/v1/message/' + mensaje.ID)).json()).Text.match(/\b\d{6}\b/)[0];
      else await pausa(250);
    }
    assert.ok(codigo);
    assert.equal((await enviar('/api/auth/verificar', { correo, codigo })).status, 200);
    navegador = await chromium.launch({ channel: 'msedge', headless: true });
    const pagina = await navegador.newPage({ viewport: { width: 375, height: 800 } });
    await pagina.goto(WEB + '/login');
    await pagina.getByLabel(/^Correo/).fill(correo);
    await pagina.getByLabel(/^Contraseña/).fill('Incorrecta123!');
    const estados = [];
    for (let i = 0; i < 5; i++) {
      const respuesta = pagina.waitForResponse((r) => r.url() === API + '/api/auth/login');
      await pagina.getByRole('button', { name: 'Entrar', exact: true }).click();
      estados.push((await respuesta).status());
    }
    assert.deepEqual(estados, [401, 401, 401, 401, 423]);
    resultado.estados = estados;
    resultado.inicio = new Date().toISOString();
    const inicio = Date.now();
    await pagina.getByLabel(/^Contraseña/).fill('Segura123!');
    assert.equal(await pagina.getByRole('button', { name: 'Entrar', exact: true }).isDisabled(), true);
    await pagina.screenshot({ path: path.join(salida, 'bloqueo-vigente.png'), fullPage: true });
    console.log('Bloqueo iniciado:', resultado.inicio, 'Esperando 15 minutos naturales sin recargar ni cambiar el correo.');
    const fin = inicio + 15 * 60 * 1000 + 2000;
    while (Date.now() < fin) await pausa(Math.min(30000, fin - Date.now()));
    assert.equal(await pagina.getByLabel(/^Correo/).inputValue(), correo);
    assert.equal(await pagina.getByRole('button', { name: 'Entrar', exact: true }).isDisabled(), false);
    await pagina.getByRole('button', { name: 'Entrar', exact: true }).click();
    await pagina.waitForURL(WEB + '/');
    resultado.fin = new Date().toISOString();
    resultado.segundosTranscurridos = Math.round((Date.now() - inicio) / 1000);
    resultado.resultado = 'correcto';
    resultado.metodo = 'Espera natural, sin modificar reloj, bloqueo en BD, correo ni recargar la pantalla.';
    console.log('OK', resultado);
  } catch (error) {
    resultado.resultado = 'fallido'; resultado.error = error.message;
    console.error(error); process.exitCode = 1;
  } finally {
    fs.writeFileSync(path.join(salida, 'resultado.json'), JSON.stringify(resultado, null, 2));
    await navegador?.close();
  }
})();
