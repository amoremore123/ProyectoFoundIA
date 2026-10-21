export const CAMPOS_AVANZADOS = ['ubicacion', 'desde', 'hasta', 'latitud', 'longitud', 'radioKm'];

export function validarFiltros(filtros) {
  if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) {
    return 'La fecha inicial no puede ser posterior a la fecha final.';
  }
  const tieneLatitud = filtros.latitud !== '';
  const tieneLongitud = filtros.longitud !== '';
  if (tieneLatitud !== tieneLongitud) return 'Completa la latitud y la longitud para buscar por cercanía.';
  if (tieneLatitud && (!Number.isFinite(Number(filtros.latitud)) || Math.abs(Number(filtros.latitud)) > 90)) {
    return 'La latitud debe estar entre -90 y 90.';
  }
  if (tieneLongitud && (!Number.isFinite(Number(filtros.longitud)) || Math.abs(Number(filtros.longitud)) > 180)) {
    return 'La longitud debe estar entre -180 y 180.';
  }
  if (filtros.radioKm && (!tieneLatitud || !Number.isFinite(Number(filtros.radioKm))
      || Number(filtros.radioKm) <= 0 || Number(filtros.radioKm) > 1000)) {
    return 'El radio debe ser mayor que 0 y como máximo 1000 km, con ambas coordenadas.';
  }
  return '';
}

// Misma fórmula y radio terrestre que BusquedaService. Solo se muestra si hay GPS real.
export function distanciaKm(origen, objeto) {
  if (origen.latitud === '' || origen.longitud === '' || objeto.latitud == null || objeto.longitud == null) return null;
  const [lat, lon, otraLat, otraLon] = [origen.latitud, origen.longitud, objeto.latitud, objeto.longitud].map(Number);
  if (![lat, lon, otraLat, otraLon].every(Number.isFinite)) return null;
  const rad = Math.PI / 180;
  const a = Math.sin((otraLat - lat) * rad / 2) ** 2
    + Math.cos(lat * rad) * Math.cos(otraLat * rad) * Math.sin((otraLon - lon) * rad / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
}
