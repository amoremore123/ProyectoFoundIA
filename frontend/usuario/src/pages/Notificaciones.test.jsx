import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Notificaciones from './Notificaciones';
import { getNotificaciones, marcarNotificacionLeida } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getNotificaciones: vi.fn(), marcarNotificacionLeida: vi.fn(),
}));

let servidor;

const AVISOS = [
  { id: 1, tipo: 'RECLAMO_RECIBIDO', titulo: 'Nuevo reclamo de objeto', mensaje: 'Un usuario ha enviado un reclamo sobre tu publicación #7.', leida: false, fechaCreacion: '2026-10-19T10:30:00' },
  { id: 2, tipo: 'CONTACTO_RECIBIDO', titulo: 'Nueva solicitud de contacto', mensaje: 'Alguien desea contactarte por tu publicación de objeto encontrado #7.', leida: true, fechaCreacion: '2026-10-18T09:00:00' },
];

function renderizar() {
  return render(<MemoryRouter><Notificaciones /></MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  servidor = AVISOS.map((n) => ({ ...n }));
  getNotificaciones.mockImplementation(async () => servidor.map((n) => ({ ...n })));
  marcarNotificacionLeida.mockImplementation(async (id) => {
    servidor = servidor.map((n) => (n.id === id ? { ...n, leida: true } : n));
    return servidor.find((n) => n.id === id);
  });
});
afterEach(cleanup);

describe('H18/H20 - Pantalla de notificaciones', () => {
  it('muestra el tipo de cada aviso, el contador y explica qué genera cada uno', async () => {
    renderizar();
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
    expect(screen.getByText('Reclamo')).toBeTruthy();
    expect(screen.getByText('Solicitud de contacto')).toBeTruthy();
    expect(screen.getByText('1 sin leer')).toBeTruthy();
    expect(screen.getByText(/Tus datos de contacto nunca se comparten/)).toBeTruthy();
    expect(screen.queryByText(/@/)).toBeNull();
  });

  it('abrir un aviso lo marca como leído y se conserva al consultar de nuevo', async () => {
    const user = userEvent.setup();
    const primera = renderizar();
    await user.click(await screen.findByRole('button', { name: /Reclamo: Nuevo reclamo de objeto\. Sin leer/ }));

    await waitFor(() => expect(marcarNotificacionLeida).toHaveBeenCalledWith(1));
    expect(await screen.findByText('Todas leídas')).toBeTruthy();

    primera.unmount();
    renderizar();
    expect(await screen.findByRole('button', { name: /Reclamo: Nuevo reclamo de objeto\. Leída/ })).toBeTruthy();
  });

  it('un aviso ya leído no vuelve a llamar a la API', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Solicitud de contacto: Nueva solicitud de contacto\. Leída/ }));
    expect(marcarNotificacionLeida).not.toHaveBeenCalled();
  });

  it('si falla marcar como leído, el aviso sigue sin leer y no hay éxito ficticio', async () => {
    const user = userEvent.setup();
    marcarNotificacionLeida.mockRejectedValueOnce({ response: { status: 404, data: { mensaje: 'Notificación no encontrada' } } });
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Reclamo: Nuevo reclamo de objeto\. Sin leer/ }));

    expect(await screen.findByText('Notificación no encontrada')).toBeTruthy();
    expect(screen.getByText('1 sin leer')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
  });

  it('lista vacía muestra su mensaje', async () => {
    servidor = [];
    renderizar();
    expect(await screen.findByText('No tienes notificaciones todavía.')).toBeTruthy();
  });

  it('un fallo de consulta se distingue de la lista vacía y Reintentar recupera', async () => {
    const user = userEvent.setup();
    getNotificaciones.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();
    expect(await screen.findByText(/No se pudieron cargar las notificaciones/)).toBeTruthy();
    expect(screen.queryByText('No tienes notificaciones todavía.')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
  });
});
