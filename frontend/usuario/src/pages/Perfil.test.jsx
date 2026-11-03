import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Perfil from './Perfil';
import { AuthProvider } from '../context/AuthContext';
import { actualizarPerfil, getPerfil } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(), getPerfil: vi.fn(), actualizarPerfil: vi.fn(),
  // H34: el perfil también consulta la solicitud de eliminación
  getSolicitudEliminacion: vi.fn(async () => ({ id: null, estado: 'SIN_SOLICITUD', fechaSolicitud: null, repetido: false, informacion: '' })),
}));

const PERFIL = { id: 1, nombre: 'Ana', apellido: 'Pérez', correo: 'ana@prueba.test', rol: 'USUARIO' };

function renderizar() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider><Perfil /></AuthProvider>
    </MemoryRouter>,
  );
}

const campo = (nombre) => screen.getByLabelText(nombre, { exact: true });
const guardar = () => screen.getByRole('button', { name: 'Guardar cambios' });

async function reemplazar(user, etiqueta, valor) {
  await user.clear(campo(etiqueta));
  if (valor) await user.type(campo(etiqueta), valor);
}

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
  localStorage.setItem('token', 'jwt-de-prueba');
  getPerfil.mockResolvedValue(PERFIL);
  actualizarPerfil.mockImplementation(async (p) => ({ ...PERFIL, nombre: p.nombre, apellido: p.apellido }));
});
afterEach(cleanup);

describe('H13 - Pantalla Perfil', () => {
  it('guarda nombre y apellido válidos sin enviar contraseña y muestra los datos de la API', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByDisplayValue('Ana');

    await reemplazar(user, 'Nombre *', 'Ána María');
    await reemplazar(user, 'Apellido *', 'Núñez');
    await user.click(guardar());

    await waitFor(() => expect(actualizarPerfil).toHaveBeenCalledWith({ nombre: 'Ána María', apellido: 'Núñez' }));
    expect(await screen.findByDisplayValue('Ána María')).toBeTruthy();
    expect(guardar().disabled).toBe(true); // sin cambios pendientes tras guardar
  });

  it('Cancelar restaura los datos cargados y no llama a la API', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByDisplayValue('Ana');

    await reemplazar(user, 'Nombre *', 'Otro');
    await user.type(campo('Nueva contraseña'), 'Algo123!');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(campo('Nombre *').value).toBe('Ana');
    expect(campo('Nueva contraseña').value).toBe('');
    expect(actualizarPerfil).not.toHaveBeenCalled();
  });

  it('muestra los errores junto a cada campo y no envía datos inválidos', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByDisplayValue('Ana');

    await reemplazar(user, 'Nombre *', 'Ana123');
    await reemplazar(user, 'Apellido *', '');
    await user.click(guardar());

    expect(screen.getByText('El nombre solo puede contener letras.')).toBeTruthy();
    expect(screen.getByText('El apellido es obligatorio.')).toBeTruthy();
    expect(campo('Nombre *').getAttribute('aria-invalid')).toBe('true');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
    expect(actualizarPerfil).not.toHaveBeenCalled();
  });

  it('para cambiar la contraseña exige confirmación y la contraseña actual', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByDisplayValue('Ana');

    await user.type(campo('Nueva contraseña'), 'Nueva456#');
    await user.click(guardar());

    expect(screen.getByText('Confirma la nueva contraseña.')).toBeTruthy();
    expect(screen.getByText('Para cambiar la contraseña, escribe tu contraseña actual.')).toBeTruthy();
    expect(actualizarPerfil).not.toHaveBeenCalled();
  });

  it('envía la contraseña nueva con la actual y luego vacía los campos de contraseña', async () => {
    const user = userEvent.setup();
    renderizar();
    await screen.findByDisplayValue('Ana');

    await user.type(campo('Nueva contraseña'), 'Nueva456#');
    await user.type(campo('Confirmar nueva contraseña'), 'Nueva456#');
    await user.type(campo('Contraseña actual'), 'Segura123!');
    await user.click(guardar());

    await waitFor(() => expect(actualizarPerfil).toHaveBeenCalledWith({
      nombre: 'Ana', apellido: 'Pérez', password: 'Nueva456#', passwordActual: 'Segura123!',
    }));
    await waitFor(() => expect(campo('Nueva contraseña').value).toBe(''));
    expect(campo('Contraseña actual').value).toBe('');
    expect(JSON.stringify(localStorage)).not.toContain('Nueva456#');
    expect(JSON.stringify(localStorage)).not.toContain('Segura123!');
  });

  it('si la API rechaza la contraseña actual, el error aparece junto a ese campo y no hay éxito ficticio', async () => {
    const user = userEvent.setup();
    actualizarPerfil.mockRejectedValueOnce({ response: { status: 400, data: {
      mensaje: 'La contraseña actual no es correcta. No se guardaron los cambios.',
    } } });
    renderizar();
    await screen.findByDisplayValue('Ana');

    await user.type(campo('Nueva contraseña'), 'Nueva456#');
    await user.type(campo('Confirmar nueva contraseña'), 'Nueva456#');
    await user.type(campo('Contraseña actual'), 'Equivocada1!');
    await user.click(guardar());

    const error = await screen.findByText('La contraseña actual no es correcta. No se guardaron los cambios.');
    expect(error.id).toBe('error-passwordActual');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
    expect(campo('Nueva contraseña').value).toBe('Nueva456#'); // el usuario no pierde lo escrito
  });

  it('un fallo del servidor al guardar se muestra sin Reintentar y sin éxito', async () => {
    const user = userEvent.setup();
    actualizarPerfil.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    renderizar();
    await screen.findByDisplayValue('Ana');

    await reemplazar(user, 'Apellido *', 'Ruiz');
    await user.click(guardar());

    expect(await screen.findByText(/No se pudo actualizar el perfil \(error 500\)/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
    expect(campo('Apellido *').value).toBe('Ruiz');
  });

  it('si falla la carga muestra Reintentar y recupera el perfil', async () => {
    const user = userEvent.setup();
    getPerfil.mockRejectedValueOnce(Object.assign(new Error('Network Error'), { isAxiosError: true }));
    renderizar();

    expect(await screen.findByText(/No se pudo cargar tu perfil/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByDisplayValue('Ana')).toBeTruthy();
  });
});
