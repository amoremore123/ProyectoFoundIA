import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AvisosDestacados, { enlaceAviso } from './AvisosDestacados';
import PreferenciasNotificaciones from './PreferenciasNotificaciones';
import { getAvisosDestacados, getPreferenciasNotificaciones, guardarPreferenciasNotificaciones } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getAvisosDestacados: vi.fn(), getPreferenciasNotificaciones: vi.fn(), guardarPreferenciasNotificaciones: vi.fn(),
}));

beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const conRouter = async (ui) => { await act(async () => { render(<MemoryRouter>{ui}</MemoryRouter>); }); };

describe('H71 - avisos destacados en la web', () => {
  const AVISO = { id: 1, titulo: 'Semana de objetos perdidos', texto: 'Revisa la mesa de la biblioteca.', enlace: '/explorar?origen=semana', fechaFin: '2026-11-28' };

  it('muestra los vigentes con su enlace interno y se pueden ocultar en esta pestaña', async () => {
    getAvisosDestacados.mockResolvedValue([AVISO]);
    await conRouter(<AvisosDestacados />);
    expect(screen.getByRole('heading', { name: 'Semana de objetos perdidos' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver más' }).getAttribute('href')).toBe('/explorar?origen=semana');
    fireEvent.click(screen.getByRole('button', { name: /Ocultar el aviso/ }));
    expect(screen.queryByRole('heading', { name: 'Semana de objetos perdidos' })).toBeNull();
    expect(JSON.parse(sessionStorage.getItem('foundia-avisos-ocultos'))).toEqual([1]);
  });

  it('sin avisos o si la API falla no muestra nada', async () => {
    getAvisosDestacados.mockResolvedValue([]);
    const { container } = render(<MemoryRouter><AvisosDestacados /></MemoryRouter>);
    await act(async () => {});
    expect(container.textContent).toBe('');
    cleanup();
    getAvisosDestacados.mockRejectedValue(new Error('Network Error'));
    const otro = render(<MemoryRouter><AvisosDestacados /></MemoryRouter>);
    await act(async () => {});
    expect(otro.container.textContent).toBe('');
  });

  it('solo abre rutas internas', () => {
    expect(enlaceAviso('/explorar')).toBe('/explorar');
    expect(enlaceAviso('//sitio-falso.example')).toBeNull();
    expect(enlaceAviso('https://sitio-falso.example')).toBeNull();
    expect(enlaceAviso(null)).toBeNull();
  });
});

describe('H68 - elegir el canal de cada tipo de aviso', () => {
  it('por defecto todo llega por el teléfono y se puede elegir el canal de cada tipo', async () => {
    getPreferenciasNotificaciones.mockResolvedValue({ coincidencias: true, contactos: true, reclamos: true, recordatorios: true });
    guardarPreferenciasNotificaciones.mockImplementation(async (datos) => datos);
    await act(async () => { render(<PreferenciasNotificaciones />); });
    const chat = screen.getByRole('combobox', { name: 'Mensajes del chat' });
    expect(chat.value).toBe('PUSH');
    expect(screen.getAllByRole('combobox')).toHaveLength(6);
    fireEvent.change(screen.getByRole('combobox', { name: 'Posibles coincidencias' }), { target: { value: 'CORREO' } });
    fireEvent.change(chat, { target: { value: 'AMBOS' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar preferencias' })); });
    expect(guardarPreferenciasNotificaciones).toHaveBeenCalledWith(expect.objectContaining({
      canales: expect.objectContaining({ coincidencias: 'CORREO', mensajes: 'AMBOS', reclamos: 'PUSH' }),
    }));
    expect(screen.getByRole('status').textContent).toMatch(/Preferencias guardadas/);
  });

  it('respeta los canales guardados y corrige un valor desconocido a PUSH', async () => {
    getPreferenciasNotificaciones.mockResolvedValue({
      coincidencias: true, contactos: false, reclamos: true, recordatorios: true,
      canales: { coincidencias: 'NINGUNO', contactos: 'AMBOS', reclamos: 'RARO', recordatorios: 'CORREO', mensajes: 'PUSH', sistema: 'PUSH' },
    });
    await act(async () => { render(<PreferenciasNotificaciones />); });
    expect(screen.getByRole('combobox', { name: 'Posibles coincidencias' }).value).toBe('NINGUNO');
    expect(screen.getByRole('combobox', { name: 'Solicitudes de contacto' }).value).toBe('AMBOS');
    expect(screen.getByRole('combobox', { name: 'Reclamos' }).value).toBe('PUSH');
    expect(screen.getByRole('checkbox', { name: /Solicitudes de contacto/ }).checked).toBe(false);
  });
});
