import { act, cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChatAsistente from './ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import { AuthProvider } from '../context/AuthContext';
import { buscarObjetos, getMisObjetos, interpretarConIA, listarCategorias, obtenerVozFindi } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  buscarObjetos: vi.fn(),
  getMisObjetos: vi.fn(),
  obtenerVozFindi: vi.fn(),
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
  it('respeta la fuente local del contrato H45 del backend del equipo', async () => {
    interpretarConIA.mockResolvedValue({
      intencion: 'buscar', tipoBuscado: 'ENCONTRADO', categoria: { id: 1, nombre: 'Celular' },
      ubicacion: '', palabras: ['celular'], colores: [], fuente: 'local',
    });
    await preguntar('perdí mi celular');
    expect(await screen.findByText('Celular Samsung Galaxy A54')).toBeTruthy();
    expect(screen.getByText('Respuesta del motor local')).toBeTruthy();
    expect(screen.queryByText('Interpretado con IA')).toBeNull();
  });

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
      ubicacion: 'biblioteca', palabras: ['celular'], colores: [], fuente: 'claude',
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

  it('cancela el micrófono al cerrar e ignora resultados tardíos', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    const rec = ultimo;
    const abortar = vi.spyOn(rec, 'abort');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cerrar asistente' }));
    expect(abortar).toHaveBeenCalledTimes(1);
    expect(rec.onresult).toBeNull();
    expect(rec.onerror).toBeNull();
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    expect(screen.getByRole('textbox', { name: /escribe tu mensaje/i }).value).toBe('');
    expect(screen.getByTestId('estado-findi').textContent).not.toContain('Escuchando');
  });

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

  it('muestra el texto mientras se habla y deja revisar lo dictado antes de enviarlo', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    const caja = screen.getByRole('textbox', { name: /escribe tu mensaje/i });
    const parcial = Object.assign([{ transcript: 'busco una' }], { isFinal: false });
    await act(async () => { ultimo.onresult({ results: [parcial] }); });
    expect(caja.value).toBe('busco una');

    // Entre varias alternativas elige la que usa el vocabulario de FoundIA y corrige "a la 204"
    const final = Object.assign(
      [{ transcript: 'busco una muchila negra en a la 204' }, { transcript: 'busco una mochila negra en a la 204' }],
      { isFinal: true },
    );
    await act(async () => {
      ultimo.onresult({ results: [final] });
      ultimo.onend();
    });
    expect(caja.value).toBe('busco una mochila negra en aula 204');
    expect(screen.getByRole('status').textContent).toContain('Revisa el texto');
    expect(buscarObjetos).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
    expect(await screen.findByText('busco una mochila negra en aula 204')).toBeTruthy();
    expect(await screen.findByText('Mochila negra con libros')).toBeTruthy();
  });

  it('si el navegador no tiene español de Perú reintenta con español latinoamericano', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    const primero = ultimo;
    await act(async () => {
      primero.onerror({ error: 'language-not-supported' });
      primero.onend();
    });
    expect(ultimo).not.toBe(primero);
    expect(ultimo.lang).toBe('es-419');
    expect(ultimo.iniciado).toBe(true);
    expect(screen.queryByRole('alert')).toBeNull();
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

  it('si el navegador no tiene servicio de voz lo explica y oculta el micrófono', async () => {
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    // Con internet, un error "network" significa que este navegador no tiene acceso al servicio
    await act(async () => {
      ultimo.onerror({ error: 'network' });
      ultimo.onend();
    });
    expect(screen.getByRole('alert').textContent).toContain('no está disponible en este navegador');
    expect(screen.queryByRole('button', { name: 'Hablar con el asistente' })).toBeNull();
    expect(screen.getByRole('textbox', { name: /escribe tu mensaje/i })).toBeTruthy();
  });

  it('sin internet avisa de la conexión y deja el micrófono para reintentar', async () => {
    const enLinea = vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false);
    const user = await abrir();
    await user.click(screen.getByRole('button', { name: 'Hablar con el asistente' }));
    await act(async () => {
      ultimo.onerror({ error: 'network' });
      ultimo.onend();
    });
    expect(screen.getByRole('alert').textContent).toContain('necesita conexión a internet');
    expect(screen.getByRole('button', { name: 'Hablar con el asistente' })).toBeTruthy();
    enLinea.mockRestore();
  });
});

// Fase 4 - Accesos rápidos y estados visuales de FINDI
describe('FINDI - accesos rápidos y estados', () => {
  async function abrirChat({ conSesion = false } = {}) {
    const user = userEvent.setup();
    if (conSesion) {
      localStorage.setItem('token', 'token-de-prueba');
      localStorage.setItem('usuario', JSON.stringify({ nombre: 'Ana' }));
    }
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <AsistenteProvider>
            <ChatAsistente />
          </AsistenteProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    return user;
  }

  beforeEach(() => { interpretarConIA.mockRejectedValue(new Error('Not Found')); });
  afterEach(() => { localStorage.clear(); });

  it('muestra los cuatro accesos rápidos y saluda al abrir', async () => {
    await abrirChat();
    const accesos = screen.getByRole('group', { name: 'Accesos rápidos' });
    expect(within(accesos).getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Buscar un objeto perdido', 'Reportar un objeto', 'Consultar mis reportes', '¿Cómo funciona FoundIA?',
    ]);
    expect(screen.getByTestId('estado-findi').textContent).toContain('Saludando');
  });

  it('"Consultar mis reportes" pide iniciar sesión si no hay sesión', async () => {
    const user = await abrirChat();
    await user.click(screen.getByRole('button', { name: 'Consultar mis reportes' }));
    expect(screen.getByRole('link', { name: /Iniciar sesión/ }).getAttribute('href')).toBe('/login');
  });

  it('"Consultar mis reportes" lleva a Mis reportes con sesión', async () => {
    const user = await abrirChat({ conSesion: true });
    await user.click(screen.getByRole('button', { name: 'Consultar mis reportes' }));
    expect(screen.getByRole('link', { name: /Ver mis reportes/ }).getAttribute('href')).toBe('/mis-publicaciones');
  });

  it('"¿Cómo funciona FoundIA?" responde sin consultar la API', async () => {
    const user = await abrirChat();
    await user.click(screen.getByRole('button', { name: '¿Cómo funciona FoundIA?' }));
    expect(screen.getByText(/cuatro pasos/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Ver el proceso completo/ })).toBeTruthy();
    expect(buscarObjetos).not.toHaveBeenCalled();
  });

  it('conserva cada mensaje al agregar respuestas rápidas en el mismo evento', async () => {
    const errores = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      const user = await abrirChat();
      await user.click(screen.getByRole('button', { name: '¿Cómo funciona FoundIA?' }));
      await user.click(screen.getByRole('button', { name: 'Consultar mis reportes' }));
      expect(screen.getByText(/cuatro pasos/)).toBeTruthy();
      expect(screen.getByRole('link', { name: /Iniciar sesión/ })).toBeTruthy();
      expect(errores.mock.calls.some((args) => String(args[0]).includes('same key'))).toBe(false);
    } finally {
      errores.mockRestore();
    }
  });

  it('muestra "Pensando…" mientras busca y "Búsqueda completada" al terminar', async () => {
    let resolver;
    buscarObjetos.mockReturnValue(new Promise((r) => { resolver = r; }));
    const user = await abrirChat();
    await user.type(screen.getByRole('textbox', { name: /escribe tu mensaje/i }), 'Busco una mochila negra');
    await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
    expect(screen.getByTestId('estado-findi').textContent).toContain('Pensando');

    await act(async () => { resolver(objetos); });
    expect(await screen.findByText('Mochila negra con libros')).toBeTruthy();
    expect(screen.getByTestId('estado-findi').textContent).toMatch(/Búsqueda completada · \d+ resultado/);
  });

  it('sin conexión muestra el estado de error y permite reintentar sin repetir el mensaje', async () => {
    buscarObjetos.mockRejectedValueOnce(new Error('Network Error'));
    const user = await abrirChat();
    await user.type(screen.getByRole('textbox', { name: /escribe tu mensaje/i }), 'Busco una mochila negra');
    await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));

    expect(await screen.findByText(/No pude consultar/)).toBeTruthy();
    expect(screen.getByTestId('estado-findi').textContent).toContain('Sin conexión');

    buscarObjetos.mockResolvedValue(objetos);
    await user.click(screen.getByRole('button', { name: /Reintentar/ }));
    expect(await screen.findByText('Mochila negra con libros')).toBeTruthy();
    expect(screen.getAllByText('Busco una mochila negra').length).toBe(1);
  });
});

// Fase 5 - FINDI responde "mis reportes" con las publicaciones reales del usuario
describe('FINDI con IA - mis reportes', () => {
  async function conversar(texto, { conSesion = false } = {}) {
    const user = userEvent.setup();
    if (conSesion) localStorage.setItem('token', 'token-de-prueba');
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <AsistenteProvider>
            <ChatAsistente />
          </AsistenteProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    await user.type(screen.getByRole('textbox', { name: /escribe tu mensaje/i }), texto);
    await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
  }

  afterEach(() => localStorage.clear());

  it('conserva Mis reportes cuando el motor compartido no reconoce esa intención', async () => {
    interpretarConIA.mockResolvedValue({ intencion: 'vacio', palabras: [], fuente: 'local' });
    await conversar('¿Cómo van mis reportes?');
    expect(await screen.findByRole('link', { name: /Iniciar sesión/ })).toBeTruthy();
    expect(getMisObjetos).not.toHaveBeenCalled();
  });

  it('con sesión muestra mis publicaciones y su estado cuando la IA detecta la intención', async () => {
    interpretarConIA.mockResolvedValue({ intencion: 'mis_reportes', palabras: [] });
    getMisObjetos.mockResolvedValue([
      { id: 7, nombre: 'Laptop Dell', tipo: 'PERDIDO', estado: 'ACTIVO', fechaPublicacion: '2026-09-28T10:00:00' },
      { id: 8, nombre: 'Celular Samsung', tipo: 'PERDIDO', estado: 'RECUPERADO', fechaPublicacion: '2026-10-01T10:00:00' },
    ]);
    await conversar('¿cómo van mis cosas?', { conSesion: true });

    const lista = await screen.findByRole('list', { name: 'Mis reportes' });
    expect(within(lista).getAllByRole('link').map((a) => a.getAttribute('href'))).toEqual(['/objeto/8', '/objeto/7']);
    expect(within(lista).getByText('Recuperado')).toBeTruthy();
    expect(screen.getByText(/Tienes 2 reportes/)).toBeTruthy();
  });

  it('sin sesión pide iniciar sesión y no consulta los reportes', async () => {
    interpretarConIA.mockRejectedValue(new Error('Service Unavailable'));
    await conversar('¿Cómo van mis reportes?');

    expect(await screen.findByRole('link', { name: /Iniciar sesión/ })).toBeTruthy();
    expect(getMisObjetos).not.toHaveBeenCalled();
  });
});

// Fase 6 - Voz natural de FINDI (ElevenLabs vía backend) con la voz del navegador de respaldo.
// El audio, el endpoint y la voz del navegador están simulados: no se reproduce sonido real.
describe('FINDI - voz natural', () => {
  let audios;
  let reproducidos;
  class AudioFalso {
    constructor() { audios.push(this); this.pausado = false; }
    play() { reproducidos.push(this.src); this.onplaying?.(); return Promise.resolve(); }
    pause() { this.pausado = true; }
  }

  beforeEach(() => {
    audios = [];
    reproducidos = [];
    interpretarConIA.mockRejectedValue(new Error('Not Found'));
    window.Audio = AudioFalso;
    URL.createObjectURL = vi.fn(() => 'blob:findi');
    URL.revokeObjectURL = vi.fn();
    window.speechSynthesis = { speak: vi.fn((frase) => frase.onstart?.()), cancel: vi.fn() };
    window.SpeechSynthesisUtterance = class { constructor(texto) { this.text = texto; } };
  });
  afterEach(() => {
    // Primero se desmonta el chat (que corta la voz) y después se quitan las simulaciones
    cleanup();
    delete window.speechSynthesis;
    delete window.SpeechSynthesisUtterance;
  });

  async function activarVozYPreguntar() {
    const user = userEvent.setup();
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AsistenteProvider>
          <ChatAsistente />
        </AsistenteProvider>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
    await user.click(screen.getByRole('button', { name: 'Leer las respuestas en voz alta' }));
    await user.click(screen.getByRole('button', { name: '¿Cómo funciona FoundIA?' }));
    return user;
  }

  it('lee la respuesta con la voz natural, muestra "Respondiendo…" y da crédito a ElevenLabs', async () => {
    obtenerVozFindi.mockResolvedValue(new Blob(['mp3'], { type: 'audio/mpeg' }));
    await activarVozYPreguntar();

    // El primer audio es el silencio que desbloquea el sonido en celulares
    expect(reproducidos[0]).toMatch(/^data:audio\/wav/);
    expect(obtenerVozFindi).toHaveBeenCalledWith(expect.stringContaining('cuatro pasos'), expect.anything());
    expect(await screen.findByText('Voz de FINDI: ElevenLabs')).toBeTruthy();
    expect(reproducidos.at(-1)).toBe('blob:findi');
    expect(screen.getByTestId('estado-findi').textContent).toContain('Respondiendo');
    expect(window.speechSynthesis.speak).not.toHaveBeenCalled();
  });

  it('si la voz natural no está disponible usa la voz del navegador y lo avisa', async () => {
    obtenerVozFindi.mockRejectedValue({ response: { status: 503 } });
    await activarVozYPreguntar();

    expect(await screen.findByText(/se usa la voz del navegador/)).toBeTruthy();
    expect(window.speechSynthesis.speak).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('Voz de FINDI: ElevenLabs')).toBeNull();
  });

  it('al apagar la voz se corta el audio', async () => {
    obtenerVozFindi.mockResolvedValue(new Blob(['mp3'], { type: 'audio/mpeg' }));
    const user = await activarVozYPreguntar();
    await screen.findByText('Voz de FINDI: ElevenLabs');

    await user.click(screen.getByRole('button', { name: 'Dejar de leer las respuestas en voz alta' }));
    expect(audios.at(-1).pausado).toBe(true);
    expect(screen.getByTestId('estado-findi').textContent).not.toContain('Respondiendo');
  });

  it('al cerrar corta el audio y libera su URL', async () => {
    obtenerVozFindi.mockResolvedValue(new Blob(['mp3'], { type: 'audio/mpeg' }));
    const user = await activarVozYPreguntar();
    await screen.findByText('Voz de FINDI: ElevenLabs');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cerrar asistente' }));
    expect(audios.at(-1).pausado).toBe(true);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:findi');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('al cerrar cancela el audio pendiente sin activar la voz de respaldo', async () => {
    let resolver;
    obtenerVozFindi.mockReturnValue(new Promise((r) => { resolver = r; }));
    const user = await activarVozYPreguntar();
    const { signal } = obtenerVozFindi.mock.calls[0][1];
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cerrar asistente' }));
    expect(signal.aborted).toBe(true);
    await act(async () => { resolver(new Blob(['mp3'])); });
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    expect(window.speechSynthesis.speak).not.toHaveBeenCalled();
  });
});
