import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import MensajesBiblioteca from './MensajesBiblioteca';
import { puedeVer } from '../config/permisos';
import { getBandejaChat, getMensajesChat, marcarChatLeido, responderChat } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getBandejaChat: vi.fn(), getMensajesChat: vi.fn(), responderChat: vi.fn(), marcarChatLeido: vi.fn(),
}));

const CONV = {
  id: 7, tipo: 'CON_BIBLIOTECA', objetoId: 15, objetoNombre: 'Termo azul', soloLectura: false, bloqueada: false,
  otro: { id: 21, nombre: 'Ana Pérez' }, sinLeer: 1,
  ultimoMensaje: { id: 30, autorId: 21, autorNombre: 'Ana Pérez', texto: '¿Puedo pasar hoy?', enviadoEn: '2026-11-22T15:00:00' },
};
const MENSAJE = CONV.ultimoMensaje;

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.setItem('admin_token', 'tok');
  localStorage.setItem('admin_usuario', JSON.stringify({ id: 1, nombre: 'Beto', rol: 'BIBLIOTECA' }));
  getBandejaChat.mockResolvedValue([CONV]);
  getMensajesChat.mockResolvedValue({ mensajes: [MENSAJE], ultimoId: 30, soloLectura: false, bloqueada: false });
  marcarChatLeido.mockResolvedValue();
});
afterEach(() => { cleanup(); localStorage.clear(); });

const montar = async () => { await act(async () => { render(<AuthProvider><MensajesBiblioteca /></AuthProvider>); }); };

describe('H63 - bandeja de mensajes de la biblioteca', () => {
  it('BIBLIOTECA puede abrir la bandeja y ve los no leídos', async () => {
    expect(puedeVer({ rol: 'BIBLIOTECA' }, '/mensajes')).toBe(true);
    await montar();
    const item = screen.getByRole('button', { name: /Ana Pérez/ });
    expect(within(item).getByText('1 sin leer')).toBeTruthy();
    expect(within(item).getByText(/Termo azul/)).toBeTruthy();
  });

  it('abre la conversación, la marca como leída y responde', async () => {
    responderChat.mockResolvedValue({ id: 31, autorId: 1, autorNombre: 'Beto', texto: 'Sí, hasta las 6', enviadoEn: '2026-11-22T15:01:00' });
    await montar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ })); });
    expect(getMensajesChat).toHaveBeenCalledWith(7, 0);
    expect(marcarChatLeido).toHaveBeenCalledWith(7);
    expect(within(screen.getByRole('log')).getByText('¿Puedo pasar hoy?')).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Responder' })); });
    expect(screen.getByRole('alert').textContent).toBe('Escribe una respuesta.');
    fireEvent.change(screen.getByLabelText('Respuesta'), { target: { value: ' Sí, hasta las 6 ' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Responder' })); });
    expect(responderChat).toHaveBeenCalledWith(7, 'Sí, hasta las 6');
    expect(within(screen.getByRole('log')).getByText('Tú')).toBeTruthy();
  });

  it('una conversación terminada solo se puede leer', async () => {
    getMensajesChat.mockResolvedValue({ mensajes: [MENSAJE], ultimoId: 30, soloLectura: true, bloqueada: false });
    await montar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ })); });
    expect(screen.getByText(/La conversación terminó/)).toBeTruthy();
    expect(screen.queryByLabelText('Respuesta')).toBeNull();
  });

  it('sin conversaciones lo dice, y un fallo se distingue del vacío', async () => {
    getBandejaChat.mockResolvedValueOnce([]);
    await montar();
    expect(screen.getByText('Todavía nadie escribió a la biblioteca.')).toBeTruthy();
    cleanup();
    getBandejaChat.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    await montar();
    expect(screen.getByRole('alert').textContent).toMatch(/No se pudo cargar la bandeja/);
  });
});
