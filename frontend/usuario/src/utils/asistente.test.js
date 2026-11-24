import { describe, expect, it } from 'vitest';
import { adaptarInterpretacion, interpretar, rankear, responder, saludoFindi } from './asistente';

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

  it('si nombra un objeto, otra cosa de la misma categoría no es coincidencia', () => {
    const accesorio = categorias.find((c) => c.id === 6);
    const conMochilas = [...objetos,
      { id: 9, nombre: 'Mochila azul e2e adf23d08', descripcion: 'Mochila azul', ubicacion: 'Biblioteca central', tipo: 'PERDIDO', estado: 'ACTIVO', fechaObjeto: '2026-11-23', categoria: { id: 6, nombre: 'Accesorio' } }];
    // Como lo interpreta la IA: «llaves» en la categoría Accesorio
    const consulta = adaptarInterpretacion({ intencion: 'buscar', tipoBuscado: 'PERDIDO', categoria: accesorio, ubicacion: '', palabras: ['llaves'], colores: [] }, categorias);
    const res = responder('Encontré unas llaves', { objetos: conMochilas, categorias, consulta });
    expect((res.resultados || []).map((r) => r.objeto.id)).not.toContain(9);
    expect((res.resultados || []).every((r) => /llave/i.test(r.objeto.nombre))).toBe(true);
    // Sin nombrar un objeto, la categoría sola sí sirve
    const soloCategoria = rankear(conMochilas, { intencion: 'buscar', tipoBuscado: '', categoria: accesorio, ubicacion: '', palabras: [], colores: [] });
    expect(soloCategoria.map((r) => r.objeto.id)).toEqual(expect.arrayContaining([6, 9]));
  });

  it('«Encontré unas llaves» no trae una mochila que menciona su llavero en la descripción', () => {
    const llaves = categorias.find((c) => c.id === 7);
    const conMochila = [...objetos,
      { id: 10, nombre: 'Mochila azul e2e adf23d08', descripcion: 'Perdí mi mochila azul con un llavero de estrella', ubicacion: 'Biblioteca central', tipo: 'PERDIDO', estado: 'ACTIVO', fechaObjeto: '2026-11-23', categoria: { id: 6, nombre: 'Accesorio' } }];
    // Como lo interpreta la IA de verdad: categoría Llaves y la palabra «llave»
    const consulta = adaptarInterpretacion({ intencion: 'buscar', tipoBuscado: 'PERDIDO', categoria: llaves, ubicacion: '', palabras: ['llave'], colores: [] }, categorias);
    const res = responder('Encontré unas llaves', { objetos: conMochila, categorias, consulta });
    expect((res.resultados || []).map((r) => r.objeto.id)).not.toContain(10);
    expect(res.resultados.map((r) => r.objeto.id)).toEqual([3]);
    // Sin categoría en la consulta, la descripción sí cuenta
    const sinCategoria = rankear(conMochila, { intencion: 'buscar', tipoBuscado: '', categoria: null, ubicacion: '', palabras: ['llavero'], colores: [] });
    expect(sinCategoria.map((r) => r.objeto.id)).toContain(10);
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


describe('Fase 5 - Intenciones nuevas de FINDI', () => {
  it('detecta las preguntas por mis reportes y por cómo funciona, también sin IA', () => {
    expect(interpretar('¿Cómo van mis reportes?', categorias).intencion).toBe('mis_reportes');
    expect(interpretar('quiero ver el estado de mi publicación', categorias).intencion).toBe('mis_reportes');
    expect(interpretar('¿Cómo funciona FoundIA?', categorias).intencion).toBe('como_funciona');
    expect(interpretar('¿Cómo publico un objeto?', categorias).intencion).toBe('publicar');
  });

  it('acepta las intenciones nuevas que devuelve el backend', () => {
    expect(adaptarInterpretacion({ intencion: 'mis_reportes', palabras: [] }, categorias)?.intencion).toBe('mis_reportes');
    expect(adaptarInterpretacion({ intencion: 'como_funciona', palabras: [] }, categorias)?.intencion).toBe('como_funciona');
  });

  it('"cómo funciona" responde con los cuatro pasos', () => {
    const res = responder('¿Cómo funciona FoundIA?', { categorias });
    expect(res.texto).toContain('cuatro pasos');
    expect(res.acciones[0].to).toBe('/#proceso-recuperacion');
  });
});

describe('Saludo personalizado de FINDI', () => {
  it('saluda de forma breve y pregunta qué necesita', () => {
    expect(saludoFindi('', new Date(2026, 10, 16, 9, 0))).toBe('¡Hola! 👋 Soy FINDI. ¿Qué necesitas hoy?');
  });

  it('usa solo el primer nombre de la persona con sesión', () => {
    expect(saludoFindi('Ana María', new Date(2026, 10, 16, 15, 0))).toMatch(/^¡Buenas tardes, Ana! 👋 Soy FINDI\./);
    expect(saludoFindi('Ana', new Date(2026, 10, 16, 9, 0))).toMatch(/^¡Buenos días, Ana!/);
    expect(saludoFindi('Ana', new Date(2026, 10, 16, 21, 0))).toMatch(/^¡Buenas noches, Ana!/);
  });
});
