import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Buscar from './Buscar';
import { buscarObjetos, listarCategorias } from '../services/api';
import { obtenerCoordenadasActuales } from '../services/geolocalizacion';

vi.mock('../services/api', async (original) => ({ ...await original(), buscarObjetos: vi.fn(), listarCategorias: vi.fn() }));
vi.mock('../services/geolocalizacion', () => ({ obtenerCoordenadasActuales: vi.fn() }));
function Navegacion() {
  const ruta = useLocation();
  const navegar = useNavigate();
  return <><output data-testid="url">{ruta.search}</output><button onClick={() => navegar(-1)}>Atrás</button></>;
}
function abrir(url = '/buscar') {
  return render(<MemoryRouter initialEntries={[url]}><Navegacion /><Buscar /></MemoryRouter>);
}
beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 2, nombre: 'Mochilas' }]);
  buscarObjetos.mockResolvedValue([]);
});
afterEach(cleanup);
const cambiar = (nombre, valor) => fireEvent.change(screen.getByLabelText(nombre), { target: { value: valor } });

it('envía todos los filtros a la API, restaura URL y muestra distancia solo con coordenadas', async () => {
  buscarObjetos.mockResolvedValue([{ id: 1, nombre: 'Mochila real', latitud: 0, longitud: 0.01, tipo: 'ENCONTRADO', fotos: [] }]);
  abrir('/buscar?q=mochila&categoriaId=2&tipo=ENCONTRADO&ubicacion=Biblioteca&desde=2026-10-01&hasta=2026-10-20&latitud=0&longitud=0&radioKm=2');
  await screen.findByText('A 1.11 km de tu ubicación');
  expect(buscarObjetos).toHaveBeenLastCalledWith({ q: 'mochila', categoriaId: '2', tipo: 'ENCONTRADO', ubicacion: 'Biblioteca', desde: '2026-10-01', hasta: '2026-10-20', latitud: '0', longitud: '0', radioKm: '2' }, { signal: expect.any(AbortSignal) });
  expect(screen.getByLabelText('Desde').value).toBe('2026-10-01');
  fireEvent.click(screen.getByRole('button', { name: 'Quitar filtro latitud' }));
  await waitFor(() => expect(screen.getByLabelText('Latitud').value).toBe(''));
  expect(screen.getByLabelText('Longitud').value).toBe('');
  expect(screen.queryByText('A 1.11 km de tu ubicación')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Atrás' }));
  await waitFor(() => expect(screen.getByLabelText('Latitud').value).toBe('0'));
  expect(screen.getByLabelText('Hasta').value).toBe('2026-10-20');
});

it('rechaza fechas invertidas y coordenadas incompletas sin enviar otra búsqueda', async () => {
  abrir();
  await screen.findByText('Todavía no hay publicaciones.');
  cambiar('Desde', '2026-10-20'); cambiar('Hasta', '2026-10-01');
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  expect(screen.getByRole('alert').textContent).toContain('fecha inicial');
  cambiar('Hasta', '2026-10-20'); cambiar('Latitud', '-12');
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  expect(screen.getByRole('alert').textContent).toContain('latitud y la longitud');
  expect(buscarObjetos).toHaveBeenCalledTimes(1);
});

it('permite continuar por ubicación manual si se deniega GPS y limpiar todos los filtros', async () => {
  obtenerCoordenadasActuales.mockRejectedValue(new Error('Permiso denegado'));
  abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Usar mi ubicación' }));
  await screen.findByRole('alert');
  cambiar('Ubicación', 'Aula | cómputo');
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith({ q: '', ubicacion: 'Aula | cómputo' }, { signal: expect.any(AbortSignal) }));
  expect(screen.getByLabelText('Ubicación').value).toBe('Aula | cómputo');
  fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
  await waitFor(() => expect(screen.getByTestId('url').textContent).toBe(''));
  expect(screen.getByLabelText('Ubicación').value).toBe('');
});

it('obtiene GPS real y conserva fechas y categoría al aplicar cercanía', async () => {
  obtenerCoordenadasActuales.mockResolvedValue({ latitud: -12, longitud: -77 });
  abrir('/buscar?categoriaId=2&desde=2026-10-01');
  fireEvent.click(screen.getByRole('button', { name: 'Usar mi ubicación' }));
  await waitFor(() => expect(screen.getByLabelText('Latitud').value).toBe('-12'));
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith({ q: '', categoriaId: '2', desde: '2026-10-01', latitud: '-12', longitud: '-77', radioKm: '5' }, { signal: expect.any(AbortSignal) }));
});
