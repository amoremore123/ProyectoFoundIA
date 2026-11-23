import { act, cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CLAVE_ORIGEN, ContadorCampanas, anotarVisita, origenGuardado, origenValido } from './campanas';
import { registrarVisitaCampana } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), registrarVisitaCampana: vi.fn() }));

beforeEach(() => { vi.resetAllMocks(); registrarVisitaCampana.mockResolvedValue(); });
afterEach(cleanup);

const abrir = async (url) => {
  await act(async () => { render(<MemoryRouter initialEntries={[url]}><ContadorCampanas /></MemoryRouter>); });
};

describe('H67 - contar las visitas que llegan por una campaña', () => {
  it('cuenta una sola visita por origen en la pestaña y guarda el origen para el registro', async () => {
    await abrir('/explorar?origen=semana-perdidos-2026');
    expect(registrarVisitaCampana).toHaveBeenCalledTimes(1);
    const { origen, visitaId } = registrarVisitaCampana.mock.calls[0][0];
    expect(origen).toBe('semana-perdidos-2026');
    expect(visitaId).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(origenGuardado()).toBe('semana-perdidos-2026');
    cleanup();
    await abrir('/?origen=semana-perdidos-2026');
    expect(registrarVisitaCampana).toHaveBeenCalledTimes(1);
  });

  it('sin origen o con un origen inválido no cuenta nada', async () => {
    await abrir('/explorar');
    await abrir('/?origen=Con%20Espacios');
    expect(registrarVisitaCampana).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(CLAVE_ORIGEN)).toBeNull();
  });

  it('un error al contar no rompe la página', async () => {
    registrarVisitaCampana.mockRejectedValue(new Error('Network Error'));
    await abrir('/?origen=feria-2027');
    expect(registrarVisitaCampana).toHaveBeenCalledTimes(1);
  });

  it('valida el formato del origen como H60', () => {
    expect(origenValido('ab')).toBe(false);
    expect(origenValido('semana-2026')).toBe(true);
    expect(origenValido('semana--2026')).toBe(false);
    expect(anotarVisita('?origen=MAYUSCULAS')).toBeNull();
  });
});
