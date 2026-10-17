import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MisPublicaciones from './MisPublicaciones';
import { eliminarObjeto, getMisObjetos } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  eliminarObjeto: vi.fn(),
  getMisObjetos: vi.fn(),
}));

const publicacionActiva = {
  id: 42,
  nombre: 'Mochila encontrada',
  descripcion: 'Mochila azul',
  ubicacion: 'Biblioteca',
  fechaObjeto: '2026-10-14',
  tipo: 'ENCONTRADO',
  estado: 'ACTIVO',
  categoria: { id: 3, nombre: 'Mochila' },
};

let publicacionesServidor;

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/mis-publicaciones']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/mis-publicaciones" element={<MisPublicaciones />} />
        <Route path="/objeto/:id" element={<p>Detalle de publicación</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  publicacionesServidor = [{ ...publicacionActiva }];
  getMisObjetos.mockImplementation(async () => publicacionesServidor.map((objeto) => ({ ...objeto })));
  eliminarObjeto.mockImplementation(async (id) => {
    publicacionesServidor = publicacionesServidor.map((objeto) =>
      objeto.id === id ? { ...objeto, estado: 'ELIMINADO' } : objeto);
  });
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('Historial de mis publicaciones', () => {
  it('conserva la publicación eliminada en pantalla y después de recargar', async () => {
    const primeraCarga = renderizar();
    await screen.findByRole('link', { name: 'Mochila encontrada' });
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText('Se conserva en tu historial y ya no aparece en las búsquedas públicas.'))
      .toBeTruthy();
    expect(screen.getByText('Eliminado')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Eliminar' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Mochila encontrada' })).toBeNull();
    expect(eliminarObjeto).toHaveBeenCalledWith(42);

    primeraCarga.unmount();
    renderizar();
    await waitFor(() => expect(getMisObjetos).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Mochila encontrada')).toBeTruthy();
    expect(screen.getByText('Eliminado')).toBeTruthy();
    expect(screen.getByText('Se conserva en tu historial y ya no aparece en las búsquedas públicas.'))
      .toBeTruthy();
  });
});
