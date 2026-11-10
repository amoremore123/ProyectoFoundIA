import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Usuarios from './Usuarios';
import Custodia from './Custodia';
import Reclamos from './Reclamos';
import Vencidos from './Vencidos';
import UltimoRespaldo, { tamanoLegible } from '../components/UltimoRespaldo';
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { inicioDe, puedeVer } from '../config/permisos';
import {
  cambiarRolUsuario, getCambiosRol, getCustodias, getReclamosCustodia, getUltimoRespaldo, getUsuarios,
  disponerCustodia, getCustodiasVencidas, nombreExportacion, registrarCustodia, registrarRetiro, resolverReclamo, verificarQrRetiro,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getUsuarios: vi.fn(), cambiarRolUsuario: vi.fn(), getCambiosRol: vi.fn(),
  getCustodias: vi.fn(), registrarCustodia: vi.fn(),
  getReclamosCustodia: vi.fn(), resolverReclamo: vi.fn(), verificarQrRetiro: vi.fn(), registrarRetiro: vi.fn(),
  getUltimoRespaldo: vi.fn(), getCustodiasVencidas: vi.fn(), disponerCustodia: vi.fn(),
}));

const pagina = (results) => ({ count: results.length, results, next: null, previous: null });
const conProveedores = (ui) => <ToastProvider><AuthProvider>{ui}</AuthProvider></ToastProvider>;
const montar = async (ui) => { await act(async () => { render(conProveedores(ui)); }); };

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
  localStorage.setItem('admin_token', 'jwt');
  localStorage.setItem('admin_usuario', JSON.stringify({ id: 1, nombre: 'Admin', rol: 'ADMIN' }));
});
afterEach(cleanup);

describe('H36 - permisos del panel por rol', () => {
  it('BIBLIOTECA solo ve custodia y reclamos y empieza en custodia', () => {
    const b = { rol: 'BIBLIOTECA' };
    expect(puedeVer(b, '/custodia')).toBe(true);
    expect(puedeVer(b, '/reclamos')).toBe(true);
    expect(puedeVer(b, '/vencidos')).toBe(true);
    expect(puedeVer(b, '/')).toBe(false);
    expect(puedeVer(b, '/usuarios')).toBe(false);
    expect(inicioDe(b)).toBe('/custodia');
    expect(puedeVer({ rol: 'ADMIN' }, '/usuarios')).toBe(true);
    expect(inicioDe({ rol: 'ADMIN' })).toBe('/');
    expect(puedeVer({ rol: 'USUARIO' }, '/custodia')).toBe(false);
  });
});

describe('H36 - cambio de rol', () => {
  const USUARIOS = [
    { id: 1, nombre: 'Admin', apellido: 'Uno', correo: 'a@x.test', rol: 'ADMIN', estado: 'ACTIVO' },
    { id: 2, nombre: 'Ana', apellido: 'Pérez', correo: 'ana@x.test', rol: 'USUARIO', estado: 'ACTIVO' },
  ];

  it('no ofrece cambiar el propio rol y exige motivo antes de confirmar', async () => {
    getUsuarios.mockResolvedValue(USUARIOS.map((u) => ({ ...u })));
    await montar(<Usuarios />);
    expect(screen.queryByRole('button', { name: 'Cambiar el rol de Admin Uno' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar el rol de Ana Pérez' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('alert').textContent).toContain('motivo');
    expect(cambiarRolUsuario).not.toHaveBeenCalled();
  });

  it('confirma, envía rol y motivo, y muestra el nuevo rol', async () => {
    getUsuarios.mockResolvedValue(USUARIOS.map((u) => ({ ...u })));
    cambiarRolUsuario.mockResolvedValue({ ...USUARIOS[1], rol: 'BIBLIOTECA' });
    await montar(<Usuarios />);
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar el rol de Ana Pérez' }));
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'BIBLIOTECA' } });
    fireEvent.change(screen.getByPlaceholderText(/Apoya la atención/), { target: { value: 'Atiende custodia' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    const dialogo = screen.getByRole('alertdialog');
    expect(dialogo.textContent).toContain('de Usuario a Biblioteca');
    await act(async () => { fireEvent.click(within(dialogo).getByRole('button', { name: 'Sí, cambiar rol' })); });
    expect(cambiarRolUsuario).toHaveBeenCalledWith(2, { rol: 'BIBLIOTECA', motivo: 'Atiende custodia' });
    expect(screen.getAllByText('Biblioteca').length).toBeGreaterThan(0);
  });

  it('si la API rechaza el cambio, muestra su mensaje y no cambia la tabla', async () => {
    getUsuarios.mockResolvedValue(USUARIOS.map((u) => ({ ...u })));
    cambiarRolUsuario.mockRejectedValue({ response: { status: 400, data: { detail: 'Debe quedar al menos un ADMIN en el sistema.' } } });
    await montar(<Usuarios />);
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar el rol de Ana Pérez' }));
    fireEvent.change(screen.getByPlaceholderText(/Apoya la atención/), { target: { value: 'Prueba' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, cambiar rol' })); });
    expect(screen.getByText('Debe quedar al menos un ADMIN en el sistema.')).toBeTruthy();
  });

  it('muestra el historial de cambios o su estado vacío', async () => {
    getUsuarios.mockResolvedValue(USUARIOS.map((u) => ({ ...u })));
    getCambiosRol.mockResolvedValueOnce([
      { id: 3, rolAnterior: 'USUARIO', rolNuevo: 'BIBLIOTECA', cambiadoPor: 'a@x.test', motivo: 'Apoyo', fecha: '2026-11-09T15:00:00Z' },
    ]).mockResolvedValueOnce([]);
    await montar(<Usuarios />);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Ver el historial de roles de Ana Pérez' })); });
    expect(screen.getByText('Usuario → Biblioteca')).toBeTruthy();
    expect(screen.getByText('Motivo: Apoyo')).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Ver el historial de roles de Admin Uno' })); });
    expect(screen.getByText('Esta cuenta nunca cambió de rol.')).toBeTruthy();
  });
});

describe('H38 - último respaldo', () => {
  it('muestra estado, tamaño y el error de un respaldo fallido', async () => {
    getUltimoRespaldo.mockResolvedValue({
      id: 2, nombreArchivo: 'foundia_x.sql.gz', tamanoBytes: 6215, estado: 'FALLIDO',
      iniciadoEn: '2026-11-09T03:00:00Z', finalizadoEn: null, mensaje: 'mysqldump falló',
    });
    await act(async () => { render(<UltimoRespaldo />); });
    expect(screen.getByText('Fallido')).toBeTruthy();
    expect(screen.getByText('6.1 KB')).toBeTruthy();
    expect(screen.getByText('mysqldump falló')).toBeTruthy();
  });

  it('avisa cuando todavía no hay respaldos', async () => {
    getUltimoRespaldo.mockResolvedValue(null);
    await act(async () => { render(<UltimoRespaldo />); });
    expect(screen.getByText(/Todavía no hay respaldos/)).toBeTruthy();
  });

  it('si falla la consulta permite reintentar', async () => {
    getUltimoRespaldo.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce(null);
    await act(async () => { render(<UltimoRespaldo />); });
    expect(screen.getByRole('alert')).toBeTruthy();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    expect(screen.getByText(/Todavía no hay respaldos/)).toBeTruthy();
  });

  it('convierte bytes a una medida legible', () => {
    expect(tamanoLegible(512)).toBe('512 B');
    expect(tamanoLegible(2 * 1024 * 1024)).toBe('2.0 MB');
    expect(tamanoLegible(null)).toBe('—');
  });
});

describe('H32 - nombre del archivo exportado', () => {
  it('sigue el mismo formato que el servidor', () => {
    expect(nombreExportacion({ desde: '2026-10-01', hasta: '2026-10-07' }, 'xlsx')).toBe('foundia-reporte_2026-10-01_2026-10-07.xlsx');
    expect(nombreExportacion({ desde: '2026-10-01' }, 'pdf')).toBe('foundia-reporte_desde-2026-10-01.pdf');
    expect(nombreExportacion({ hasta: '2026-10-07' }, 'pdf')).toBe('foundia-reporte_hasta-2026-10-07.pdf');
    expect(nombreExportacion({}, 'xlsx', new Date('2026-11-09T12:00:00Z'))).toBe('foundia-reporte_completo-2026-11-09.xlsx');
  });
});

describe('H41 - custodia', () => {
  const CUSTODIA = {
    id: 5, codigo: 'FND-2026-0001', estado: 'EN_CUSTODIA', fechaRecepcion: '2026-11-09T15:04:05Z',
    objeto: { id: 12, nombre: 'Mochila azul', categoria: 'Mochilas' }, recibidoPor: { id: 3, nombre: 'Beto Biblioteca' },
  };

  it('lista lo recibido y muestra el vacío sin registros', async () => {
    getCustodias.mockResolvedValueOnce(pagina([CUSTODIA]));
    await montar(<Custodia />);
    expect(screen.getByText('FND-2026-0001')).toBeTruthy();
    expect(screen.getByText('Beto Biblioteca')).toBeTruthy();
    cleanup();
    getCustodias.mockResolvedValueOnce(pagina([]));
    await montar(<Custodia />);
    expect(screen.getByText('Aún no se recibió ningún objeto en custodia.')).toBeTruthy();
  });

  it('valida el número de publicación y registra la recepción', async () => {
    getCustodias.mockResolvedValue(pagina([]));
    registrarCustodia.mockResolvedValue(CUSTODIA);
    await montar(<Custodia />);
    fireEvent.click(screen.getByRole('button', { name: 'Registrar recepción' }));
    expect(screen.getByRole('alert').textContent).toContain('número de la publicación');
    expect(registrarCustodia).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText('Ej. 12'), { target: { value: '12' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Registrar recepción' })); });
    expect(registrarCustodia).toHaveBeenCalledWith({ objetoId: 12, observaciones: '' });
    expect(screen.getByRole('button', { name: 'Abrir la etiqueta QR de FND-2026-0001' })).toBeTruthy();
  });

  it('muestra el error del servidor si el objeto ya está en custodia', async () => {
    getCustodias.mockResolvedValue(pagina([]));
    registrarCustodia.mockRejectedValue({ response: { status: 409, data: { detail: 'La publicación ya está registrada en custodia.' } } });
    await montar(<Custodia />);
    fireEvent.change(screen.getByPlaceholderText('Ej. 12'), { target: { value: '12' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Registrar recepción' })); });
    expect(screen.getByText('La publicación ya está registrada en custodia.')).toBeTruthy();
  });
});

describe('H43 - reclamos de custodia', () => {
  const RECLAMO = {
    id: 8, estado: 'PENDIENTE', fechaReclamo: '2026-11-09T14:00:00Z',
    objeto: { id: 12, nombre: 'Mochila azul', categoria: 'Mochilas', descripcion: 'Cierre rojo' },
    reclamante: { id: 21, nombre: 'Ana Pérez' }, qrRetiroUrl: null, qrRetiroVigente: false,
  };

  it('pide los pendientes por defecto y exige motivo antes de confirmar', async () => {
    getReclamosCustodia.mockResolvedValue(pagina([RECLAMO]));
    await montar(<Reclamos />);
    expect(getReclamosCustodia).toHaveBeenCalledWith({ estado: 'PENDIENTE', q: '' }, 1);
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reclamo #8' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText('Explica el motivo de la decisión.')).toBeTruthy();
    expect(resolverReclamo).not.toHaveBeenCalled();
  });

  it('acepta con confirmación y recarga la bandeja', async () => {
    getReclamosCustodia.mockResolvedValue(pagina([RECLAMO]));
    resolverReclamo.mockResolvedValue({ ...RECLAMO, estado: 'ACEPTADO' });
    await montar(<Reclamos />);
    fireEvent.click(screen.getByRole('button', { name: 'Resolver reclamo #8' }));
    fireEvent.change(screen.getByPlaceholderText(/Documento de identidad/), { target: { value: 'DNI verificado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, aceptar' })); });
    expect(resolverReclamo).toHaveBeenCalledWith(8, { decision: 'ACEPTADO', motivo: 'DNI verificado' });
    expect(getReclamosCustodia).toHaveBeenCalledTimes(2);
  });

  it('verifica el QR de retiro y solo permite entregar si está vigente', async () => {
    getReclamosCustodia.mockResolvedValue(pagina([]));
    verificarQrRetiro.mockResolvedValueOnce({ valido: true, estadoQr: 'USADO', reclamo: { ...RECLAMO, estado: 'RETIRADO' } });
    await montar(<Reclamos />);
    fireEvent.change(screen.getByPlaceholderText(/Escanea el QR que muestra/), { target: { value: 'tok-1' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Verificar' })); });
    expect(screen.getByText(/ya se usó/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Entregar objeto' })).toBeNull();

    verificarQrRetiro.mockResolvedValueOnce({ valido: true, estadoQr: 'VIGENTE', reclamo: { ...RECLAMO, estado: 'ACEPTADO' } });
    registrarRetiro.mockResolvedValue({});
    fireEvent.change(screen.getByPlaceholderText(/Escanea el QR que muestra/), { target: { value: 'tok-2' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Verificar' })); });
    fireEvent.click(screen.getByRole('button', { name: 'Entregar objeto' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, entregar' })); });
    expect(registrarRetiro).toHaveBeenCalledWith('tok-2');
  });

  it('un fallo de carga se distingue del vacío', async () => {
    getReclamosCustodia.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    await montar(<Reclamos />);
    expect(screen.getByText(/No se pudieron cargar los reclamos/)).toBeTruthy();
    expect(screen.queryByText('No hay reclamos pendientes.')).toBeNull();
  });
});

describe('H54 - objetos vencidos', () => {
  const VENCIDA = {
    id: 7, codigo: 'FND-2026-0101', diasEnCustodia: 45, reclamosPendientes: 1, bloqueada: false,
    fechaRecepcion: '2026-09-01T10:00:00Z', objeto: { id: 30, nombre: 'Mochila azul', categoria: 'Mochilas' },
  };

  it('lista los vencidos con el plazo y muestra el vacío', async () => {
    getCustodiasVencidas.mockResolvedValueOnce({ plazoDias: 30, total: 1, resultados: [VENCIDA] });
    await montar(<Vencidos />);
    expect(screen.getByText(/hace más de 30 días/)).toBeTruthy();
    expect(screen.getByText('FND-2026-0101')).toBeTruthy();
    cleanup();
    getCustodiasVencidas.mockResolvedValueOnce({ plazoDias: 30, total: 0, resultados: [] });
    await montar(<Vencidos />);
    expect(screen.getByText(/No hay objetos vencidos/)).toBeTruthy();
  });

  it('exige motivo, confirma y ofrece descargar el acta', async () => {
    getCustodiasVencidas.mockResolvedValue({ plazoDias: 30, total: 1, resultados: [VENCIDA] });
    disponerCustodia.mockResolvedValue({ ...VENCIDA, estado: 'DISPUESTO', disposicion: { codigoActa: 'ACTA-FND-2026-0101' } });
    await montar(<Vencidos />);
    fireEvent.click(screen.getByRole('button', { name: 'Disponer de FND-2026-0101' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText(/al menos 10 caracteres/)).toBeTruthy();
    expect(disponerCustodia).not.toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText('Descartar'));
    fireEvent.change(screen.getByPlaceholderText(/Sin reclamos tras el plazo/), { target: { value: 'Objeto deteriorado sin reclamos' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, descartar' })); });
    expect(disponerCustodia).toHaveBeenCalledWith(7, { destino: 'DESCARTADO', motivo: 'Objeto deteriorado sin reclamos' });
    expect(screen.getByRole('button', { name: 'Descargar el acta de FND-2026-0101' })).toBeTruthy();
  });

  it('un reclamo aceptado bloquea la disposición y un error del servidor se muestra', async () => {
    getCustodiasVencidas.mockResolvedValue({ plazoDias: 30, total: 2, resultados: [{ ...VENCIDA, bloqueada: true }, { ...VENCIDA, id: 8, codigo: 'FND-2026-0102' }] });
    disponerCustodia.mockRejectedValue({ response: { status: 400, data: { detail: 'El plazo de custodia aún no vence: faltan 3 días.' } } });
    await montar(<Vencidos />);
    expect(screen.getByText('Esperando retiro')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Disponer de FND-2026-0102' }));
    fireEvent.change(screen.getByPlaceholderText(/Sin reclamos tras el plazo/), { target: { value: 'Sin reclamos tras el plazo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Sí, donar' })); });
    expect(screen.getByText('El plazo de custodia aún no vence: faltan 3 días.')).toBeTruthy();
  });
});
