import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Categorias from './Categorias';
import { ToastProvider } from '../context/ToastContext';
import { crearCategoria, getCategorias, patchCategoria } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getCategorias: vi.fn(), crearCategoria: vi.fn(), patchCategoria: vi.fn(),
}));

const LISTA = [
  { id: 1, nombre: 'Celulares', descripcion: 'Teléfonos', estado: true },
  { id: 2, nombre: 'Paraguas', descripcion: '', estado: false },
];

async function renderizar() {
  await act(async () => { render(<ToastProvider><Categorias /></ToastProvider>); });
}
const fila = (nombre) => screen.getByText(nombre).closest('tr');

beforeEach(() => {
  getCategorias.mockReset(); crearCategoria.mockReset(); patchCategoria.mockReset();
  getCategorias.mockResolvedValue(LISTA.map((c) => ({ ...c })));
});
afterEach(() => cleanup());

describe('H26 - Gestión de categorías', () => {
  it('lista activas e inactivas con su contador', async () => {
    await renderizar();
    expect(screen.getByText('1 activas · 2 en total')).toBeTruthy();
    expect(within(fila('Paraguas')).getByText('Inactiva')).toBeTruthy();
  });

  it('desactivar pide confirmación y cancelar no cambia nada', async () => {
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Desactivar Celulares' }));
    expect(screen.getByRole('alertdialog').textContent).toContain('Las publicaciones que ya la usan la conservan');
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(patchCategoria).not.toHaveBeenCalled();
    expect(within(fila('Celulares')).getByText('Activa')).toBeTruthy();
  });

  it('al confirmar desactiva con PATCH estado:false, sin borrar la fila', async () => {
    patchCategoria.mockResolvedValueOnce({ ...LISTA[0], estado: false });
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Desactivar Celulares' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, desactivar' })); });

    expect(patchCategoria).toHaveBeenCalledWith(1, { estado: false });
    expect(within(fila('Celulares')).getByText('Inactiva')).toBeTruthy();
    expect(screen.getByText('0 activas · 2 en total')).toBeTruthy();
  });

  it('reactivar una inactiva no pide confirmación', async () => {
    patchCategoria.mockResolvedValueOnce({ ...LISTA[1], estado: true });
    await renderizar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Activar Paraguas' })); });
    expect(patchCategoria).toHaveBeenCalledWith(2, { estado: true });
    expect(within(fila('Paraguas')).getByText('Activa')).toBeTruthy();
  });

  it('edita el nombre conservando el ID y muestra el duplicado junto al campo', async () => {
    patchCategoria.mockRejectedValueOnce({ response: { status: 400, data: { nombre: ['Ya existe una categoría con ese nombre.'] } } });
    patchCategoria.mockResolvedValueOnce({ ...LISTA[0], nombre: 'Celulares y tablets' });
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Editar Celulares' }));

    fireEvent.change(screen.getByLabelText('Nombre de Celulares'), { target: { value: 'paraguas' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar' })); });
    expect(screen.getByText('Ya existe una categoría con ese nombre.')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Nombre de Celulares'), { target: { value: '  Celulares y tablets ' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar' })); });
    expect(patchCategoria).toHaveBeenLastCalledWith(1, { nombre: 'Celulares y tablets', descripcion: 'Teléfonos' });
    expect(screen.getByText('Celulares y tablets')).toBeTruthy();
  });

  it('no crea una categoría sin nombre', async () => {
    await renderizar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Crear categoría' })); });
    expect(screen.getByText('El nombre es obligatorio.')).toBeTruthy();
    expect(crearCategoria).not.toHaveBeenCalled();
  });
});
