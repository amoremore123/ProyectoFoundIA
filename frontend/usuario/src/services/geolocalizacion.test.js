import { afterEach, expect, it, vi } from 'vitest';
import { obtenerUbicacionActual } from './geolocalizacion';

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });
it('libera Publicar con coordenadas al vencer la espera de geocodificación', async () => {
  vi.useFakeTimers();
  vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: (bien) => bien({ coords: { latitude: -12, longitude: -77 } }) } });
  const fetchLento = vi.fn(() => new Promise(() => {}));
  vi.stubGlobal('fetch', fetchLento);
  const resultado = obtenerUbicacionActual();
  await vi.advanceTimersByTimeAsync(5000);
  expect(await resultado).toEqual({ latitud: -12, longitud: -77, direccion: '' });
  expect(fetchLento.mock.calls[0][1].signal.aborted).toBe(true);
});
it('conserva la dirección cuando el proveedor responde a tiempo', async () => {
  vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: (bien) => bien({ coords: { latitude: -12, longitude: -77 } }) } });
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ display_name: 'Cómputo, ñ' }) }));
  expect((await obtenerUbicacionActual()).direccion).toBe('Cómputo, ñ');
});
