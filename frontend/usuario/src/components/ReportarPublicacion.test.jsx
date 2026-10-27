import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ReportarPublicacion from './ReportarPublicacion';
import { reportarPublicacion } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), reportarPublicacion: vi.fn() }));

function renderizar(props = {}) {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ReportarPublicacion objetoId={15} conSesion esPropia={false} {...props} />
    </MemoryRouter>,
  );
}
const abrir = () => fireEvent.click(screen.getByRole('button', { name: 'Reportar esta publicación' }));
const enviar = async () => { await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' })); }); };

beforeEach(() => reportarPublicacion.mockReset());
afterEach(() => cleanup());

describe('H27 - Reportar publicación', () => {
  it('sin sesión invita a iniciar sesión y en una publicación propia no aparece', () => {
    const { unmount } = renderizar({ conSesion: false });
    expect(screen.getByRole('link', { name: 'Inicia sesión para reportarla' })).toBeTruthy();
    unmount();
    renderizar({ esPropia: true });
    expect(screen.queryByRole('button', { name: 'Reportar esta publicación' })).toBeNull();
  });

  it('exige motivo, y explicación cuando el motivo es "Otro"', async () => {
    renderizar();
    abrir();
    await enviar();
    expect(screen.getByRole('alert').textContent).toBe('Elige un motivo.');
    fireEvent.click(screen.getByLabelText('Otro motivo'));
    await enviar();
    expect(screen.getByRole('alert').textContent).toContain('cuando eliges "Otro"');
    expect(reportarPublicacion).not.toHaveBeenCalled();
  });

  it('cancelar no envía nada', () => {
    renderizar();
    abrir();
    fireEvent.click(screen.getByLabelText('Spam o publicidad'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(reportarPublicacion).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Reportar esta publicación' })).toBeTruthy();
  });

  it('envía motivo y explicación y confirma solo con la respuesta', async () => {
    reportarPublicacion.mockResolvedValueOnce({ id: 3, objetoId: 15, motivo: 'SPAM', estado: 'PENDIENTE', repetido: false });
    renderizar();
    abrir();
    fireEvent.click(screen.getByLabelText('Spam o publicidad'));
    fireEvent.change(screen.getByLabelText(/Explicación/), { target: { value: '  Vende productos ' } });
    await enviar();

    expect(reportarPublicacion).toHaveBeenCalledWith(15, { motivo: 'SPAM', descripcion: 'Vende productos' });
    expect(screen.getByRole('status').textContent).toContain('Tu reporte quedó registrado');
  });

  it('una denuncia repetida no se presenta como nueva', async () => {
    reportarPublicacion.mockResolvedValueOnce({ id: 3, repetido: true });
    renderizar();
    abrir();
    fireEvent.click(screen.getByLabelText('Información falsa'));
    await enviar();
    expect(reportarPublicacion).toHaveBeenCalledWith(15, { motivo: 'INFORMACION_FALSA' });
    expect(screen.getByRole('status').textContent).toContain('Ya habías reportado');
  });

  it('si la publicación fue retirada lo informa sin mostrar éxito', async () => {
    reportarPublicacion.mockRejectedValueOnce({ response: { status: 404, data: {} } });
    renderizar();
    abrir();
    fireEvent.click(screen.getByLabelText('Contenido inapropiado'));
    await enviar();
    expect(screen.getByRole('alert').textContent).toBe('Esta publicación ya no está disponible.');
    expect(screen.queryByRole('status')).toBeNull();
  });
});
