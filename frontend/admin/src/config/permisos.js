// H36: qué ve cada rol en el panel. BIBLIOTECA solo atiende custodia y reclamos;
// las rutas de sistema responden 403 para ese rol, así que ni se muestran ni se piden.
export const RUTAS_BIBLIOTECA = ['/custodia', '/reclamos', '/vencidos'];

export function esAdmin(usuario) {
  return usuario?.rol === 'ADMIN';
}

export function puedeVer(usuario, ruta) {
  if (esAdmin(usuario)) return true;
  if (usuario?.rol === 'BIBLIOTECA') return RUTAS_BIBLIOTECA.includes(ruta);
  return false;
}

export function inicioDe(usuario) {
  return usuario?.rol === 'BIBLIOTECA' ? '/custodia' : '/';
}
