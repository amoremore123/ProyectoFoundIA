import { describe, expect, it } from 'vitest';
import { calcularEstadoFindi, desenlaceDe, ESTADOS_FINDI, etiquetaEstadoFindi } from './expresionFindi';
import { EXPRESIONES_FINDI } from '../config/findi';

describe('Expresiones de FINDI', () => {
  it('cada estado usa una de las 10 imágenes aprobadas de /findi/expresiones', () => {
    Object.values(ESTADOS_FINDI).forEach(({ expresion }) => {
      expect(EXPRESIONES_FINDI[expresion]?.src).toMatch(/^\/findi\/expresiones\/web\/[a-z-]+\.webp$/);
    });
    expect(new Set(Object.values(EXPRESIONES_FINDI).map((e) => e.src)).size).toBe(10);
  });

  it('prioriza el micrófono, luego la consulta en curso y luego la voz', () => {
    expect(calcularEstadoFindi({ escuchando: true, pensando: true, hablando: true })).toBe('escuchando');
    expect(calcularEstadoFindi({ pensando: true, buscando: true, hablando: true })).toBe('buscando');
    expect(calcularEstadoFindi({ pensando: true })).toBe('pensando');
    expect(calcularEstadoFindi({ hablando: true, desenlace: { tipo: 'coincidencia', n: 1 } })).toBe('hablando');
    expect(calcularEstadoFindi({ preparando: true, escribiendo: true })).toBe('preparando');
    expect(calcularEstadoFindi({ escribiendo: true, notificacion: true })).toBe('escribiendo');
    expect(calcularEstadoFindi({ notificacion: true, desenlace: { tipo: 'exito' } })).toBe('notificacion');
    expect(ESTADOS_FINDI.notificacion.expresion).toBe('alerta');
    expect(calcularEstadoFindi({ error: true, saludando: true })).toBe('error');
    expect(calcularEstadoFindi({ saludando: true })).toBe('saludando');
    expect(calcularEstadoFindi({})).toBe('en-linea');
  });

  it('solo marca coincidencia con objetos reales y sin resultados cuando se buscó', () => {
    expect(desenlaceDe({ resultados: [{}, {}] })).toEqual({ tipo: 'coincidencia', n: 2 });
    expect(desenlaceDe({ tarjetas: [{ tipo: 'OBJETO' }] })).toEqual({ tipo: 'coincidencia', n: 1 });
    expect(desenlaceDe({ consulta: {} }, { buscaba: true })).toEqual({ tipo: 'sin-resultados' });
    expect(desenlaceDe({ consulta: {} })).toBeNull();
    expect(desenlaceDe({ misReportes: [{}] })).toEqual({ tipo: 'exito' });
    expect(desenlaceDe({ texto: 'Hola' })).toBeNull();
  });

  it('describe el resultado de la búsqueda en la etiqueta', () => {
    expect(etiquetaEstadoFindi('coincidencia', { tipo: 'coincidencia', n: 3 })).toBe('Búsqueda completada · 3 resultados');
    expect(etiquetaEstadoFindi('en-linea')).toBe('');
  });
});
