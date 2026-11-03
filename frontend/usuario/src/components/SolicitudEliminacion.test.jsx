import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SolicitudEliminacion, { fechaSolicitudLocal } from './SolicitudEliminacion';
import { getSolicitudEliminacion, solicitarEliminacion } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getSolicitudEliminacion: vi.fn(), solicitarEliminacion: vi.fn(),
}));

const INFO = 'Esta solicitud queda pendiente de revisión. No se han eliminado tu cuenta ni tus datos.';
const SIN = { id: null, estado: 'SIN_SOLICITUD', fechaSolicitud: null, repetido: false, informacion: INFO };
const PENDIENTE = { id: 9, estado: 'PENDIENTE', fechaSolicitud: '2026-10-27T15:04:05', repetido: false, informacion: INFO };

async function renderizar() {
  await act(async () => { render(<SolicitudEliminacion />); });
}

const botonSolicitar = () => screen.queryByRole('button', { name: 'Solicitar eliminación de mi cuenta' });

beforeEach(() => { getSolicitudEliminacion.mockReset(); solicitarEliminacion.mockReset(); });
afterEach(() => cleanup());

describe('H34 - Solicitud de eliminación de la cuenta', () => {
  it('sin solicitud explica el trámite y ofrece el botón', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(SIN);
    await renderizar();
    expect(screen.getByText(INFO)).toBeTruthy();
    expect(botonSolicitar()).toBeTruthy();
  });

  it('cancelar la confirmación no envía nada', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(SIN);
    await renderizar();
    fireEvent.click(botonSolicitar());
    expect(screen.getByRole('alertdialog')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(solicitarEliminacion).not.toHaveBeenCalled();
  });

  it('al confirmar registra la solicitud y vuelve a consultar el estado real', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(SIN).mockResolvedValueOnce(PENDIENTE);
    solicitarEliminacion.mockResolvedValueOnce({ ...PENDIENTE });
    await renderizar();
    fireEvent.click(botonSolicitar());
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, enviar solicitud' })); });

    expect(solicitarEliminacion).toHaveBeenCalledTimes(1);
    expect(getSolicitudEliminacion).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Pendiente de revisión')).toBeTruthy();
    expect(screen.getByRole('status').textContent).toContain('Tu cuenta sigue activa');
    expect(botonSolicitar()).toBeNull();
    // Nunca se afirma que la cuenta fue borrada
    expect(document.body.textContent).not.toMatch(/cuenta (fue )?(borrada|eliminada)/i);
  });

  it('una solicitud repetida no se presenta como nueva', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(SIN).mockResolvedValueOnce(PENDIENTE);
    solicitarEliminacion.mockResolvedValueOnce({ ...PENDIENTE, repetido: true });
    await renderizar();
    fireEvent.click(botonSolicitar());
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, enviar solicitud' })); });

    expect(screen.getByRole('status').textContent).toContain('Ya tenías una solicitud pendiente');
  });

  it('si el envío falla muestra el error y no cambia el estado', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(SIN);
    solicitarEliminacion.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    await renderizar();
    fireEvent.click(botonSolicitar());
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, enviar solicitud' })); });

    expect(screen.getByRole('alert').textContent).toContain('No se pudo registrar la solicitud');
    expect(screen.queryByText('Pendiente de revisión')).toBeNull();
    expect(botonSolicitar()).toBeTruthy();
  });

  it('con una solicitud pendiente muestra el estado y la fecha, sin botón', async () => {
    getSolicitudEliminacion.mockResolvedValueOnce(PENDIENTE);
    await renderizar();
    expect(screen.getByText('Pendiente de revisión')).toBeTruthy();
    expect(screen.getByText(/Solicitada el/)).toBeTruthy();
    expect(botonSolicitar()).toBeNull();
  });

  it('si no se puede consultar permite reintentar', async () => {
    getSolicitudEliminacion.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(SIN);
    await renderizar();
    expect(screen.getByRole('alert')).toBeTruthy();
    expect(botonSolicitar()).toBeNull();

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(botonSolicitar()).toBeTruthy();
  });

  it('interpreta la fecha de la API como UTC', () => {
    const esperado = new Date('2026-10-27T15:04:05Z').toLocaleString('es-PE', { dateStyle: 'long', timeStyle: 'short' });
    expect(fechaSolicitudLocal('2026-10-27T15:04:05')).toBe(esperado);
    expect(fechaSolicitudLocal(null)).toBe('');
  });
});
