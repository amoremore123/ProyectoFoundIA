import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RestaurarVersion from './RestaurarVersion';
import { getRestauracion, solicitarRestauracion } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), solicitarRestauracion: vi.fn(), getRestauracion: vi.fn(),
}));
const V = '20261120T020849Z';
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const escribir = (version, texto) => {
  fireEvent.change(screen.getByLabelText(/Escribe la versión/), { target: { value: version } });
  fireEvent.change(screen.getByLabelText(/Escribe RESTAURAR/), { target: { value: texto } });
};

describe('H70 - restaurar desde el panel', () => {
  it('no deja pedir la restauración sin la doble confirmación', () => {
    render(<RestaurarVersion version={V} onCerrar={() => {}} />);
    const boton = screen.getByRole('button', { name: 'Restaurar' });
    expect(boton.disabled).toBe(true);
    escribir(V, 'restaurar');
    expect(boton.disabled).toBe(true);
    escribir('otra', 'RESTAURAR');
    expect(boton.disabled).toBe(true);
    escribir(V, 'RESTAURAR');
    expect(boton.disabled).toBe(false);
  });

  it('avisa lo que se pierde y envía las dos confirmaciones', async () => {
    solicitarRestauracion.mockResolvedValue({ id: 7, estado: 'SOLICITADA', respaldoSeguridad: null });
    render(<RestaurarVersion version={V} onCerrar={() => {}} intervaloMs={50} />);
    expect(screen.getByText(/Se cerrarán todas las sesiones/)).toBeTruthy();
    escribir(V, 'RESTAURAR');
    fireEvent.click(screen.getByRole('button', { name: 'Restaurar' }));
    await waitFor(() => expect(solicitarRestauracion).toHaveBeenCalledWith(V, { confirmacionVersion: V, confirmacionTexto: 'RESTAURAR' }));
    expect((await screen.findByRole('status')).textContent).toMatch(/Solicitada/);
  });

  it('sigue el avance hasta terminar y avisa que hay que iniciar sesión otra vez', async () => {
    solicitarRestauracion.mockResolvedValue({ id: 7, estado: 'SOLICITADA' });
    getRestauracion.mockResolvedValueOnce({ id: 7, estado: 'EN_PROCESO' })
      .mockResolvedValue({ id: 7, estado: 'COMPLETADA', respaldoSeguridad: '20261127T000000Z', mensaje: 'objetos 21 -> 15' });
    const fin = vi.fn();
    render(<RestaurarVersion version={V} onCerrar={() => {}} onTerminada={fin} intervaloMs={20} />);
    escribir(V, 'RESTAURAR');
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Restaurar' })); });
    await waitFor(() => expect(screen.getByRole('status').textContent).toMatch(/Completada/));
    expect(screen.getByText(/vuelve a iniciar sesión/)).toBeTruthy();
    expect(screen.getByText('20261127T000000Z')).toBeTruthy();
    expect(fin).toHaveBeenCalled();
  });

  it('muestra el motivo si el servidor la rechaza', async () => {
    solicitarRestauracion.mockRejectedValue({ response: { status: 409, data: { detail: 'Ya hay una restauración en curso.' } } });
    render(<RestaurarVersion version={V} onCerrar={() => {}} />);
    escribir(V, 'RESTAURAR');
    fireEvent.click(screen.getByRole('button', { name: 'Restaurar' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/Ya hay una restauración/);
  });
});
