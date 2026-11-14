// Aviso de cookies y almacenamiento local (Ley 29733).
// FoundIA no crea cookies propias ni usa analítica o publicidad. En el navegador guarda:
//   - token y usuario: la sesión. Esencial: sin ellos no se puede iniciar sesión.
//   - foundia-almacenamiento: esta misma elección. Esencial: evita volver a preguntar.
//   - foundia-tema: el modo claro u oscuro. Preferencia: solo si la persona lo permite.
//   - foundia-borrador-publicar (sessionStorage): lo escrito en Publicar. Esencial y temporal (H52).
export const CLAVE_CONSENTIMIENTO = 'foundia-almacenamiento';
export const CLAVE_TEMA = 'foundia-tema';
export const EVENTO_CONSENTIMIENTO = 'foundia-almacenamiento';

export const OPCIONES = { TODO: 'preferencias', ESENCIAL: 'esencial' };

export function leerConsentimiento() {
  try {
    const valor = localStorage.getItem(CLAVE_CONSENTIMIENTO);
    return Object.values(OPCIONES).includes(valor) ? valor : null;
  } catch {
    return null; // sin localStorage (modo privado estricto) no se recuerda nada
  }
}

export function puedeGuardarPreferencias() {
  return leerConsentimiento() === OPCIONES.TODO;
}

export function guardarConsentimiento(valor) {
  if (!Object.values(OPCIONES).includes(valor)) return;
  try {
    localStorage.setItem(CLAVE_CONSENTIMIENTO, valor);
    // Elegir «solo lo esencial» borra la preferencia que se hubiera guardado antes
    if (valor === OPCIONES.ESENCIAL) localStorage.removeItem(CLAVE_TEMA);
  } catch {
    // Sin persistencia la elección vale solo para esta pestaña
  }
  window.dispatchEvent(new CustomEvent(EVENTO_CONSENTIMIENTO, { detail: valor }));
}
