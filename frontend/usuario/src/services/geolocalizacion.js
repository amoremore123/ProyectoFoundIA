// Geolocalización del navegador + geocodificación inversa (OpenStreetMap)
export function obtenerCoordenadasActuales() {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('Tu navegador no permite usar la ubicación'));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const latitud = Number(pos.coords.latitude.toFixed(7));
        const longitud = Number(pos.coords.longitude.toFixed(7));
        resolve({ latitud, longitud });
      },
      (error) => {
        const mensajes = {
          1: 'No se pudo acceder a tu ubicación (permiso denegado)',
          2: 'No se pudo determinar tu ubicación',
          3: 'La solicitud de ubicación tardó demasiado',
        };
        reject(new Error(mensajes[error.code] || 'No se pudo obtener tu ubicación'));
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
    );
  });
}

export async function obtenerUbicacionActual() {
  const coordenadas = await obtenerCoordenadasActuales();
  const controlador = new AbortController();
  let temporizador;
  try {
    // También acota la lectura del cuerpo; una red lenta no bloquea Publicar.
    const direccion = await Promise.race([
      (async () => {
        const respuesta = await fetch(
          `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${coordenadas.latitud}&lon=${coordenadas.longitud}&accept-language=es`,
          { signal: controlador.signal },
        );
        if (!respuesta.ok) return '';
        const datos = await respuesta.json();
        return datos?.display_name || '';
      })(),
      new Promise((resolve) => {
        temporizador = setTimeout(() => { controlador.abort(); resolve(''); }, 5000);
      }),
    ]);
    return { ...coordenadas, direccion };
  } catch {
    return { ...coordenadas, direccion: '' };
  } finally {
    clearTimeout(temporizador);
  }
}
