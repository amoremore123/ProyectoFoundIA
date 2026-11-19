import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { TemaProvider } from '../context/TemaContext';
import { ToastProvider } from '../context/ToastContext';
import MainLayout from '../layouts/MainLayout';
import AvisoCookies from '../components/AvisoCookies';
import { CLAVE_EMBEBIDO, leerEmbebido } from './embebido';

afterEach(() => { cleanup(); localStorage.clear(); });

const abrir = (url) => render(
  <TemaProvider><ToastProvider><AuthProvider>
    <MemoryRouter initialEntries={[url]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/terminos" element={<h1>Términos de uso</h1>} />
          <Route path="/privacidad" element={<h1>Política de privacidad</h1>} />
        </Route>
      </Routes>
      <AvisoCookies />
    </MemoryRouter>
  </AuthProvider></ToastProvider></TemaProvider>,
);

describe('H73 - páginas de la web dentro del WebView de la app', () => {
  it('con ?embebido=1 solo se ve la página: sin menú, barra inferior, FINDI ni aviso de cookies', () => {
    abrir('/terminos?embebido=1');
    expect(screen.getByRole('heading', { name: 'Términos de uso' })).toBeTruthy();
    expect(screen.getByRole('main')).toBeTruthy();
    expect(screen.queryByRole('navigation')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Saltar al contenido' })).toBeNull();
    expect(screen.queryByRole('button', { name: /FINDI/ })).toBeNull();
    expect(screen.queryByRole('region', { name: /cookies/i })).toBeNull();
    expect(screen.queryByText(/cookies/i)).toBeNull();
    expect(sessionStorage.getItem(CLAVE_EMBEBIDO)).toBe('1');
  });

  it('la elección sobrevive a la navegación interna (otra página sin el parámetro)', () => {
    sessionStorage.setItem(CLAVE_EMBEBIDO, '1');
    abrir('/privacidad');
    expect(screen.getByRole('heading', { name: 'Política de privacidad' })).toBeTruthy();
    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('sin el parámetro la página se ve igual que siempre', () => {
    abrir('/terminos');
    expect(screen.getAllByRole('navigation').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toBeTruthy();
    expect(sessionStorage.getItem(CLAVE_EMBEBIDO)).toBeNull();
  });

  it('?embebido=0 vuelve a la web normal y cualquier otro valor no activa el modo', () => {
    sessionStorage.setItem(CLAVE_EMBEBIDO, '1');
    expect(leerEmbebido('?embebido=0')).toBe(false);
    expect(sessionStorage.getItem(CLAVE_EMBEBIDO)).toBeNull();
    expect(leerEmbebido('?embebido=si')).toBe(false);
    expect(leerEmbebido('?embebido=1')).toBe(true);
  });
});
