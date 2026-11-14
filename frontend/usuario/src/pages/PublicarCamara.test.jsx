import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Publicar from './Publicar';
import { crearObjeto, listarCategorias, subirFotoObjeto } from '../services/api';
import { CLAVE_BORRADOR } from '../utils/borradorPublicar';
import { reducirFoto } from '../utils/fotos';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), crearObjeto: vi.fn(), listarCategorias: vi.fn(), subirFotoObjeto: vi.fn(),
}));
vi.mock('../services/geolocalizacion', () => ({ obtenerUbicacionActual: vi.fn() }));

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

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 3, nombre: 'Mochila', estado: true }]);
  crearObjeto.mockResolvedValue({ id: 55 });
  subirFotoObjeto.mockResolvedValue({ id: 99, url: '/uploads/foto.jpg' });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('H52 - Volver de la cámara sin perder la publicación', () => {
  it('si el navegador recarga la página, recupera lo escrito y avisa que se vuelva a elegir la foto', async () => {
    const user = userEvent.setup();
    const primera = renderizar();
    await user.type(screen.getByRole('textbox', { name: /^Nombre/ }), 'Casaca blanca');
    await user.click(screen.getByRole('button', { name: /Encontré un objeto/ }));
    expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR))).toMatchObject({ tipo: 'ENCONTRADO', form: { nombre: 'Casaca blanca' } });

    // Android cierra la pestaña al abrir la cámara: al volver, la página se monta otra vez
    primera.unmount();
    renderizar();
    expect(screen.getByRole('textbox', { name: /^Nombre/ }).value).toBe('Casaca blanca');
    expect(screen.getByText(/Recuperamos lo que estabas escribiendo/)).toBeTruthy();
    expect(screen.getByRole('button', { name: /Encontré un objeto/ }).getAttribute('aria-pressed')).toBe('true');

    await user.click(screen.getByRole('button', { name: 'Empezar de nuevo' }));
    expect(screen.getByRole('textbox', { name: /^Nombre/ }).value).toBe('');
    expect(sessionStorage.getItem(CLAVE_BORRADOR)).toBeNull();
  });

  it('sin nada escrito no ofrece recuperar ni guarda un borrador vacío', () => {
    renderizar();
    expect(screen.queryByText(/Recuperamos lo que estabas escribiendo/)).toBeNull();
    expect(sessionStorage.getItem(CLAVE_BORRADOR)).toBeNull();
  });

  it('al publicar se borra el borrador para no duplicar la publicación', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.type(screen.getByRole('textbox', { name: /^Nombre/ }), 'Mochila de prueba');
    await user.type(screen.getByRole('textbox', { name: /^Descripción/ }), 'Objeto ficticio');
    await screen.findByRole('option', { name: 'Mochila' });
    await user.selectOptions(screen.getByRole('combobox', { name: /^Categoría/ }), '3');
    await user.type(screen.getByRole('textbox', { name: /^Ubicación/ }), 'Biblioteca central');
    const hoy = new Date();
    fireEvent.change(screen.getByLabelText(/^Fecha/), {
      target: { value: `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}` },
    });
    expect(sessionStorage.getItem(CLAVE_BORRADOR)).not.toBeNull();
    await user.click(screen.getByRole('button', { name: /publicar objeto/i }));
    expect(await screen.findByText('Detalle de prueba')).toBeTruthy();
    expect(sessionStorage.getItem(CLAVE_BORRADOR)).toBeNull();
  });
});

describe('H52 - Reducir la foto de la cámara antes de subirla', () => {
  function simularNavegadorConCanvas({ ancho, alto }) {
    const cerrar = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: ancho, height: alto, close: cerrar })));
    const dibujar = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: dibujar });
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function toBlob(cb) {
      cb(new Blob([new Uint8Array(800 * 1024)], { type: 'image/jpeg' }));
    });
    return { cerrar, dibujar };
  }

  it('convierte una foto grande de la cámara en un JPEG de máximo 1920 px', async () => {
    const { cerrar, dibujar } = simularNavegadorConCanvas({ ancho: 4000, alto: 3000 });
    const original = new File([new Uint8Array(7 * 1024 * 1024)], 'IMG_2026.jpg', { type: 'image/jpeg' });
    const reducida = await reducirFoto(original);
    expect(reducida.type).toBe('image/jpeg');
    expect(reducida.name).toBe('IMG_2026.jpg');
    expect(reducida.size).toBeLessThan(5 * 1024 * 1024);
    expect(dibujar).toHaveBeenCalledWith(expect.anything(), 0, 0, 1920, 1440);
    expect(cerrar).toHaveBeenCalled();
  });

  it('deja intacta una foto liviana y pequeña', async () => {
    simularNavegadorConCanvas({ ancho: 800, alto: 600 });
    const original = new File([new Uint8Array(200 * 1024)], 'chica.png', { type: 'image/png' });
    expect(await reducirFoto(original)).toBe(original);
  });

  it('si el navegador no puede leer la imagen, devuelve el original', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn(async () => { throw new Error('formato no soportado'); }));
    const original = new File(['x'], 'foto.heic', { type: 'image/heic' });
    expect(await reducirFoto(original)).toBe(original);
  });

  it('en Publicar, una foto de 7 MB de la cámara se reduce y se puede publicar', async () => {
    simularNavegadorConCanvas({ ancho: 4000, alto: 3000 });
    renderizar();
    const camara = screen.getByLabelText('Tomar foto');
    fireEvent.change(camara, { target: { files: [new File([new Uint8Array(7 * 1024 * 1024)], 'IMG_2026.jpg', { type: 'image/jpeg' })] } });
    expect(await screen.findByText('Seleccionada: IMG_2026.jpg')).toBeTruthy();
    await waitFor(() => expect(screen.queryByText(/no debe superar los 5 MiB/)).toBeNull());
  });
});
