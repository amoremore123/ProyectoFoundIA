import { useLocation } from 'react-router-dom';

// H73: la app Android abre algunas páginas de la web dentro de un WebView con ?embebido=1.
// En ese modo la web no muestra su menú, su pie, el chat de FINDI ni el aviso de cookies:
// la app ya tiene los suyos. La elección se guarda en sessionStorage (solo en esa pestaña o
// WebView) para que sobreviva a la navegación interna; ?embebido=0 la quita.
export const CLAVE_EMBEBIDO = 'foundia-embebido';

export function leerEmbebido(search = '') {
  const valor = new URLSearchParams(search).get('embebido');
  try {
    if (valor === '1') sessionStorage.setItem(CLAVE_EMBEBIDO, '1');
    else if (valor === '0') sessionStorage.removeItem(CLAVE_EMBEBIDO);
    return sessionStorage.getItem(CLAVE_EMBEBIDO) === '1';
  } catch {
    // Sin sessionStorage solo vale el parámetro de la dirección actual
    return valor === '1';
  }
}

export function useEmbebido() {
  const { search } = useLocation();
  return leerEmbebido(search);
}
