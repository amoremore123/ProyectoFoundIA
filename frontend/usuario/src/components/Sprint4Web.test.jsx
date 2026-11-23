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

describe('H68 - elegir el canal de los avisos', () => {
  it('muestra push encendido y correo apagado por defecto, y guarda los canales', async () => {
    getPreferenciasNotificaciones.mockResolvedValue({ coincidencias: true, contactos: true, reclamos: true, recordatorios: true });
    guardarPreferenciasNotificaciones.mockImplementation(async (datos) => datos);
    await act(async () => { render(<PreferenciasNotificaciones />); });
    const push = screen.getByRole('checkbox', { name: /Notificaciones en el teléfono/ });
    const correo = screen.getByRole('checkbox', { name: /Correo electrónico/ });
    expect(push.checked).toBe(true);
    expect(correo.checked).toBe(false);
    fireEvent.click(correo);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar preferencias' })); });
    expect(guardarPreferenciasNotificaciones).toHaveBeenCalledWith(expect.objectContaining({ push: true, correo: true }));
    expect(screen.getByRole('status').textContent).toMatch(/Preferencias guardadas/);
  });

  it('respeta los canales que ya estaban guardados', async () => {
    getPreferenciasNotificaciones.mockResolvedValue({ coincidencias: true, contactos: false, reclamos: true, recordatorios: true, push: false, correo: true });
    await act(async () => { render(<PreferenciasNotificaciones />); });
    expect(screen.getByRole('checkbox', { name: /Notificaciones en el teléfono/ }).checked).toBe(false);
    expect(screen.getByRole('checkbox', { name: /Correo electrónico/ }).checked).toBe(true);
    expect(screen.getByRole('checkbox', { name: /Solicitudes de contacto/ }).checked).toBe(false);
  });
});
