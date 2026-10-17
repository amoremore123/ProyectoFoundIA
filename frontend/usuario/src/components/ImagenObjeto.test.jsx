import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import ImagenObjeto from './ImagenObjeto';

afterEach(cleanup);

const objeto = { nombre: 'Mochila de cálculo', categoria: { nombre: 'Mochila' }, fotos: [] };

describe('Integración Buscar/Detalle - Imágenes de publicaciones', () => {
  it('una imagen válida conserva su URL y texto alternativo', () => {
    render(<ImagenObjeto objeto={{ ...objeto, fotos: [{ url: 'https://ejemplo.invalid/foto.jpg' }] }} />);
    expect(screen.getByRole('img', { name: objeto.nombre }).getAttribute('src')).toBe('https://ejemplo.invalid/foto.jpg');
    expect(screen.queryByText('Sin foto', { exact: true })).toBeNull();
  });

  it('sin imagen distingue la referencia de una fotografía del objeto', () => {
    render(<ImagenObjeto objeto={objeto} />);
    expect(screen.getByText('Sin foto', { exact: true })).toBeTruthy();
    expect(screen.getByText(/imagen de referencia/)).toBeTruthy();
    expect(screen.getByRole('img').getAttribute('alt')).toMatch(/^Foto referencial:/);
  });

  it('una imagen fallida muestra Sin foto y permite una URL válida posterior', () => {
    const { rerender } = render(<ImagenObjeto objeto={{ ...objeto, fotos: [{ url: 'https://ejemplo.invalid/fallida.jpg' }] }} />);
    fireEvent.error(screen.getByRole('img', { name: objeto.nombre }));
    expect(screen.getByText('Sin foto', { exact: true })).toBeTruthy();
    rerender(<ImagenObjeto objeto={{ ...objeto, fotos: [{ url: 'https://ejemplo.invalid/nueva.jpg' }] }} />);
    expect(screen.getByRole('img', { name: objeto.nombre }).getAttribute('src')).toBe('https://ejemplo.invalid/nueva.jpg');
    expect(screen.queryByText('Sin foto', { exact: true })).toBeNull();
  });
});
