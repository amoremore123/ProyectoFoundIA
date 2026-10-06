import { StrictMode } from 'react';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Buscar from './Buscar';
import { buscarObjetos, listarCategorias } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  buscarObjetos: vi.fn(),
  listarCategorias: vi.fn(),
}));

const categorias = [
  { id: 1, nombre: 'Celular', estado: true },
  { id: 2, nombre: 'Mochila', estado: true },
  { id: 3, nombre: 'Documentos', estado: false },
];
const mochila = {
  id: 10, nombre: 'Mochila negra', ubicacion: 'Biblioteca central',
  categoria: categorias[1], tipo: 'PERDIDO', fechaPublicacion: '2026-10-05T12:00:00', fotos: [],
};
const celular = { ...mochila, id: 11, nombre: 'Celular azul', ubicacion: 'Comedor', categoria: categorias[0] };

function NavegacionPrueba() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output data-testid="ruta">{location.pathname}{location.search}</output>
      <button onClick={() => navigate(-1)}>Atrás de prueba</button>
      <button onClick={() => navigate(1)}>Adelante de prueba</button>
    </>
  );
}

function renderizar(ruta = '/buscar', estricto = false) {
  const contenido = (
    <MemoryRouter initialEntries={[ruta]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <NavegacionPrueba />
      <Routes>
        <Route path="/buscar" element={<Buscar />} />
        <Route path="/objeto/:id" element={<p>Detalle del objeto</p>} />
      </Routes>
    </MemoryRouter>
  );
  return render(estricto ? <StrictMode>{contenido}</StrictMode> : contenido);
}

function parametrosActuales() {
  return new URLSearchParams(screen.getByTestId('ruta').textContent.split('?')[1] || '');
}

function respuestaPendiente() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function categoriasListas() {
  await waitFor(() => expect(screen.getByRole('combobox', { name: 'Categoría' }).disabled).toBe(false));
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue(categorias);
  buscarObjetos.mockResolvedValue([mochila]);
});
afterEach(cleanup);

describe('H6/H7 - Búsqueda y filtros en la pantalla de resultados', () => {
  it('restaura el texto, tipo y categoría de un enlace compartido', async () => {
    renderizar('/buscar?q=mochila&categoriaId=2&tipo=PERDIDO');
    await categoriasListas();
    await screen.findByRole('heading', { name: 'Mochila negra' });
    expect(screen.getByRole('searchbox').value).toBe('mochila');
    expect(screen.getByRole('combobox').value).toBe('2');
    expect(screen.getByRole('button', { name: 'Perdidos' }).getAttribute('aria-pressed')).toBe('true');
    expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'mochila', categoriaId: '2', tipo: 'PERDIDO' }, { signal: expect.any(AbortSignal) },
    );
  });

  it('envía una búsqueda parcial sin espacios sobrantes y actualiza la URL', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByRole('heading', { name: 'Mochila negra' });
    await user.type(screen.getByRole('searchbox'), '  moch  ');
    await user.click(screen.getByRole('button', { name: 'Buscar', exact: true }));
    await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'moch' }, { signal: expect.any(AbortSignal) },
    ));
    expect(parametrosActuales().get('q')).toBe('moch');
    expect(screen.getByRole('searchbox').value).toBe('moch');
  });

  it('cambiar de categoría conserva el texto y el tipo aplicados', async () => {
    const user = userEvent.setup();
    renderizar('/buscar?q=mochila&tipo=PERDIDO');
    await categoriasListas();
    await user.selectOptions(screen.getByRole('combobox'), '2');
    await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'mochila', tipo: 'PERDIDO', categoriaId: '2' }, { signal: expect.any(AbortSignal) },
    ));
    expect(parametrosActuales().get('categoriaId')).toBe('2');
    expect(screen.getByRole('searchbox').value).toBe('mochila');
  });

  it('los botones de tipo se combinan con la búsqueda y la categoría', async () => {
    const user = userEvent.setup();
    renderizar('/buscar?q=mochila&categoriaId=2');
    await screen.findByRole('heading', { name: 'Mochila negra' });
    await user.click(screen.getByRole('button', { name: 'Encontrados' }));
    await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'mochila', categoriaId: '2', tipo: 'ENCONTRADO' }, { signal: expect.any(AbortSignal) },
    ));
    expect(screen.getByRole('button', { name: 'Encontrados' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('Todas elimina solamente el filtro de categoría', async () => {
    const user = userEvent.setup();
    renderizar('/buscar?q=mochila&categoriaId=2&tipo=PERDIDO');
    await categoriasListas();
    await user.selectOptions(screen.getByRole('combobox'), '');
    await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'mochila', tipo: 'PERDIDO' }, { signal: expect.any(AbortSignal) },
    ));
    expect(parametrosActuales().has('categoriaId')).toBe(false);
  });

  it('muestra un estado vacío cuando la búsqueda no coincide', async () => {
    buscarObjetos.mockResolvedValue([]);
    renderizar('/buscar?q=no-existe');
    await screen.findByText('Sin resultados para esa búsqueda.');
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText('0 resultados')).toBeNull();
  });

  it('sin filtros ni publicaciones no pide introducir un texto innecesario', async () => {
    buscarObjetos.mockResolvedValue([]);
    renderizar();
    await screen.findByText('Todavía no hay publicaciones.');
    expect(buscarObjetos).toHaveBeenCalledWith({ q: '' }, { signal: expect.any(AbortSignal) });
  });

  it('un error de búsqueda se distingue de una lista vacía y permite reintentar', async () => {
    const user = userEvent.setup();
    buscarObjetos.mockRejectedValueOnce(new Error('Servicio no disponible'));
    renderizar('/buscar?q=mochila');
    const alerta = await screen.findByRole('alert');
    expect(alerta.textContent).toContain('Servicio no disponible');
    expect(screen.queryByText('Sin resultados para esa búsqueda.')).toBeNull();
    expect(screen.queryByText('0 resultados')).toBeNull();
    await user.click(within(alerta).getByRole('button', { name: 'Reintentar' }));
    await screen.findByRole('heading', { name: 'Mochila negra' });
    expect(buscarObjetos).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('el error de categorías no se pierde al cargar resultados y reintenta el catálogo', async () => {
    const user = userEvent.setup();
    listarCategorias.mockRejectedValueOnce(new Error('Catálogo no disponible'));
    renderizar();
    const alerta = await screen.findByRole('alert');
    await screen.findByRole('heading', { name: 'Mochila negra' });
    expect(alerta.textContent).toContain('Catálogo no disponible');
    expect(screen.getByRole('combobox').disabled).toBe(true);
    await user.click(within(alerta).getByRole('button', { name: 'Reintentar' }));
    await categoriasListas();
    expect(listarCategorias).toHaveBeenCalledTimes(2);
    expect(buscarObjetos).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('una respuesta anterior lenta no reemplaza los resultados del filtro nuevo', async () => {
    const user = userEvent.setup();
    const anterior = respuestaPendiente();
    buscarObjetos.mockReturnValueOnce(anterior.promise).mockResolvedValueOnce([celular]);
    renderizar();
    await categoriasListas();
    const signal = buscarObjetos.mock.calls[0][1].signal;
    await user.selectOptions(screen.getByRole('combobox'), '1');
    await screen.findByRole('heading', { name: 'Celular azul' });
    expect(signal.aborted).toBe(true);
    await act(async () => anterior.resolve([mochila]));
    expect(screen.queryByRole('heading', { name: 'Mochila negra' })).toBeNull();
    expect(screen.getByRole('heading', { name: 'Celular azul' })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Resultados de búsqueda' }).getAttribute('aria-busy')).toBe('false');
  });

  it('un error tardío de la consulta cancelada tampoco oculta resultados válidos', async () => {
    const user = userEvent.setup();
    const anterior = respuestaPendiente();
    buscarObjetos.mockReturnValueOnce(anterior.promise).mockResolvedValueOnce([celular]);
    renderizar();
    await categoriasListas();
    await user.selectOptions(screen.getByRole('combobox'), '1');
    await screen.findByRole('heading', { name: 'Celular azul' });
    await act(async () => anterior.reject(new Error('Fallo anterior')));
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Celular azul' })).toBeTruthy();
  });

  it('al salir de la página cancela tanto resultados como categorías pendientes', async () => {
    const objetos = respuestaPendiente();
    const cats = respuestaPendiente();
    buscarObjetos.mockReturnValue(objetos.promise);
    listarCategorias.mockReturnValue(cats.promise);
    const { unmount } = renderizar();
    const signalObjetos = buscarObjetos.mock.calls[0][1].signal;
    const signalCategorias = listarCategorias.mock.calls[0][0].signal;
    unmount();
    expect(signalObjetos.aborted).toBe(true);
    expect(signalCategorias.aborted).toBe(true);
    await act(async () => { objetos.resolve([mochila]); cats.resolve(categorias); });
  });

  it('Atrás y Adelante restauran los filtros y el texto del formulario', async () => {
    const user = userEvent.setup();
    renderizar('/buscar?q=mochila&categoriaId=2');
    await categoriasListas();
    await user.clear(screen.getByRole('searchbox'));
    await user.type(screen.getByRole('searchbox'), 'celular');
    await user.click(screen.getByRole('button', { name: 'Buscar', exact: true }));
    await waitFor(() => expect(parametrosActuales().get('q')).toBe('celular'));
    await user.selectOptions(screen.getByRole('combobox'), '1');
    await user.click(screen.getByRole('button', { name: 'Atrás de prueba' }));
    expect(screen.getByRole('searchbox').value).toBe('celular');
    expect(screen.getByRole('combobox').value).toBe('2');
    await user.click(screen.getByRole('button', { name: 'Atrás de prueba' }));
    expect(screen.getByRole('searchbox').value).toBe('mochila');
    await user.click(screen.getByRole('button', { name: 'Adelante de prueba' }));
    expect(screen.getByRole('searchbox').value).toBe('celular');
    await waitFor(() => expect(buscarObjetos).toHaveBeenLastCalledWith(
      { q: 'celular', categoriaId: '2' }, { signal: expect.any(AbortSignal) },
    ));
  });

  it('volver a pulsar Buscar con el mismo texto actualiza los resultados', async () => {
    const user = userEvent.setup();
    renderizar('/buscar?q=mochila');
    await screen.findByRole('heading', { name: 'Mochila negra' });
    await user.click(screen.getByRole('button', { name: 'Buscar', exact: true }));
    await waitFor(() => expect(buscarObjetos).toHaveBeenCalledTimes(2));
  });

  it('conserva el filtro de ubicación que ya envía la página de inicio', async () => {
    buscarObjetos.mockResolvedValue([mochila, celular]);
    renderizar('/buscar?ubicacion=BIBLIOTECA');
    await screen.findByRole('heading', { name: 'Mochila negra' });
    expect(screen.queryByRole('heading', { name: 'Celular azul' })).toBeNull();
    expect(buscarObjetos).toHaveBeenCalledWith({ q: '' }, { signal: expect.any(AbortSignal) });
  });

  it('funciona con StrictMode sin aceptar la carga inicial cancelada', async () => {
    renderizar('/buscar', true);
    await screen.findByRole('heading', { name: 'Mochila negra' });
    expect(buscarObjetos.mock.calls[0][1].signal.aborted).toBe(true);
    expect(buscarObjetos.mock.lastCall[1].signal.aborted).toBe(false);
    expect(listarCategorias.mock.calls[0][0].signal.aborted).toBe(true);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('permite abrir el detalle desde la tarjeta de un resultado', async () => {
    const user = userEvent.setup();
    renderizar();
    const link = await screen.findByRole('link', { name: /Mochila negra/ });
    expect(link.getAttribute('href')).toBe('/objeto/10');
    await user.click(link);
    await screen.findByText('Detalle del objeto');
  });
});
