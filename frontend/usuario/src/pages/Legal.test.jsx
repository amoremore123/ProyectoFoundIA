import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Legal from './Legal';
import { getDocumentosLegales } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getDocumentosLegales: vi.fn(),
}));

const DOCUMENTOS = {
  version: 'proyecto-2026-10-26-v1',
  estado: 'TEXTO_DEL_PROYECTO_PENDIENTE_REVISION_INSTITUCIONAL',
  terminos: '# Términos de uso de FoundIA\n## Alcance\nTexto de términos.',
  privacidad: '# Política de privacidad de FoundIA\n## Datos que tratamos\nTexto de privacidad.',
  sha256: 'abc',
};

function renderizar(tipo) {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Legal tipo={tipo} />
    </MemoryRouter>,
  );
}

beforeEach(() => getDocumentosLegales.mockReset());
afterEach(cleanup);

describe('H48 - Páginas públicas de privacidad y términos', () => {
  it('muestra la política de privacidad vigente sin iniciar sesión', async () => {
    getDocumentosLegales.mockResolvedValue(DOCUMENTOS);
    renderizar('privacidad');
    expect(screen.getByRole('heading', { level: 1, name: 'Política de privacidad' })).toBeTruthy();
    expect(await screen.findByText('Texto de privacidad.')).toBeTruthy();
    expect(screen.getByText('Datos que tratamos')).toBeTruthy();
    expect(screen.queryByText('Texto de términos.')).toBeNull();
    // H51: los títulos siguen al h1 sin saltar niveles (h1 → h2 → h3)
    expect(screen.getByRole('heading', { level: 2, name: 'Política de privacidad de FoundIA' })).toBeTruthy();
    expect(screen.getByRole('heading', { level: 3, name: 'Datos que tratamos' })).toBeTruthy();
  });

  it('muestra los términos y enlaza a la política de privacidad', async () => {
    getDocumentosLegales.mockResolvedValue(DOCUMENTOS);
    renderizar('terminos');
    expect(await screen.findByText('Texto de términos.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver Política de privacidad' }).getAttribute('href')).toBe('/privacidad');
  });

  it('si falla la carga no muestra un texto inventado y permite reintentar', async () => {
    getDocumentosLegales.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(DOCUMENTOS);
    renderizar('privacidad');
    expect((await screen.findByRole('alert')).textContent).toContain('No se pudo cargar el documento');
    expect(screen.queryByText('Texto de privacidad.')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Texto de privacidad.')).toBeTruthy();
    expect(getDocumentosLegales).toHaveBeenCalledTimes(2);
  });

  it('un documento incompleto se trata como error', async () => {
    getDocumentosLegales.mockResolvedValue({ ...DOCUMENTOS, privacidad: '' });
    renderizar('privacidad');
    expect(await screen.findByRole('alert')).toBeTruthy();
  });
});
