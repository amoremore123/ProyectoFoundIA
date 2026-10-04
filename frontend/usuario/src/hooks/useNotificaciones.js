import { useCallback, useEffect, useState } from 'react';
import { getNotificaciones, marcarNotificacionLeida, mensajeError } from '../services/api';
import { useAuth } from '../context/AuthContext';

export function useNotificaciones() {
  const { token } = useAuth();
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    if (!token) {
      setNotificaciones([]);
      return;
    }
    setCargando(true);
    setError('');
    try {
      const data = await getNotificaciones();
      setNotificaciones(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar las notificaciones'));
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const marcarLeida = async (id) => {
    await marcarNotificacionLeida(id);
    setNotificaciones((prev) => prev.map((n) => (n.id === id ? { ...n, leida: true } : n)));
  };

  const noLeidas = notificaciones.filter((n) => !n.leida).length;

  return { notificaciones, cargando, error, noLeidas, recargar: cargar, marcarLeida };
}
