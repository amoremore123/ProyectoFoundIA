import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
});
