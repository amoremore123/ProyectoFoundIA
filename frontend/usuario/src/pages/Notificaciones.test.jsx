import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Notificaciones, { enlaceInterno } from './Notificaciones';
import { getHistorialNotificaciones, getPreferenciasNotificaciones, marcarNotificacionLeida, marcarTodasNotificacionesLeidas } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getHistorialNotificaciones: vi.fn(), marcarNotificacionLeida: vi.fn(), marcarTodasNotificacionesLeidas: vi.fn(),
  getPreferenciasNotificaciones: vi.fn(),
}));

let servidor;

const AVISOS = [
  { id: 1, tipo: 'RECLAMO_RECIBIDO', titulo: 'Nuevo reclamo de objeto', mensaje: 'Un usuario ha enviado un reclamo sobre tu publicación #7.', leida: false, fechaCreacion: '2026-10-19T10:30:00' },
  { id: 2, tipo: 'CONTACTO_RECIBIDO', titulo: 'Nueva solicitud de contacto', mensaje: 'Alguien desea contactarte por tu publicación de objeto encontrado #7.', leida: true, fechaCreacion: '2026-10-18T09:00:00' },
];

function renderizar() {
  return render(<MemoryRouter><Notificaciones /></MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  servidor = AVISOS.map((n) => ({ ...n }));
  getPreferenciasNotificaciones.mockResolvedValue({ coincidencias: true, contactos: true, reclamos: true });
  // H25: el historial del servidor filtra y pagina; aquí se simula con la lista en memoria
  getHistorialNotificaciones.mockImplementation(async ({ estado = 'TODAS', pagina = 0, tamano = 20 } = {}) => {
    const filtrados = servidor.filter((n) => (estado === 'LEIDAS' ? n.leida : estado === 'NO_LEIDAS' ? !n.leida : true));
    const contenido = filtrados.slice(pagina * tamano, (pagina + 1) * tamano).map((n) => ({ ...n }));
    return { contenido, pagina, tamano, total: filtrados.length, totalPaginas: Math.ceil(filtrados.length / tamano), estado, noLeidas: servidor.filter((n) => !n.leida).length };
  });
  marcarTodasNotificacionesLeidas.mockImplementation(async () => {
    const actualizadas = servidor.filter((n) => !n.leida).length;
    servidor = servidor.map((n) => ({ ...n, leida: true }));
    return { actualizadas, noLeidas: 0 };
  });
  marcarNotificacionLeida.mockImplementation(async (id) => {
    servidor = servidor.map((n) => (n.id === id ? { ...n, leida: true } : n));
    return servidor.find((n) => n.id === id);
  });
});
afterEach(cleanup);

describe('H18/H20 - Pantalla de notificaciones', () => {
  it('muestra el tipo de cada aviso, el contador y explica qué genera cada uno', async () => {
    renderizar();
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
    expect(screen.getByText('Reclamo')).toBeTruthy();
    expect(screen.getByText('Solicitud de contacto')).toBeTruthy();
    expect(screen.getByText('1 sin leer')).toBeTruthy();
    expect(screen.getByText(/Tus datos de contacto nunca se comparten/)).toBeTruthy();
    expect(screen.queryByText(/@/)).toBeNull();
  });

  it('abrir un aviso lo marca como leído y se conserva al consultar de nuevo', async () => {
    const user = userEvent.setup();
    const primera = renderizar();
    await user.click(await screen.findByRole('button', { name: /Reclamo Nuevo reclamo de objeto/ }));

    await waitFor(() => expect(marcarNotificacionLeida).toHaveBeenCalledWith(1));
    expect(await screen.findByText('Todas leídas')).toBeTruthy();

    primera.unmount();
    renderizar();
    expect(await screen.findByRole('button', { name: /Reclamo Nuevo reclamo de objeto.*Leída/ })).toBeTruthy();
  });

  it('un aviso ya leído no vuelve a llamar a la API', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Solicitud de contacto Nueva solicitud de contacto.*Leída/ }));
    expect(marcarNotificacionLeida).not.toHaveBeenCalled();
  });

  it('si falla marcar como leído, el aviso sigue sin leer y no hay éxito ficticio', async () => {
    const user = userEvent.setup();
    marcarNotificacionLeida.mockRejectedValueOnce({ response: { status: 404, data: { mensaje: 'Notificación no encontrada' } } });
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Reclamo Nuevo reclamo de objeto/ }));

    expect(await screen.findByText('Notificación no encontrada')).toBeTruthy();
    expect(screen.getByText('1 sin leer')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
  });

  it('lista vacía muestra su mensaje', async () => {
    servidor = [];
    renderizar();
    expect(await screen.findByText('No tienes notificaciones todavía.')).toBeTruthy();
  });

  it('un fallo de consulta se distingue de la lista vacía y Reintentar recupera', async () => {
    const user = userEvent.setup();
    getHistorialNotificaciones.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();
    expect(await screen.findByText(/No se pudieron cargar las notificaciones/)).toBeTruthy();
    expect(screen.queryByText('No tienes notificaciones todavía.')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
  });
});

describe('H23 - Aviso de posible coincidencia', () => {
  const AVISO = { id: 5, tipo: 'COINCIDENCIA_NUEVA', titulo: 'Posible coincidencia para tu objeto', mensaje: 'Encontramos un objeto parecido a tu mochila.', leida: false, fechaCreacion: '2026-10-20T08:00:00', enlace: '/coincidencias/15-27' };

  function renderizarConRutas() {
    return render(
      <MemoryRouter initialEntries={['/notificaciones']}>
        <Routes>
          <Route path="/notificaciones" element={<Notificaciones />} />
          <Route path="/coincidencias/:id" element={<p>Pantalla de comparación</p>} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('marca el aviso como leído y abre la comparación de la pareja', async () => {
    const user = userEvent.setup();
    servidor = [{ ...AVISO }];
    renderizarConRutas();
    expect(await screen.findByText('Posible coincidencia')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: /Posible coincidencia/ }));

    await waitFor(() => expect(marcarNotificacionLeida).toHaveBeenCalledWith(5));
    expect(await screen.findByText('Pantalla de comparación')).toBeTruthy();
  });

  it('si no se pudo marcar como leído no navega', async () => {
    const user = userEvent.setup();
    servidor = [{ ...AVISO }];
    marcarNotificacionLeida.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    renderizarConRutas();
    await user.click(await screen.findByRole('button', { name: /Posible coincidencia/ }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.queryByText('Pantalla de comparación')).toBeNull();
  });

  it('solo acepta enlaces internos conocidos', () => {
    expect(enlaceInterno('/coincidencias/15-27')).toBe('/coincidencias/15-27');
    expect(enlaceInterno('/objeto/9')).toBe('/objeto/9');
    expect(enlaceInterno('https://sitio-falso.example/coincidencias/1')).toBeNull();
    expect(enlaceInterno('//sitio-falso.example')).toBeNull();
    expect(enlaceInterno('/perfil')).toBeNull();
    expect(enlaceInterno(null)).toBeNull();
  });

  it('H53: muestra el recordatorio y abre la publicación del objeto', async () => {
    servidor = [{ id: 5, tipo: 'RECORDATORIO_OBJETO', titulo: 'Tu objeto sigue sin resolverse', mensaje: 'Revisa tu publicación #9.', leida: false, fechaCreacion: '2026-11-01T08:00:00', enlace: '/objeto/9' }];
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/notificaciones']}>
        <Routes>
          <Route path="/notificaciones" element={<Notificaciones />} />
          <Route path="/objeto/:id" element={<p>Pantalla del objeto</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('Recordatorio')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: /Recordatorio Tu objeto sigue sin resolverse/ }));
    expect(await screen.findByText('Pantalla del objeto')).toBeTruthy();
    expect(marcarNotificacionLeida).toHaveBeenCalledWith(5);
  });
});

describe('H25 - Historial con filtro y marcar todas', () => {
  it('filtra sin leer y leídas, con su vacío propio', async () => {
    const user = userEvent.setup();
    renderizar();
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Sin leer' }));
    expect(getHistorialNotificaciones).toHaveBeenLastCalledWith({ estado: 'NO_LEIDAS', pagina: 0, tamano: 20 });
    expect(await screen.findByText('Nuevo reclamo de objeto')).toBeTruthy();
    expect(screen.queryByText('Nueva solicitud de contacto')).toBeNull();
    servidor = [];
    await user.click(screen.getByRole('button', { name: 'Leídas' }));
    expect(await screen.findByText('Aún no has leído ningún aviso.')).toBeTruthy();
  });

  it('marcar todas deja el contador en cero y avisa cuántas cambió', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Marcar todas como leídas/ }));
    expect(await screen.findByText('Marcaste 1 aviso como leído.')).toBeTruthy();
    expect(screen.getByText('Todas leídas')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Marcar todas como leídas/ })).toBeNull();
  });

  it('si falla marcar todas, se informa y los avisos siguen sin leer', async () => {
    marcarTodasNotificacionesLeidas.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    const user = userEvent.setup();
    renderizar();
    await user.click(await screen.findByRole('button', { name: /Marcar todas como leídas/ }));
    expect(await screen.findByText(/No se pudieron marcar los avisos como leídos/)).toBeTruthy();
    expect(screen.getByText('1 sin leer')).toBeTruthy();
  });

  it('pagina con "Ver más avisos" sin repetir', async () => {
    servidor = Array.from({ length: 25 }, (_, i) => ({ id: 100 + i, tipo: 'RECLAMO_RECIBIDO', titulo: `Aviso ${i + 1}`, mensaje: 'm', leida: true, fechaCreacion: '2026-10-19T10:30:00' }));
    const user = userEvent.setup();
    renderizar();
    expect(await screen.findByText('Mostrando 20 de 25')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Ver más avisos' }));
    expect(await screen.findByText('Aviso 25')).toBeTruthy();
    expect(screen.getAllByRole('listitem')).toHaveLength(25);
    expect(screen.queryByRole('button', { name: 'Ver más avisos' })).toBeNull();
  });
});
