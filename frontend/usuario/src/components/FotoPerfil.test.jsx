import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FotoPerfil, { validarFotoPerfil } from './FotoPerfil';
import { getFotoPerfil, quitarFotoPerfil, subirFotoPerfil } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getFotoPerfil: vi.fn(), subirFotoPerfil: vi.fn(), quitarFotoPerfil: vi.fn(),
}));
// En jsdom no hay decodificador de imágenes: el archivo se sube tal cual
vi.mock('../utils/fotos', () => ({ reducirFoto: vi.fn(async (archivo) => archivo) }));

const foto = (tipo = 'image/jpeg', bytes = 2048, nombre = 'yo.jpg') =>
  new File([new Uint8Array(bytes)], nombre, { type: tipo });

beforeEach(() => {
  vi.resetAllMocks();
  URL.createObjectURL = vi.fn(() => 'blob:foto-perfil');
  URL.revokeObjectURL = vi.fn();
});
afterEach(cleanup);

const elegir = (archivo) => fireEvent.change(document.getElementById('foto-perfil-archivo'), { target: { files: [archivo] } });

describe('H64 - foto de perfil en la web', () => {
  it('sin foto muestra la inicial, ofrece subirla y explica quién la ve', () => {
    render(<FotoPerfil fotoPerfilUrl={null} iniciales="A" />);
    expect(screen.getByText('A')).toBeTruthy();
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByRole('button', { name: /Subir foto/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Quitar foto' })).toBeNull();
    expect(screen.getByText(/Solo tú ves tu foto: no aparece en tus publicaciones/)).toBeTruthy();
    expect(getFotoPerfil).not.toHaveBeenCalled();
  });

  it('con foto la descarga con la sesión y la muestra (no usa la ruta pública)', async () => {
    getFotoPerfil.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
    render(<FotoPerfil fotoPerfilUrl="/api/perfil/foto?v=abc" iniciales="A" />);
    const img = await screen.findByRole('img', { name: 'Tu foto de perfil' });
    expect(img.getAttribute('src')).toBe('blob:foto-perfil');
    expect(getFotoPerfil).toHaveBeenCalledWith('/api/perfil/foto?v=abc');
    expect(screen.getByRole('button', { name: /Cambiar foto/ })).toBeTruthy();
  });

  it('sube una foto válida y la muestra con la nueva versión', async () => {
    subirFotoPerfil.mockResolvedValue({ fotoPerfilUrl: '/api/perfil/foto?v=nueva' });
    getFotoPerfil.mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
    render(<FotoPerfil fotoPerfilUrl={null} iniciales="A" />);
    await act(async () => { elegir(foto('image/png', 1000, 'yo.png')); });
    expect(subirFotoPerfil).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('img', { name: 'Tu foto de perfil' })).toBeTruthy();
    expect(getFotoPerfil).toHaveBeenCalledWith('/api/perfil/foto?v=nueva');
    expect(screen.getByRole('status').textContent).toMatch(/Foto de perfil actualizada/);
  });

  it('un archivo que no es imagen o de más de 5 MiB muestra el error y no se envía', async () => {
    render(<FotoPerfil fotoPerfilUrl={null} iniciales="A" />);
    await act(async () => { elegir(foto('application/pdf', 100, 'cv.pdf')); });
    expect(screen.getByRole('alert').textContent).toBe('La foto debe ser JPEG o PNG.');
    await act(async () => { elegir(foto('image/jpeg', 5 * 1024 * 1024 + 1)); });
    expect(screen.getByRole('alert').textContent).toBe('La foto no debe superar los 5 MiB.');
    expect(subirFotoPerfil).not.toHaveBeenCalled();
    expect(validarFotoPerfil(foto())).toBe('');
  });

  it('si el servidor rechaza la foto muestra su mensaje y conserva la anterior', async () => {
    getFotoPerfil.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
    subirFotoPerfil.mockRejectedValue({ response: { status: 400, data: { mensaje: 'La imagen supera los 40 megapíxeles' } } });
    render(<FotoPerfil fotoPerfilUrl="/api/perfil/foto?v=1" iniciales="A" />);
    await screen.findByRole('img', { name: 'Tu foto de perfil' });
    await act(async () => { elegir(foto()); });
    expect(screen.getByRole('alert').textContent).toMatch(/40 megapíxeles/);
    expect(screen.getByRole('img', { name: 'Tu foto de perfil' })).toBeTruthy();
  });

  it('quitar pide confirmación y vuelve a la inicial', async () => {
    getFotoPerfil.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
    quitarFotoPerfil.mockResolvedValue();
    render(<FotoPerfil fotoPerfilUrl="/api/perfil/foto?v=1" iniciales="A" />);
    await screen.findByRole('img', { name: 'Tu foto de perfil' });
    fireEvent.click(screen.getByRole('button', { name: 'Quitar foto' }));
    expect(quitarFotoPerfil).not.toHaveBeenCalled();
    await act(async () => { fireEvent.click(screen.getAllByRole('button', { name: 'Quitar foto' }).at(-1)); });
    expect(quitarFotoPerfil).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByRole('img')).toBeNull());
    expect(screen.getByText('A')).toBeTruthy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto-perfil');
  });
});
