import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Publicar from './Publicar';
import { crearObjeto, listarCategorias, sugerirPublicacion } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), crearObjeto: vi.fn(), listarCategorias: vi.fn(), subirFotoObjeto: vi.fn(), sugerirPublicacion: vi.fn(),
}));
vi.mock('../services/geolocalizacion', () => ({ obtenerUbicacionActual: vi.fn() }));

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/publicar']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/publicar" element={<Publicar />} />
        <Route path="/objeto/:id" element={<p>Detalle de prueba</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

const nombre = () => screen.getByRole('textbox', { name: /^Nombre/ });
const descripcion = () => screen.getByRole('textbox', { name: /^Descripción \*/ });
const categoria = () => screen.getByRole('combobox', { name: /^Categoría/ });
const botonSugerir = () => screen.getByRole('button', { name: /Sugerir categoría y descripción/ });

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 3, nombre: 'Mochila', estado: true }, { id: 4, nombre: 'Antigua', estado: false }]);
  crearObjeto.mockResolvedValue({ id: 55 });
});
afterEach(cleanup);

describe('H49 - Sugerencia de FINDI al publicar', () => {
  it('no se puede pedir sin texto y no cambia nada hasta aceptar', async () => {
    const user = userEvent.setup();
    sugerirPublicacion.mockResolvedValue({
      categoria: { id: 3, nombre: 'Mochila' }, descripcion: 'Mochila negra con llavero rojo.', origen: 'IA', aviso: 'Es solo una sugerencia: revísala antes de publicar.',
    });
    renderizar();
    await screen.findByRole('option', { name: 'Mochila' });
    expect(botonSugerir().disabled).toBe(true);

    await user.type(nombre(), 'mochila');
    await user.type(descripcion(), 'mochila negra llavero rojo');
    await user.click(botonSugerir());
    expect(sugerirPublicacion).toHaveBeenCalledWith({ nombre: 'mochila', descripcion: 'mochila negra llavero rojo', tipo: 'PERDIDO' });
    expect(await screen.findByText('Sugerencia de FINDI')).toBeTruthy();
    // Aún no se aplicó
    expect(descripcion().value).toBe('mochila negra llavero rojo');
    expect(categoria().value).toBe('');

    // Se puede editar antes de usarla
    const sugerida = screen.getByRole('textbox', { name: /Descripción sugerida/ });
    await user.clear(sugerida);
    await user.type(sugerida, 'Mochila negra con llavero rojo y cierre roto.');
    await user.click(screen.getByRole('button', { name: 'Usar sugerencia' }));
    expect(descripcion().value).toBe('Mochila negra con llavero rojo y cierre roto.');
    expect(categoria().value).toBe('3');
    expect(crearObjeto).not.toHaveBeenCalled();
  });

  it('descartar deja el formulario como estaba', async () => {
    const user = userEvent.setup();
    sugerirPublicacion.mockResolvedValue({ categoria: { id: 3, nombre: 'Mochila' }, descripcion: 'Mochila negra.', origen: 'LOCAL' });
    renderizar();
    await screen.findByRole('option', { name: 'Mochila' });
    await user.type(nombre(), 'mochila');
    await user.click(botonSugerir());
    await user.click(await screen.findByRole('button', { name: 'Descartar' }));
    expect(screen.queryByText('Sugerencia de FINDI')).toBeNull();
    expect(categoria().value).toBe('');
    expect(descripcion().value).toBe('');
  });

  it('ignora una categoría desactivada que llegue del servidor', async () => {
    const user = userEvent.setup();
    sugerirPublicacion.mockResolvedValue({ categoria: { id: 4, nombre: 'Antigua' }, descripcion: null, origen: 'IA' });
    renderizar();
    await screen.findByRole('option', { name: 'Mochila' });
    await user.type(nombre(), 'algo viejo');
    await user.click(botonSugerir());
    expect(await screen.findByText(/no encontró nada que mejorar/)).toBeTruthy();
  });

  it('si la sugerencia falla se informa y se publica igual', async () => {
    const user = userEvent.setup();
    sugerirPublicacion.mockRejectedValue(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();
    await screen.findByRole('option', { name: 'Mochila' });
    await user.type(nombre(), 'Mochila de prueba');
    await user.type(descripcion(), 'Objeto ficticio');
    await user.click(botonSugerir());
    expect(await screen.findByText(/No se pudo obtener la sugerencia/)).toBeTruthy();

    await user.selectOptions(categoria(), '3');
    await user.type(screen.getByRole('textbox', { name: /^Ubicación/ }), 'Biblioteca central');
    const hoy = new Date();
    fireEvent.change(screen.getByLabelText(/^Fecha/), { target: { value: `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}` } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    expect(await screen.findByText('Detalle de prueba')).toBeTruthy();
    expect(crearObjeto).toHaveBeenCalled();
  });
});
