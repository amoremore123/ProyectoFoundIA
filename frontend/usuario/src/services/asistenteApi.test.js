import axios from 'axios';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { interpretarConIA } from './api';

beforeEach(() => {
  localStorage.clear();
  vi.spyOn(axios, 'post').mockResolvedValue({ data: { intencion: 'ayuda', fuente: 'local' } });
});
afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

it('permite consultas públicas sin cabecera de sesión', async () => {
  expect((await interpretarConIA('ayuda')).fuente).toBe('local');
  expect(axios.post.mock.calls[0][2].headers).toBeUndefined();
});

it('envía el token al contrato H45 para habilitar el resumen propio', async () => {
  localStorage.setItem('token', 'sesion-de-prueba');
  await interpretarConIA('ayuda');
  expect(axios.post.mock.calls[0][2].headers).toEqual({ Authorization: 'Bearer sesion-de-prueba' });
});

it('un error del asistente conserva la sesión para el respaldo local', async () => {
  localStorage.setItem('token', 'sesion-de-prueba');
  axios.post.mockRejectedValueOnce(new Error('No disponible'));
  await expect(interpretarConIA('ayuda')).rejects.toThrow('No disponible');
  expect(localStorage.getItem('token')).toBe('sesion-de-prueba');
});
