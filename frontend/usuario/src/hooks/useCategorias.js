import { useCallback, useEffect, useState } from 'react';
import { listarCategorias, mensajeError } from '../services/api';

const ORDEN_CATEGORIAS = new Intl.Collator('es', { sensitivity: 'base' });

function ordenarCategorias(categorias) {
  return [...categorias].sort((a, b) =>
    ORDEN_CATEGORIAS.compare(a.nombre || '', b.nombre || '') || Number(a.id) - Number(b.id));
}

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
          const lista = Array.isArray(data) ? data : data?.results || [];
          setCategorias(ordenarCategorias(lista));
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
