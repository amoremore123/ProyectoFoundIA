import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Login from './Login';
import { AuthProvider } from '../context/AuthContext';
import { login } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), login: vi.fn() }));

function renderizar() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider><Login /></AuthProvider>
    </MemoryRouter>,
  );
}

async function intentar(correo = 'ana@tecsup.edu.pe') {
  fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: correo } });
  fireEvent.change(screen.getByLabelText(/^Contraseña/), { target: { value: 'Clave#123' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Entrar' })); });
}

beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); localStorage.clear(); });
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('H12 - Bloqueo del inicio de sesión', () => {
  it('reactiva Entrar al vencer el bloqueo sin editar el correo ni recargar', async () => {
    login.mockRejectedValueOnce({ response: { status: 423, data: {
      mensaje: 'Tu cuenta está bloqueada por demasiados intentos fallidos. Intenta de nuevo en 2 minutos.',
    } } });
    renderizar();
    await intentar();

    const boton = screen.getByRole('button', { name: 'Entrar' });
    expect(boton.disabled).toBe(true);
    expect(screen.getByRole('alert').textContent).toContain('2 minutos');

    await act(async () => { vi.advanceTimersByTime(2 * 60 * 1000 - 1000); });
    expect(boton.disabled).toBe(true);

    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(boton.disabled).toBe(false);
    expect(screen.getByLabelText(/^Correo/).value).toBe('ana@tecsup.edu.pe');
  });

  it('usa 15 minutos si el mensaje del servidor no indica el tiempo', async () => {
    login.mockRejectedValueOnce({ response: { status: 423, data: { mensaje: 'Cuenta bloqueada.' } } });
    renderizar();
    await intentar();
    const boton = screen.getByRole('button', { name: 'Entrar' });
    await act(async () => { vi.advanceTimersByTime(14 * 60 * 1000); });
    expect(boton.disabled).toBe(true);
    await act(async () => { vi.advanceTimersByTime(60 * 1000); });
    expect(boton.disabled).toBe(false);
  });

  it('muestra en español la falta de conexión con el servidor', async () => {
    login.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();
    await intentar();
    expect(screen.getByRole('alert').textContent).toContain('No hay conexión con el servidor');
  });
});
