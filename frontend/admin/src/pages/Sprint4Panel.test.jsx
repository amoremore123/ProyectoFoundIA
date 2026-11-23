import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import AvisosDestacados, { validarAviso } from './AvisosDestacados';
import ReportesChat from './ReportesChat';
import VersionesRestaurables, { comandoEnsayo } from '../components/VersionesRestaurables';
import {
  crearAvisoDestacado, getAvisosDestacados, getCampanas, getReportesMensajes, getRestauraciones, getVersionesRespaldo,
  resolverReporteMensaje, retirarAvisoDestacado,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getAvisosDestacados: vi.fn(), crearAvisoDestacado: vi.fn(), editarAvisoDestacado: vi.fn(), retirarAvisoDestacado: vi.fn(),
  getCampanas: vi.fn(), getReportesMensajes: vi.fn(), resolverReporteMensaje: vi.fn(),
  getVersionesRespaldo: vi.fn(), getRestauraciones: vi.fn(),
}));

const pagina = (results) => ({ count: results.length, results, next: null, previous: null });
const montar = async (ui) => { await act(async () => { render(<ToastProvider>{ui}</ToastProvider>); }); };

beforeEach(() => {
  vi.resetAllMocks();
  getCampanas.mockResolvedValue(pagina([{ id: 4, nombre: 'Semana de objetos perdidos', estado: 'ACTIVA' }]));
});
afterEach(cleanup);

describe('H71 - avisos destacados en el panel', () => {
  const VIGENTE = { id: 1, titulo: 'Semana de objetos perdidos', texto: 'Revisa la mesa de la biblioteca', enlace: '/explorar', destino: '/explorar', campanaId: null, fechaInicio: '2026-11-23', fechaFin: '2026-11-28', estado: 'VIGENTE' };
  const RETIRADO = { ...VIGENTE, id: 2, titulo: 'Horario especial', estado: 'RETIRADO' };

  it('lista los avisos con su estado y solo las acciones posibles', async () => {
    getAvisosDestacados.mockResolvedValue(pagina([VIGENTE, RETIRADO]));
    await montar(<AvisosDestacados />);
    const fila = screen.getByText('Semana de objetos perdidos').closest('tr');
    expect(within(fila).getByText('Vigente')).toBeTruthy();
    expect(within(fila).getByRole('button', { name: /Retirar/ })).toBeTruthy();
    const retirado = screen.getByText('Horario especial').closest('tr');
    expect(within(retirado).queryByRole('button', { name: /Editar|Retirar/ })).toBeNull();
  });

  it('crea un aviso que abre una campaña', async () => {
    getAvisosDestacados.mockResolvedValue(pagina([]));
    crearAvisoDestacado.mockResolvedValue(VIGENTE);
    await montar(<AvisosDestacados />);
    fireEvent.click(screen.getByRole('button', { name: /Nuevo aviso/ }));
    fireEvent.change(screen.getByLabelText('Título *'), { target: { value: 'Feria de objetos' } });
    fireEvent.change(screen.getByLabelText('Texto *'), { target: { value: 'Ven a la feria en el patio central' } });
    fireEvent.click(screen.getByLabelText('Abre una campaña'));
    fireEvent.change(screen.getByLabelText('Campaña *'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('Hasta *'), { target: { value: '2099-12-31' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Crear aviso' })); });
    expect(crearAvisoDestacado).toHaveBeenCalledWith(expect.objectContaining({ titulo: 'Feria de objetos', campanaId: 4, destino: null }));
  });

  it('retirar pide confirmación', async () => {
    getAvisosDestacados.mockResolvedValue(pagina([VIGENTE]));
    retirarAvisoDestacado.mockResolvedValue({ ...VIGENTE, estado: 'RETIRADO' });
    await montar(<AvisosDestacados />);
    fireEvent.click(screen.getByRole('button', { name: /Retirar Semana/ }));
    expect(retirarAvisoDestacado).not.toHaveBeenCalled();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, retirar' })); });
    expect(retirarAvisoDestacado).toHaveBeenCalledWith(1);
  });

  it('valida título, texto, ruta y fechas', () => {
    const e = validarAviso({ titulo: 'a', texto: '', abre: 'ruta', destino: 'https://otro.sitio', campanaId: '', fechaInicio: '2026-11-30', fechaFin: '2026-11-20' });
    expect(Object.keys(e).sort()).toEqual(['destino', 'fechaFin', 'texto', 'titulo']);
    expect(validarAviso({ titulo: 'Hola', texto: 'Texto', abre: 'campana', destino: '', campanaId: '', fechaInicio: '2026-11-20', fechaFin: '2026-11-21' }).campanaId).toBeTruthy();
  });
});

describe('H56 - reportes de mensajes del chat', () => {
  const REPORTE = {
    id: 4, estado: 'PENDIENTE', motivo: 'Me pidió mi teléfono', fechaReporte: '2026-11-22T15:20:00Z', conversacionId: 7,
    mensaje: { id: 31, texto: 'Pásame tu número', imagenUrl: null, enviadoEn: '2026-11-22T15:18:00Z' },
    autor: { id: 3, nombre: 'Luis García', estado: 'ACTIVO' }, reportadoPor: { id: 2, nombre: 'Ana Pérez' },
  };

  it('pide los pendientes y muestra el mensaje, el autor y quién reportó', async () => {
    getReportesMensajes.mockResolvedValue(pagina([REPORTE]));
    await montar(<ReportesChat />);
    expect(getReportesMensajes).toHaveBeenCalledWith({ estado: 'PENDIENTE' }, 1);
    const fila = screen.getByText('Pásame tu número').closest('tr');
    expect(within(fila).getByText('Luis García')).toBeTruthy();
    expect(within(fila).getByText('Ana Pérez')).toBeTruthy();
  });

  it('suspender exige motivo y una confirmación de peligro', async () => {
    getReportesMensajes.mockResolvedValue(pagina([REPORTE]));
    resolverReporteMensaje.mockResolvedValue({ ...REPORTE, estado: 'RESUELTO' });
    await montar(<ReportesChat />);
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    fireEvent.click(screen.getByLabelText('Suspender la cuenta del autor'));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Continuar' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/3 a 1000/);
    fireEvent.change(screen.getByLabelText(/Motivo de la decisión/), { target: { value: 'Pide datos personales' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Continuar' })); });
    expect(screen.getByRole('alertdialog', { name: /Suspender la cuenta/ })).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, suspender' })); });
    expect(resolverReporteMensaje).toHaveBeenCalledWith(4, { accion: 'SUSPENDER', resolucion: 'Pide datos personales' });
  });

  it('si ya se resolvió con otra decisión (409) lo explica', async () => {
    getReportesMensajes.mockResolvedValue(pagina([REPORTE]));
    resolverReporteMensaje.mockRejectedValue({ response: { status: 409, data: {} } });
    await montar(<ReportesChat />);
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reporte #4' }));
    fireEvent.change(screen.getByLabelText(/Motivo de la decisión/), { target: { value: 'Advertencia' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Continuar' })); });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, advertir' })); });
    expect(screen.getByRole('alert').textContent).toMatch(/otra decisión/);
  });
});

describe('H70 - versiones restaurables e historial', () => {
  it('muestra las versiones, el motivo de las no disponibles y el comando para ensayar', async () => {
    getVersionesRespaldo.mockResolvedValue(pagina([
      { version: '20261123T051908Z', fecha: '2026-11-23T05:19:08Z', disponible: true, motivo: null, base: { tamanoBytes: 24965 }, fotos: { tamanoBytes: 555096 } },
      { version: '20261110T165159Z', fecha: '2026-11-10T16:51:59Z', disponible: false, motivo: 'No hay respaldo de fotos de la misma ejecución.', base: { tamanoBytes: 6600 }, fotos: null },
    ]));
    getRestauraciones.mockResolvedValue(pagina([
      { id: 1, version: '20261120T020849Z', modo: 'ENSAYO', estado: 'COMPLETADA', responsable: 'Leonel', iniciadoEn: '2026-11-22T23:15:00Z', mensaje: 'objetos 21 -> 21', respaldoSeguridad: null },
    ]));
    await montar(<VersionesRestaurables />);
    expect(screen.getByText('No hay respaldo de fotos de la misma ejecución.')).toBeTruthy();
    expect(screen.getByText('Ensayo')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Restaurar$/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Ver cómo restaurar la versión 20261123T051908Z' }));
    const region = screen.getByRole('region', { name: /Cómo restaurar la versión 20261123T051908Z/ });
    expect(within(region).getByText(comandoEnsayo('20261123T051908Z'))).toBeTruthy();
    expect(comandoEnsayo('X')).not.toMatch(/RESTAURACION_APLICAR/);
  });
});
