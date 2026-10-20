import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Publicar from './Publicar';
import { actualizarObjeto, getMisObjetos, listarCategorias } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), actualizarObjeto: vi.fn(), getMisObjetos: vi.fn(), listarCategorias: vi.fn(),
}));

const PROPIA = {
  id: 42, nombre: 'Mochila encontrada', descripcion: 'Mochila azul', ubicacion: 'Biblioteca',
  fechaObjeto: '2026-10-14', tipo: 'ENCONTRADO', estado: 'ACTIVO', latitud: null, longitud: null,
  categoria: { id: 3, nombre: 'Mochila' }, fotos: [],
};

function renderizar(id = 42) {
  return render(
    <MemoryRouter initialEntries={[`/mis-publicaciones/${id}/editar`]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/mis-publicaciones/:id/editar" element={<Publicar />} />
        <Route path="/mis-publicaciones" element={<p>Lista de mis publicaciones</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 3, nombre: 'Mochila', estado: true }, { id: 4, nombre: 'Llaves', estado: true }]);
  getMisObjetos.mockResolvedValue([PROPIA]);
  actualizarObjeto.mockImplementation(async (id, p) => ({ ...PROPIA, ...p, id: Number(id) }));
});
afterEach(cleanup);

describe('H5/H14 - Editar una publicación propia', () => {
  it('carga los datos actuales en el formulario', async () => {
    renderizar();
    expect(await screen.findByRole('heading', { name: 'Editar publicación' })).toBeTruthy();
    expect(screen.getByRole('textbox', { name: /^Nombre/ }).value).toBe('Mochila encontrada');
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).value).toBe('Biblioteca');
    expect(screen.getByRole('button', { name: 'Encontré un objeto' }).getAttribute('aria-pressed')).toBe('true');
    await waitFor(() => expect(screen.getByRole('combobox', { name: /^Categoría/ }).value).toBe('3'));
  });

  it('guarda los cambios con PUT y vuelve a mis publicaciones', async () => {
    const user = userEvent.setup();
    renderizar();
    const nombre = await screen.findByRole('textbox', { name: /^Nombre/ });
    await user.clear(nombre);
    await user.type(nombre, 'Mochila azul con llavero');
    await user.click(screen.getByRole('button', { name: 'GUARDAR CAMBIOS' }));

    await waitFor(() => expect(actualizarObjeto).toHaveBeenCalledWith('42', expect.objectContaining({
      nombre: 'Mochila azul con llavero', categoriaId: 3, tipo: 'ENCONTRADO', ubicacion: 'Biblioteca',
    })));
    expect(await screen.findByText('Lista de mis publicaciones')).toBeTruthy();
  });

  it('mantiene las validaciones: nombre vacío no se envía', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.clear(await screen.findByRole('textbox', { name: /^Nombre/ }));
    await user.click(screen.getByRole('button', { name: 'GUARDAR CAMBIOS' }));
    expect(screen.getByText('El nombre es obligatorio')).toBeTruthy();
    expect(actualizarObjeto).not.toHaveBeenCalled();
  });

  it('si la API rechaza el permiso, lo explica sin éxito ficticio', async () => {
    const user = userEvent.setup();
    actualizarObjeto.mockRejectedValueOnce({ response: { status: 403, data: {} } });
    renderizar();
    await screen.findByRole('textbox', { name: /^Nombre/ });
    await user.click(screen.getByRole('button', { name: 'GUARDAR CAMBIOS' }));
    expect(await screen.findByText('No puedes editar esta publicación: no es tuya o ya fue eliminada.')).toBeTruthy();
    expect(screen.queryByText('Lista de mis publicaciones')).toBeNull();
  });

  it('una publicación ajena o eliminada no se puede abrir para editar', async () => {
    getMisObjetos.mockResolvedValue([{ ...PROPIA, estado: 'ELIMINADO' }]);
    renderizar();
    expect(await screen.findByText('No encontramos esta publicación entre las tuyas o ya no se puede editar.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'GUARDAR CAMBIOS' })).toBeNull();

    cleanup();
    getMisObjetos.mockResolvedValue([PROPIA]);
    renderizar(999);
    expect(await screen.findByText('No encontramos esta publicación entre las tuyas o ya no se puede editar.')).toBeTruthy();
  });

  it('Cancelar vuelve a mis publicaciones sin guardar', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByRole('textbox', { name: /^Nombre/ });
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.getByText('Lista de mis publicaciones')).toBeTruthy();
    expect(actualizarObjeto).not.toHaveBeenCalled();
  });
});
