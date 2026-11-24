import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import Etiqueta from './Etiqueta';
import MisEtiquetas from '../components/MisEtiquetas';
import {
  avisarEtiqueta, crearEtiqueta, desactivarEtiqueta, getEtiquetaPublica, getEtiquetas,
} from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getEtiquetas: vi.fn(),
  crearEtiqueta: vi.fn(),
  desactivarEtiqueta: vi.fn(),
  getEtiquetaPublica: vi.fn(),
  avisarEtiqueta: vi.fn(),
}));
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async () => 'data:image/png;base64,QR'), toCanvas: vi.fn() } }));

const FUTURO = { v7_startTransition: true, v7_relativeSplatPath: true };

function Ubicacion() {
  const location = useLocation();
  return <p data-testid="ruta">{location.pathname}|{location.state?.volverA || ''}</p>;
}

function abrirEtiqueta(conSesion) {
  localStorage.clear();
  if (conSesion) {
    localStorage.setItem('token', 'token-prueba');
    localStorage.setItem('usuario', JSON.stringify({ id: 7, nombre: 'Luis' }));
  }
  return render(
    <MemoryRouter initialEntries={['/etiqueta/abcdefghijklmnopqrstuv']} future={FUTURO}>
      <AuthProvider>
        <Routes>
          <Route path="/etiqueta/:codigo" element={<Etiqueta />} />
          <Route path="*" element={<Ubicacion />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  getEtiquetaPublica.mockResolvedValue({ nombre: 'Calculadora Casio', descripcion: 'Sticker de gato', propia: false });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe('Etiqueta QR: lo que ve quien la escanea', () => {
  it('muestra solo el nombre y la descripción, y con sesión avisa al dueño y abre el chat', async () => {
    avisarEtiqueta.mockResolvedValue({ id: 41 });
    abrirEtiqueta(true);
    expect(await screen.findByRole('heading', { name: 'Calculadora Casio' })).toBeTruthy();
    expect(screen.getByText('Sticker de gato')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Avisar a su dueño/ }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/dónde lo encontraste/);
    expect(avisarEtiqueta).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Mensaje para su dueño'), { target: { value: 'Está en la biblioteca' } });
    fireEvent.click(screen.getByRole('button', { name: /Avisar a su dueño/ }));
    await waitFor(() => expect(screen.getByTestId('ruta').textContent).toBe('/mensajes/41|'));
    expect(avisarEtiqueta).toHaveBeenCalledWith('abcdefghijklmnopqrstuv', 'Está en la biblioteca');
  });

  it('sin sesión pide iniciar sesión y vuelve a la etiqueta después', async () => {
    abrirEtiqueta(false);
    fireEvent.click(await screen.findByRole('link', { name: /Inicia sesión para avisar/ }));
    expect(screen.getByTestId('ruta').textContent).toBe('/login|/etiqueta/abcdefghijklmnopqrstuv');
    expect(screen.getByTestId('ruta')).toBeTruthy();
  });

  it('si es mía lo indica y no ofrece avisar; si está desactivada lo explica', async () => {
    getEtiquetaPublica.mockResolvedValue({ nombre: 'USB', descripcion: null, propia: true });
    abrirEtiqueta(true);
    expect(await screen.findByText(/Esta etiqueta es tuya/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Avisar/ })).toBeNull();
    cleanup();

    getEtiquetaPublica.mockRejectedValue({ response: { status: 404 } });
    abrirEtiqueta(false);
    expect(await screen.findByRole('heading', { name: 'Esta etiqueta ya no está activa' })).toBeTruthy();
  });
});

describe('Etiqueta QR: mis etiquetas en el perfil', () => {
  const renderizar = () => render(<MemoryRouter future={FUTURO}><MisEtiquetas /></MemoryRouter>);

  it('crea una etiqueta con su QR y la desactiva con confirmación', async () => {
    getEtiquetas.mockResolvedValue([]);
    crearEtiqueta.mockResolvedValue({ id: 3, nombre: 'Casaca azul', descripcion: null, codigo: 'abcdefghijklmnopqrstuv' });
    desactivarEtiqueta.mockResolvedValue();
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: 'Crear etiqueta' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/qué cosa/);
    expect(crearEtiqueta).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('¿Qué cosa es?'), { target: { value: '  Casaca azul ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear etiqueta' }));
    expect(await screen.findByAltText('Código QR de Casaca azul')).toBeTruthy();
    expect(crearEtiqueta).toHaveBeenCalledWith({ nombre: 'Casaca azul', descripcion: '' });
    expect(screen.getByRole('status').textContent).toMatch(/Casaca azul/);

    fireEvent.click(screen.getByRole('button', { name: 'Desactivar' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Sí, desactivar' }));
    await waitFor(() => expect(screen.queryByAltText('Código QR de Casaca azul')).toBeNull());
    expect(desactivarEtiqueta).toHaveBeenCalledWith(3);
    expect(screen.getByRole('status').textContent).toMatch(/ya no funciona/);
  });

  it('si no carga lo dice y permite reintentar', async () => {
    getEtiquetas.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValueOnce([
      { id: 1, nombre: 'USB', descripcion: 'Rojo', codigo: 'abcdefghijklmnopqrstuv' },
    ]);
    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('USB')).toBeTruthy();
  });
});
