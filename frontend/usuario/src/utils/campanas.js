import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { registrarVisitaCampana } from '../services/api';

// H67: alcance de las campañas sin datos personales. Al llegar con ?origen= se guarda el origen y un
// identificador al azar de la visita (solo en esta pestaña) y se avisa una vez por origen. Si la persona
// se registra después, el registro lleva ese origen. No se guarda IP, navegador ni correo.
export const CLAVE_ORIGEN = 'foundia-origen';
export const CLAVE_VISITAS = 'foundia-visitas';
const ORIGEN_VALIDO = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function origenValido(origen) {
  return typeof origen === 'string' && origen.length >= 3 && origen.length <= 40 && ORIGEN_VALIDO.test(origen);
}

function nuevoId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  return `v${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

function leerVisitas() {
  try { return JSON.parse(sessionStorage.getItem(CLAVE_VISITAS) || '{}') || {}; } catch { return {}; }
}

// Devuelve el visitaId si es la primera vez que se ve este origen en la pestaña; si no, null
export function anotarVisita(search) {
  const origen = new URLSearchParams(search).get('origen');
  if (!origenValido(origen)) return null;
  try {
    sessionStorage.setItem(CLAVE_ORIGEN, origen);
    const visitas = leerVisitas();
    if (visitas[origen]) return null;
    const visitaId = nuevoId();
    visitas[origen] = visitaId;
    sessionStorage.setItem(CLAVE_VISITAS, JSON.stringify(visitas));
    return { origen, visitaId };
  } catch {
    return null; // sin almacenamiento no se cuenta: mejor no contar que contar de más
  }
}

export function origenGuardado() {
  try {
    const origen = sessionStorage.getItem(CLAVE_ORIGEN);
    return origenValido(origen) ? origen : null;
  } catch {
    return null;
  }
}

// Se monta una vez dentro del router: escucha los cambios de dirección
export function useVisitasCampana() {
  const { search } = useLocation();
  useEffect(() => {
    const visita = anotarVisita(search);
    // Un error al contar nunca molesta a la persona
    if (visita) registrarVisitaCampana(visita).catch(() => {});
  }, [search]);
}

export function ContadorCampanas() {
  useVisitasCampana();
  return null;
}
