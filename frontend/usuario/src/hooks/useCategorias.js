import { useCallback, useEffect, useState } from 'react';
import { listarCategorias, mensajeError } from '../services/api';

const comparador = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

/** Orden alfabético en español (tildes y mayúsculas no alteran el orden); los IDs no cambian. */
export function ordenarCategorias(lista) {
  return [...lista].sort((a, b) => comparador.compare(a.nombre || '', b.nombre || ''));
}

const normalizar = (texto) => (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

/** Filtra por texto parcial sin distinguir mayúsculas ni tildes ("mo" encuentra "Móvil" y "Mochila"). */
export function filtrarCategorias(lista, texto) {
  const buscado = normalizar(texto);
  if (!buscado) return lista;
  return lista.filter((c) => normalizar(c.nombre).includes(buscado));
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
          setCategorias(ordenarCategorias(Array.isArray(data) ? data : data?.results || []));
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
