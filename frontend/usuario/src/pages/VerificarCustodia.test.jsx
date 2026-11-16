import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import VerificarCustodia from './VerificarCustodia';
import { verificarCustodia } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), verificarCustodia: vi.fn() }));

beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const abrir = (url) => render(
  <MemoryRouter initialEntries={[url]}>
    <Routes><Route path="/custodia/verificar" element={<VerificarCustodia />} /></Routes>
  </MemoryRouter>,
);

describe('H41 - al escanear la etiqueta se ve el objeto en custodia', () => {
  it('muestra el objeto, el código y el enlace para reclamarlo', async () => {
    verificarCustodia.mockResolvedValue({
      codigo: 'FND-2026-0003', estado: 'EN_CUSTODIA', fechaRecepcion: '2026-11-10T15:00:00',
      objeto: { id: 15, nombre: 'Mochila azul', categoria: 'Mochilas', fotoUrl: '/uploads/m.jpg' },
    });
    abrir('/custodia/verificar?t=tok.firmado.qr');
    expect(await screen.findByRole('heading', { name: 'Objeto en custodia en la biblioteca' })).toBeTruthy();
    expect(verificarCustodia).toHaveBeenCalledWith('tok.firmado.qr');
    expect(screen.getByText('Etiqueta FND-2026-0003')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Mochila azul' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver el objeto y reclamarlo' }).getAttribute('href')).toBe('/objeto/15');
  });

  it('si ya fue retirado lo dice y no ofrece reclamarlo', async () => {
    verificarCustodia.mockResolvedValue({
      codigo: 'FND-2026-0003', estado: 'RETIRADO', fechaRecepcion: null,
      objeto: { id: 15, nombre: 'Mochila azul', categoria: null, fotoUrl: null },
    });
    abrir('/custodia/verificar?t=tok');
    expect(await screen.findByRole('heading', { name: 'Objeto ya devuelto' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: /reclamarlo/ })).toBeNull();
  });

  it('una etiqueta falsa, manipulada o sin token se rechaza', async () => {
    verificarCustodia.mockRejectedValue({ response: { status: 404 } });
    abrir('/custodia/verificar?t=falso');
    expect((await screen.findByRole('alert')).textContent).toMatch(/Etiqueta no válida/);
    cleanup();
    abrir('/custodia/verificar');
    expect(screen.getByRole('alert').textContent).toMatch(/Etiqueta no válida/);
  });
});
