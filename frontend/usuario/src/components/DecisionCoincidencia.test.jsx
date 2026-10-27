import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DecisionCoincidencia from './DecisionCoincidencia';
import { confirmarRecuperacion, descartarCoincidencia } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), confirmarRecuperacion: vi.fn(), descartarCoincidencia: vi.fn(),
}));

let resuelta;
function renderizar() {
  resuelta = vi.fn();
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <DecisionCoincidencia coincidenciaId="15-27" perdidoId={15} onResuelta={resuelta} />
    </MemoryRouter>,
  );
}

async function confirmarDialogo(nombre) {
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: nombre })); });
}

beforeEach(() => { confirmarRecuperacion.mockReset(); descartarCoincidencia.mockReset(); });
afterEach(() => cleanup());

describe('H22 - Confirmar recuperación', () => {
  it('pide confirmación y cancelar no envía nada', () => {
    renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, lo recuperé' }));
    expect(screen.getByRole('alertdialog').textContent).toContain('no cambia la publicación de quien lo encontró');
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(confirmarRecuperacion).not.toHaveBeenCalled();
  });

  it('muestra el éxito solo tras la respuesta, con la fecha registrada', async () => {
    confirmarRecuperacion.mockResolvedValueOnce({ id: '15-27', estado: 'ACEPTADA', estadoObjetoPerdido: 'RECUPERADO', fechaRecuperacion: '2026-10-25T22:00:00Z', repetido: false });
    renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, lo recuperé' }));
    await confirmarDialogo('Confirmar recuperación');

    expect(confirmarRecuperacion).toHaveBeenCalledWith('15-27');
    const esperado = new Date('2026-10-25T22:00:00Z').toLocaleString('es-PE', { dateStyle: 'long', timeStyle: 'short' });
    expect(screen.getByRole('status').textContent).toContain(`Registramos que recuperaste tu objeto el ${esperado}`);
    expect(resuelta).toHaveBeenCalledWith('confirmar');
    expect(screen.getByRole('link', { name: 'Volver a mi publicación' }).getAttribute('href')).toBe('/objeto/15');
  });

  it('un 409 se muestra como error y no como éxito', async () => {
    confirmarRecuperacion.mockRejectedValueOnce({ response: { status: 409, data: { mensaje: 'Ya confirmaste otra coincidencia para este objeto.' } } });
    renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, lo recuperé' }));
    await confirmarDialogo('Confirmar recuperación');

    expect(screen.getByText('Ya confirmaste otra coincidencia para este objeto.')).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
    expect(resuelta).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Sí, lo recuperé' })).toBeTruthy();
  });
});

describe('H21 - Descartar coincidencia', () => {
  it('descarta tras confirmar e informa que ya no aparecerá', async () => {
    descartarCoincidencia.mockResolvedValueOnce({ id: '15-27', estado: 'DESCARTADA', repetido: false });
    renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'No es mi objeto' }));
    await confirmarDialogo('Sí, descartar');

    expect(descartarCoincidencia).toHaveBeenCalledWith('15-27');
    expect(screen.getByRole('status').textContent).toContain('Ya no aparecerá en tus sugerencias');
    expect(resuelta).toHaveBeenCalledWith('descartar');
  });

  it('una repetición no se presenta como un descarte nuevo', async () => {
    descartarCoincidencia.mockResolvedValueOnce({ id: '15-27', estado: 'DESCARTADA', repetido: true });
    renderizar();
    fireEvent.click(screen.getByRole('button', { name: 'No es mi objeto' }));
    await confirmarDialogo('Sí, descartar');
    expect(screen.getByRole('status').textContent).toBe('Ya habías descartado esta coincidencia.');
  });
});
