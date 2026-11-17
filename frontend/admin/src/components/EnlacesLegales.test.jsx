import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import EnlacesLegales, { WEB_URL } from './EnlacesLegales';

afterEach(cleanup);

describe('H48 - Enlaces legales en el panel', () => {
  it('enlaza a las páginas públicas de privacidad y términos de la web', () => {
    render(<EnlacesLegales />);
    expect(screen.getByRole('navigation', { name: 'Documentos legales' })).toBeTruthy();
    expect(screen.getByRole('link', { name: /privacidad/i }).getAttribute('href')).toBe(`${WEB_URL}/privacidad`);
    expect(screen.getByRole('link', { name: /términos/i }).getAttribute('href')).toBe(`${WEB_URL}/terminos`);
  });

  it('avisa que se abren en otra pestaña y no comparte la ventana del panel', () => {
    render(<EnlacesLegales />);
    const enlaces = screen.getAllByRole('link', { name: /se abre en otra pestaña/i });
    expect(enlaces).toHaveLength(2);
    for (const enlace of enlaces) {
      expect(enlace.getAttribute('target')).toBe('_blank');
      expect(enlace.getAttribute('rel')).toBe('noopener noreferrer');
    }
  });
});
