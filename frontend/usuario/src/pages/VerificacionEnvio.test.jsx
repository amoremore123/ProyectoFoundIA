import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import Login from './Login';
import Verificar from './Verificar';
import { login, reenviarCodigo } from '../services/api';

vi.mock('../services/api', async (original) => ({ ...await original(), login: vi.fn(), reenviarCodigo: vi.fn() }));
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); });
afterEach(cleanup);
function abrir(ruta) {
  return render(<MemoryRouter initialEntries={[ruta]}><AuthProvider><Routes>
    <Route path="/login" element={<Login />} /><Route path="/verificar" element={<Verificar />} />
  </Routes></AuthProvider></MemoryRouter>);
}

it('un enlace con correo no afirma que se acaba de enviar un código', () => {
  abrir('/verificar?correo=amore@prueba.invalid');
  expect(screen.queryByText(/Te enviamos un código/)).toBeNull();
  expect(screen.getByRole('button', { name: 'Reenviar código' }).disabled).toBe(false);
});

it('el login conserva el error de reenvío sin navegar a una confirmación falsa', async () => {
  login.mockRejectedValue({ response: { status: 403, data: { mensaje: 'Debes verificar tu correo' } } });
  reenviarCodigo.mockRejectedValue({ response: { status: 503, data: { mensaje: 'No se pudo enviar el correo' } } });
  abrir('/login');
  fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: 'amore@prueba.invalid' } });
  fireEvent.change(screen.getByLabelText(/^Contraseña/), { target: { value: 'Segura123!' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Enviar código y verificar' }));
  await screen.findByText(/No se pudo enviar el correo/);
  expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
  expect(screen.queryByText(/Te enviamos un código/)).toBeNull();
  reenviarCodigo.mockResolvedValue({ mensaje: 'Código enviado' });
  fireEvent.click(screen.getByRole('button', { name: 'Enviar código y verificar' }));
  await screen.findByRole('heading', { name: 'Verifica tu correo' });
  expect(screen.getByText(/Te enviamos un código/)).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Reenviar en 60s' }).disabled).toBe(true);
});

it('el reenvío directo muestra el fallo y permite recuperarlo sin esperar un envío inexistente', async () => {
  reenviarCodigo.mockRejectedValueOnce(new Error('Correo no disponible'));
  reenviarCodigo.mockResolvedValue({ mensaje: 'Código enviado correctamente' });
  abrir('/verificar?correo=amore@prueba.invalid');
  fireEvent.click(screen.getByRole('button', { name: 'Reenviar código' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('Código enviado correctamente')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Reenviar código' }));
  await screen.findByText('Código enviado correctamente');
});
