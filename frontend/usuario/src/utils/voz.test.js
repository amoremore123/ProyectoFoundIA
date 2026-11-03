import { describe, expect, it } from 'vitest';
import { corregirDictado, elegirAlternativa, puntajeDominio } from './voz';

describe('H46 - Dictado por voz', () => {
  it('corrige errores típicos del reconocimiento', () => {
    expect(corregirDictado('perdí mi celular en a la 204')).toBe('perdí mi celular en aula 204');
    expect(corregirDictado('dejé mi lap top en la biblio teca')).toBe('dejé mi laptop en la biblioteca');
    expect(corregirDictado('mi toma todo azul')).toBe('mi tomatodo azul');
  });

  it('da más puntaje a las frases con vocabulario de FoundIA', () => {
    expect(puntajeDominio('perdí mi mochila negra en la biblioteca')).toBeGreaterThan(puntajeDominio('perdí mi muchacha negra en la vida teca'));
  });

  it('elige la alternativa que más se parece al vocabulario del proyecto', () => {
    const alternativas = [
      { transcript: 'perdí mi muchila negra en la vida teca' },
      { transcript: 'perdí mi mochila negra en la biblioteca' },
    ];
    expect(elegirAlternativa(alternativas)).toBe('perdí mi mochila negra en la biblioteca');
  });

  it('ante un empate respeta la primera alternativa del navegador', () => {
    expect(elegirAlternativa([{ transcript: 'hola buenos días' }, { transcript: 'ola buenos días' }])).toBe('hola buenos días');
    expect(elegirAlternativa([])).toBe('');
  });
});
