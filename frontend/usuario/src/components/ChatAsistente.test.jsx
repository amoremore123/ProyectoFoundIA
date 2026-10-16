import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChatAsistente from './ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import { buscarObjetos, interpretarConIA, listarCategorias } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  buscarObjetos: vi.fn(),
  interpretarConIA: vi.fn(),
  listarCategorias: vi.fn(),
}));

const categorias = [
  { id: 1, nombre: 'Celular', descripcion: 'Teléfonos inteligentes y celulares', estado: true },
  { id: 3, nombre: 'Mochila', descripcion: 'Mochilas y bolsos', estado: true },
];

const objetos = [
  { id: 1, nombre: 'Mochila negra con libros', descripcion: 'Mochila negra.', ubicacion: 'Comedor', tipo: 'ENCONTRADO', estado: 'ACTIVO', fechaObjeto: '2026-10-02', categoria: { id: 3, nombre: 'Mochila' } },
  { id: 2, nombre: 'Celular Samsung Galaxy A54', descripcion: 'Celular azul.', ubicacion: 'Biblioteca', tipo: 'ENCONTRADO', estado: 'ACTIVO', fechaObjeto: '2026-10-01', categoria: { id: 1, nombre: 'Celular' } },
];

async function preguntar(texto) {
  const user = userEvent.setup();
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AsistenteProvider>
        <ChatAsistente />
      </AsistenteProvider>
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
  await user.type(screen.getByRole('textbox', { name: /escribe tu mensaje/i }), texto);
  await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
}

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue(categorias);
  buscarObjetos.mockResolvedValue(objetos);
});
afterEach(cleanup);

describe('Asistente con IA - ChatAsistente', () => {
  it('usa la interpretación de Claude cuando el backend la devuelve', async () => {
    // El motor local no reconoce "equipito", pero Claude lo interpreta como un celular
    interpretarConIA.mockResolvedValue({
      intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { id: 1, nombre: 'Celular' },
      ubicacion: 'biblioteca', palabras: ['celular'], colores: [],
    });
    await preguntar('se me quedó el equipito en la biblio');

    expect(await screen.findByText('Celular Samsung Galaxy A54')).toBeTruthy();
    expect(screen.queryByText('Mochila negra con libros')).toBeNull();
    expect(interpretarConIA).toHaveBeenCalledWith('se me quedó el equipito en la biblio');
  });

  it('vuelve al motor local si el backend falla o no tiene API key', async () => {
    interpretarConIA.mockRejectedValue(Object.assign(new Error('Service Unavailable'), { response: { status: 503 } }));
    await preguntar('Busco una mochila negra');

    expect(await screen.findByText('Mochila negra con libros')).toBeTruthy();
    expect(screen.queryByText(/No pude consultar/)).toBeNull();
  });

  it('vuelve al motor local si la respuesta de la IA no tiene la forma esperada', async () => {
    interpretarConIA.mockResolvedValue({ texto: 'algo inesperado' });
    await preguntar('Perdí mi celular');

    expect(await screen.findByText('Celular Samsung Galaxy A54')).toBeTruthy();
  });
});
