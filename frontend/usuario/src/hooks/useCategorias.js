import { useCallback, useEffect, useState } from 'react';
import { listarCategorias, mensajeError } from '../services/api';

export default function useCategorias() {
  const [categorias, setCategorias] = useState([]);
  const [cargandoCategorias, setCargandoCategorias] = useState(true);
  const [errorCategorias, setErrorCategorias] = useState('');
  const [reintentos, setReintentos] = useState(0);

  useEffect(() => {
    const controlador = new AbortController();
    const cargar = async () => {
      setCargandoCategorias(true);
      setErrorCategorias('');
      try {
        const data = await listarCategorias({ signal: controlador.signal });
        if (!controlador.signal.aborted) {
          setCategorias(Array.isArray(data) ? data : data?.results || []);
        }
      } catch (e) {
        if (!controlador.signal.aborted) {
          setErrorCategorias(mensajeError(e, 'No se pudieron cargar las categorías'));
        }
      } finally {
        if (!controlador.signal.aborted) setCargandoCategorias(false);
      }
    };
    cargar();
    return () => controlador.abort();
  }, [reintentos]);

  const reintentarCategorias = useCallback(() => setReintentos((valor) => valor + 1), []);
  return { categorias, cargandoCategorias, errorCategorias, reintentarCategorias };
}
