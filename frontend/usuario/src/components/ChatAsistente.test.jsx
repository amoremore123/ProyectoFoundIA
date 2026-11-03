import { act, cleanup, render, screen } from '@testing-library/react';
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

  it('indica si la respuesta viene del motor local', async () => {
    interpretarConIA.mockRejectedValue(new Error('Not Found'));
    await preguntar('Busco una mochila negra');
    expect(await screen.findByText('Respuesta del motor local')).toBeTruthy();
  });

  it('indica cuando la frase fue interpretada con IA', async () => {
    interpretarConIA.mockResolvedValue({
      intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { id: 1, nombre: 'Celular' },
      ubicacion: 'biblioteca', palabras: ['celular'], colores: [],
    });
    await preguntar('perdí mi celular');
    expect(await screen.findByText('Interpretado con IA')).toBeTruthy();
  });
});

// H46 - Voz en la web con un reconocimiento de voz simulado (no se usa un micrófono real)
describe('Asistente por voz - ChatAsistente', () => {
  let ultimo;
  class ReconocimientoFalso {
    constructor() { ultimo = this; this.iniciado = false; }
    start() { this.iniciado = true; }
    stop() { this.onend?.(); }
    abort() { this.onend?.(); }
  }

  async function abrir() {
    const user = userEvent.setup();
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AsistenteProvider>
          <ChatAsistente />
        </AsistenteProvider>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    return user;
  }

  beforeEach(() => {
    ultimo = null;
    interpretarConIA.mockRejectedValue(new Error('Not Found'));
    window.webkitSpeechRecognition = ReconocimientoFalso;
  });
  afterEach(() => { delete window.webkitSpeechRecognition; });

  it('sin soporte del navegador no muestra el micrófono', async () => {
    delete window.webkitSpeechRecognition;
    await abrir();
    expect(screen.queryByRole('button', { name: 'Hablar con el asistente' })).toBeNull();
  });

  it('avisa de la privacidad de la voz antes de escuchar y usa español de Perú', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    expect(screen.getByText(/tu navegador envía el audio/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver la política de privacidad' }).getAttribute('href')).toBe('/privacidad');
    expect(ultimo.lang).toBe('es-PE');
    expect(ultimo.iniciado).toBe(true);
    expect(screen.getByRole('status').textContent).toContain('Escuchando');
  });

  it('lo que se dicta se envía al asistente como una pregunta', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    await act(async () => {
      ultimo.onresult({ results: [[{ transcript: 'busco una mochila negra' }]] });
      ultimo.onend();
    });
    expect(await screen.findByText('busco una mochila negra')).toBeTruthy();
    expect(await screen.findByText('Mochila negra con libros')).toBeTruthy();
  });

  it('si se niega el permiso del micrófono lo explica y se puede seguir escribiendo', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    await act(async () => {
      ultimo.onerror({ error: 'not-allowed' });
      ultimo.onend();
    });
    expect(screen.getByRole('alert').textContent).toContain('No diste permiso para usar el micrófono');
    expect(screen.getByRole('textbox', { name: /escribe tu mensaje/i })).toBeTruthy();
  });
});
