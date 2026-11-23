import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import EnviosMasivos, { validarEnvio } from './EnviosMasivos';
import AlcanceCampanas from '../components/AlcanceCampanas';
import PromocionCampana, { conZonaLima } from '../components/PromocionCampana';
import {
  enviarEnvioMasivo, getAlcanceCampana, getAlcanceCampanas, getEnviosMasivos, getPromociones, getSugerenciaPromocion,
  registrarPromocion, vistaPreviaEnvioMasivo,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getAlcanceCampanas: vi.fn(), getAlcanceCampana: vi.fn(), getSugerenciaPromocion: vi.fn(), getPromociones: vi.fn(),
  registrarPromocion: vi.fn(), vistaPreviaEnvioMasivo: vi.fn(), enviarEnvioMasivo: vi.fn(), getEnviosMasivos: vi.fn(),
  getDestinatariosEnvio: vi.fn(),
}));

const pagina = (results) => ({ count: results.length, results, next: null, previous: null });
const montar = async (ui) => { await act(async () => { render(<ToastProvider>{ui}</ToastProvider>); }); };
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

describe('H67 - alcance de las campañas', () => {
  it('muestra los números de la API, el total y el detalle por día', async () => {
    getAlcanceCampanas.mockResolvedValue({
      desde: '2026-11-01', hasta: '2026-11-30',
      campanas: [{ id: 3, nombre: 'Semana de objetos perdidos', origen: 'semana-perdidos-2026', estado: 'ACTIVA', visitas: 128, registros: 9, conversion: 7.0 }],
      total: { visitas: 128, registros: 9 },
    });
    getAlcanceCampana.mockResolvedValue({
      campana: { id: 3 }, desde: '2026-11-24', hasta: '2026-11-25', visitas: 40, registros: 3, conversion: 7.5,
      porDia: [{ fecha: '2026-11-24', visitas: 12, registros: 1 }, { fecha: '2026-11-25', visitas: 28, registros: 2 }],
    });
    await montar(<AlcanceCampanas />);
    const fila = screen.getByText('Semana de objetos perdidos').closest('tr');
    expect(within(fila).getByText('128')).toBeTruthy();
    expect(within(fila).getByText('7%')).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Ver por día Semana de objetos perdidos' })); });
    const detalle = screen.getByRole('region', { name: /Alcance por día/ });
    expect(within(detalle).getByText('28 visitas · 2 registros')).toBeTruthy();
  });

  it('no acepta un rango invertido', async () => {
    getAlcanceCampanas.mockResolvedValue({ campanas: [], total: { visitas: 0, registros: 0 } });
    await montar(<AlcanceCampanas />);
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-30' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-11-01' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Ver alcance' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/no puede ser anterior/);
    expect(getAlcanceCampanas).toHaveBeenCalledTimes(1);
  });
});

describe('H66 - promociones en redes', () => {
  const CAMPANA = { id: 3, nombre: 'Semana de objetos perdidos' };

  it('prepara el texto de la red, cuenta sus caracteres y registra la publicación', async () => {
    getPromociones.mockResolvedValue(pagina([]));
    getSugerenciaPromocion.mockResolvedValue({ texto: 'Semana de objetos perdidos. Más información: https://x/?origen=s #FoundIA', maximo: 280 });
    registrarPromocion.mockResolvedValue({ id: 1 });
    await montar(<PromocionCampana campana={CAMPANA} onCerrar={() => {}} />);
    fireEvent.change(screen.getByRole('combobox', { name: 'Red' }), { target: { value: 'X' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /Preparar texto/ })); });
    expect(getSugerenciaPromocion).toHaveBeenCalledWith(3, 'X');
    expect(screen.getByLabelText('Texto de la publicación *').value).toMatch(/origen=s/);
    expect(screen.getByText(/\/280 caracteres para X/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText(/Dirección de la publicación/), { target: { value: 'https://x.com/foundia/status/1' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Registrar publicación' })); });
    expect(registrarPromocion).toHaveBeenCalledWith(expect.objectContaining({ campanaId: 3, red: 'X', urlPublicacion: 'https://x.com/foundia/status/1' }));
    expect(registrarPromocion.mock.calls[0][0].publicadaEn).toMatch(/-05:00$/);
  });

  it('un texto más largo que el de la red o una dirección sin https se marcan junto al campo', async () => {
    getPromociones.mockResolvedValue(pagina([]));
    await montar(<PromocionCampana campana={CAMPANA} onCerrar={() => {}} />);
    fireEvent.change(screen.getByRole('combobox', { name: 'Red' }), { target: { value: 'X' } });
    fireEvent.change(screen.getByLabelText('Texto de la publicación *'), { target: { value: 'a'.repeat(281) } });
    fireEvent.change(screen.getByLabelText(/Dirección de la publicación/), { target: { value: 'http://x.com/1' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Registrar publicación' })); });
    expect(screen.getByText(/entre 3 y 280 caracteres para X/)).toBeTruthy();
    expect(screen.getByText(/debe empezar con https/)).toBeTruthy();
    expect(registrarPromocion).not.toHaveBeenCalled();
    expect(conZonaLima('2026-11-24T15:30')).toBe('2026-11-24T15:30:00-05:00');
  });
});

describe('H69 - avisos masivos', () => {
  const llenar = () => {
    fireEvent.change(screen.getByLabelText('Título *'), { target: { value: 'La biblioteca cierra el lunes' } });
    fireEvent.change(screen.getByLabelText('Mensaje *'), { target: { value: 'Por el feriado, atiende el martes.' } });
  };

  it('pide vista previa, muestra cuántos lo recibirán y envía con el código tras confirmar', async () => {
    getEnviosMasivos.mockResolvedValue(pagina([]));
    vistaPreviaEnvioMasivo.mockResolvedValue({ destinatarios: 128, muestra: [{ id: 2, nombre: 'Ana Torres' }], confirmacion: 'cod-1', expiraEn: '2026-11-23T15:40:00Z' });
    enviarEnvioMasivo.mockResolvedValue({ id: 4, totalDestinatarios: 128 });
    await montar(<EnviosMasivos />);
    llenar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Vista previa' })); });
    expect(screen.getByText(/Lo recibirán 128 cuentas/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Enviar a 128 cuentas' }));
    expect(enviarEnvioMasivo).not.toHaveBeenCalled();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, enviar' })); });
    expect(enviarEnvioMasivo).toHaveBeenCalledWith(expect.objectContaining({ confirmacion: 'cod-1', destinatarios: 'TODOS' }));
  });

  it('cambiar el texto invalida la vista previa; un código vencido pide otra', async () => {
    getEnviosMasivos.mockResolvedValue(pagina([]));
    vistaPreviaEnvioMasivo.mockResolvedValue({ destinatarios: 5, muestra: [], confirmacion: 'cod-2' });
    enviarEnvioMasivo.mockRejectedValue({ response: { status: 400, data: { confirmacion: ['El código de confirmación venció.'] } } });
    await montar(<EnviosMasivos />);
    llenar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Vista previa' })); });
    fireEvent.change(screen.getByLabelText('Mensaje *'), { target: { value: 'Otro texto distinto' } });
    expect(screen.queryByRole('button', { name: /Enviar a/ })).toBeNull();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Vista previa' })); });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar a 5 cuentas' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, enviar' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/Vuelve a pedir la vista previa/);
  });

  it('el límite diario (429) se explica; valida título, mensaje y enlace', async () => {
    getEnviosMasivos.mockResolvedValue(pagina([]));
    vistaPreviaEnvioMasivo.mockRejectedValue({ response: { status: 429, data: {} } });
    await montar(<EnviosMasivos />);
    llenar();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Vista previa' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/3 envíos masivos permitidos hoy/);
    expect(Object.keys(validarEnvio({ titulo: 'a', mensaje: '', enlace: 'https://x', destinatarios: 'TODOS' })).sort()).toEqual(['enlace', 'mensaje', 'titulo']);
  });
});
