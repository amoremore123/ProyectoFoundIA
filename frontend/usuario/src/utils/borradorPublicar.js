// H52: al abrir la cámara, Android puede cerrar la pestaña del navegador para liberar memoria
// y recargarla al volver. Este borrador guarda lo escrito en Publicar en sessionStorage
// (solo esa pestaña; se borra al publicar o al cerrarla) para no perderlo. La foto no se
// guarda: después de una recarga hay que volver a tomarla.
export const CLAVE_BORRADOR = 'foundia-borrador-publicar';

const CAMPOS = ['nombre', 'descripcion', 'categoriaId', 'ubicacion', 'fechaObjeto'];

export function leerBorrador() {
  try {
    const crudo = sessionStorage.getItem(CLAVE_BORRADOR);
    if (!crudo) return null;
    const datos = JSON.parse(crudo);
    if (!datos || typeof datos !== 'object' || !datos.form) return null;
    const form = {};
    for (const c of CAMPOS) form[c] = typeof datos.form[c] === 'string' ? datos.form[c] : '';
    const tipo = datos.tipo === 'ENCONTRADO' ? 'ENCONTRADO' : 'PERDIDO';
    // Un borrador vacío no se ofrece
    return CAMPOS.some((c) => form[c].trim()) ? { tipo, form } : null;
  } catch {
    return null;
  }
}

export function guardarBorrador(tipo, form) {
  try {
    const hayAlgo = CAMPOS.some((c) => String(form[c] || '').trim());
    if (!hayAlgo) {
      sessionStorage.removeItem(CLAVE_BORRADOR);
      return;
    }
    const soloCampos = Object.fromEntries(CAMPOS.map((c) => [c, String(form[c] || '')]));
    sessionStorage.setItem(CLAVE_BORRADOR, JSON.stringify({ tipo, form: soloCampos }));
  } catch {
    // Sin sessionStorage el formulario sigue funcionando, solo sin recuperación
  }
}

export function borrarBorrador() {
  try {
    sessionStorage.removeItem(CLAVE_BORRADOR);
  } catch {
    // nada que borrar
  }
}
