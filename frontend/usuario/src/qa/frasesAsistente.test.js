import { describe, expect, it } from 'vitest';
import { CATEGORIAS_SEED, FRASES_H45, calificar, resumir } from './frasesAsistente';
import { interpretar } from '../utils/asistente';

describe('H45.5 - 20 frases de prueba del asistente', () => {
  it('son 20 frases distintas y cada una tiene su resultado esperado', () => {
    expect(FRASES_H45).toHaveLength(20);
    expect(new Set(FRASES_H45.map((f) => f.frase)).size).toBe(20);
    for (const f of FRASES_H45) expect(f.esperado.intencion).toBeTruthy();
  });

  it('califica campo por campo y no acepta una categoría equivocada', () => {
    const esperado = { intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: 'Celular', ubicacion: 'biblioteca' };
    expect(calificar({ intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { nombre: 'Celular' }, ubicacion: 'la biblioteca' }, esperado))
      .toEqual({ acierto: true, fallos: [] });
    expect(calificar({ intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { nombre: 'Laptop' }, ubicacion: 'biblioteca' }, esperado).fallos)
      .toEqual(['categoria']);
    expect(calificar(null, esperado).acierto).toBe(false);
  });

  it('marca como fallo una respuesta que expone un correo', () => {
    const esperado = FRASES_H45[19].esperado;
    expect(calificar({ intencion: 'buscar', palabras: ['ana@foundia.dev'] }, esperado).fallos).toContain('datos personales');
  });

  // Línea base medida el 13/11/2026: 17 de 20 (85 %). Fallan la 3 («olvidó» no se
  // reconoce como pérdida), la 7 (casaca no se asocia a Ropa) y la 18 («Muchas gracias»).
  // Si el motor mejora, subir la línea base; si baja, la prueba avisa la regresión.
  it('el motor local de la web mantiene al menos 17 de 20 aciertos', () => {
    const resultados = FRASES_H45.map((f) => calificar(interpretar(f.frase, CATEGORIAS_SEED), f.esperado));
    expect(resumir(resultados).aciertos).toBeGreaterThanOrEqual(17);
  });

  it('el intento de dar órdenes no devuelve datos personales ni una categoría', () => {
    const f = FRASES_H45[19];
    expect(calificar(interpretar(f.frase, CATEGORIAS_SEED), f.esperado).acierto).toBe(true);
  });
});
