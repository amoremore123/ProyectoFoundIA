import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RecuperarPassword from './RecuperarPassword';
import { TemaProvider } from '../context/TemaContext';
import { solicitarRecuperacion } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), solicitarRecuperacion: vi.fn() }));

const MENSAJE = 'Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.';

function renderizar(ruta = '/recuperar-password') {
  return render(
    <TemaProvider>
      <MemoryRouter initialEntries={[ruta]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <RecuperarPassword />
      </MemoryRouter>
    </TemaProvider>,
  );
}

async function enviar() {
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: /enlace/i })); });
}

beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); solicitarRecuperacion.mockReset(); });
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('H35 - Solicitar enlace de recuperación', () => {
  it('envía el correo normalizado y muestra el mensaje neutro con espera de 60 s', async () => {
    solicitarRecuperacion.mockResolvedValueOnce({ mensaje: MENSAJE });
    renderizar();
    fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: '  Ana@Tecsup.edu.pe ' } });
    await enviar();

    expect(solicitarRecuperacion).toHaveBeenCalledWith('ana@tecsup.edu.pe');
    expect(screen.getByRole('status').textContent).toContain(MENSAJE);
    const boton = screen.getByRole('button', { name: /Reenviar en 60 s/ });
    expect(boton.disabled).toBe(true);

    for (let i = 0; i < 60; i += 1) {
      await act(async () => { vi.advanceTimersByTime(1000); });
    }
    expect(screen.getByRole('button', { name: 'Reenviar enlace' }).disabled).toBe(false);
  });

  it('no llama a la API si el correo está vacío o no es válido', async () => {
    renderizar();
    await enviar();
    expect(screen.getByRole('alert').textContent).toContain('Ingresa el correo de tu cuenta.');

    fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: 'ana@' } });
    await enviar();
    expect(screen.getByRole('alert').textContent).toContain('Ingresa un correo válido.');
    expect(solicitarRecuperacion).not.toHaveBeenCalled();
  });

  it('completa el correo que venía escrito en el login', () => {
    renderizar('/recuperar-password?correo=luis%40foundia.dev');
    expect(screen.getByLabelText(/^Correo/).value).toBe('luis@foundia.dev');
  });

  it('avisa si no hay conexión sin mostrar el mensaje de éxito', async () => {
    solicitarRecuperacion.mockRejectedValueOnce(new Error('Network Error'));
    renderizar();
    fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: 'ana@tecsup.edu.pe' } });
    await enviar();

    expect(screen.getByRole('alert').textContent).toContain('No hay conexión');
    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByRole('button', { name: 'Enviar enlace' }).disabled).toBe(false);
  });
});
