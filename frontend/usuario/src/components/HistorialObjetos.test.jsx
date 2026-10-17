import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import HistorialObjetos from './HistorialObjetos';
import { listarObjetos } from '../services/api';

vi.mock('../services/api', async (original) => ({ ...await original(), listarObjetos: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
const objetos = [
  { id: 1, nombre: 'Mochila de clase', tipo: 'PERDIDO', estado: 'ACTIVO' },
  { id: 2, nombre: 'Llaves recuperadas', tipo: 'PERDIDO', estado: 'RECUPERADO' },
  { id: 3, nombre: 'Celular en biblioteca', tipo: 'ENCONTRADO', estado: 'ACTIVO' },
];
const mostrar = (props) => render(<MemoryRouter><HistorialObjetos {...props} /></MemoryRouter>);

it('distingue una falla de conexión de un historial vacío y permite reintentar', async () => {
  listarObjetos.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(objetos);
  mostrar();
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect(screen.queryByText('Todavía no hay publicaciones en la comunidad.')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('Mochila de clase')).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
});

it('incluye recuperados y filtra el historial sin solicitar solo objetos activos', async () => {
  listarObjetos.mockResolvedValue(objetos);
  mostrar();
  await screen.findByText('Llaves recuperadas');
  expect(listarObjetos).toHaveBeenCalledWith();
  await userEvent.click(screen.getByRole('button', { name: 'Recuperados' }));
  expect(screen.getByText('Llaves recuperadas')).toBeTruthy();
  expect(screen.queryByText('Mochila de clase')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Encontrados' }));
  expect(screen.getByText('Celular en biblioteca')).toBeTruthy();
  expect(screen.queryByText('Llaves recuperadas')).toBeNull();
});

it('permite consultar las publicaciones anteriores después de las primeras doce', async () => {
  listarObjetos.mockResolvedValue(Array.from({ length: 13 }, (_, i) => ({ id: i, nombre: `Objeto ${i}`, tipo: 'PERDIDO', estado: 'ACTIVO' })));
  mostrar();
  await screen.findByText('Objeto 0');
  expect(screen.queryByText('Objeto 12')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Ver más publicaciones' }));
  expect(screen.getByText('Objeto 12')).toBeTruthy();
});
