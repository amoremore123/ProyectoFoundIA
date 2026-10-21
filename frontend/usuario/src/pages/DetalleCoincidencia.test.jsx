import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import DetalleCoincidencia from './DetalleCoincidencia';
import { getDetalleCoincidencia, iniciarContacto } from '../services/api';

vi.mock('../services/api', async (original) => ({ ...await original(), getDetalleCoincidencia: vi.fn(), iniciarContacto: vi.fn() }));
const objeto = { id: 7, nombre: 'Mochila perdida', descripcion: 'Azul', tipo: 'PERDIDO', ubicacion: 'Biblioteca', fechaObjeto: '2026-10-20', fotos: [{ id: 1, url: '/uploads/real.png' }], publicadoPor: { id: 1, nombre: 'Amore' }, categoria: { nombre: 'Mochilas' } };
const pareja = { id: '7-8', perdido: objeto, encontrado: { ...objeto, id: 8, nombre: 'Mochila encontrada', tipo: 'ENCONTRADO', fotos: [], publicadoPor: { id: 2, nombre: 'Bruno' } }, porcentaje: 100, nivel: 'ALTA', criterios: { nombre: 40, categoria: 25, ubicacion: 20, fecha: 15 } };
function abrir(autor = null) {
  if (autor) { localStorage.setItem('token', 'prueba'); localStorage.setItem('usuario', JSON.stringify({ id: autor })); }
  return render(<MemoryRouter initialEntries={['/coincidencias/7-8']}><AuthProvider><Routes><Route path="/coincidencias/:id" element={<DetalleCoincidencia />} /></Routes></AuthProvider></MemoryRouter>);
}
beforeEach(() => { localStorage.clear(); vi.resetAllMocks(); getDetalleCoincidencia.mockResolvedValue(pareja); iniciarContacto.mockResolvedValue({ repetido: false }); });
afterEach(() => { cleanup(); localStorage.clear(); });

it('recupera el identificador de pareja y compara ambos objetos sin datos privados', async () => {
  abrir();
  await screen.findByRole('heading', { name: 'Comparar posible coincidencia' });
  expect(getDetalleCoincidencia).toHaveBeenCalledWith('7-8', { signal: expect.any(AbortSignal) });
  expect(screen.getByRole('article', { name: 'Objeto perdido' })).toBeTruthy();
  expect(screen.getByRole('article', { name: 'Objeto encontrado' })).toBeTruthy();
  expect(screen.getByText('100% de similitud · ALTA')).toBeTruthy();
  expect(screen.getByText('Nombre: 40 puntos')).toBeTruthy();
  const foto = within(screen.getByRole('article', { name: 'Objeto perdido' })).getByRole('img');
  fireEvent.error(foto);
  expect(within(screen.getByRole('article', { name: 'Objeto perdido' })).getByText('Sin foto')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Contactar' })).toBeNull();
});

it('solo el autor del perdido inicia contacto y puede reintentar sin éxito ficticio', async () => {
  iniciarContacto.mockRejectedValueOnce(new Error('Sin conexión'));
  abrir(1);
  fireEvent.click(await screen.findByRole('button', { name: 'Contactar' }));
  await screen.findByRole('alert');
  expect(screen.queryByText(/Solicitud enviada/)).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar contacto' }));
  await screen.findByText(/Solicitud enviada/);
  expect(iniciarContacto).toHaveBeenLastCalledWith(7, 8);
});

it('otra sesión no recibe la acción privada de contactar', async () => {
  abrir(2);
  await screen.findByRole('heading', { name: 'Comparar posible coincidencia' });
  expect(screen.queryByRole('button', { name: 'Contactar' })).toBeNull();
});

it('una pareja retirada devuelve estado no disponible, sin datos anteriores', async () => {
  getDetalleCoincidencia.mockRejectedValue({ response: { status: 404 } });
  abrir(1);
  await screen.findByRole('heading', { name: 'Coincidencia no disponible' });
  expect(screen.queryByText('Mochila perdida')).toBeNull();
  expect(iniciarContacto).not.toHaveBeenCalled();
});

it('distingue error de consulta y permite reintentar', async () => {
  getDetalleCoincidencia.mockRejectedValueOnce(new Error('Sin conexión'));
  abrir();
  await screen.findByRole('alert');
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  await screen.findByRole('heading', { name: 'Comparar posible coincidencia' });
  await waitFor(() => expect(getDetalleCoincidencia).toHaveBeenCalledTimes(2));
});
