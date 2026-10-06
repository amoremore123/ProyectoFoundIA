import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Inicio from './Inicio';
import { listarCategorias, listarObjetos } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  listarCategorias: vi.fn(),
  listarObjetos: vi.fn(),
}));

const categorias = [
  { id: 2, nombre: 'Mochila', estado: true },
  { id: 3, nombre: 'Documentos', estado: false },
];

function RutaDeBusqueda() {
  const location = useLocation();
  return <output data-testid="ruta">{location.search}</output>;
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/buscar" element={<RutaDeBusqueda />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue(categorias);
  listarObjetos.mockResolvedValue([]);
});
afterEach(cleanup);

describe('H6/H7 - Entrada de búsqueda desde Inicio', () => {
  it('envía texto, categoría y ubicación en un enlace de búsqueda', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByRole('option', { name: 'Mochila' });
    await user.type(screen.getByRole('searchbox'), '  moch  ');
    await user.selectOptions(screen.getByRole('combobox'), '2');
    await user.type(screen.getByRole('textbox', { name: 'Ubicación' }), '  Biblioteca  ');
    await user.click(screen.getByRole('button', { name: 'Buscar', exact: true }));
    const ruta = await screen.findByTestId('ruta');
    expect(Object.fromEntries(new URLSearchParams(ruta.textContent)))
      .toEqual({ q: 'moch', categoriaId: '2', ubicacion: 'Biblioteca' });
  });

  it('marca las categorías desactivadas sin impedir buscar publicaciones antiguas', async () => {
    renderizar();
    const option = await screen.findByRole('option', { name: 'Documentos (desactivada)' });
    expect(option.disabled).toBe(false);
  });

  it('reintenta las categorías sin confundir su error con el listado reciente', async () => {
    const user = userEvent.setup();
    listarCategorias.mockRejectedValueOnce(new Error('Catálogo no disponible'));
    renderizar();
    const alerta = await screen.findByRole('alert');
    await screen.findByText('Todavía no hay publicaciones.');
    expect(alerta.textContent).toContain('Catálogo no disponible');
    expect(screen.getByRole('combobox').disabled).toBe(true);
    await user.click(within(alerta).getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(screen.getByRole('combobox').disabled).toBe(false));
    expect(listarCategorias).toHaveBeenCalledTimes(2);
    expect(listarObjetos).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
