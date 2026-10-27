import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard, { formatearDuracion } from './Dashboard';
import { getDashboard } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), getDashboard: vi.fn() }));

const respuesta = {
  usuarios: 12,
  objetosActivos: 7,
  reportesPendientes: 1,
  categorias: 5,
  rango: { desde: null, hasta: null, campo: 'fecha_publicacion', zonaHoraria: 'UTC' },
  objetos: { perdidos: 6, encontrados: 4, recuperados: 2, activos: 7 },
  recuperacion: {
    promedioHoras: 27, promedioDias: 1.1, medianaHoras: 27, minimoHoras: 6, maximoHoras: 48,
    muestra: 2, sinFecha: 1, fechasIncoherentes: 0,
  },
  categoriasTop: [
    { id: 2, nombre: 'Celulares', total: 4, perdidos: 3, encontrados: 1 },
    { id: 1, nombre: 'Mochilas', total: 2, perdidos: 2, encontrados: 0 },
  ],
  ubicacionesTop: [{ nombre: 'Biblioteca', total: 3, perdidos: 2, encontrados: 1 }],
  sinUbicacion: 1,
};

async function renderizar() {
  await act(async () => { render(<Dashboard />); });
}

beforeEach(() => {
  getDashboard.mockReset();
  // Sin animación de contadores: los valores se ven de inmediato
  window.matchMedia = vi.fn().mockReturnValue({ matches: true });
});
afterEach(() => cleanup());

describe('H30 - Formato del tiempo de recuperación', () => {
  it('usa horas hasta 48 h y días desde ahí', () => {
    expect(formatearDuracion(null)).toBe('—');
    expect(formatearDuracion(0.4)).toBe('menos de 1 h');
    expect(formatearDuracion(27)).toBe('27 h');
    expect(formatearDuracion(6.25)).toBe('6.3 h');
    expect(formatearDuracion(48)).toBe('2 días');
    expect(formatearDuracion(84)).toBe('3.5 días');
  });
});

describe('H29/H30/H31 - Dashboard administrativo', () => {
  it('muestra los conteos del periodo, el tiempo de recuperación y los rankings', async () => {
    getDashboard.mockResolvedValueOnce(respuesta);
    await renderizar();

    expect(getDashboard).toHaveBeenCalledWith({ desde: '', hasta: '' }, expect.anything());
    expect(screen.getByText('Todo el historial')).toBeTruthy();
    expect(screen.getByText('Parte de los perdidos')).toBeTruthy();

    expect(screen.getByTestId('promedio-recuperacion').textContent).toBe('27 h');
    expect(screen.getByText(/Basado en 2 recuperaciones/)).toBeTruthy();
    expect(screen.getByText('1 recuperación antigua no tiene fecha y no se incluye.')).toBeTruthy();

    const categorias = screen.getByRole('region', { name: /Categorías con más reportes/ });
    const filas = within(categorias).getAllByRole('listitem');
    expect(filas.map((f) => within(f).getByText(/Celulares|Mochilas/).textContent)).toEqual(['Celulares', 'Mochilas']);
    expect(within(filas[0]).getByText('4 publicaciones')).toBeTruthy();
    expect(within(filas[0]).getByText('3 perdidos · 1 encontrado')).toBeTruthy();

    const ubicaciones = screen.getByRole('region', { name: /Ubicaciones con más reportes/ });
    expect(within(ubicaciones).getByText('Biblioteca')).toBeTruthy();
    expect(within(ubicaciones).getByText('1 publicación no indica ubicación.')).toBeTruthy();
  });

  it('sin recuperaciones ni publicaciones muestra estados vacíos, no ceros inventados', async () => {
    getDashboard.mockResolvedValueOnce({
      ...respuesta,
      recuperacion: { promedioHoras: null, promedioDias: null, medianaHoras: null, minimoHoras: null,
        maximoHoras: null, muestra: 0, sinFecha: 0, fechasIncoherentes: 0 },
      categoriasTop: [],
      ubicacionesTop: [],
      sinUbicacion: 0,
    });
    await renderizar();

    expect(screen.getByText('Aún no hay recuperaciones con fecha en este periodo.')).toBeTruthy();
    expect(screen.queryByTestId('promedio-recuperacion')).toBeNull();
    expect(screen.getByText('No hay publicaciones en este periodo.')).toBeTruthy();
    expect(screen.getByText('No hay publicaciones con ubicación en este periodo.')).toBeTruthy();
  });

  it('aplica y limpia el filtro de fechas', async () => {
    getDashboard.mockResolvedValue(respuesta);
    await renderizar();

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-10-01' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-31' } });
    getDashboard.mockResolvedValueOnce({ ...respuesta, rango: { ...respuesta.rango, desde: '2026-10-01', hasta: '2026-10-31' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Aplicar' })); });

    expect(getDashboard).toHaveBeenLastCalledWith({ desde: '2026-10-01', hasta: '2026-10-31' }, expect.anything());
    expect(screen.getByText('Del 01/10/2026 al 31/10/2026')).toBeTruthy();

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Limpiar' })); });
    expect(getDashboard).toHaveBeenLastCalledWith({ desde: '', hasta: '' }, expect.anything());
    expect(screen.getByLabelText('Desde').value).toBe('');
  });

  it('no consulta si el rango está invertido', async () => {
    getDashboard.mockResolvedValue(respuesta);
    await renderizar();
    const llamadas = getDashboard.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-10-31' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-01' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Aplicar' })); });

    expect(screen.getByRole('alert').textContent).toContain('no puede ser posterior');
    expect(getDashboard.mock.calls.length).toBe(llamadas);
  });

  it('muestra el error del servidor y permite reintentar', async () => {
    getDashboard.mockRejectedValueOnce({ response: { status: 400, data: { detail: 'Filtros admitidos: desde y hasta (AAAA-MM-DD).' } } });
    await renderizar();
    expect(screen.getByRole('alert').textContent).toContain('Filtros admitidos');

    getDashboard.mockResolvedValueOnce(respuesta);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByTestId('promedio-recuperacion')).toBeTruthy();
  });
});
