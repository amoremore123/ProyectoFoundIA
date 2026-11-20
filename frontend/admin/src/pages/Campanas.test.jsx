import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import Campanas, { origenSugerido, validarCampana } from './Campanas';
import { crearCampana, editarCampana, finalizarCampana, getCampanas } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getCampanas: vi.fn(), crearCampana: vi.fn(), editarCampana: vi.fn(), finalizarCampana: vi.fn(),
}));

const ACTIVA = {
  id: 3, nombre: 'Semana de objetos perdidos', descripcion: 'Pabellones y redes', origen: 'semana-perdidos-2026',
  destino: '/', enlace: 'http://localhost:5173/?origen=semana-perdidos-2026', fechaInicio: '2026-11-18', fechaFin: '2026-11-30',
  estado: 'ACTIVA',
};
const PROGRAMADA = { ...ACTIVA, id: 4, nombre: 'Feria de bienvenida', origen: 'feria-2027', enlace: 'http://localhost:5173/?origen=feria-2027', estado: 'PROGRAMADA' };
const FINALIZADA = { ...ACTIVA, id: 5, nombre: 'Campaña de octubre', origen: 'octubre', estado: 'FINALIZADA' };
const pagina = (results) => ({ count: results.length, results, next: null, previous: null });

beforeEach(() => {
  vi.resetAllMocks();
  getCampanas.mockResolvedValue(pagina([ACTIVA, PROGRAMADA, FINALIZADA]));
});
afterEach(cleanup);

const montar = async () => { await act(async () => { render(<ToastProvider><Campanas /></ToastProvider>); }); };
const fila = (nombre) => screen.getByText(nombre).closest('tr');

describe('H60 - campañas de difusión en el panel', () => {
  it('lista las campañas con su estado, su enlace y solo las acciones posibles', async () => {
    await montar();
    expect(getCampanas).toHaveBeenCalledWith({ estado: '' }, 1);
    const activa = fila('Semana de objetos perdidos');
    expect(within(activa).getByText('Activa')).toBeTruthy();
    expect(within(activa).getByText(ACTIVA.enlace)).toBeTruthy();
    expect(within(activa).getByRole('button', { name: /Finalizar/ })).toBeTruthy();
    expect(within(fila('Feria de bienvenida')).queryByRole('button', { name: /Finalizar/ })).toBeNull();
    expect(within(fila('Feria de bienvenida')).getByRole('button', { name: /Editar/ })).toBeTruthy();
    expect(within(fila('Campaña de octubre')).queryByRole('button', { name: /Editar|Finalizar/ })).toBeNull();
  });

  it('crea una campaña sugiriendo el origen a partir del nombre', async () => {
    crearCampana.mockResolvedValue({ ...PROGRAMADA, id: 9 });
    await montar();
    fireEvent.click(screen.getByRole('button', { name: /Nueva campaña/ }));
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'Semana de Objetos Perdidos 2026' } });
    expect(screen.getByLabelText('Origen *').value).toBe('semana-de-objetos-perdidos-2026');
    fireEvent.change(screen.getByLabelText('Inicio *'), { target: { value: '2026-11-24' } });
    fireEvent.change(screen.getByLabelText('Fin *'), { target: { value: '2026-11-30' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Crear campaña' })); });
    expect(crearCampana).toHaveBeenCalledWith({
      nombre: 'Semana de Objetos Perdidos 2026', descripcion: '', destino: '/', fechaInicio: '2026-11-24',
      fechaFin: '2026-11-30', origen: 'semana-de-objetos-perdidos-2026',
    });
    expect(getCampanas).toHaveBeenCalledTimes(2);
  });

  it('con campos vacíos o inválidos muestra el error junto a cada campo y no envía', async () => {
    await montar();
    fireEvent.click(screen.getByRole('button', { name: /Nueva campaña/ }));
    fireEvent.change(screen.getByLabelText('Inicio *'), { target: { value: '2026-11-30' } });
    fireEvent.change(screen.getByLabelText('Fin *'), { target: { value: '2026-11-20' } });
    fireEvent.change(screen.getByLabelText('Página de destino'), { target: { value: 'https://otra.web' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Crear campaña' })); });
    expect(crearCampana).not.toHaveBeenCalled();
    expect(screen.getByText('Escribe un nombre de al menos 3 caracteres.')).toBeTruthy();
    expect(screen.getByText(/El origen debe tener entre 3 y 40/)).toBeTruthy();
    expect(screen.getByText('La fecha de fin no puede ser anterior al inicio.')).toBeTruthy();
    expect(screen.getByText(/Debe ser una ruta de FoundIA/)).toBeTruthy();
    expect(screen.getByLabelText('Nombre *').getAttribute('aria-invalid')).toBe('true');
  });

  it('muestra junto al campo el error del servidor (origen repetido)', async () => {
    crearCampana.mockRejectedValue({ response: { status: 400, data: { origen: ['Ya existe una campaña con este origen.'] } } });
    await montar();
    fireEvent.click(screen.getByRole('button', { name: /Nueva campaña/ }));
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'Feria' } });
    fireEvent.change(screen.getByLabelText('Fin *'), { target: { value: '2099-12-31' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Crear campaña' })); });
    expect(screen.getByText('Ya existe una campaña con este origen.')).toBeTruthy();
    expect(screen.getByRole('form', { name: 'Nueva campaña' })).toBeTruthy();
  });

  it('al editar, el origen queda bloqueado y no se envía', async () => {
    editarCampana.mockResolvedValue(PROGRAMADA);
    await montar();
    fireEvent.click(within(fila('Feria de bienvenida')).getByRole('button', { name: /Editar/ }));
    expect(screen.getByLabelText('Origen *').disabled).toBe(true);
    expect(screen.getByText(/No se puede cambiar: el enlace ya pudo compartirse/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'Feria de bienvenida 2027' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' })); });
    expect(editarCampana).toHaveBeenCalledTimes(1);
    expect(editarCampana.mock.calls[0][0]).toBe(4);
    expect(editarCampana.mock.calls[0][1].nombre).toBe('Feria de bienvenida 2027');
    expect(editarCampana.mock.calls[0][1]).not.toHaveProperty('origen');
  });

  it('finalizar pide confirmación', async () => {
    finalizarCampana.mockResolvedValue({ ...ACTIVA, estado: 'FINALIZADA' });
    await montar();
    fireEvent.click(within(fila('Semana de objetos perdidos')).getByRole('button', { name: /Finalizar/ }));
    expect(finalizarCampana).not.toHaveBeenCalled();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, finalizar' })); });
    expect(finalizarCampana).toHaveBeenCalledWith(3);
  });

  it('copia el enlace tal como lo arma el servidor', async () => {
    const escribir = vi.fn().mockResolvedValue();
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: escribir }, configurable: true });
    await montar();
    await act(async () => { fireEvent.click(within(fila('Semana de objetos perdidos')).getByRole('button', { name: /Copiar el enlace/ })); });
    expect(escribir).toHaveBeenCalledWith(ACTIVA.enlace);
    expect(within(fila('Semana de objetos perdidos')).getByRole('button', { name: /Copiar el enlace/ }).textContent).toBe('Copiado');
  });

  it('reglas: origen sugerido y validación', () => {
    expect(origenSugerido('  Campaña Ñandú: ¡Pabellón B!  ')).toBe('campana-nandu-pabellon-b');
    const conMayusculas = { nombre: 'Feria', descripcion: '', origen: 'Con Mayus', destino: '/', fechaInicio: '2026-11-20', fechaFin: '2026-11-20' };
    expect(validarCampana(conMayusculas, false).origen).toMatch(/minúsculas/);
    const editando = { nombre: 'Feria', descripcion: '', origen: 'x', destino: '//otro', fechaInicio: '2026-11-20', fechaFin: '2026-11-21' };
    expect(Object.keys(validarCampana(editando, true))).toEqual(['destino']);
  });
});
