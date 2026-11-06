pm.test('H33: documentos públicos disponibles', () => pm.response.to.have.status(200));
const documentos = pm.response.json();
pm.test('H33: nueva versión identifica las fuentes de Tecsup', () => {
  pm.expect(documentos.version).to.equal('foundia-tecsup-2026-11-05-v2');
  pm.expect(documentos.estado).to.equal('FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA');
});
pm.test('H33: ambas fuentes son oficiales y tienen fecha de consulta', () => {
  pm.expect(documentos.fuentes).to.have.length(2);
  for (const fuente of documentos.fuentes) {
    pm.expect(fuente.url).to.match(/^https:\/\/www\.tecsup\.edu\.pe\//);
    pm.expect(fuente.consultadoEl).to.equal('2026-11-05');
  }
});
pm.test('H33: la huella de aceptación es SHA-256', () => {
  pm.expect(documentos.sha256).to.match(/^[a-f0-9]{64}$/);
});
pm.test('H33: no añade publicidad ni inventa aprobación institucional', () => {
  pm.expect(documentos.aviso).to.include('no autoriza publicidad');
  pm.expect(documentos.aviso).to.include('ni acredita aprobación institucional');
});
pm.test('H33: incluye la transcripción de los términos institucionales', () => {
  pm.expect(documentos.terminos).to.include('Autorizo a TECSUP');
  pm.expect(documentos.terminos).to.include('no otorga las autorizaciones de publicidad');
});
pm.test('H33: incluye también el final del PDF institucional', () => {
  pm.expect(documentos.privacidad).to.include('VIII.');
  pm.expect(documentos.privacidad).to.include('No cumplir con las disposiciones');
});
pm.test('H33: metadatos y huellas de las fuentes están en los textos aceptados', () => {
  for (const fuente of documentos.fuentes) {
    const texto = fuente.id === 'terminos-tecsup' ? documentos.terminos : documentos.privacidad;
    pm.expect(fuente.sha256Original).to.match(/^[a-f0-9]{64}$/);
    pm.expect(fuente.sha256Texto).to.match(/^[a-f0-9]{64}$/);
    pm.expect(texto).to.include(fuente.url);
    pm.expect(texto).to.include(fuente.sha256Original);
    pm.expect(texto).to.include(fuente.sha256Texto);
  }
});
