import { describe, expect, it } from 'vitest';
import { adaptarInterpretacion, interpretar, rankear, responder } from './asistente';

const categorias = [
  { id: 1, nombre: 'Celular', descripcion: 'Teléfonos inteligentes y celulares', estado: true },
  { id: 3, nombre: 'Mochila', descripcion: 'Mochilas y bolsos', estado: true },
  { id: 6, nombre: 'Accesorio', descripcion: 'Relojes, gafas, joyas y otros accesorios', estado: true },
  { id: 7, nombre: 'Llaves', descripcion: 'Llaves y llaveros', estado: true },
];

const objetos = [
  { id: 1, nombre: 'Mochila negra con libros', descripcion: 'Mochila negra con pulsera roja.', ubicacion: 'Comedor principal', tipo: 'ENCONTRADO', estado: 'ACTIVO', fechaObjeto: '2026-10-02', categoria: { id: 3, nombre: 'Mochila' } },
  { id: 2, nombre: 'Celular Samsung Galaxy A54', descripcion: 'Celular azul oscuro con funda.', ubicacion: 'Biblioteca', tipo: 'PERDIDO', estado: 'ACTIVO', fechaObjeto: '2026-10-01', categoria: { id: 1, nombre: 'Celular' } },
  { id: 3, nombre: 'Llavero con llaves de casa', descripcion: 'Llavero metálico azul.', ubicacion: 'Biblioteca', tipo: 'ENCONTRADO', estado: 'ACTIVO', fechaObjeto: '2026-10-03', categoria: { id: 7, nombre: 'Llaves' } },
  { id: 6, nombre: 'Gafas de sol', descripcion: 'Gafas negras en estuche.', ubicacion: 'Entrada principal', tipo: 'ENCONTRADO', estado: 'ACTIVO', fechaObjeto: '2026-10-03', categoria: { id: 6, nombre: 'Accesorio' } },
];

describe('Asistente FoundIA - interpretación', () => {
  it('entiende que si perdí algo debe buscar entre lo encontrado, con lugar y categoría', () => {
    const c = interpretar('Perdí mi celular en la biblioteca', categorias);
    expect(c.intencion).toBe('buscar');
    expect(c.tipoBuscado).toBe('ENCONTRADO');
    expect(c.ubicacion).toBe('biblioteca');
    expect(c.categoria.id).toBe(1);
    expect(c.palabras).toEqual(['celular']);
  });

  it('si encontré algo busca entre lo perdido y reconoce sinónimos de la categoría', () => {
    const c = interpretar('Encontré unos lentes en el aula 204', categorias);
    expect(c.tipoBuscado).toBe('PERDIDO');
    expect(c.ubicacion).toBe('aula 204');
    expect(c.categoria.id).toBe(6);
  });

  it('detecta saludos y preguntas sobre cómo publicar', () => {
    expect(interpretar('hola', categorias).intencion).toBe('saludo');
    expect(interpretar('¿Cómo publico un objeto?', categorias).intencion).toBe('publicar');
  });
});

describe('Asistente FoundIA - resultados', () => {
  it('ordena por relevancia usando nombre, color y sinónimos', () => {
    const r = rankear(objetos, interpretar('busco mi mochila negra', categorias));
    expect(r.map((x) => x.objeto.id)).toEqual([1]);
    expect(r[0].porcentaje).toBeGreaterThan(80);
  });

  it('prioriza objetos encontrados cuando el usuario perdió algo', () => {
    const res = responder('se me perdieron mis llaves', { objetos, categorias });
    expect(res.resultados.map((r) => r.objeto.id)).toEqual([3]);
  });

  it('no sugiere objetos distintos solo porque coinciden en color o lugar', () => {
    const res = responder('perdí mi celular azul en la biblioteca', { objetos, categorias });
    expect(res.resultados.map((r) => r.objeto.id)).toEqual([2]);
    expect(res.texto).toContain('Nadie lo ha reportado como encontrado');
  });

  it('sugiere publicar cuando no hay coincidencias', () => {
    const res = responder('perdí mi laptop lenovo', { objetos, categorias });
    expect(res.resultados).toBeUndefined();
    expect(res.acciones[0].to).toBe('/publicar');
  });
});

describe('Asistente FoundIA - interpretación de Claude (backend)', () => {
  const ia = {
    intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { id: 1, nombre: 'Celular' },
    ubicacion: 'Biblioteca', palabras: ['Celulares', 'azul'], colores: ['azul'],
  };

  it('la adapta a la forma de interpretar() usando las categorías locales', () => {
    const c = adaptarInterpretacion(ia, categorias);
    expect(c).toEqual({
      intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: categorias[0],
      ubicacion: 'biblioteca', palabras: ['celular', 'azul'], colores: ['azul'],
    });
  });

  it('responder usa la interpretación recibida en lugar de la local', () => {
    // El motor local no entendería "smartphone extraviado", pero Claude sí
    const consulta = adaptarInterpretacion(ia, categorias);
    const res = responder('se me fue el smartphone', { objetos, categorias, consulta });
    expect(res.consulta).toBe(consulta);
    expect(res.resultados.map((r) => r.objeto.id)).toEqual([2]);
  });

  it('devuelve null si la respuesta no tiene la forma esperada, para volver al motor local', () => {
    expect(adaptarInterpretacion(null, categorias)).toBeNull();
    expect(adaptarInterpretacion({ intencion: 'otra', palabras: [] }, categorias)).toBeNull();
    expect(adaptarInterpretacion({ intencion: 'buscar' }, categorias)).toBeNull();
  });

  it('ignora categorías desconocidas o inactivas y marca como vacío si no queda nada que buscar', () => {
    const c = adaptarInterpretacion({ intencion: 'buscar', tipoBuscado: 'X', categoria: { id: 99 }, ubicacion: '', palabras: [], colores: [] }, categorias);
    expect(c.categoria).toBeNull();
    expect(c.tipoBuscado).toBe('');
    expect(c.intencion).toBe('vacio');
  });
});
