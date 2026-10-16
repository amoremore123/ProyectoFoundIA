import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';

function Estado() {
  const { token, user } = useAuth();
  return <p>{token ? `Sesión de ${user?.nombre}` : 'Sin sesión'}</p>;
}

// Simula el evento que el navegador envía a las demás pestañas al cambiar localStorage
function otraPestana(cambio) {
  cambio();
  window.dispatchEvent(new StorageEvent('storage', { key: 'token' }));
}

beforeEach(() => localStorage.clear());
afterEach(cleanup);

describe('H12 - Sesión sincronizada entre pestañas', () => {
  it('cierra la sesión cuando otra pestaña hace logout', () => {
    localStorage.setItem('token', 'jwt-de-prueba');
    localStorage.setItem('usuario', JSON.stringify({ nombre: 'Ana' }));
    render(<AuthProvider><Estado /></AuthProvider>);
    expect(screen.getByText('Sesión de Ana')).toBeTruthy();

    act(() => otraPestana(() => { localStorage.removeItem('token'); localStorage.removeItem('usuario'); }));
    expect(screen.getByText('Sin sesión')).toBeTruthy();
  });

  it('abre la sesión cuando otra pestaña inicia sesión', () => {
    render(<AuthProvider><Estado /></AuthProvider>);
    act(() => otraPestana(() => {
      localStorage.setItem('usuario', JSON.stringify({ nombre: 'Luis' }));
      localStorage.setItem('token', 'jwt-nuevo');
    }));
    expect(screen.getByText('Sesión de Luis')).toBeTruthy();
  });
});
