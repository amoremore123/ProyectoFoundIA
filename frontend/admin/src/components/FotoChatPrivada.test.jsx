import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FotoChatPrivada from './FotoChatPrivada';
import { getFotoChat } from '../services/api';

vi.mock('../services/api', async (importOriginal) => ({ ...await importOriginal(), getFotoChat: vi.fn() }));

beforeEach(() => {
  vi.resetAllMocks();
  URL.createObjectURL = vi.fn(() => 'blob:foto-chat');
  URL.revokeObjectURL = vi.fn();
});
afterEach(cleanup);

describe('H62 - foto privada del chat en la web', () => {
  it('pide la foto con la sesión y la muestra como blob', async () => {
    getFotoChat.mockResolvedValue(new Blob(['x'], { type: 'image/jpeg' }));
    render(<FotoChatPrivada url="/api/conversaciones/1/mensajes/3/foto" alt="Foto enviada por Luis" />);
    const img = await screen.findByAltText('Foto enviada por Luis');
    expect(img.getAttribute('src')).toBe('blob:foto-chat');
    expect(getFotoChat).toHaveBeenCalledWith('/api/conversaciones/1/mensajes/3/foto');
  });

  it('si la API la niega avisa en vez de dejar una imagen rota', async () => {
    getFotoChat.mockRejectedValue(new Error('403'));
    render(<FotoChatPrivada url="/api/conversaciones/1/mensajes/3/foto" alt="Foto" />);
    await waitFor(() => expect(screen.getByRole('alert').textContent).toMatch(/No se pudo cargar/));
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('libera el blob al salir', async () => {
    getFotoChat.mockResolvedValue(new Blob(['x']));
    const { unmount } = render(<FotoChatPrivada url="/api/x" alt="Foto" />);
    await screen.findByAltText('Foto');
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto-chat');
  });
});
