import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EstadoReclamoCustodia from './EstadoReclamoCustodia';
import InvitarAmigo from './InvitarAmigo';
import { crearInvitacion, getQrRetiro } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getQrRetiro: vi.fn(), crearInvitacion: vi.fn(),
}));
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async (texto) => `data:image/png;base64,QR-${texto}`) } }));

beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

describe('H42/H44 - Estado del reclamo en custodia y QR de retiro', () => {
  it('pendiente: explica que la biblioteca está revisando y no muestra QR', () => {
    render(<EstadoReclamoCustodia estado="PENDIENTE" objetoId={8} nombre="Mochila" />);
    expect(screen.getByRole('heading', { name: 'Reclamo en revisión' })).toBeTruthy();
    expect(screen.queryByRole('img')).toBeNull();
    expect(getQrRetiro).not.toHaveBeenCalled();
  });

  it('aceptado: muestra el QR del token firmado, su vencimiento y permite descargarlo', async () => {
    getQrRetiro.mockResolvedValue({ token: 'tok-firmado', expira: '2026-11-23T15:30:00' });
    render(<EstadoReclamoCustodia estado="ACEPTADO" objetoId={8} nombre="Mochila" />);
    const qr = await screen.findByRole('img', { name: 'QR de retiro de Mochila' });
    expect(qr.getAttribute('src')).toBe('data:image/png;base64,QR-tok-firmado');
    expect(screen.getByText(/Válido hasta el/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Descargar QR/ }).getAttribute('download')).toBe('qr-retiro-8.png');
  });

  it('aceptado con QR vencido o usado: lo dice y permite reintentar', async () => {
    getQrRetiro.mockRejectedValue({ response: { status: 409, data: { mensaje: 'El QR de retiro ya no está disponible' } } });
    render(<EstadoReclamoCustodia estado="ACEPTADO" objetoId={8} nombre="Mochila" />);
    expect((await screen.findByRole('alert')).textContent).toMatch(/ya no está disponible/);
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeTruthy();
  });

  it('rechazado y retirado tienen su propio mensaje; un estado desconocido no muestra nada', () => {
    const { rerender, container } = render(<EstadoReclamoCustodia estado="RECHAZADO" objetoId={8} nombre="Mochila" />);
    expect(screen.getByRole('heading', { name: 'Reclamo rechazado' })).toBeTruthy();
    rerender(<EstadoReclamoCustodia estado="RETIRADO" objetoId={8} nombre="Mochila" />);
    expect(screen.getByRole('heading', { name: 'Objeto retirado' })).toBeTruthy();
    rerender(<EstadoReclamoCustodia estado="ASIGNADO_A_OTRO" objetoId={8} nombre="Mochila" />);
    expect(screen.getByRole('heading', { name: 'Este objeto ya tiene dueño confirmado' })).toBeTruthy();
    rerender(<EstadoReclamoCustodia estado="SIN_RECLAMO" objetoId={8} nombre="Mochila" />);
    expect(container.textContent).toBe('');
  });
});

describe('H40 - Invitar a un amigo desde el perfil', () => {
  const URL_INVITACION = 'http://localhost:5173/registro?invitacion=AbCdEfGhIjKlMnOpQrStUvWxYz012345';

  it('crea el enlace y lo copia, sin datos personales en el texto', async () => {
    crearInvitacion.mockResolvedValue({ url: URL_INVITACION });
    // userEvent trae su propio portapapeles simulado: se lee de ahí
    const user = userEvent.setup();
    render(<InvitarAmigo />);
    expect(screen.getByText(/no incluye tu nombre, tu correo ni tu sesión/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Crear enlace de invitación' }));
    expect(screen.getByRole('textbox', { name: 'Tu enlace de invitación' }).value).toBe(URL_INVITACION);
    await user.click(screen.getByRole('button', { name: 'Copiar enlace' }));
    expect(await navigator.clipboard.readText()).toBe(URL_INVITACION);
    expect(screen.getByRole('status').textContent).toMatch(/Enlace copiado/);
  });

  it('cancelar el menú de compartir no se anuncia como enviado', async () => {
    crearInvitacion.mockResolvedValue({ url: URL_INVITACION });
    navigator.share = vi.fn().mockRejectedValue(Object.assign(new Error('cancelado'), { name: 'AbortError' }));
    const user = userEvent.setup();
    render(<InvitarAmigo />);
    await user.click(screen.getByRole('button', { name: 'Crear enlace de invitación' }));
    await act(async () => { await user.click(screen.getByRole('button', { name: 'Compartir' })); });
    expect(navigator.share).toHaveBeenCalledWith(expect.objectContaining({ url: URL_INVITACION }));
    expect(screen.queryByText(/compartida/)).toBeNull();
    delete navigator.share;
  });

  it('si el servidor falla, lo dice sin mostrar un enlace', async () => {
    crearInvitacion.mockRejectedValue({ response: { status: 500 } });
    const user = userEvent.setup();
    render(<InvitarAmigo />);
    await user.click(screen.getByRole('button', { name: 'Crear enlace de invitación' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/No se pudo crear la invitación/);
    expect(screen.queryByRole('textbox')).toBeNull();
  });
});
