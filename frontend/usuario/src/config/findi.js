// FINDI, asistente de FoundIA: imágenes del avatar aprobado (en /public/findi).
export const AVATAR_FINDI = '/findi/findi-avatar.webp';
export const IMAGEN_FINDI = '/findi/findi.webp';

// Expresiones 3D aprobadas de FINDI (medio cuerpo, fondo transparente).
// Originales de 512 px en /findi/expresiones/; la web usa copias optimizadas de 384 px en
// /findi/expresiones/web/ (≈40 KB cada una). Para cambiar una expresión basta con reemplazar
// su archivo con el mismo nombre (y volver a generar la copia optimizada).
const ruta = (nombre) => `/findi/expresiones/web/${nombre}.webp`;

export const EXPRESIONES_FINDI = {
  // La expresión alegre de reposo reutiliza el saludo
  alegre: { src: ruta('saludando'), nombre: 'Alegre' },
  saludando: { src: ruta('saludando'), nombre: 'Saludando' },
  escuchando: { src: ruta('escuchando'), nombre: 'Escuchando' },
  pensando: { src: ruta('pensando'), nombre: 'Pensando' },
  buscando: { src: ruta('buscando'), nombre: 'Buscando' },
  escribiendo: { src: ruta('escribiendo'), nombre: 'Escribiendo' },
  hablando: { src: ruta('hablando'), nombre: 'Hablando' },
  exito: { src: ruta('exito'), nombre: 'Éxito' },
  coincidencia: { src: ruta('coincidencia'), nombre: 'Coincidencia' },
  'sin-resultados': { src: ruta('sin-resultados'), nombre: 'Sin resultados' },
  alerta: { src: ruta('alerta'), nombre: 'Alerta' },
};

// Rutas únicas, para precargarlas al abrir el chat y evitar parpadeos al cambiar
export const RUTAS_EXPRESIONES = [...new Set(Object.values(EXPRESIONES_FINDI).map((e) => e.src))];
