import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Respaldos from './Respaldos';
import { huellaCorta } from '../components/UltimoRespaldo';
import { getRespaldos, getRestauraciones, getUltimoRespaldo, getVersionesRespaldo } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getRespaldos: vi.fn(), getUltimoRespaldo: vi.fn(), getVersionesRespaldo: vi.fn(), getRestauraciones: vi.fn(),
}));

const HUELLA = 'a'.repeat(58) + 'b12345';
const BASE = { id: 9, tipo: 'BASE', nombreArchivo: 'foundia_objetos_perdidos_db_20261120.sql.gz', huellaSha256: HUELLA, tamanoBytes: 6981, estado: 'COMPLETADO', iniciadoEn: '2026-11-20T03:00:00Z', finalizadoEn: '2026-11-20T03:00:02Z', mensaje: null };
const FOTOS = { ...BASE, id: 10, tipo: 'FOTOS', nombreArchivo: 'foundia_fotos_20261120.tar.gz', tamanoBytes: 1721572 };
const FALLIDO = { ...FOTOS, id: 11, estado: 'FALLIDO', huellaSha256: null, tamanoBytes: null, mensaje: 'falta la columna respaldos.tipo' };
const pagina = (results) => ({ count: results.length, results, next: null, previous: null });

beforeEach(() => {
  vi.resetAllMocks();
  getUltimoRespaldo.mockImplementation(async (tipo) => (tipo === 'FOTOS' ? FOTOS : BASE));
  getRespaldos.mockResolvedValue(pagina([FALLIDO, FOTOS, BASE]));
  getVersionesRespaldo.mockResolvedValue(pagina([]));
  getRestauraciones.mockResolvedValue(pagina([]));
});
afterEach(cleanup);

const montar = async () => { await act(async () => { render(<Respaldos />); }); };

describe('H58/H59 - respaldos de la base y de las fotos en el panel', () => {
  it('muestra el último respaldo de cada tipo con su huella abreviada', async () => {
    await montar();
    expect(getUltimoRespaldo).toHaveBeenCalledWith('BASE');
    expect(getUltimoRespaldo).toHaveBeenCalledWith('FOTOS');
    const fotos = screen.getByRole('region', { name: /Respaldo de las fotos/ });
    expect(within(fotos).getByText('foundia_fotos_20261120.tar.gz')).toBeTruthy();
    expect(within(fotos).getByText('1.6 MB')).toBeTruthy();
    expect(within(fotos).getByText(huellaCorta(HUELLA)).getAttribute('title')).toBe(HUELLA);
    expect(screen.getByRole('region', { name: /Respaldo de la base/ })).toBeTruthy();
  });

  it('un respaldo fallido se ve como fallido con su motivo, no como vacío', async () => {
    await montar();
    const tabla = screen.getByRole('table');
    expect(within(tabla).getByText('Fallido')).toBeTruthy();
    expect(within(tabla).getByText('falta la columna respaldos.tipo')).toBeTruthy();
    expect(within(tabla).getAllByText('Completado')).toHaveLength(2);
  });

  it('filtra el historial por tipo y avisa cuando no hay respaldos de ese tipo', async () => {
    await montar();
    expect(getRespaldos).toHaveBeenLastCalledWith({ tipo: '' }, 1);
    getRespaldos.mockResolvedValueOnce(pagina([]));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Fotos' })); });
    expect(getRespaldos).toHaveBeenLastCalledWith({ tipo: 'FOTOS' }, 1);
    expect(screen.getByRole('button', { name: 'Fotos' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByText('Todavía no hay respaldos de fotos.')).toBeTruthy();
  });

  it('si falla el historial lo dice y permite reintentar', async () => {
    getRespaldos.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    await montar();
    expect(screen.getAllByRole('alert').some((a) => /historial de respaldos/.test(a.textContent))).toBe(true);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(screen.getByRole('table')).toBeTruthy();
  });

  it('muestra el procedimiento de recuperación y no ofrece restaurar desde el panel', async () => {
    await montar();
    const pasos = screen.getByRole('region', { name: /cómo recuperar/ });
    expect(within(pasos).getByText(/recuperar_todo\.sh/)).toBeTruthy();
    expect(within(pasos).getByText(/docker compose down -v/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Restaurar/ })).toBeNull();
  });
});
