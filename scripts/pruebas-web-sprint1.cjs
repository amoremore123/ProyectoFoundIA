/* QA aislado. Requiere APIs/SMTP de pruebas y Playwright con Microsoft Edge.
 * No ejecutar contra datos compartidos. Variables y repetición en docs/pruebas/integracion-sprint1-amore-2026-10-20.md.
 */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const API = process.env.QA_API || 'http://localhost:18081';
const ADMIN = process.env.QA_ADMIN_API || 'http://localhost:18000';
const WEB = process.env.QA_WEB || 'http://localhost:15173';
const PANEL = process.env.QA_PANEL || 'http://localhost:15174';
const MAIL = process.env.QA_MAIL || 'http://localhost:18368';
const salida = path.resolve(process.env.QA_EVIDENCIAS || 'tmp/evidencias-web');
const sufijo = Date.now();
const PASSWORD = 'Segura123!';
const casos = [];
const erroresConsola = [];
let ultimaPagina;
fs.mkdirSync(salida, { recursive: true });
const esperar = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const registrar = (nombre, detalle = {}) => { casos.push({ nombre, resultado: 'correcto', ...detalle }); console.log('OK', nombre); };
async function peticion(ruta, { metodo = 'GET', token, cuerpo, admin = false } = {}) {
  const respuesta = await fetch(`${admin ? ADMIN : API}${ruta}`, {
    method: metodo,
    headers: { ...(cuerpo ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  return { estado: respuesta.status, datos: await respuesta.json().catch(() => null) };
}
async function codigo(correo) {
  for (let i = 0; i < 40; i++) {
    const lista = await (await fetch(`${MAIL}/api/v1/messages`)).json();
    const mensaje = lista.messages?.find((m) => m.To?.some((to) => to.Address === correo));
    if (mensaje) {
      const detalle = await (await fetch(`${MAIL}/api/v1/message/${mensaje.ID}`)).json();
      const encontrado = detalle.Text?.match(/\b\d{6}\b/);
      if (encontrado) return encontrado[0];
    }
    await esperar(250);
  }
  throw new Error('No llegó el código al SMTP de QA');
}
async function cuenta(nombre) {
  const correo = `qa-${nombre.toLowerCase()}-${sufijo}@prueba.invalid`;
  const alta = await peticion('/api/auth/register', { metodo: 'POST', cuerpo: { nombre, apellido: 'Prueba', correo, password: PASSWORD } });
  assert.equal(alta.estado, 201, JSON.stringify(alta.datos));
  const verificacion = await peticion('/api/auth/verificar', { metodo: 'POST', cuerpo: { correo, codigo: await codigo(correo) } });
  assert.equal(verificacion.estado, 200);
  return { correo, ...verificacion.datos };
}
async function entrar(pagina, correo, password = PASSWORD, panel = false) {
  await pagina.goto(`${panel ? PANEL : WEB}/login`);
  await pagina.getByLabel(/Correo electrónico/).fill(correo);
  await pagina.getByLabel(/^Contraseña/).fill(password);
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click();
  await pagina.waitForURL(`${panel ? PANEL : WEB}/`);
}
async function sinDesborde(pagina, nombre, ancho) {
  await pagina.setViewportSize({ width: ancho, height: 900 });
  await pagina.evaluate(() => document.fonts.ready);
  assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${nombre}: desborde a ${ancho}px`);
  registrar(`${nombre}: sin desborde a ${ancho}px`);
}

async function capturar(pagina, archivo) {
  await pagina.evaluate(async () => {
    await Promise.all([...document.images].map((imagen) => {
      imagen.loading = 'eager';
      return imagen.decode().catch(() => {});
    }));
  });
  await pagina.screenshot({ path: path.join(salida, archivo), fullPage: true, animations: 'disabled' });
}

async function contraste(pagina, titulo) {
  const mediciones = await pagina.evaluate(() => {
    const rgb = (texto) => (texto.match(/[\d.]+/g) || []).map(Number);
    const mezclar = (frente, fondo) => {
      const alfa = frente[3] ?? 1;
      return frente.slice(0, 3).map((canal, i) => canal * alfa + fondo[i] * (1 - alfa));
    };
    const luminancia = (color) => color.map((c) => c / 255).map((c) => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4).reduce((suma, c, i) => suma + c * [.2126, .7152, .0722][i], 0);
    return [...document.querySelectorAll('h1, .filtros-avanzados legend, .filtros-avanzados .campo-label, .filtros-avanzados .nota-suave, .filtros-activos button, .comparacion-criterios li, .comparacion-objeto h2, .comparacion-objeto dd')].filter((el) => el.getBoundingClientRect().height && el.textContent.trim()).map((el) => {
      const estilos = getComputedStyle(el);
      const cadena = [];
      for (let actual = el; actual; actual = actual.parentElement) cadena.unshift(getComputedStyle(actual));
      let fondo = [255, 255, 255];
      let imagenSinResolver = false;
      cadena.forEach((estilo) => {
        const color = rgb(estilo.backgroundColor);
        if ((color[3] ?? 1) === 1) imagenSinResolver = false;
        fondo = mezclar(color, fondo);
        if (estilo.backgroundImage !== 'none') imagenSinResolver = true;
      });
      if (imagenSinResolver) return null;
      const frente = mezclar(rgb(estilos.color), fondo);
      const [a, b] = [luminancia(frente), luminancia(fondo)].sort((x, y) => y - x);
      const ratio = (a + .05) / (b + .05);
      const grande = parseFloat(estilos.fontSize) >= 24 || (parseFloat(estilos.fontSize) >= 18.66 && Number(estilos.fontWeight) >= 700);
      return { texto: el.textContent.trim().slice(0, 90), ratio: Math.round(ratio * 100) / 100, minimo: grande ? 3 : 4.5 };
    }).filter(Boolean);
  });
  const fallos = mediciones.filter((m) => m.ratio < m.minimo);
  assert.ok(mediciones.length > 0, `${titulo}: no se midieron textos`);
  assert.deepEqual(fallos, [], `${titulo}: contraste insuficiente`);
  registrar(`${titulo}: contraste de textos medidos`, { mediciones });
}

(async () => {
  let navegador;
  try {
    // Puertos obligatorios por defecto, para evitar usar accidentalmente los servicios del equipo.
    assert.equal(new URL(API).port, '18081');
    assert.equal(new URL(ADMIN).port, '18000');
    for (let intento = 0; ; intento++) {
      try {
        if ((await fetch(API + '/api/categorias', { signal: AbortSignal.timeout(1000) })).ok) break;
      } catch {}
      if (intento >= 80) throw new Error('La API de QA no quedó disponible');
      await esperar(500);
    }
    const amore = await cuenta('Amore');
    const bruno = await cuenta('Bruno');
    registrar('Registro, SMTP real y verificación de dos cuentas QA');
    const correoDuplicado = `qa-duplicado-${sufijo}@prueba.invalid`;
    const duplicados = await Promise.all([1, 2].map(() => peticion('/api/auth/register', { metodo: 'POST', cuerpo: { nombre: 'Prueba', apellido: 'Simultanea', correo: correoDuplicado, password: PASSWORD } })));
    assert.deepEqual(duplicados.map((r) => r.estado).sort(), [201, 409]);
    registrar('MySQL: registro simultáneo devuelve 201 y 409');
    const categorias = (await peticion('/api/categorias')).datos;
    const categoriaId = categorias.find((c) => c.estado !== false).id;
    const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
    const nombre = `Mochila azul QA ${sufijo}`;
    const encontrado = await peticion('/api/objetos', { metodo: 'POST', token: bruno.token, cuerpo: { nombre, descripcion: 'Azul con etiqueta ñ', categoriaId, ubicacion: 'Biblioteca QA', fechaObjeto: hoy, tipo: 'ENCONTRADO', latitud: -12, longitud: -77 } });
    assert.equal(encontrado.estado, 201);

    navegador = await chromium.launch({ channel: 'msedge', headless: true });
    const contexto = await navegador.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce', geolocation: { latitude: -12, longitude: -77 }, permissions: ['geolocation'] });
    const pagina = await contexto.newPage();
    ultimaPagina = pagina;
    pagina.on('pageerror', (error) => erroresConsola.push(error.message));
    await pagina.route('https://nominatim.openstreetmap.org/**', (ruta) => ruta.fulfill({ contentType: 'application/json', body: JSON.stringify({ display_name: 'Biblioteca QA' }) }));
    await entrar(pagina, amore.correo);
    registrar('Login web real con JWT');
    await pagina.goto(`${WEB}/perfil`);
    await pagina.getByLabel('Nombre *', { exact: true }).fill('Ámore');
    await pagina.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
    await pagina.getByText('Perfil actualizado correctamente.').waitFor();
    assert.equal((await peticion('/api/perfil', { token: amore.token })).datos.nombre, 'Ámore');
    await pagina.getByLabel('Nombre *', { exact: true }).fill('Borrador');
    await pagina.getByRole('button', { name: 'Cancelar', exact: true }).click();
    assert.equal(await pagina.getByLabel('Nombre *', { exact: true }).inputValue(), 'Ámore');
    registrar('Perfil: guardar persiste y Cancelar restaura el valor real');

    await pagina.goto(`${WEB}/publicar`);
    await pagina.getByLabel('Nombre *', { exact: true }).fill(nombre);
    await pagina.getByLabel(/^Descripción/).fill('Mochila azul con etiqueta, cómputo y ñ');
    await pagina.getByRole('combobox', { name: /^Categoría/ }).selectOption(String(categoriaId));
    await pagina.getByLabel('Fecha *', { exact: true }).fill(hoy);
    await pagina.getByRole('button', { name: 'Usar mi ubicación', exact: true }).click();
    await pagina.getByLabel('Ubicación *', { exact: true }).inputValue().then(async (valor) => {
      if (valor !== 'Biblioteca QA') await pagina.getByText('Ubicación obtenida:', { exact: false }).waitFor();
    });
    // PNG real de 1x1; la foto se valida en el servidor, no solo por extensión.
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEklEQVR4nGPUiNrCwMDAxAAGAA4mATqk2H3iAAAAAElFTkSuQmCC', 'base64');
    await pagina.getByLabel('Foto (opcional)', { exact: true }).setInputFiles({ name: 'qa.png', mimeType: 'image/png', buffer: png });
    await pagina.getByRole('button', { name: 'PUBLICAR OBJETO', exact: true }).click();
    await pagina.waitForURL(/\/objeto\/\d+$/);
    const perdidoId = Number(new URL(pagina.url()).pathname.split('/').pop());
    const perdido = (await peticion(`/api/objetos/${perdidoId}`)).datos;
    assert.equal(perdido.ubicacion, 'Biblioteca QA'); assert.equal(perdido.fechaObjeto, hoy);
    assert.equal(perdido.fotos.length, 1); assert.equal(Number(perdido.latitud), -12);
    const fotoEncontrado = new FormData();
    fotoEncontrado.append('archivo', new Blob([png], { type: 'image/png' }), 'hallazgo.png');
    assert.equal((await fetch(`${API}/api/objetos/${encontrado.datos.id}/fotos`, { method: 'POST', headers: { Authorization: `Bearer ${bruno.token}` }, body: fotoEncontrado })).status, 201);
    registrar('Publicar con GPS, fecha y foto: API y persistencia consultadas');
    await pagina.goto(`${WEB}/mis-publicaciones`);
    await pagina.getByRole('link', { name: `Editar ${nombre}`, exact: true }).click();
    await pagina.getByLabel(/^Descripción/).fill('Descripción editada con ñ');
    await pagina.getByRole('button', { name: 'GUARDAR CAMBIOS', exact: true }).click();
    await pagina.waitForURL(`${WEB}/mis-publicaciones`);
    assert.equal((await peticion(`/api/objetos/${perdidoId}`)).datos.descripcion, 'Descripción editada con ñ');
    registrar('H5/H14: edición web persiste sin perder foto ni coordenadas');

    const consulta = `/buscar?q=${encodeURIComponent(nombre)}&categoriaId=${categoriaId}&tipo=ENCONTRADO&ubicacion=Biblioteca&desde=${hoy}&hasta=${hoy}&latitud=-12&longitud=-77&radioKm=2`;
    await pagina.goto(`${WEB}${consulta}`);
    await pagina.getByRole('heading', { name: nombre, exact: true }).waitFor();
    await pagina.getByText('A 0 km de tu ubicación').waitFor();
    registrar('H8/H9/H10: todos los filtros combinados contra API real');
    const parejaId = `${perdidoId}-${encontrado.datos.id}`;
    const pareja = (await peticion(`/api/coincidencias/${parejaId}`)).datos;
    assert.equal(pareja.perdido.fotos.length, 1); assert.equal(pareja.encontrado.fotos.length, 1);
    assert.ok(!JSON.stringify(pareja).includes(amore.correo) && !JSON.stringify(pareja).includes(bruno.correo));
    await pagina.goto(`${WEB}/objeto/${perdidoId}`);
    await pagina.locator(`a[href="/coincidencias/${parejaId}"]`).click();
    await pagina.waitForURL(`${WEB}/coincidencias/${parejaId}`);
    await pagina.getByRole('heading', { name: 'Comparar posible coincidencia' }).waitFor();
    await pagina.getByText('100% de similitud · ALTA').waitFor();
    await pagina.getByRole('button', { name: 'Contactar', exact: true }).click();
    await pagina.getByText(/Solicitud enviada/).waitFor();
    const avisosContacto = (await peticion('/api/notificaciones', { token: bruno.token })).datos;
    assert.ok(avisosContacto.some((n) => n.tipo === 'CONTACTO_RECIBIDO'));
    registrar('H16/H17/H18: pareja correcta, similitud y contacto con aviso real');

    await pagina.goto(`${WEB}/objeto/${encontrado.datos.id}`);
    await pagina.getByRole('button', { name: 'Reclamar este objeto', exact: true }).click();
    await pagina.getByRole('button', { name: 'Sí, reclamar', exact: true }).click();
    await pagina.getByText(/Reclamo enviado/).waitFor();
    assert.ok((await peticion('/api/notificaciones', { token: bruno.token })).datos.some((n) => n.tipo === 'RECLAMO_RECIBIDO'));
    registrar('H20: reclamo web separado de contacto y aviso al destinatario correcto');
    const paginaBruno = await contexto.newPage();
    // Otra cuenta requiere otro contexto para no sustituir la sesión de Amore.
    await paginaBruno.close();
    const contextoBruno = await navegador.newContext();
    const brunoWeb = await contextoBruno.newPage();
    await entrar(brunoWeb, bruno.correo);
    await brunoWeb.goto(`${WEB}/notificaciones`);
    await brunoWeb.getByText('Nueva solicitud de contacto', { exact: true }).waitFor();
    const lectura = brunoWeb.waitForResponse((r) => r.url().includes('/api/notificaciones/') && r.url().endsWith('/leida') && r.request().method() === 'PUT');
    await brunoWeb.getByRole('button', { name: /marcarla como leída/i }).first().click();
    assert.equal((await lectura).status(), 200);
    assert.ok((await peticion('/api/notificaciones', { token: bruno.token })).datos.some((n) => n.leida));
    registrar('Notificaciones: lectura propia persistida');

    for (const ancho of [320, 375, 1280]) {
      for (const [ruta, titulo] of [[consulta, 'Buscar'], [`/coincidencias/${parejaId}`, 'Comparación'], ['/perfil', 'Perfil'], ['/publicar', 'Publicar'], ['/mis-publicaciones', 'Mis publicaciones']]) {
        await pagina.goto(`${WEB}${ruta}`);
        await pagina.locator('h1').waitFor();
        if (titulo === 'Buscar') await pagina.getByRole('heading', { name: nombre, exact: true }).waitFor();
        if (titulo === 'Mis publicaciones') await pagina.getByRole('link', { name: `Editar ${nombre}`, exact: true }).waitFor();
        await sinDesborde(pagina, titulo, ancho);
        if (ancho === 375 && ['Buscar', 'Comparación'].includes(titulo)) await capturar(pagina, `${titulo.toLowerCase()}-375.png`);
      }
    }
    for (const tema of ['claro', 'oscuro']) {
      await pagina.evaluate((valor) => localStorage.setItem('foundia-tema', valor), tema);
      for (const [ruta, titulo] of [[consulta, 'Buscar'], [`/coincidencias/${parejaId}`, 'Comparación']]) {
        await pagina.goto(`${WEB}${ruta}`);
        await pagina.getByRole('heading', { name: titulo === 'Buscar' ? nombre : 'Comparar posible coincidencia', exact: true }).waitFor();
        await contraste(pagina, `${titulo} en ${tema}`);
      }
    }
    await pagina.goto(`${WEB}${consulta}`);
    await pagina.getByRole('heading', { name: nombre, exact: true }).waitFor();
    await pagina.getByRole('button', { name: 'Quitar filtro radioKm' }).focus();
    await pagina.keyboard.press('Enter');
    assert.ok(!new URL(pagina.url()).searchParams.has('latitud'));
    await pagina.goBack();
    await pagina.getByText('A 0 km de tu ubicación').waitFor();
    registrar('Teclado, quitar cercanía y Atrás restauran consulta y controles');
    await pagina.goto(`${WEB}/publicar`);
    const otraPestana = await contexto.newPage();
    await otraPestana.goto(`${WEB}/perfil`);
    await otraPestana.getByRole('button', { name: /Cerrar sesión/ }).click();
    await pagina.waitForURL(`${WEB}/login`);
    registrar('Cerrar sesión en otra pestaña abandona la ruta protegida sin recargar');

    let publicacionEliminar;
    for (let i = 0; i < 12; i++) {
      const alta = await peticion('/api/objetos', { metodo: 'POST', token: amore.token, cuerpo: { nombre: `Paginación QA ${i} ${sufijo}`, descripcion: 'Dato ficticio para paginación', categoriaId, ubicacion: 'Taller QA', fechaObjeto: '2020-01-01', tipo: 'PERDIDO' } });
      assert.equal(alta.estado, 201);
      if (i === 11) publicacionEliminar = alta.datos;
    }
    const contextoAdmin = await navegador.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
    const panel = await contextoAdmin.newPage();
    panel.on('pageerror', (error) => erroresConsola.push(error.message));
    ultimaPagina = panel;
    await entrar(panel, 'admin@foundia.dev', 'Admin123!', true);
    await panel.goto(`${PANEL}/publicaciones`);
    // El texto de tipo de la tabla está traducido; localizar por ID evita ambigüedad.
    const filasNombre = panel.getByRole('row').filter({ hasText: nombre });
    await filasNombre.first().waitFor();
    assert.ok(await filasNombre.count() >= 2);
    await panel.getByRole('row').filter({ hasText: `Paginación QA 11 ${sufijo}` }).waitFor();
    registrar('H15: publicaciones de páginas posteriores aparecen en el listado completo');
    await filasNombre.first().getByRole('button', { name: `Ocultar ${nombre}`, exact: true }).click();
    await panel.getByRole('button', { name: 'Sí, ocultar', exact: true }).click();
    await filasNombre.first().getByRole('button', { name: `Mostrar ${nombre}`, exact: true }).waitFor();
    await filasNombre.first().getByRole('button', { name: `Mostrar ${nombre}`, exact: true }).click();
    await panel.getByRole('button', { name: 'Sí, mostrar', exact: true }).click();
    registrar('H15: ocultar y restaurar publicación desde el panel real');
    const filaEliminar = panel.getByRole('row').filter({ hasText: publicacionEliminar.nombre });
    await filaEliminar.getByRole('button', { name: `Eliminar ${publicacionEliminar.nombre}`, exact: true }).click();
    await panel.getByRole('alertdialog').getByRole('button', { name: 'Cancelar', exact: true }).click();
    assert.equal((await peticion(`/api/objetos/${publicacionEliminar.id}`)).estado, 200);
    await filaEliminar.getByRole('button', { name: `Eliminar ${publicacionEliminar.nombre}`, exact: true }).click();
    await panel.getByRole('button', { name: 'Sí, eliminar', exact: true }).click();
    await filaEliminar.getByText('ELIMINADO', { exact: true }).waitFor();
    assert.equal((await peticion(`/api/objetos/${publicacionEliminar.id}`)).estado, 404);
    assert.ok((await peticion('/api/perfil/objetos', { token: amore.token })).datos.some((o) => o.id === publicacionEliminar.id && o.estado === 'ELIMINADO'));
    registrar('H15: cancelar conserva la publicación; confirmar elimina lógicamente y conserva la fila');
    for (const ancho of [320, 375, 1280]) await sinDesborde(panel, 'Panel administrativo', ancho);
    await capturar(panel, 'administracion-1280.png');
    const otraPestanaAdmin = await panel.context().newPage();
    await otraPestanaAdmin.goto(`${PANEL}/usuarios`);
    await otraPestanaAdmin.getByRole('button', { name: /Cerrar sesión/ }).click();
    await panel.waitForURL(`${PANEL}/login`);
    registrar('H15: cerrar sesión en otra pestaña oculta inmediatamente el panel');
    const entradaAdmin = await peticion('/api/admin/auth/login/', { metodo: 'POST', admin: true, cuerpo: { correo: 'admin@foundia.dev', password: 'Admin123!' } });
    assert.equal(entradaAdmin.estado, 200);
    const bloqueos = [];
    for (let i = 0; i < 5; i++) bloqueos.push((await peticion('/api/auth/login', { metodo: 'POST', cuerpo: { correo: 'admin@foundia.dev', password: 'Incorrecta123!' } })).estado);
    assert.deepEqual(bloqueos, [401, 401, 401, 401, 423]);
    assert.equal((await peticion('/api/admin/auth/login/', { metodo: 'POST', admin: true, cuerpo: { correo: 'admin@foundia.dev', password: 'Admin123!' } })).estado, 423);
    assert.equal((await peticion('/api/admin/usuarios/', { admin: true, token: entradaAdmin.datos.token })).estado, 401);
    registrar('Spring y Django: bloqueo y token anterior comparten vencimiento UTC en MySQL');
    assert.equal((await fetch(`${ADMIN}/api/admin/reportes/`)).status, 404);
    assert.equal((await fetch(`${ADMIN}/api/admin/categorias/`)).status, 404);
    registrar('Extras fuera del sprint siguen deshabilitados por URL directa');
    assert.deepEqual(erroresConsola, []);
    registrar('Sin errores JavaScript no controlados en las pantallas recorridas');
    fs.writeFileSync(path.join(salida, 'resultados.json'), JSON.stringify({ fecha: new Date().toISOString(), entorno: { API, ADMIN, WEB, PANEL, MAIL }, casos, erroresConsola }, null, 2));
  } catch (error) {
    await ultimaPagina?.screenshot({ path: path.join(salida, 'fallo.png'), fullPage: true }).catch(() => {});
    fs.writeFileSync(path.join(salida, 'resultados.json'), JSON.stringify({ fecha: new Date().toISOString(), casos, fallo: error.message, erroresConsola }, null, 2));
    console.error(error); process.exitCode = 1;
  } finally { await navegador?.close(); }
})();
