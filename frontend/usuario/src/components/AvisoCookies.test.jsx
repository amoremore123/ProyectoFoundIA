import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import AvisoCookies from './AvisoCookies';
import Cookies from '../pages/Cookies';
import { TemaProvider, useTema } from '../context/TemaContext';
import { CLAVE_CONSENTIMIENTO, CLAVE_TEMA } from '../utils/almacenamiento';

const RUTER = { v7_startTransition: true, v7_relativeSplatPath: true };

function BotonTema() {
  const { tema, alternarTema } = useTema();
  return <button type="button" onClick={alternarTema}>Tema {tema}</button>;
}

function renderizar(contenido) {
  return render(
    <MemoryRouter future={RUTER}>
      <TemaProvider>{contenido}</TemaProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => localStorage.clear());
afterEach(cleanup);

describe('Aviso de cookies y almacenamiento local', () => {
  it('se muestra sin bloquear la página mientras no hay una elección', () => {
    renderizar(<><AvisoCookies /><BotonTema /></>);
    const aviso = screen.getByRole('region', { name: 'Tu privacidad en FoundIA' });
    expect(aviso.textContent).toMatch(/No usamos cookies de publicidad ni de seguimiento/);
    expect(screen.getByRole('link', { name: 'Ver detalle' }).getAttribute('href')).toBe('/cookies');
    // No es un diálogo modal: el resto de la página sigue disponible
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByRole('button', { name: /Tema/ })).toBeTruthy();
  });

  it('sin elección el tema no se guarda; «Solo lo esencial» lo borra y oculta el aviso', async () => {
    localStorage.setItem(CLAVE_TEMA, 'oscuro'); // guardado antes de existir el aviso
    const user = userEvent.setup();
    renderizar(<><AvisoCookies /><BotonTema /></>);
    await user.click(screen.getByRole('button', { name: /Tema/ }));
    expect(localStorage.getItem(CLAVE_TEMA)).toBe('oscuro'); // no se sobrescribe sin permiso

    await user.click(screen.getByRole('button', { name: 'Solo lo esencial' }));
    expect(localStorage.getItem(CLAVE_CONSENTIMIENTO)).toBe('esencial');
    expect(localStorage.getItem(CLAVE_TEMA)).toBeNull();
    expect(screen.queryByRole('region', { name: 'Tu privacidad en FoundIA' })).toBeNull();

    await user.click(screen.getByRole('button', { name: /Tema/ }));
    expect(localStorage.getItem(CLAVE_TEMA)).toBeNull();
  });

  it('«Recordar mis preferencias» guarda el tema actual y los cambios siguientes', async () => {
    const user = userEvent.setup();
    renderizar(<><AvisoCookies /><BotonTema /></>);
    const temaActual = document.documentElement.dataset.tema;
    await user.click(screen.getByRole('button', { name: 'Recordar mis preferencias' }));
    expect(localStorage.getItem(CLAVE_CONSENTIMIENTO)).toBe('preferencias');
    expect(localStorage.getItem(CLAVE_TEMA)).toBe(temaActual);

    await user.click(screen.getByRole('button', { name: /Tema/ }));
    expect(localStorage.getItem(CLAVE_TEMA)).toBe(temaActual === 'oscuro' ? 'claro' : 'oscuro');
  });

  it('no se superpone a la página /cookies, que ya ofrece la elección', () => {
    render(
      <MemoryRouter future={RUTER} initialEntries={['/cookies']}>
        <TemaProvider><AvisoCookies /></TemaProvider>
      </MemoryRouter>,
    );
    expect(screen.queryByRole('region', { name: 'Tu privacidad en FoundIA' })).toBeNull();
  });

  it('con una elección guardada el aviso no vuelve a aparecer', () => {
    localStorage.setItem(CLAVE_CONSENTIMIENTO, 'esencial');
    renderizar(<AvisoCookies />);
    expect(screen.queryByRole('region', { name: 'Tu privacidad en FoundIA' })).toBeNull();
  });

  it('la página /cookies lista lo que se guarda y permite cambiar la elección', async () => {
    const user = userEvent.setup();
    renderizar(<><Cookies /><AvisoCookies /></>);
    expect(screen.getByRole('heading', { level: 1, name: 'Cookies y almacenamiento' })).toBeTruthy();
    for (const dato of ['token', 'usuario', CLAVE_CONSENTIMIENTO, CLAVE_TEMA]) {
      expect(screen.getByRole('rowheader', { name: dato })).toBeTruthy();
    }
    expect(screen.getByRole('status').textContent).toBe('Todavía no elegiste una opción.');

    const [esencialPagina] = screen.getAllByRole('button', { name: 'Solo lo esencial' });
    await user.click(esencialPagina);
    expect(screen.getByRole('status').textContent).toBe('Solo guardamos lo esencial.');
    // La elección hecha en la página también oculta el aviso flotante
    expect(screen.queryByRole('region', { name: 'Tu privacidad en FoundIA' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Solo lo esencial' }).getAttribute('aria-pressed')).toBe('true');

    await act(async () => user.click(screen.getByRole('button', { name: 'Recordar mis preferencias' })));
    expect(screen.getByRole('status').textContent).toBe('Recordamos tus preferencias.');
  });
});
