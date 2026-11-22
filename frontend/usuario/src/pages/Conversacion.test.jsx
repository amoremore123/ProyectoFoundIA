import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import Conversacion from './Conversacion';
import Mensajes from './Mensajes';
import { enlaceInterno } from './Notificaciones';
import { horaChat, unirMensajes, validarFotoChat, validarTexto } from '../utils/chat';
import {
  bloquearConversacion, desbloquearConversacion, enviarMensaje, getConversacion, getConversaciones, getMensajes,
  marcarConversacionLeida, reportarMensaje,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getConversacion: vi.fn(), getConversaciones: vi.fn(), getMensajes: vi.fn(), enviarMensaje: vi.fn(),
  enviarFotoChat: vi.fn(), marcarConversacionLeida: vi.fn(), bloquearConversacion: vi.fn(),
  desbloquearConversacion: vi.fn(), reportarMensaje: vi.fn(),
}));

const YO = { id: 1, nombre: 'Ana' };
const CONV = {
  id: 7, tipo: 'ENTRE_USUARIOS', objetoId: 15, objetoNombre: 'Mochila azul', estado: 'ABIERTA',
  soloLectura: false, bloqueada: false, otro: { id: 2, nombre: 'Luis' }, ultimoMensaje: null, sinLeer: 0,
};
const MENSAJES = [
  { id: 10, autorId: 1, autorNombre: 'Ana', texto: 'Hola, ¿es mi mochila?', imagenUrl: null, enviadoEn: '2026-11-21T15:00:00' },
  { id: 11, autorId: 2, autorNombre: 'Luis', texto: 'Sí, tiene un llavero.', imagenUrl: null, enviadoEn: '2026-11-21T15:01:00' },
];
const respuesta = (mensajes, extra = {}) => ({ mensajes, ultimoId: mensajes.at(-1)?.id || 0, soloLectura: false, bloqueada: false, ...extra });

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.setItem('token', 'tok');
  localStorage.setItem('usuario', JSON.stringify(YO));
  getConversacion.mockResolvedValue(CONV);
  getMensajes.mockResolvedValue(respuesta(MENSAJES));
  marcarConversacionLeida.mockResolvedValue();
});
afterEach(() => { cleanup(); localStorage.clear(); vi.useRealTimers(); });

const abrir = async (ruta = '/mensajes/7') => {
  await act(async () => {
    render(
      <MemoryRouter initialEntries={[ruta]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <Routes>
            <Route path="/mensajes" element={<Mensajes />} />
            <Route path="/mensajes/:id" element={<Conversacion />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
  });
};

describe('H55 - conversación', () => {
  it('muestra los mensajes propios y ajenos, el objeto y la marca como leída', async () => {
    await abrir();
    expect(screen.getByRole('heading', { level: 1, name: 'Luis' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Mochila azul' }).getAttribute('href')).toBe('/objeto/15');
    const log = screen.getByRole('log');
    expect(within(log).getByText('Tú')).toBeTruthy();
    expect(within(log).getByText('Sí, tiene un llavero.')).toBeTruthy();
    expect(getMensajes).toHaveBeenCalledWith('7', 0);
    expect(marcarConversacionLeida).toHaveBeenCalledWith('7');
    // Solo se puede reportar el mensaje del otro
    expect(within(log).getAllByRole('button', { name: /Reportar/ })).toHaveLength(1);
  });

  it('un mensaje vacío muestra el error junto al campo y no se envía', async () => {
    await abrir();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar' })); });
    expect(screen.getByRole('alert').textContent).toBe('Escribe un mensaje.');
    expect(enviarMensaje).not.toHaveBeenCalled();
  });

  it('envía el mensaje, lo agrega a la lista y limpia el campo', async () => {
    enviarMensaje.mockResolvedValue({ id: 12, autorId: 1, autorNombre: 'Ana', texto: 'Mañana a las 10', imagenUrl: null, enviadoEn: '2026-11-21T15:02:00' });
    await abrir();
    fireEvent.change(screen.getByLabelText('Mensaje', { selector: 'textarea' }), { target: { value: '  Mañana a las 10 ' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar' })); });
    expect(enviarMensaje).toHaveBeenCalledWith('7', 'Mañana a las 10');
    expect(within(screen.getByRole('log')).getByText('Mañana a las 10')).toBeTruthy();
    expect(screen.getByLabelText('Mensaje', { selector: 'textarea' }).value).toBe('');
  });

  it('demasiados mensajes seguidos (429) lo explica sin perder lo escrito', async () => {
    enviarMensaje.mockRejectedValue({ response: { status: 429, data: {} } });
    await abrir();
    fireEvent.change(screen.getByLabelText('Mensaje', { selector: 'textarea' }), { target: { value: 'Hola otra vez' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/muchos mensajes seguidos/);
    expect(screen.getByLabelText('Mensaje', { selector: 'textarea' }).value).toBe('Hola otra vez');
  });

  it('consulta cada pocos segundos con el cursor y agrega lo nuevo', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await abrir();
    getMensajes.mockResolvedValueOnce(respuesta([{ id: 13, autorId: 2, autorNombre: 'Luis', texto: '¿Vienes?', imagenUrl: null, enviadoEn: '2026-11-21T15:05:00' }]));
    await act(async () => { await vi.advanceTimersByTimeAsync(4100); });
    expect(getMensajes).toHaveBeenLastCalledWith('7', 11);
    expect(within(screen.getByRole('log')).getByText('¿Vienes?')).toBeTruthy();
  });

  it('H61: en solo lectura no hay campo para escribir y se explica', async () => {
    getMensajes.mockResolvedValue(respuesta(MENSAJES, { soloLectura: true }));
    await abrir();
    expect(screen.getByText(/La conversación terminó/)).toBeTruthy();
    expect(screen.queryByLabelText('Mensaje', { selector: 'textarea' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Bloquear' })).toBeNull();
  });

  it('H56: bloquear pide confirmación, deshabilita la escritura y desbloquear la devuelve', async () => {
    bloquearConversacion.mockResolvedValue({ ...CONV, bloqueada: true });
    desbloquearConversacion.mockResolvedValue({ ...CONV, bloqueada: false });
    await abrir();
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }));
    expect(bloquearConversacion).not.toHaveBeenCalled();
    await act(async () => { fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Bloquear' })); });
    expect(screen.getByText(/hay un bloqueo/)).toBeTruthy();
    expect(screen.queryByLabelText('Mensaje', { selector: 'textarea' })).toBeNull();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Desbloquear' })); });
    expect(screen.getByLabelText('Mensaje', { selector: 'textarea' })).toBeTruthy();
  });

  it('H56: si la otra persona también bloqueó, desbloquear lo explica', async () => {
    getMensajes.mockResolvedValue(respuesta(MENSAJES, { bloqueada: true }));
    desbloquearConversacion.mockResolvedValue({ ...CONV, bloqueada: true });
    await abrir();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Desbloquear' })); });
    expect(screen.getByText(/La otra persona también bloqueó/)).toBeTruthy();
  });

  it('H56: reportar exige un motivo y lo envía', async () => {
    reportarMensaje.mockResolvedValue();
    await abrir();
    fireEvent.click(screen.getByRole('button', { name: 'Reportar el mensaje de Luis' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/3 a 255 caracteres/);
    fireEvent.change(screen.getByLabelText(/Por qué reportas/), { target: { value: 'Me pidió mi teléfono' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' })); });
    expect(reportarMensaje).toHaveBeenCalledWith('7', 11, 'Me pidió mi teléfono');
  });

  it('una conversación ajena o inexistente (404) no se muestra', async () => {
    getConversacion.mockRejectedValue({ response: { status: 404 } });
    await abrir();
    expect(screen.getByRole('heading', { name: 'Conversación no disponible' })).toBeTruthy();
  });

  it('H63: con la biblioteca no se ofrece bloquear', async () => {
    getConversacion.mockResolvedValue({ ...CONV, tipo: 'CON_BIBLIOTECA', otro: { id: null, nombre: 'Biblioteca' } });
    getMensajes.mockResolvedValue(respuesta([]));
    await abrir();
    expect(screen.getByRole('heading', { level: 1, name: 'Biblioteca' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Bloquear' })).toBeNull();
    expect(screen.getByText(/Escribe a la biblioteca/)).toBeTruthy();
  });
});

describe('H55 - lista de conversaciones', () => {
  it('muestra el último mensaje, los no leídos y las terminadas', async () => {
    getConversaciones.mockResolvedValue([
      { ...CONV, sinLeer: 2, ultimoMensaje: MENSAJES[1] },
      { ...CONV, id: 8, objetoNombre: 'Casco', soloLectura: true, otro: { id: 3, nombre: 'Carla' }, ultimoMensaje: { ...MENSAJES[0], texto: null, imagenUrl: '/uploads/x.jpg' } },
    ]);
    await abrir('/mensajes');
    const luis = screen.getByRole('link', { name: /Luis/ });
    expect(luis.getAttribute('href')).toBe('/mensajes/7');
    expect(within(luis).getByText('2 sin leer')).toBeTruthy();
    const carla = screen.getByRole('link', { name: /Carla/ });
    expect(within(carla).getByText('Foto')).toBeTruthy();
    expect(within(carla).getByText('Terminada')).toBeTruthy();
  });

  it('sin conversaciones explica cómo empezar una', async () => {
    getConversaciones.mockResolvedValue([]);
    await abrir('/mensajes');
    expect(screen.getByText('Todavía no tienes conversaciones.')).toBeTruthy();
  });
});

describe('utilidades del chat', () => {
  it('valida texto y foto, une sin repetir y lleva el aviso a la conversación', () => {
    expect(validarTexto('   ')).toBe('Escribe un mensaje.');
    expect(validarTexto('a'.repeat(1001))).toMatch(/1000/);
    expect(validarFotoChat(new File(['x'], 'a.gif', { type: 'image/gif' }))).toMatch(/JPEG o PNG/);
    expect(validarFotoChat(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'a.png', { type: 'image/png' }))).toMatch(/5 MiB/);
    expect(unirMensajes([{ id: 2 }, { id: 1 }], [{ id: 2 }, { id: 3 }]).map((m) => m.id)).toEqual([1, 2, 3]);
    expect(horaChat('2026-11-21T15:00:00', new Date('2026-11-25T12:00:00Z'))).toMatch(/21/);
    expect(enlaceInterno(null, { tipo: 'MENSAJE_CHAT', mensaje: 'Tienes un mensaje nuevo en la conversación #42.' })).toBe('/mensajes/42');
    expect(enlaceInterno(null, { tipo: 'MENSAJE_CHAT', mensaje: 'Mensaje nuevo' })).toBe('/mensajes');
    expect(enlaceInterno('/mensajes/5')).toBe('/mensajes/5');
  });
});
