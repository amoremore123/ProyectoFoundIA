// H55: utilidades del chat.
export const MAX_TEXTO = 1000;
export const MAX_FOTO_BYTES = 5 * 1024 * 1024;
export const TIPOS_FOTO = ['image/jpeg', 'image/png'];
export const INTERVALO_MS = 4000; // consulta periódica con el cursor despuesDe

// La API envía las fechas en UTC sin zona: se interpretan como UTC y se muestran en la hora local
export function fechaChat(valor) {
  if (!valor) return null;
  const fecha = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(valor) ? valor : `${valor}Z`);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

export function horaChat(valor, ahora = new Date()) {
  const fecha = fechaChat(valor);
  if (!fecha) return '';
  const mismoDia = fecha.toDateString() === ahora.toDateString();
  return mismoDia
    ? fecha.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
    : fecha.toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}

export function validarTexto(texto) {
  const limpio = texto.trim();
  if (!limpio) return 'Escribe un mensaje.';
  if (limpio.length > MAX_TEXTO) return `El mensaje no puede superar los ${MAX_TEXTO} caracteres.`;
  return '';
}

export function validarFotoChat(archivo) {
  if (!archivo) return '';
  if (!TIPOS_FOTO.includes(archivo.type)) return 'La foto debe ser JPEG o PNG.';
  if (archivo.size > MAX_FOTO_BYTES) return 'La foto no debe superar los 5 MiB.';
  return '';
}

// Junta mensajes nuevos con los que ya están, sin repetir (el cursor y el envío propio pueden traer el mismo)
export function unirMensajes(actuales, nuevos) {
  if (!nuevos?.length) return actuales;
  const vistos = new Set(actuales.map((m) => m.id));
  return [...actuales, ...nuevos.filter((m) => !vistos.has(m.id))].sort((a, b) => a.id - b.id);
}
