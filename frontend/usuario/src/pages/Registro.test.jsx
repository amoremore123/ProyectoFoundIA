import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Registro from './Registro';
import { AuthProvider } from '../context/AuthContext';
import { getDocumentosLegales, register } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  getDocumentosLegales: vi.fn(),
  register: vi.fn(),
}));

const DOCUMENTOS = {
  version: 'foundia-tecsup-2026-11-05-v2',
  estado: 'FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA',
  aviso: 'La casilla solo acepta condiciones de FoundIA, no autoriza publicidad ni acredita aprobación institucional.',
  terminos: '# Términos de uso de FoundIA\n## Alcance\nTexto de términos.',
  privacidad: '# Política de privacidad de FoundIA\nTexto de privacidad.',
  sha256: 'abc',
  fuentes: [
    { id: 'terminos-tecsup', titulo: 'Términos y Condiciones de Tecsup', url: 'https://www.tecsup.edu.pe/terminos-y-condiciones/', consultadoEl: '2026-11-05' },
    { id: 'privacidad-tecsup', titulo: 'Política para el Tratamiento de Datos Personales de Tecsup', url: 'https://www.tecsup.edu.pe/wp-content/uploads/2024/08/politica-tratamiento-personales-tecsup.pdf', consultadoEl: '2026-11-05' },
  ],
};

function renderizar() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider><Registro /></AuthProvider>
    </MemoryRouter>,
  );
}

function llenarFormulario() {
  fireEvent.change(screen.getByLabelText(/^Nombre/), { target: { value: 'Ana' } });
  fireEvent.change(screen.getByLabelText(/^Apellido/), { target: { value: 'Pérez' } });
  fireEvent.change(screen.getByLabelText(/^Correo/), { target: { value: 'Ana@Tecsup.edu.pe' } });
  fireEvent.change(screen.getByLabelText(/^Contraseña/), { target: { value: 'Segura123!' } });
  fireEvent.change(screen.getByLabelText(/^Confirmar contraseña/), { target: { value: 'Segura123!' } });
}

const casilla = () => screen.getByRole('checkbox', { name: /He leído y acepto/ });
const botonRegistrar = () => screen.getByRole('button', { name: /Registrarme|Creando cuenta/ });

beforeEach(() => {
  localStorage.clear();
  getDocumentosLegales.mockReset();
  register.mockReset();
});
afterEach(cleanup);

describe('H33 - Aceptar términos y privacidad en el registro web', () => {
  it('muestra los documentos con su versión y la casilla empieza desmarcada', async () => {
    getDocumentosLegales.mockResolvedValueOnce(DOCUMENTOS);
    renderizar();

    expect(botonRegistrar().disabled).toBe(true); // mientras cargan los documentos
    await screen.findByText(/Versión/);
    expect(screen.getByText(DOCUMENTOS.version)).toBeTruthy();
    expect(screen.getByText('Términos de uso de FoundIA')).toBeTruthy();
    expect(screen.getByText('Política de privacidad de FoundIA')).toBeTruthy();
    expect(casilla().checked).toBe(false);
    expect(botonRegistrar().disabled).toBe(false);
  });

  it('no envía el registro si no se aceptan los términos', async () => {
    getDocumentosLegales.mockResolvedValueOnce(DOCUMENTOS);
    renderizar();
    await screen.findByText(/Versión/);
    llenarFormulario();

    await act(async () => { fireEvent.click(botonRegistrar()); });

    expect(register).not.toHaveBeenCalled();
    expect(screen.getByText(/Debes aceptar los términos/)).toBeTruthy();
  });

  it('envía la aceptación expresa y la versión presentada', async () => {
    getDocumentosLegales.mockResolvedValueOnce(DOCUMENTOS);
    register.mockResolvedValueOnce({ mensaje: 'ok', correo: 'ana@tecsup.edu.pe' });
    renderizar();
    await screen.findByText(/Versión/);
    llenarFormulario();
    fireEvent.click(casilla());

    await act(async () => { fireEvent.click(botonRegistrar()); });

    expect(register).toHaveBeenCalledWith({
      nombre: 'Ana',
      apellido: 'Pérez',
      correo: 'ana@tecsup.edu.pe',
      password: 'Segura123!',
      aceptaTerminos: true,
      versionLegal: DOCUMENTOS.version,
    });
  });

  it('presenta fuentes oficiales externas sin convertir su lectura en aceptación ni publicidad', async () => {
    getDocumentosLegales.mockResolvedValueOnce(DOCUMENTOS);
    renderizar();
    await screen.findByText('Fuentes oficiales de Tecsup');
    for (const fuente of DOCUMENTOS.fuentes) {
      const link = screen.getByRole('link', { name: `${fuente.titulo} (sitio externo)` });
      expect(link.getAttribute('href')).toBe(fuente.url);
      expect(link.getAttribute('target')).toBe('_blank');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
      expect(link.getAttribute('referrerpolicy')).toBe('no-referrer');
    }
    expect(screen.getByText(/La casilla solo acepta condiciones de FoundIA/)).toBeTruthy();
    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    expect(casilla().checked).toBe(false);
    expect(register).not.toHaveBeenCalled();
  });

  it('no ofrece enlaces que no pertenezcan al sitio HTTPS oficial', async () => {
    getDocumentosLegales.mockResolvedValueOnce({ ...DOCUMENTOS, fuentes: [
      { id: 'invalida', titulo: 'Fuente manipulada', url: 'javascript:alert(1)' },
      { id: 'otro-host', titulo: 'Otro sitio', url: 'https://www.tecsup.edu.pe.ejemplo.invalid/' },
    ] });
    renderizar();
    await screen.findByText('Fuentes oficiales de Tecsup');
    expect(screen.queryByRole('link', { name: /Fuente manipulada|Otro sitio/ })).toBeNull();
    expect(casilla().checked).toBe(false);
  });

  it('si los documentos no cargan, no permite registrarse y deja reintentar', async () => {
    getDocumentosLegales.mockRejectedValueOnce(new Error('Network Error'));
    getDocumentosLegales.mockResolvedValueOnce(DOCUMENTOS);
    renderizar();

    await screen.findByText(/No se pudieron cargar los documentos/);
    expect(botonRegistrar().disabled).toBe(true);
    expect(screen.queryByRole('checkbox')).toBeNull();

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reintentar' })); });
    await screen.findByText(/Versión/);
    expect(botonRegistrar().disabled).toBe(false);
  });

  it('si la versión cambió, recarga los documentos y vuelve a pedir la aceptación', async () => {
    getDocumentosLegales
      .mockResolvedValueOnce(DOCUMENTOS)
      .mockResolvedValueOnce({ ...DOCUMENTOS, version: 'foundia-tecsup-2026-11-06-v3' });
    register.mockRejectedValueOnce({ response: { status: 400, data: {
      mensaje: 'La versión de los documentos cambió. Léelos de nuevo antes de registrarte.',
    } } });
    renderizar();
    await screen.findByText(/Versión/);
    llenarFormulario();
    fireEvent.click(casilla());

    await act(async () => { fireEvent.click(botonRegistrar()); });

    await waitFor(() => expect(screen.getByText('foundia-tecsup-2026-11-06-v3')).toBeTruthy());
    expect(getDocumentosLegales).toHaveBeenCalledTimes(2);
    expect(casilla().checked).toBe(false);
    expect(screen.getByRole('alert').textContent).toContain('La versión de los documentos cambió');
  });
});
