import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Reportes from './Reportes';
import { ToastProvider } from '../context/ToastContext';
import { getReportes, resolverReporte } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getReportes: vi.fn(), resolverReporte: vi.fn(),
}));

const LISTA = [
  { id: 4, objeto: 15, motivo: 'SPAM', descripcion: 'Publica anuncios', estado: 'PENDIENTE', fecha_reporte: '2026-10-26T10:00:00Z', resolucion: null, fecha_resolucion: null },
  { id: 5, objeto: 16, motivo: 'OTRO', descripcion: 'Foto ajena', estado: 'RESUELTO', fecha_reporte: '2026-10-20T10:00:00Z', resolucion: 'Foto retirada', fecha_resolucion: '2026-10-21T10:00:00Z' },
];

async function renderizar() {
  await act(async () => { render(<ToastProvider><Reportes /></ToastProvider>); });
}

beforeEach(() => {
  getReportes.mockReset(); resolverReporte.mockReset();
  getReportes.mockResolvedValue(LISTA.map((r) => ({ ...r })));
});
afterEach(() => cleanup());

describe('H28 - Resolución de reportes', () => {
  it('muestra por defecto los reportes por atender y permite ver todos', async () => {
    await renderizar();
    expect(screen.getByText('1 por atender · 2 en total')).toBeTruthy();
    expect(screen.getByText('Spam')).toBeTruthy();
    expect(screen.queryByText('Foto retirada')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Todos' }));
    expect(screen.getByText('Foto retirada')).toBeTruthy();
    expect(screen.getByText('Cerrado')).toBeTruthy();
  });

  it('exige una explicación antes de enviar', async () => {
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar decisión' })); });
    expect(screen.getByRole('alert').textContent).toContain('Explica la decisión');
    expect(resolverReporte).not.toHaveBeenCalled();
  });

  it('cancelar cierra el panel sin enviar', async () => {
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('form', { name: /Resolver reporte/ })).toBeNull();
    expect(resolverReporte).not.toHaveBeenCalled();
  });

  it('rechaza con explicación y actualiza la fila con la respuesta del servidor', async () => {
    resolverReporte.mockResolvedValueOnce({ id: 4, estado: 'RECHAZADO', resolucion: 'No incumple las normas', resueltoPorId: 1, fechaResolucion: '2026-10-27T12:00:00Z', repetido: false });
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    fireEvent.click(screen.getByLabelText(/Rechazado/));
    fireEvent.change(screen.getByLabelText(/Explicación/), { target: { value: '  No incumple las normas ' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar decisión' })); });

    expect(resolverReporte).toHaveBeenCalledWith(4, { estado: 'RECHAZADO', resolucion: 'No incumple las normas' });
    fireEvent.click(screen.getByRole('button', { name: 'Todos' }));
    const fila = screen.getByText('#4 · Publicación #15').closest('tr');
    expect(within(fila).getByText('RECHAZADO')).toBeTruthy();
    expect(within(fila).getByText('No incumple las normas')).toBeTruthy();
    expect(screen.getByText('0 por atender · 2 en total')).toBeTruthy();
  });

  it('un 409 se muestra como error y el reporte sigue abierto', async () => {
    resolverReporte.mockRejectedValueOnce({ response: { status: 409, data: { detail: 'El reporte ya tiene una resolución diferente.' } } });
    await renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    fireEvent.change(screen.getByLabelText(/Explicación/), { target: { value: 'Revisado' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar decisión' })); });

    expect(screen.getByRole('alert').textContent).toContain('ya tiene una resolución diferente');
    expect(screen.getByText('1 por atender · 2 en total')).toBeTruthy();
  });
});
