import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard from './Dashboard';
import { getDashboard, getPublicaciones, getReportes, getUsuarios } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getDashboard: vi.fn(),
  getPublicaciones: vi.fn(),
  getReportes: vi.fn(),
  getUsuarios: vi.fn(),
}));
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ user: { nombre: 'Yajaira' } }) }));

const resumen = {
  usuarios: 12,
  objetosActivos: 7,
  reportesPendientes: 3,
  categorias: 5,
  objetos: { perdidos: 6, encontrados: 4, recuperados: 3, activos: 7 },
};
const objetos = [
  { id: 1, nombre: 'Mochila negra', tipo: 'PERDIDO', estado: 'ACTIVO', ubicacion: 'Biblioteca', fecha_publicacion: new Date().toISOString(), publicado_por: 'Ana Pérez' },
  { id: 2, nombre: 'Oculto', tipo: 'PERDIDO', estado: 'OCULTO', fecha_publicacion: new Date().toISOString() },
];

async function renderizar() {
  await act(async () => { render(<MemoryRouter><Dashboard /></MemoryRouter>); });
}

beforeEach(() => {
  [getDashboard, getPublicaciones, getReportes, getUsuarios].forEach((f) => f.mockReset());
  // Sin animación de contadores: los valores se ven de inmediato
  window.matchMedia = vi.fn().mockReturnValue({ matches: true });
});
afterEach(() => cleanup());

describe('Fase 3 - Dashboard administrativo', () => {
  it('muestra los indicadores reales, el saludo y el porcentaje de recuperación', async () => {
    getDashboard.mockResolvedValueOnce(resumen);
    getPublicaciones.mockResolvedValueOnce(objetos);
    getReportes.mockResolvedValueOnce([]);
    getUsuarios.mockResolvedValueOnce([]);
    await renderizar();

    expect(screen.getByRole('heading', { name: /Hola, Yajaira/ })).toBeTruthy();
    const indicadores = screen.getByRole('region', { name: 'Indicadores generales' });
    const valor = (etiqueta) => within(indicadores).getByText(etiqueta).parentElement.querySelector('.indicador-valor').textContent;
    expect(valor('Objetos reportados')).toBe('10');
    expect(valor('Objetos recuperados')).toBe('3');
    expect(valor('Denuncias pendientes')).toBe('3');
    expect(valor('Usuarios registrados')).toBe('12');
    expect(within(indicadores).getByText('50% de los perdidos')).toBeTruthy();
    expect(screen.getByRole('img', { name: '50% de los objetos perdidos fueron recuperados' })).toBeTruthy();
  });

  it('lista solo objetos visibles en recientes y en la actividad', async () => {
    getDashboard.mockResolvedValueOnce(resumen);
    getPublicaciones.mockResolvedValueOnce(objetos);
    getReportes.mockResolvedValueOnce([]);
    getUsuarios.mockResolvedValueOnce([]);
    await renderizar();

    const recientes = screen.getByRole('region', { name: /Objetos recientes/ });
    expect(within(recientes).getByText('Mochila negra')).toBeTruthy();
    expect(within(recientes).queryByText('Oculto')).toBeNull();
    expect(screen.getByText('Objeto perdido reportado')).toBeTruthy();
  });

  it('sin objetos muestra estados vacíos, no cifras inventadas', async () => {
    getDashboard.mockResolvedValueOnce({ ...resumen, objetos: { perdidos: 0, encontrados: 0, recuperados: 0, activos: 0 } });
    getPublicaciones.mockResolvedValueOnce([]);
    getReportes.mockResolvedValueOnce([]);
    getUsuarios.mockResolvedValueOnce([]);
    await renderizar();

    expect(screen.getByText('Aún no hay objetos publicados en estos meses.')).toBeTruthy();
    expect(screen.getByText('Aún no hay objetos publicados.')).toBeTruthy();
    expect(screen.getByText('Todavía no hay actividad.')).toBeTruthy();
    expect(screen.getByRole('img', { name: 'Aún no hay objetos perdidos' })).toBeTruthy();
  });

  it('si una lista falla, avisa y mantiene el resto del panel', async () => {
    getDashboard.mockResolvedValueOnce(resumen);
    getPublicaciones.mockResolvedValueOnce(objetos);
    getReportes.mockRejectedValueOnce(new Error('caída'));
    getUsuarios.mockResolvedValueOnce([]);
    await renderizar();

    expect(screen.getByText(/No se pudieron cargar: denuncias/)).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Indicadores generales' })).toBeTruthy();
  });

  it('muestra el error del servidor y permite reintentar', async () => {
    getDashboard.mockRejectedValueOnce({ response: { status: 500, data: { detail: 'Servidor no disponible' } } });
    [getPublicaciones, getReportes, getUsuarios].forEach((f) => f.mockResolvedValue([]));
    await renderizar();
    expect(screen.getByRole('alert').textContent).toContain('Servidor no disponible');

    getDashboard.mockResolvedValueOnce(resumen);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('region', { name: 'Indicadores generales' })).toBeTruthy();
  });
});
