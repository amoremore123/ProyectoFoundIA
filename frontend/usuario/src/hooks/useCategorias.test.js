import { describe, expect, it } from 'vitest';
import { filtrarCategorias, ordenarCategorias } from './useCategorias';
import { mensajeError } from '../services/api';

const categorias = [
  { id: 7, nombre: 'útiles' }, { id: 2, nombre: 'Mochila' }, { id: 9, nombre: 'Electrónicos' },
  { id: 4, nombre: 'Móvil', estado: false }, { id: 1, nombre: 'Accesorios' }, { id: 3, nombre: 'llaves' },
];

describe('H7 - Categorías ordenadas y con búsqueda', () => {
  it('ordena alfabéticamente en español sin importar tildes ni mayúsculas y conserva los IDs', () => {
    const ordenadas = ordenarCategorias(categorias);
    expect(ordenadas.map((c) => c.nombre)).toEqual(['Accesorios', 'Electrónicos', 'llaves', 'Mochila', 'Móvil', 'útiles']);
    expect(ordenadas.map((c) => c.id)).toEqual([1, 9, 3, 2, 4, 7]);
    expect(categorias[0].id).toBe(7); // no modifica la lista original
  });

  it('filtra por texto parcial ignorando mayúsculas y tildes', () => {
    expect(filtrarCategorias(categorias, 'mo').map((c) => c.id)).toEqual([2, 4]);
    expect(filtrarCategorias(categorias, 'MÓ').map((c) => c.id)).toEqual([2, 4]);
    expect(filtrarCategorias(categorias, 'utiles').map((c) => c.id)).toEqual([7]);
    expect(filtrarCategorias(categorias, 'zzz')).toEqual([]);
    expect(filtrarCategorias(categorias, '  ')).toHaveLength(categorias.length);
  });
});

describe('Mensajes de error en español', () => {
  it('prioriza el mensaje del servidor', () => {
    expect(mensajeError({ response: { status: 400, data: { mensaje: 'Nombre inválido' } } })).toBe('Nombre inválido');
  });

  it('traduce la falta de conexión y el tiempo de espera de axios', () => {
    expect(mensajeError(Object.assign(new Error('Network Error'), { isAxiosError: true })))
      .toBe('Ocurrió un error inesperado: no hay conexión con el servidor. Revisa tu conexión e inténtalo de nuevo.');
    expect(mensajeError(Object.assign(new Error('timeout of 12000ms exceeded'), { isAxiosError: true, code: 'ECONNABORTED' }), 'No se pudo realizar la búsqueda'))
      .toBe('No se pudo realizar la búsqueda: el servidor tardó demasiado en responder.');
  });

  it('un error HTTP sin mensaje usa el texto en español con el código', () => {
    expect(mensajeError({ response: { status: 500, data: {} } }, 'No se pudo realizar la búsqueda'))
      .toBe('No se pudo realizar la búsqueda (error 500).');
  });
});
