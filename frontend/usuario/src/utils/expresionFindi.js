// Mecanismo central de expresiones de FINDI: a partir del estado REAL del chat decide qué estado
// mostrar (etiqueta) y qué expresión usa el avatar. Nada se activa por tiempo inventado: cada estado
// sale del micrófono, de una petición en curso, de la voz o del resultado de la última consulta.
export const ESTADOS_FINDI = {
  escuchando: { expresion: 'escuchando', etiqueta: 'Escuchando…' },
  pensando: { expresion: 'pensando', etiqueta: 'Pensando…' },
  buscando: { expresion: 'buscando', etiqueta: 'Buscando coincidencias…' },
  error: { expresion: 'alerta', etiqueta: 'Sin conexión' },
  preparando: { expresion: 'escribiendo', etiqueta: 'Preparando la voz…' },
  escribiendo: { expresion: 'escribiendo', etiqueta: 'Escribiendo…' },
  notificacion: { expresion: 'alerta', etiqueta: 'Tienes una posible coincidencia nueva' },
  hablando: { expresion: 'hablando', etiqueta: 'Respondiendo…' },
  coincidencia: { expresion: 'coincidencia', etiqueta: 'Búsqueda completada' },
  'sin-resultados': { expresion: 'sin-resultados', etiqueta: 'Sin coincidencias por ahora' },
  exito: { expresion: 'exito', etiqueta: 'Listo' },
  saludando: { expresion: 'saludando', etiqueta: 'Saludando' },
  'en-linea': { expresion: 'alegre', etiqueta: '' },
};

/**
 * @param {object} s
 * @param {boolean} s.escuchando  micrófono activo
 * @param {boolean} s.pensando    hay una consulta en curso
 * @param {boolean} s.buscando    la consulta en curso es una búsqueda de objetos
 * @param {boolean} s.error       la última respuesta fue un error
 * @param {boolean} s.preparando  se está generando el audio de la respuesta (ElevenLabs)
 * @param {boolean} s.hablando    se está reproduciendo la voz
 * @param {boolean} s.escribiendo se está presentando una respuesta en pantalla
 * @param {boolean} s.notificacion llegó una notificación importante (nueva coincidencia sin leer)
 * @param {{tipo: string, n?: number}|null} s.desenlace  resultado reciente de la última consulta
 * @param {boolean} s.saludando   primera apertura del chat
 */
export function calcularEstadoFindi({
  escuchando = false, pensando = false, buscando = false, error = false,
  preparando = false, hablando = false, escribiendo = false, notificacion = false,
  desenlace = null, saludando = false,
} = {}) {
  if (escuchando) return 'escuchando';
  if (pensando) return buscando ? 'buscando' : 'pensando';
  if (error) return 'error';
  if (hablando) return 'hablando';
  if (preparando) return 'preparando';
  if (escribiendo) return 'escribiendo';
  if (notificacion) return 'notificacion';
  if (desenlace?.tipo && ESTADOS_FINDI[desenlace.tipo]) return desenlace.tipo;
  if (saludando) return 'saludando';
  return 'en-linea';
}

export function etiquetaEstadoFindi(estado, desenlace = null) {
  if (estado === 'coincidencia' && desenlace?.n) {
    return `${ESTADOS_FINDI.coincidencia.etiqueta} · ${desenlace.n} ${desenlace.n === 1 ? 'resultado' : 'resultados'}`;
  }
  return ESTADOS_FINDI[estado]?.etiqueta || '';
}

// Qué dejó la última respuesta: una coincidencia real, ninguna, o una acción completada.
export function desenlaceDe(respuesta, { buscaba = false } = {}) {
  const objetos = (respuesta?.resultados?.length || 0)
    + (respuesta?.tarjetas || []).filter((t) => t.tipo === 'OBJETO').length;
  if (objetos) return { tipo: 'coincidencia', n: respuesta?.resultados?.length || objetos };
  if (buscaba && respuesta?.consulta) return { tipo: 'sin-resultados' };
  if (respuesta?.misReportes?.length || (respuesta?.tarjetas || []).some((t) => t.tipo === 'QR_RETIRO')) return { tipo: 'exito' };
  return null;
}
