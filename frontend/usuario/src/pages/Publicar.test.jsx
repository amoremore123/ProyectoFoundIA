import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Publicar from './Publicar';
import { crearObjeto, listarCategorias } from '../services/api';
import { obtenerUbicacionActual } from '../services/geolocalizacion';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), crearObjeto: vi.fn(), listarCategorias: vi.fn(),
}));
vi.mock('../services/geolocalizacion', () => ({ obtenerUbicacionActual: vi.fn() }));

function hoyISO() {
  const fecha = new Date();
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

const gps = { latitud: 19.43261, longitud: -99.1332, direccion: 'Dirección obtenida por GPS' };

function consultaPendiente() {
  let resolver;
  const promise = new Promise((resolve) => { resolver = resolve; });
  return { promise, resolver };
}

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/publicar']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/publicar" element={<Publicar />} />
        <Route path="/objeto/:id" element={<p>Detalle de prueba</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function rellenar(user) {
  await user.type(screen.getByRole('textbox', { name: /^Nombre/ }), 'Mochila de prueba');
  await user.type(screen.getByRole('textbox', { name: /^Descripción/ }), 'Objeto ficticio');
  await screen.findByRole('option', { name: 'Mochila' });
  await user.selectOptions(screen.getByRole('combobox', { name: /^Categoría/ }), '3');
  await user.type(screen.getByRole('textbox', { name: /^Ubicación/ }), 'Biblioteca central');
  fireEvent.change(screen.getByLabelText(/^Fecha/), { target: { value: hoyISO() } });
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 3, nombre: 'Mochila', estado: true }]);
  crearObjeto.mockResolvedValue({ id: 55 });
  obtenerUbicacionActual.mockResolvedValue(gps);
});
afterEach(cleanup);

describe('H1/H3/H4 - Formulario de publicación', () => {
  it('marca los campos obligatorios y no envía un formulario vacío', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    expect(screen.getByText('El nombre es obligatorio')).not.toBeNull();
    expect(screen.getByText('La descripción es obligatoria')).not.toBeNull();
    expect(screen.getByText('La ubicación es obligatoria')).not.toBeNull();
    expect(screen.getByText('La fecha es obligatoria')).not.toBeNull();
    expect(crearObjeto).not.toHaveBeenCalled();
  });

  it('publica una ubicación manual con coordenadas null y abre el detalle', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await screen.findByText('Detalle de prueba');
    expect(crearObjeto).toHaveBeenCalledWith({
      nombre: 'Mochila de prueba', descripcion: 'Objeto ficticio', categoriaId: 3,
      ubicacion: 'Biblioteca central', fechaObjeto: hoyISO(), tipo: 'PERDIDO',
      latitud: null, longitud: null,
    });
  });

  it('limita el input del nombre a 150 caracteres', () => {
    renderizar();
    expect(screen.getByRole('textbox', { name: /^Nombre/ }).maxLength).toBe(150);
  });

  it('admite un nombre de 150 caracteres', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    fireEvent.change(screen.getByRole('textbox', { name: /^Nombre/ }), { target: { value: 'N'.repeat(150) } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0].nombre).toHaveLength(150);
  });

  it('rechaza un nombre largo aunque se inyecte saltando el maxLength del input', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    fireEvent.change(screen.getByRole('textbox', { name: /^Nombre/ }), { target: { value: 'N'.repeat(151) } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    expect(screen.getByText('El nombre no debe superar los 150 caracteres')).not.toBeNull();
    expect(crearObjeto).not.toHaveBeenCalled();
  });

  it('limita el input de ubicación a 255 caracteres', () => {
    renderizar();
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).maxLength).toBe(255);
  });

  it('admite una dirección manual de 255 caracteres sin coordenadas', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    fireEvent.change(screen.getByRole('textbox', { name: /^Ubicación/ }), { target: { value: 'U'.repeat(255) } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({ ubicacion: 'U'.repeat(255), latitud: null, longitud: null });
  });

  it('rechaza una ubicación larga aunque se inyecte saltando el maxLength del input', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    fireEvent.change(screen.getByRole('textbox', { name: /^Ubicación/ }), { target: { value: 'U'.repeat(256) } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    expect(screen.getByText('La ubicación no debe superar los 255 caracteres')).not.toBeNull();
    expect(crearObjeto).not.toHaveBeenCalled();
  });

  it('publica la dirección y las coordenadas del GPS cuando no se editan', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    await screen.findByText(/Ubicación obtenida:/);
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).value).toBe(gps.direccion);
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({
      ubicacion: gps.direccion, latitud: gps.latitud, longitud: gps.longitud,
    });
  });

  it('borra las coordenadas del GPS al editar manualmente la dirección', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    await screen.findByText(/Ubicación obtenida:/);
    const ubicacion = screen.getByRole('textbox', { name: /^Ubicación/ });
    await user.clear(ubicacion);
    await user.type(ubicacion, 'Otra dirección manual');
    expect(screen.queryByText(/Ubicación obtenida:/)).toBeNull();
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({
      ubicacion: 'Otra dirección manual', latitud: null, longitud: null,
    });
  });

  it('conserva las coordenadas cuando se edita un campo distinto de la dirección', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    await screen.findByText(/Ubicación obtenida:/);
    await user.type(screen.getByRole('textbox', { name: /^Nombre/ }), ' nueva');
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({
      ubicacion: gps.direccion, latitud: gps.latitud, longitud: gps.longitud,
    });
  });

  it('ignora un resultado GPS tardío si ya se escribió otra dirección', async () => {
    const consulta = consultaPendiente();
    obtenerUbicacionActual.mockReturnValueOnce(consulta.promise);
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    fireEvent.change(screen.getByRole('textbox', { name: /^Ubicación/ }), { target: { value: 'Dirección manual nueva' } });
    await act(async () => consulta.resolver(gps));
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).value).toBe('Dirección manual nueva');
    expect(screen.queryByText(/Ubicación obtenida:/)).toBeNull();
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({ latitud: null, longitud: null });
  });

  it('una nueva consulta GPS no se sobrescribe con una respuesta antigua invalidada', async () => {
    const primera = consultaPendiente();
    const segunda = consultaPendiente();
    obtenerUbicacionActual.mockReturnValueOnce(primera.promise).mockReturnValueOnce(segunda.promise);
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    fireEvent.change(screen.getByRole('textbox', { name: /^Ubicación/ }), { target: { value: 'Dirección manual' } });
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    const nuevoGps = { latitud: 20, longitud: -100, direccion: 'Nuevo GPS' };
    await act(async () => segunda.resolver(nuevoGps));
    await act(async () => primera.resolver(gps));
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).value).toBe(nuevoGps.direccion);
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({
      ubicacion: nuevoGps.direccion, latitud: nuevoGps.latitud, longitud: nuevoGps.longitud,
    });
  });

  it('sin geocodificación usa el texto de las coordenadas, no una dirección manual anterior', async () => {
    obtenerUbicacionActual.mockResolvedValueOnce({ ...gps, direccion: '' });
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    await screen.findByText(/Ubicación obtenida:/);
    expect(screen.getByRole('textbox', { name: /^Ubicación/ }).value).toBe(`${gps.latitud}, ${gps.longitud}`);
  });

  it('permite publicar a mano cuando se deniega el permiso GPS', async () => {
    obtenerUbicacionActual.mockRejectedValueOnce(new Error('No se pudo acceder a tu ubicación (permiso denegado)'));
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    await screen.findByText(/permiso denegado/);
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
    expect(crearObjeto.mock.calls[0][0]).toMatchObject({
      ubicacion: 'Biblioteca central', latitud: null, longitud: null,
    });
  });

  it('espera a que termine la consulta GPS antes de publicar', async () => {
    const consulta = consultaPendiente();
    obtenerUbicacionActual.mockReturnValueOnce(consulta.promise);
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: /Usar mi ubicación/ }));
    const publicar = screen.getByRole('button', { name: 'PUBLICAR OBJETO' });
    expect(publicar.disabled).toBe(true);
    await user.click(publicar);
    expect(crearObjeto).not.toHaveBeenCalled();
    await act(async () => consulta.resolver(gps));
    expect(publicar.disabled).toBe(false);
    await user.click(publicar);
    await waitFor(() => expect(crearObjeto).toHaveBeenCalled());
  });

  it('rechaza una fecha futura aunque se salte el límite del calendario', async () => {
    const user = userEvent.setup();
    renderizar();
    await rellenar(user);
    const manana = new Date();
    manana.setDate(manana.getDate() + 1);
    const fecha = `${manana.getFullYear()}-${String(manana.getMonth() + 1).padStart(2, '0')}-${String(manana.getDate()).padStart(2, '0')}`;
    fireEvent.change(screen.getByLabelText(/^Fecha/), { target: { value: fecha } });
    await user.click(screen.getByRole('button', { name: 'PUBLICAR OBJETO' }));
    expect(screen.getByText('La fecha no puede ser futura')).not.toBeNull();
    expect(crearObjeto).not.toHaveBeenCalled();
  });
});
