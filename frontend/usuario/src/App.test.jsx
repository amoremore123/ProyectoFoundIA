import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import { buscarObjetos, listarCategorias, listarObjetos } from './services/api';

vi.mock('./services/api', async (original) => ({
  ...await original(),
  buscarObjetos: vi.fn(),
  listarCategorias: vi.fn(),
  listarObjetos: vi.fn(),
}));

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/');
  vi.clearAllMocks();
  buscarObjetos.mockResolvedValue([]);
  listarCategorias.mockResolvedValue([]);
  listarObjetos.mockResolvedValue([]);
});
afterEach(cleanup);

it('la portada busca por palabras clave sin abrir el asistente ni mostrar extras', async () => {
  render(<App />);
  expect(screen.queryByText('Asistente con IA')).toBeNull();
  expect(screen.queryByRole('link', { name: /historial/i })).toBeNull();
  expect(screen.queryByText('Casos ya recuperados')).toBeNull();
  fireEvent.change(screen.getByRole('textbox', { name: 'Buscar objeto por palabras clave' }), {
    target: { value: '  cédula  ' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar objetos' }));
  await waitFor(() => expect(buscarObjetos).toHaveBeenCalled());
  expect(new URLSearchParams(window.location.search).get('q')).toBe('cédula');
  expect(screen.getByRole('heading', { name: 'Buscar objetos' })).toBeTruthy();
});

it('una URL directa al historial lleva al listado de publicaciones del sprint', async () => {
  window.history.replaceState({}, '', '/historial');
  render(<App />);
  await screen.findByText('Todavía no hay publicaciones.');
  expect(window.location.pathname).toBe('/explorar');
  expect(screen.queryByRole('heading', { name: 'Historial de objetos' })).toBeNull();
});

it('mantiene la protección de Publicar al reducir el alcance visible', async () => {
  window.history.replaceState({}, '', '/publicar');
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
  expect(window.location.pathname).toBe('/login');
});
