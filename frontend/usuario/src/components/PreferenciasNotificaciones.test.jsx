import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PreferenciasNotificaciones from './PreferenciasNotificaciones';
import { getPreferenciasNotificaciones, guardarPreferenciasNotificaciones } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getPreferenciasNotificaciones: vi.fn(), guardarPreferenciasNotificaciones: vi.fn(),
}));

const TODAS = { coincidencias: true, contactos: true, reclamos: true };

async function renderizar() {
  await act(async () => { render(<PreferenciasNotificaciones />); });
}
const casilla = (nombre) => screen.getByRole('checkbox', { name: new RegExp(nombre) });
const botonGuardar = () => screen.getByRole('button', { name: 'Guardar preferencias' });

beforeEach(() => { getPreferenciasNotificaciones.mockReset(); guardarPreferenciasNotificaciones.mockReset(); });
afterEach(() => cleanup());

describe('H24 - Preferencias de avisos', () => {
  it('muestra los valores guardados y no permite guardar sin cambios', async () => {
    getPreferenciasNotificaciones.mockResolvedValueOnce({ coincidencias: true, contactos: false, reclamos: true });
    await renderizar();
    expect(casilla('Posibles coincidencias').checked).toBe(true);
    expect(casilla('Solicitudes de contacto').checked).toBe(false);
    expect(botonGuardar().disabled).toBe(true);
  });

  it('envía los tres valores y confirma solo tras la respuesta', async () => {
    getPreferenciasNotificaciones.mockResolvedValueOnce(TODAS);
    guardarPreferenciasNotificaciones.mockResolvedValueOnce({ ...TODAS, reclamos: false });
    await renderizar();
    fireEvent.click(casilla('Reclamos'));
    await act(async () => { fireEvent.click(botonGuardar()); });

    expect(guardarPreferenciasNotificaciones).toHaveBeenCalledWith({ coincidencias: true, contactos: true, reclamos: false });
    expect(screen.getByRole('status').textContent).toContain('Preferencias guardadas');
    expect(casilla('Reclamos').checked).toBe(false);
    expect(botonGuardar().disabled).toBe(true);
  });

  it('si falla el guardado muestra el error y conserva lo que elegiste', async () => {
    getPreferenciasNotificaciones.mockResolvedValueOnce(TODAS);
    guardarPreferenciasNotificaciones.mockRejectedValueOnce({ response: { status: 400, data: { mensaje: 'Los tres valores son obligatorios.' } } });
    await renderizar();
    fireEvent.click(casilla('Posibles coincidencias'));
    await act(async () => { fireEvent.click(botonGuardar()); });

    expect(screen.getByRole('alert').textContent).toContain('Los tres valores son obligatorios.');
    expect(screen.queryByText(/Preferencias guardadas/)).toBeNull();
    expect(casilla('Posibles coincidencias').checked).toBe(false);
  });

  it('cancelar vuelve a los valores guardados', async () => {
    getPreferenciasNotificaciones.mockResolvedValueOnce(TODAS);
    await renderizar();
    fireEvent.click(casilla('Solicitudes de contacto'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(casilla('Solicitudes de contacto').checked).toBe(true);
    expect(guardarPreferenciasNotificaciones).not.toHaveBeenCalled();
  });

  it('si no carga permite reintentar', async () => {
    getPreferenciasNotificaciones.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(TODAS);
    await renderizar();
    expect(screen.getByRole('alert')).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(casilla('Reclamos').checked).toBe(true);
  });
});
