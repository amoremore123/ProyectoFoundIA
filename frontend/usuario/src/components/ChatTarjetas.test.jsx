import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChatAsistente from './ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import { buscarObjetos, interpretarConIA, listarCategorias } from '../services/api';
import { accionesValidas, ajustarATarjetaObjeto, datosPrellenado, tarjetasValidas } from '../utils/tarjetasFindi';

vi.mock('../services/api', async (importOriginal) => ({
  ...await importOriginal(),
  buscarObjetos: vi.fn(), getMisObjetos: vi.fn(), obtenerVozFindi: vi.fn(), interpretarConIA: vi.fn(), listarCategorias: vi.fn(),
}));

function Destino({ nombre }) {
  const { state } = useLocation();
  return <p>{nombre}: {JSON.stringify(state)}</p>;
}

async function preguntar(texto) {
  const user = userEvent.setup();
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AsistenteProvider>
        <Routes>
          <Route path="/" element={<ChatAsistente />} />
          <Route path="/publicar" element={<Destino nombre="Publicar" />} />
          <Route path="/objeto/:id" element={<Destino nombre="Detalle" />} />
        </Routes>
      </AsistenteProvider>
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: /abrir asistente/i }));
  await user.type(screen.getByRole('textbox', { name: /escribe tu mensaje/i }), texto);
  await user.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
  return user;
}

const BASE = { intencion: 'publicar', tipoBuscado: 'ENCONTRADO', ubicacion: 'biblioteca', palabras: [], colores: [] };

beforeEach(() => {
  vi.resetAllMocks();
  listarCategorias.mockResolvedValue([{ id: 3, nombre: 'Mochila', descripcion: 'Mochilas', estado: true }]);
  buscarObjetos.mockResolvedValue([]);
});
afterEach(cleanup);

describe('H47 - catálogo de tarjetas y acciones en la web', () => {
  it('descarta tipos y destinos que no están en el catálogo', () => {
    expect(tarjetasValidas([{ tipo: 'OBJETO', enlace: '/objeto/3' }, { tipo: 'HTML', enlace: '/x' }, { tipo: 'OBJETO', enlace: 'https://falso.example' }]))
      .toHaveLength(1);
    expect(accionesValidas([
      { tipo: 'PRELLENAR_PUBLICAR', destino: '/publicar' },
      { tipo: 'PRELLENAR_PUBLICAR', destino: '/admin' },
      { tipo: 'BORRAR_CUENTA', destino: '/perfil' },
      { tipo: 'ABRIR_RECLAMO', destino: '/objeto/12' },
    ])).toHaveLength(2);
    expect(datosPrellenado({ tipo: 'PERDIDO', categoriaId: 3, ubicacion: ' Aula 2 ', extra: 'x', nombre: 'y' }))
      .toEqual({ tipo: 'PERDIDO', categoriaId: '3', ubicacion: 'Aula 2' });
    expect(datosPrellenado({ tipo: 'OTRO', categoriaId: 'abc' })).toEqual({});
  });

  it('solo cambia el texto cuando hay tarjeta de objeto y la búsqueda no encontró nada', () => {
    const sinResultados = { texto: 'No encontré publicaciones…', acciones: [{ texto: 'Publicar ahora', to: '/publicar' }, { texto: 'Ver búsqueda completa', to: '/buscar?q=x' }], consulta: {} };
    const tarjeta = [{ tipo: 'OBJETO', titulo: 'Gafas de sol' }];
    expect(ajustarATarjetaObjeto(sinResultados, tarjeta)).toMatchObject({
      texto: 'Esta es la publicación que mencionaste: «Gafas de sol».',
      acciones: [{ texto: 'Ver búsqueda completa', to: '/buscar?q=x' }],
    });
    expect(ajustarATarjetaObjeto(sinResultados, [{ tipo: 'RECLAMOS', titulo: 'Tus reclamos' }])).toBe(sinResultados);
    const conResultados = { ...sinResultados, resultados: [{}] };
    expect(ajustarATarjetaObjeto(conResultados, tarjeta)).toBe(conResultados);
    const saludo = { texto: '¡Hola!' };
    expect(ajustarATarjetaObjeto(saludo, tarjeta)).toBe(saludo);
  });

  it('pide confirmación antes de abrir Publicar y Cancelar no navega', async () => {
    interpretarConIA.mockResolvedValue({
      ...BASE,
      acciones: [{ tipo: 'PRELLENAR_PUBLICAR', texto: 'Abrir Publicar con estos datos', confirmacion: '¿Abrir Publicar?', destino: '/publicar', datos: { tipo: 'PERDIDO', categoriaId: 3, ubicacion: 'biblioteca' }, requiereConfirmacion: true }],
    });
    const user = await preguntar('quiero publicar que perdí mi mochila');
    await user.click(await screen.findByRole('button', { name: /Abrir Publicar con estos datos/ }));
    const dialogo = screen.getByRole('alertdialog');
    expect(within(dialogo).getByText('¿Abrir Publicar?')).toBeTruthy();
    await user.click(within(dialogo).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByText(/^Publicar:/)).toBeNull();

    await user.click(screen.getByRole('button', { name: /Abrir Publicar con estos datos/ }));
    await user.click(screen.getByRole('button', { name: 'Sí, continuar' }));
    expect(await screen.findByText('Publicar: {"prellenar":{"tipo":"PERDIDO","categoriaId":"3","ubicacion":"biblioteca"}}')).toBeTruthy();
  });

  it('muestra la tarjeta del objeto y la acción de reclamo abre el detalle sin enviar nada', async () => {
    interpretarConIA.mockResolvedValue({
      ...BASE, intencion: 'buscar',
      tarjetas: [{ tipo: 'OBJETO', titulo: 'Mochila azul', datos: { id: 12, tipo: 'ENCONTRADO', estado: 'EN_CUSTODIA', ubicacion: 'Biblioteca' }, enlace: '/objeto/12' }],
      acciones: [{ tipo: 'ABRIR_RECLAMO', texto: 'Reclamar este objeto', confirmacion: '¿Abrir para reclamar?', destino: '/objeto/12', datos: { objetoId: 12 }, requiereConfirmacion: true }],
    });
    const user = await preguntar('la #12 es mía');
    const tarjetas = await screen.findByRole('list', { name: 'Tarjetas de FINDI' });
    expect(within(tarjetas).getByText('Mochila azul')).toBeTruthy();
    expect(within(tarjetas).getByText('En custodia')).toBeTruthy();
    // El texto no contradice la tarjeta ni propone publicar un objeto que ya existe
    expect(screen.getByText('Esta es la publicación que mencionaste: «Mochila azul».')).toBeTruthy();
    expect(screen.queryByText(/No encontré publicaciones/)).toBeNull();
    expect(screen.queryByRole('link', { name: /Publicar ahora/ })).toBeNull();
    await user.click(screen.getByRole('button', { name: /Reclamar este objeto/ }));
    await user.click(screen.getByRole('button', { name: 'Sí, continuar' }));
    expect(await screen.findByText('Detalle: {"abrirReclamo":true}')).toBeTruthy();
  });

  it('muestra las tarjetas de reclamos y QR como cifras', async () => {
    interpretarConIA.mockResolvedValue({
      ...BASE, intencion: 'ayuda',
      tarjetas: [
        { tipo: 'RECLAMOS', titulo: 'Tus reclamos', datos: { pendientes: 1, aceptados: 2, retirados: 0, rechazados: 0 }, enlace: '/mis-publicaciones' },
        { tipo: 'QR_RETIRO', titulo: 'QR de retiro vigentes', datos: { vigentes: 1 }, enlace: '/mis-publicaciones' },
      ],
    });
    await preguntar('¿cómo van mis reclamos?');
    expect(await screen.findByText(/1 pendientes · 2 aceptados/)).toBeTruthy();
    expect(screen.getByText('Tienes 1 QR listo para recoger tu objeto.')).toBeTruthy();
  });
});
