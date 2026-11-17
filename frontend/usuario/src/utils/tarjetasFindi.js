// H47: catálogo cerrado de tarjetas y acciones que la web sabe dibujar.
// Lo que no esté aquí, o traiga un destino distinto al esperado, se descarta.
export const TARJETAS = ['OBJETO', 'RECLAMOS', 'QR_RETIRO'];
export const ACCIONES = ['PRELLENAR_PUBLICAR', 'ABRIR_RECLAMO'];

const RUTA_OBJETO = /^\/objeto\/\d{1,9}$/;

function destinoValido(tipo, destino) {
  if (tipo === 'PRELLENAR_PUBLICAR') return destino === '/publicar';
  if (tipo === 'ABRIR_RECLAMO') return RUTA_OBJETO.test(destino || '');
  return false;
}

export function tarjetasValidas(lista) {
  if (!Array.isArray(lista)) return [];
  return lista.filter((t) => t && TARJETAS.includes(t.tipo)
    && (t.enlace == null || t.enlace === '/mis-publicaciones' || RUTA_OBJETO.test(t.enlace)));
}

export function accionesValidas(lista) {
  if (!Array.isArray(lista)) return [];
  return lista.filter((a) => a && ACCIONES.includes(a.tipo) && destinoValido(a.tipo, a.destino));
}

// Solo se prellenan campos conocidos y con valores del tipo esperado
export function datosPrellenado(datos) {
  const salida = {};
  if (!datos || typeof datos !== 'object') return salida;
  if (datos.tipo === 'PERDIDO' || datos.tipo === 'ENCONTRADO') salida.tipo = datos.tipo;
  if (Number.isInteger(datos.categoriaId) && datos.categoriaId > 0) salida.categoriaId = String(datos.categoriaId);
  if (typeof datos.ubicacion === 'string' && datos.ubicacion.trim()) salida.ubicacion = datos.ubicacion.trim().slice(0, 255);
  return salida;
}

// H47: si FINDI muestra la tarjeta de la publicación que la persona mencionó (#12),
// la búsqueda por palabras no debe contradecirla con «No encontré publicaciones…»
// ni proponer publicar el objeto como si no existiera.
export function ajustarATarjetaObjeto(respuesta, tarjetas) {
  const objetos = (tarjetas || []).filter((t) => t.tipo === 'OBJETO');
  if (!objetos.length || respuesta?.resultados?.length || !respuesta?.consulta) return respuesta;
  return {
    ...respuesta,
    texto: objetos.length === 1
      ? `Esta es la publicación que mencionaste: «${objetos[0].titulo}».`
      : 'Estas son las publicaciones que mencionaste:',
    acciones: (respuesta.acciones || []).filter((a) => a.to !== '/publicar'),
  };
}
