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

describe('H52 - Cámara dentro de la página (sin abrir la app de cámara)', () => {
  function simularCamara({ falla } = {}) {
    const detener = vi.fn();
    const stream = { getTracks: () => [{ stop: detener }] };
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: true });
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(() => (falla ? Promise.reject(Object.assign(new Error('x'), { name: falla })) : Promise.resolve(stream))) },
    });
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLVideoElement.prototype, 'videoWidth', 'get').mockReturnValue(1280);
    vi.spyOn(HTMLVideoElement.prototype, 'videoHeight', 'get').mockReturnValue(960);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() });
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb) => cb(new Blob(['jpg'], { type: 'image/jpeg' })));
    URL.createObjectURL = vi.fn(() => 'blob:captura');
    URL.revokeObjectURL = vi.fn();
    return { detener };
  }

  afterEach(() => {
    delete window.isSecureContext;
    delete navigator.mediaDevices;
  });

  it('captura, permite repetir y usa la foto sin salir de la página', async () => {
    const { detener } = simularCamara();
    const user = userEvent.setup();
    renderizar();
    // Con HTTPS «Tomar foto» es un botón que abre la cámara aquí mismo (no una entrada de archivo)
    await user.click(screen.getByRole('button', { name: 'Tomar foto' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Tomar foto' });
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(expect.objectContaining({ audio: false }));
    const disparador = await screen.findByRole('button', { name: 'Capturar foto' });
    await waitFor(() => expect(disparador.disabled).toBe(false));

    await user.click(disparador);
    expect(screen.getByRole('img', { name: 'Foto tomada, lista para revisar' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Repetir' }));
    await user.click(screen.getByRole('button', { name: 'Capturar foto' }));
    await user.click(screen.getByRole('button', { name: 'Usar esta foto' }));

    expect(dialogo.isConnected).toBe(false);
    expect(await screen.findByText(/^Seleccionada: foto-\d+\.jpg$/)).toBeTruthy();
    expect(detener).toHaveBeenCalled(); // la cámara se apaga al terminar
  });

  it('si se niega el permiso, explica cómo seguir y deja elegir de la galería', async () => {
    simularCamara({ falla: 'NotAllowedError' });
    const user = userEvent.setup();
    renderizar();
    await user.click(screen.getByRole('button', { name: 'Tomar foto' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/No diste permiso para usar la cámara/);
    await user.click(screen.getByRole('button', { name: 'Volver al formulario' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByLabelText('Elegir archivo')).toBeTruthy();
  });

  it('cerrar con Escape apaga la cámara', async () => {
    const { detener } = simularCamara();
    const user = userEvent.setup();
    renderizar();
    await user.click(screen.getByRole('button', { name: 'Tomar foto' }));
    await screen.findByRole('dialog', { name: 'Tomar foto' });
    await waitFor(() => expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled());
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    await waitFor(() => expect(detener).toHaveBeenCalled());
  });
});

describe('H52 - La cámara nunca ignora un toque en silencio', () => {
  it('si el video aún no tiene imagen, avisa que la cámara se está preparando', async () => {
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: true });
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [{ stop: vi.fn() }] })) },
    });
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLVideoElement.prototype, 'videoWidth', 'get').mockReturnValue(0);
    const user = userEvent.setup();
    renderizar();
    await user.click(screen.getByRole('button', { name: 'Tomar foto' }));
    const disparador = await screen.findByRole('button', { name: 'Capturar foto' });
    await waitFor(() => expect(disparador.disabled).toBe(false));
    await user.click(disparador);
    expect((await screen.findByRole('alert')).textContent).toMatch(/todavía se está preparando/);
    delete window.isSecureContext;
    delete navigator.mediaDevices;
  });
});
