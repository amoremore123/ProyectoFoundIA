import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChatAsistente from './ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import { buscarObjetos, buscarPorFoto, listarCategorias } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  buscarObjetos: vi.fn(), buscarPorFoto: vi.fn(), getMisObjetos: vi.fn(), obtenerVozFindi: vi.fn(), interpretarConIA: vi.fn(), listarCategorias: vi.fn(),
}));

function Destino() {
  const { state } = useLocation();
  return <p>Publicar: {state?.fotoAdjunta ? `con foto ${state.fotoAdjunta.name}` : 'sin foto'}</p>;
}

function renderizar() {
  const vista = render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AsistenteProvider>
        <Routes>
          <Route path="/" element={<ChatAsistente />} />
          <Route path="/publicar" element={<Destino />} />
        </Routes>
      </AsistenteProvider>
    </MemoryRouter>,
  );
  return vista;
}

const foto = () => new File(['jpg'], 'lentes.jpg', { type: 'image/jpeg' });

async function enviarFoto(user, vista) {
  await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
  await user.click(screen.getByRole('button', { name: 'Buscar con una foto' }));
  // Antes de la primera foto se avisa que va al proveedor de IA y que no se guarda
  const aviso = screen.getByRole('alertdialog', { name: 'Buscar con una foto' });
  expect(within(aviso).getByText(/No se guarda ni se publica/)).toBeTruthy();
  await user.click(within(aviso).getByRole('button', { name: 'Entendido, continuar' }));
  // Sin HTTPS (jsdom) se usa la galería: la entrada de archivo oculta del chat
  const entrada = vista.container.ownerDocument.querySelector('.chat-entrada input[type="file"]');
  fireEvent.change(entrada, { target: { files: [foto()] } });
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 6, nombre: 'Accesorio', descripcion: 'Relojes, gafas, joyas y otros accesorios', estado: true }]);
  buscarObjetos.mockResolvedValue([
    { id: 13, nombre: 'Lentes de sol', descripcion: 'Lentes marrones', ubicacion: 'Laboratorio', tipo: 'ENCONTRADO', estado: 'ACTIVO', categoria: { id: 6, nombre: 'Accesorio' }, fotos: [] },
    { id: 2, nombre: 'Celular Samsung', descripcion: 'Azul', ubicacion: 'Biblioteca', tipo: 'PERDIDO', estado: 'ACTIVO', categoria: null, fotos: [] },
  ]);
  URL.createObjectURL = vi.fn(() => 'blob:vista');
});
afterEach(cleanup);

describe('Buscar con una foto en FINDI', () => {
  it('describe la foto con IA, busca en las publicaciones y permite publicar con esa foto', async () => {
    buscarPorFoto.mockResolvedValue({ descripcion: 'Lentes de sol marrones', categoria: { id: 6, nombre: 'Accesorio' }, palabras: ['lentes', 'sol'], colores: ['marron'] });
    const user = userEvent.setup();
    const vista = renderizar();
    await enviarFoto(user, vista);

    expect(screen.getByRole('img', { name: 'Foto enviada para buscar' })).toBeTruthy();
    expect(await screen.findByText(/En tu foto veo: Lentes de sol marrones\./)).toBeTruthy();
    expect(buscarPorFoto).toHaveBeenCalledWith(expect.stringMatching(/^data:image\/jpeg;base64,/));
    expect(screen.getByRole('link', { name: /Lentes de sol/ }).getAttribute('href')).toBe('/objeto/13');
    expect(screen.getByText('Interpretado con IA')).toBeTruthy();

    await user.click(screen.getByRole('link', { name: /Publicar con esta foto/ }));
    expect(await screen.findByText('Publicar: con foto lentes.jpg')).toBeTruthy();
  });

  it('si la IA no puede analizar la foto, ofrece describirlo o publicarlo con la foto', async () => {
    buscarPorFoto.mockRejectedValue(Object.assign(new Error('503'), { response: { status: 503 } }));
    const user = userEvent.setup();
    const vista = renderizar();
    await enviarFoto(user, vista);
    expect(await screen.findByText(/No pude analizar la foto ahora/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Publicar con esta foto/ })).toBeTruthy();
  });

  it('si no reconoce un objeto, lo dice en vez de mostrar resultados al azar', async () => {
    buscarPorFoto.mockResolvedValue({ descripcion: '', categoria: null, palabras: [], colores: [] });
    const user = userEvent.setup();
    const vista = renderizar();
    await enviarFoto(user, vista);
    expect(await screen.findByText(/No reconocí un objeto en la foto/)).toBeTruthy();
    expect(screen.queryByRole('link', { name: /Lentes de sol/ })).toBeNull();
  });

  it('cancelar el aviso no abre la cámara ni envía nada', async () => {
    const user = userEvent.setup();
    renderizar();
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    await user.click(screen.getByRole('button', { name: 'Buscar con una foto' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    expect(buscarPorFoto).not.toHaveBeenCalled();
  });
});
