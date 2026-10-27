import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RestablecerPassword from './RestablecerPassword';
import Login from './Login';
import { AuthProvider } from '../context/AuthContext';
import { TemaProvider } from '../context/TemaContext';
import { restablecerPassword } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), restablecerPassword: vi.fn() }));

const TOKEN = 'Abc123_-Abc123_-Abc123_-Abc123_-Abc123_-Abc';
const NUEVA = 'Nueva456#';
const ENLACE_INVALIDO = 'El enlace no es válido o ya venció. Solicita uno nuevo.';

let rutaActual = '';
function Ubicacion() {
  const { pathname, search } = useLocation();
  rutaActual = pathname + search;
  return null;
}

function renderizar(ruta = `/restablecer-password?token=${TOKEN}`) {
  return render(
    <TemaProvider>
      <MemoryRouter initialEntries={[ruta]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <Ubicacion />
          <Routes>
            <Route path="/restablecer-password" element={<RestablecerPassword />} />
            <Route path="/login" element={<Login />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </TemaProvider>,
  );
}

function escribir(password, confirmar = password) {
  fireEvent.change(screen.getByLabelText(/^Nueva contraseña/), { target: { value: password } });
  fireEvent.change(screen.getByLabelText(/^Confirmar contraseña/), { target: { value: confirmar } });
}

async function guardar() {
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' })); });
}

beforeEach(() => { localStorage.clear(); restablecerPassword.mockReset(); rutaActual = ''; });
afterEach(() => cleanup());

describe('H35 - Restablecer la contraseña desde el enlace', () => {
  it('sin token válido muestra el aviso y ofrece pedir un enlace nuevo', () => {
    renderizar('/restablecer-password');
    expect(screen.getByRole('alert').textContent).toContain(ENLACE_INVALIDO);
    expect(screen.getByRole('link', { name: 'Solicitar un nuevo enlace' }).getAttribute('href')).toBe('/recuperar-password');
    expect(screen.getByRole('button', { name: 'Guardar contraseña' }).disabled).toBe(true);
  });

  it('quita el token de la barra de direcciones', () => {
    renderizar();
    expect(rutaActual).toBe('/restablecer-password');
  });

  it('no envía una contraseña débil ni una confirmación distinta', async () => {
    renderizar();
    escribir('123456');
    await guardar();
    expect(screen.getByText('La contraseña no cumple todos los requisitos.')).toBeTruthy();

    escribir(NUEVA, 'Otra456#');
    await guardar();
    expect(screen.getByText('Las contraseñas no coinciden.')).toBeTruthy();
    expect(restablecerPassword).not.toHaveBeenCalled();
  });

  it('guarda la nueva contraseña, cierra la sesión del navegador y vuelve al login con aviso', async () => {
    localStorage.setItem('token', 'jwt-anterior');
    localStorage.setItem('usuario', JSON.stringify({ id: 1, nombre: 'Ana' }));
    restablecerPassword.mockResolvedValueOnce({ mensaje: 'Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña.' });
    renderizar();
    escribir(NUEVA);
    await guardar();

    expect(restablecerPassword).toHaveBeenCalledWith(TOKEN, NUEVA);
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('usuario')).toBeNull();
    expect(rutaActual).toBe('/login');
    expect(screen.getByRole('status').textContent).toContain('Tu contraseña se actualizó');
  });

  it('si el enlace venció o ya se usó, lo informa y bloquea el formulario', async () => {
    restablecerPassword.mockRejectedValueOnce({ response: { status: 400, data: { mensaje: ENLACE_INVALIDO } } });
    renderizar();
    escribir(NUEVA);
    await guardar();

    expect(screen.getByRole('alert').textContent).toContain(ENLACE_INVALIDO);
    expect(screen.getByRole('link', { name: 'Solicitar un nuevo enlace' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Guardar contraseña' }).disabled).toBe(true);
    expect(screen.getByLabelText(/^Nueva contraseña/).value).toBe('');
  });
});
