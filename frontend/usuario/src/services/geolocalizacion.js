// Geolocalización del navegador + geocodificación inversa (OpenStreetMap)
export function obtenerUbicacionActual() {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('Tu navegador no permite usar la ubicación'));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const latitud = Number(pos.coords.latitude.toFixed(7));
        const longitud = Number(pos.coords.longitude.toFixed(7));
        let direccion = '';

        try {
          const respuesta = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitud}&lon=${longitud}&accept-language=es`
          );
          if (respuesta.ok) {
            const datos = await respuesta.json();
            direccion = datos?.display_name || '';
          }
        } catch {
          // Sin internet seguimos solo con las coordenadas
        }

        resolve({ latitud, longitud, direccion });
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
