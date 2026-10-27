import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CompartirPublicacion from './CompartirPublicacion';
import { getObjeto } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), getObjeto: vi.fn() }));

const perdido = {
  id: 15,
  nombre: 'Mochila azul',
  tipo: 'PERDIDO',
  estado: 'ACTIVO',
  publicadoPor: { id: 1, nombre: 'Amalia', apellido: 'A.', correo: 'amalia@tecsup.edu.pe' },
};
const URL_ESPERADA = `${window.location.origin}/objeto/15`;

const compartirOriginal = navigator.share;
const portapapelesOriginal = navigator.clipboard;

function definir(nombre, valor) {
  Object.defineProperty(navigator, nombre, { value: valor, configurable: true, writable: true });
}

async function pulsar() {
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: /Compartir publicación/ })); });
}

beforeEach(() => { getObjeto.mockReset(); });
afterEach(() => {
  cleanup();
  definir('share', compartirOriginal);
  definir('clipboard', portapapelesOriginal);
});

describe('H39 - Compartir publicación', () => {
  it('revalida la publicación y abre el menú del sistema solo con la URL pública', async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    definir('share', share);
    getObjeto.mockResolvedValueOnce(perdido);
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(getObjeto).toHaveBeenCalledWith(15);
    expect(share).toHaveBeenCalledWith({ title: 'Publicación de FoundIA', url: URL_ESPERADA });
    const datos = JSON.stringify(share.mock.calls[0][0]);
    expect(datos).not.toContain('amalia');
    expect(datos).not.toContain('Amalia');
    expect(screen.getByRole('status').textContent).toContain('Elegiste dónde compartir');
  });

  it('cancelar el menú no se anuncia como compartido', async () => {
    const cancelado = Object.assign(new Error('cancelado'), { name: 'AbortError' });
    definir('share', vi.fn().mockRejectedValue(cancelado));
    getObjeto.mockResolvedValueOnce(perdido);
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('sin menú del sistema copia el enlace al portapapeles', async () => {
    definir('share', undefined);
    const writeText = vi.fn().mockResolvedValue(undefined);
    definir('clipboard', { writeText });
    getObjeto.mockResolvedValueOnce(perdido);
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(writeText).toHaveBeenCalledWith(URL_ESPERADA);
    expect(screen.getByRole('status').textContent).toContain('Enlace copiado');
  });

  it('si no se puede copiar, muestra el enlace para copiarlo a mano', async () => {
    definir('share', undefined);
    definir('clipboard', { writeText: vi.fn().mockRejectedValue(new Error('denegado')) });
    getObjeto.mockResolvedValueOnce(perdido);
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(screen.getByRole('alert').textContent).toContain('Copia el enlace manualmente');
    expect(screen.getByLabelText('Enlace de la publicación').value).toBe(URL_ESPERADA);
  });

  it('no comparte una publicación retirada mientras estaba abierta', async () => {
    const share = vi.fn();
    definir('share', share);
    getObjeto.mockRejectedValueOnce({ response: { status: 404, data: {} } });
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(share).not.toHaveBeenCalled();
    expect(screen.getByRole('alert').textContent).toContain('ya no está disponible');
  });

  it('no comparte si al revalidar ya no está vigente', async () => {
    const share = vi.fn();
    definir('share', share);
    getObjeto.mockResolvedValueOnce({ ...perdido, estado: 'OCULTO' });
    render(<CompartirPublicacion objeto={perdido} />);
    await pulsar();

    expect(share).not.toHaveBeenCalled();
    expect(screen.getByRole('alert').textContent).toContain('ya no está disponible');
  });

  it('no muestra el botón en objetos encontrados ni en publicaciones retiradas', () => {
    const { rerender } = render(<CompartirPublicacion objeto={{ ...perdido, tipo: 'ENCONTRADO' }} />);
    expect(screen.queryByRole('button')).toBeNull();
    rerender(<CompartirPublicacion objeto={{ ...perdido, estado: 'OCULTO' }} />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
