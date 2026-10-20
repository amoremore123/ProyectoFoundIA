import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import Detalle from './Detalle';
import { getCoincidencias, getObjeto, iniciarContacto } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getCoincidencias: vi.fn(),
  getObjeto: vi.fn(),
  iniciarContacto: vi.fn(),
}));

const objetoPerdido = {
  id: 15,
  nombre: 'Mochila extraviada',
  descripcion: 'Mochila azul',
  ubicacion: 'Biblioteca',
  fechaObjeto: '2026-10-18',
  tipo: 'PERDIDO',
  estado: 'ACTIVO',
  categoria: { id: 3, nombre: 'Mochila' },
  publicadoPor: { id: 1, nombre: 'Amalia', apellido: 'A.' },
  fotos: [],
};

const encontrado = {
  objetoId: 27,
  nombre: 'Mochila encontrada',
  tipo: 'ENCONTRADO',
  categoria: 'Mochila',
  ubicacion: 'Biblioteca',
  fechaObjeto: '2026-10-18',
  porcentaje: 90,
  nivel: 'ALTA',
};

function renderizar(conToken) {
  localStorage.clear();
  if (conToken) {
    localStorage.setItem('token', 'token-prueba');
    localStorage.setItem('usuario', JSON.stringify({ id: 1, nombre: 'Amalia' }));
  }
  return render(
    <MemoryRouter initialEntries={['/objeto/15']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <Routes>
          <Route path="/objeto/:id" element={<Detalle />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  getObjeto.mockResolvedValue(objetoPerdido);
  getCoincidencias.mockResolvedValue([encontrado]);
  iniciarContacto.mockResolvedValue({ id: 4, estado: 'PENDIENTE', repetido: false });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe('H18 - contacto desde coincidencia de un objeto propio', () => {
  it('permite enviar contacto al dueño del hallazgo sin mostrar datos personales', async () => {
    const conToken = renderizar(true);
    await screen.findByRole('heading', { name: 'Mochila encontrada' });
    expect(screen.getByText('Amalia A.')).toBeTruthy();
    expect(screen.queryByText(/@/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Contactar' }));
    expect(await screen.findByText('Solicitud de contacto enviada.')).toBeTruthy();
    expect(iniciarContacto).toHaveBeenCalledWith(objetoPerdido.id, encontrado.objetoId);
    expect(screen.queryByRole('button', { name: 'Contactar' })).toBeNull();
    conToken.unmount();
  });

  it('no ofrece iniciar contacto a visitantes sin sesión', async () => {
    renderizar(false);
    await screen.findByRole('heading', { name: 'Mochila encontrada' });
    expect(screen.queryByRole('button', { name: 'Contactar' })).toBeNull();
    expect(iniciarContacto).not.toHaveBeenCalled();
  });

  it('si falla conserva el botón y permite reintentar', async () => {
    iniciarContacto.mockRejectedValueOnce(new Error('Sin conexión'));
    renderizar(true);
    await screen.findByRole('heading', { name: 'Mochila encontrada' });

    fireEvent.click(screen.getByRole('button', { name: 'Contactar' }));
    expect(await screen.findByRole('alert')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Contactar' }));
    expect(await screen.findByText('Solicitud de contacto enviada.')).toBeTruthy();
    await waitFor(() => expect(iniciarContacto).toHaveBeenCalledTimes(2));
  });
});
