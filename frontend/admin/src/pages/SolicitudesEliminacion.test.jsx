import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import SolicitudesEliminacion from './SolicitudesEliminacion';
import {
  atenderEliminacion, getEliminacion, getSolicitudesEliminacion, rechazarEliminacion, reaplicarEliminaciones,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getSolicitudesEliminacion: vi.fn(), getEliminacion: vi.fn(), atenderEliminacion: vi.fn(),
  rechazarEliminacion: vi.fn(), reaplicarEliminaciones: vi.fn(),
}));

const pagina = (results) => ({ count: results.length, results, next: null, previous: null });
const USUARIO = { id: 5, nombre: 'Carla Ríos', correo: 'carla@foundia.dev', rol: 'USUARIO', estado: 'ACTIVO' };
const SOLICITUD = {
  id: 3, estado: 'PENDIENTE', fechaSolicitud: '2026-11-23T15:00:00Z', fechaLimite: '2026-12-07', vencida: false,
  usuario: USUARIO, atendidaPor: null, fechaAtencion: null, motivoRechazo: '', requiereReaplicar: false,
};
const EFECTO = {
  publicacionesQueSeEliminan: 7, publicacionesQueSeConservan: 1, fotosDePublicaciones: 2, fotoDePerfil: true,
  mensajesDelChat: 5, fotosDelChat: 1, avisos: 12, dispositivos: 1, reclamosQueSeRetiran: 1, reclamosQueSeConservan: 0,
  contactos: 0, reportes: 0,
};
const montar = async () => { await act(async () => { render(<ToastProvider><SolicitudesEliminacion /></ToastProvider>); }); };

beforeEach(() => {
  vi.resetAllMocks();
  getSolicitudesEliminacion.mockResolvedValue(pagina([SOLICITUD]));
  getEliminacion.mockResolvedValue({ ...SOLICITUD, efecto: EFECTO });
});
afterEach(cleanup);

const abrir = async () => {
  await montar();
  fireEvent.click(screen.getByRole('button', { name: /Abrir la solicitud de Carla Ríos/ }));
  await screen.findByRole('region', { name: /Solicitud de Carla Ríos/ });
};

describe('H34 - eliminación de cuentas en el panel', () => {
  it('lista las pendientes con su fecha límite y marca las vencidas', async () => {
    getSolicitudesEliminacion.mockResolvedValue(pagina([{ ...SOLICITUD, vencida: true }]));
    await montar();
    expect(getSolicitudesEliminacion).toHaveBeenCalledWith({ estado: 'PENDIENTE' }, 1);
    expect(screen.getByText('2026-12-07')).toBeTruthy();
    expect(screen.getByText(/Vencida/)).toBeTruthy();
  });

  it('muestra la vista previa de lo que se hará antes de atender', async () => {
    await abrir();
    const detalle = screen.getByLabelText('Qué se hará con la cuenta');
    expect(detalle).toBeTruthy();
    expect(screen.getByText('Publicaciones que se eliminan')).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
  });

  it('solo deja atender si se escribe el correo de la cuenta y pide una segunda confirmación', async () => {
    atenderEliminacion.mockResolvedValue({ correoEnviado: true, resumen: {} });
    await abrir();
    const boton = screen.getByRole('button', { name: 'Atender y eliminar' });
    expect(boton.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/escribe el correo/), { target: { value: 'otra@foundia.dev' } });
    expect(boton.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/escribe el correo/), { target: { value: 'CARLA@foundia.dev' } });
    expect(boton.disabled).toBe(false);
    fireEvent.click(boton);
    expect(atenderEliminacion).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la cuenta' }));
    await waitFor(() => expect(atenderEliminacion).toHaveBeenCalledWith(3, 'CARLA@foundia.dev'));
  });

  it('rechazar exige un motivo y lo envía', async () => {
    rechazarEliminacion.mockResolvedValue({ ...SOLICITUD, estado: 'RECHAZADA' });
    await abrir();
    const boton = screen.getByRole('button', { name: 'Rechazar' });
    expect(boton.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/escribe el motivo/), { target: { value: 'No es la persona' } });
    expect(boton.disabled).toBe(false);
    fireEvent.click(boton);
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Rechazar' }));
    await waitFor(() => expect(rechazarEliminacion).toHaveBeenCalledWith(3, 'No es la persona'));
  });

  it('muestra el motivo del servidor si no se puede atender (por ejemplo, cuenta del personal)', async () => {
    atenderEliminacion.mockRejectedValue({ response: { status: 409, data: { detail: 'Es una cuenta del personal.' } } });
    await abrir();
    fireEvent.change(screen.getByLabelText(/escribe el correo/), { target: { value: 'carla@foundia.dev' } });
    fireEvent.click(screen.getByRole('button', { name: 'Atender y eliminar' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la cuenta' }));
    expect((await screen.findByText('Es una cuenta del personal.'))).toBeTruthy();
  });

  it('ofrece volver a anonimizar las cuentas que una restauración trajo de vuelta', async () => {
    getSolicitudesEliminacion.mockResolvedValue(pagina([{ ...SOLICITUD, estado: 'ATENDIDA', requiereReaplicar: true }]));
    reaplicarEliminaciones.mockResolvedValue({ cuentasAnonimizadasDeNuevo: 2 });
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Volver a anonimizarlas' }));
    await waitFor(() => expect(reaplicarEliminaciones).toHaveBeenCalled());
    expect(await screen.findByText(/anonimizadas de nuevo: 2/)).toBeTruthy();
  });
});
