import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
  fotos: [{ id: 1, url: '/uploads/mochila.png' }],
};

let publicacionesServidor;

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/mis-publicaciones']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/mis-publicaciones" element={<MisPublicaciones />} />
        <Route path="/mis-publicaciones/:id/editar" element={<p>Pantalla de edición</p>} />
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
});

afterEach(cleanup);

describe('H14 - Mis publicaciones', () => {
  it('lista vacía muestra el mensaje y el acceso a publicar', async () => {
    publicacionesServidor = [];
    renderizar();
    expect(await screen.findByText('Aún no has publicado nada.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Publicar mi primer objeto' })).toBeTruthy();
  });

  it('muestra foto, estado y acciones según el estado de cada publicación', async () => {
    publicacionesServidor = [
      { ...publicacionActiva },
      { ...publicacionActiva, id: 43, nombre: 'Llaves ocultas', estado: 'OCULTO', fotos: [] },
      { ...publicacionActiva, id: 44, nombre: 'Gafas eliminadas', estado: 'ELIMINADO', fotos: [] },
    ];
    renderizar();
    await screen.findByText('3 publicaciones');

    expect(screen.getByRole('link', { name: 'Editar Mochila encontrada' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Eliminar Mochila encontrada' })).toBeTruthy();
    expect(document.querySelector('img[src$="/uploads/mochila.png"]')).toBeTruthy();

    expect(screen.getByText('Oculto')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Editar Llaves ocultas' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Eliminar Llaves ocultas' })).toBeNull();
    expect(screen.getByText(/La administración la ocultó/)).toBeTruthy();

    expect(screen.getByText('Eliminado')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Editar Gafas eliminadas' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Eliminar Gafas eliminadas' })).toBeNull();
  });

  it('Editar abre la pantalla de edición de esa publicación', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('link', { name: 'Editar Mochila encontrada' }));
    expect(screen.getByText('Pantalla de edición')).toBeTruthy();
  });

  it('cancelar el modal mantiene la publicación activa y no llama a la API', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: 'Eliminar Mochila encontrada' }));

    const dialogo = screen.getByRole('alertdialog', { name: '¿Eliminar esta publicación?' });
    expect(document.activeElement).toBe(within(dialogo).getByRole('button', { name: 'Cancelar' }));
    await user.click(within(dialogo).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(eliminarObjeto).not.toHaveBeenCalled();
    expect(screen.getByText('Activo')).toBeTruthy();
  });

  it('Escape cierra el modal sin eliminar', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: 'Eliminar Mochila encontrada' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(eliminarObjeto).not.toHaveBeenCalled();
  });

  it('confirmar elimina lógicamente y se conserva así después de recargar', async () => {
    const user = userEvent.setup();
    const primeraCarga = renderizar();
    await user.click(await screen.findByRole('button', { name: 'Eliminar Mochila encontrada' }));
    await user.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    expect(await screen.findByText('Se conserva en tu historial y ya no aparece en las búsquedas públicas.')).toBeTruthy();
    expect(screen.getByText('Eliminado')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Eliminar Mochila encontrada' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Mochila encontrada' })).toBeNull();
    expect(eliminarObjeto).toHaveBeenCalledWith(42);

    primeraCarga.unmount();
    renderizar();
    await waitFor(() => expect(getMisObjetos).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Mochila encontrada')).toBeTruthy();
    expect(screen.getByText('Eliminado')).toBeTruthy();
  });

  it('si el servidor rechaza la eliminación no hay éxito ficticio', async () => {
    const user = userEvent.setup();
    eliminarObjeto.mockRejectedValueOnce({ response: { status: 403, data: {} } });
    renderizar();
    await user.click(await screen.findByRole('button', { name: 'Eliminar Mochila encontrada' }));
    await user.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    expect(await screen.findByText('No se pudo eliminar: la publicación no es tuya o ya fue eliminada.')).toBeTruthy();
    expect(screen.getByText('Activo')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
  });

  it('un fallo al cargar se distingue de la lista vacía y Reintentar recupera', async () => {
    const user = userEvent.setup();
    getMisObjetos.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();

    expect(await screen.findByText(/No se pudieron cargar tus publicaciones/)).toBeTruthy();
    expect(screen.queryByText('Aún no has publicado nada.')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('link', { name: 'Mochila encontrada' })).toBeTruthy();
  });
});
