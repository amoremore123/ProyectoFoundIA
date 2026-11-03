import { normalizar, raiz, VOCABULARIO } from './asistente';

// H46 - Mejoras del dictado por voz para el vocabulario de FoundIA.

// Errores frecuentes del reconocimiento de voz en frases sobre objetos y lugares del campus
const CORRECCIONES = [
  [/\ba la (\d{2,4}[a-z]?)\b/gi, 'aula $1'],
  [/\bhola (\d{2,4}[a-z]?)\b/gi, 'aula $1'],
  [/\bbiblio teca\b/gi, 'biblioteca'],
  [/\blap ?top\b/gi, 'laptop'],
  [/\bair ?pods?\b/gi, 'airpods'],
  [/\btoma ?todo(s?)\b/gi, 'tomatodo$1'],
  [/\bfoto ?check\b/gi, 'fotocheck'],
  [/\bsmart ?watch\b/gi, 'smartwatch'],
  [/\bde ene i\b/gi, 'DNI'],
  [/\bcel ?ular\b/gi, 'celular'],
];

export function corregirDictado(texto = '') {
  return CORRECCIONES.reduce((t, [patron, reemplazo]) => t.replace(patron, reemplazo), String(texto))
    .replace(/\s+/g, ' ')
    .trim();
}

const vocabulario = new Set(VOCABULARIO.map((p) => raiz(normalizar(p))));

// Puntaje: cuántas palabras de la frase pertenecen al vocabulario del proyecto (objetos, colores y lugares)
export function puntajeDominio(texto = '') {
  return normalizar(texto)
    .split(' ')
    .filter(Boolean)
    .reduce((total, palabra) => total + (vocabulario.has(raiz(palabra)) || /^\d{2,4}[a-z]?$/.test(palabra) ? 1 : 0), 0);
}

// Entre las alternativas del navegador elige la que más usa el vocabulario de FoundIA.
// Ante un empate se respeta el orden del navegador (su mejor apuesta va primero).
export function elegirAlternativa(alternativas = []) {
  const candidatas = alternativas
    .map((a) => corregirDictado(typeof a === 'string' ? a : a?.transcript || ''))
    .filter(Boolean);
  if (!candidatas.length) return '';
  return candidatas.reduce((mejor, actual) => (puntajeDominio(actual) > puntajeDominio(mejor) ? actual : mejor));
}
