// FINDI, asistente de FoundIA: imágenes del avatar aprobado (en /public/findi).
export const AVATAR_FINDI = '/findi/findi-avatar.webp';
export const IMAGEN_FINDI = '/findi/findi.webp';

// Expresiones de FINDI en el chat. Cada una tiene su recurso de medio cuerpo ("cuerpo") y de rostro ("cara").
// PROVISIONAL: mientras no lleguen los archivos aprobados se reutilizan las imágenes originales y la
// expresión se comunica con una insignia animada (ver ChatAsistente). Cuando existan, basta con cambiar
// las rutas aquí, p. ej. cuerpo: '/findi/expresiones/escuchando.webp'. No se generan avatares nuevos.
const provisional = { cuerpo: IMAGEN_FINDI, cara: AVATAR_FINDI };

export const EXPRESIONES_FINDI = {
  alegre: { ...provisional, nombre: 'Alegre' },
  saludando: { ...provisional, nombre: 'Saludando' },
  escuchando: { ...provisional, nombre: 'Escuchando' },
  pensando: { ...provisional, nombre: 'Pensando' },
  buscando: { ...provisional, nombre: 'Buscando' },
  escribiendo: { ...provisional, nombre: 'Escribiendo' },
  hablando: { ...provisional, nombre: 'Hablando' },
  exito: { ...provisional, nombre: 'Éxito' },
  coincidencia: { ...provisional, nombre: 'Coincidencia' },
  'sin-resultados': { ...provisional, nombre: 'Sin resultados' },
  alerta: { ...provisional, nombre: 'Alerta' },
};
