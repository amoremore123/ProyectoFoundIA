import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import ObjetoCard from '../components/ObjetoCard';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import useCategorias from '../hooks/useCategorias';
import { buscarObjetos, mensajeError } from '../services/api';

const OPCIONES_TIPO = [
  { valor: '', texto: 'Todos' },
  { valor: 'PERDIDO', texto: 'Perdidos' },
  { valor: 'ENCONTRADO', texto: 'Encontrados' },
];

export default function Buscar() {
  const [searchParams, setSearchParams] = useSearchParams();
  const qInicial = searchParams.get('q') || '';
  const categoriaInicial = searchParams.get('categoriaId') || '';
  const ubicacionInicial = searchParams.get('ubicacion') || '';
  const tipoInicial = searchParams.get('tipo') || '';

  const [q, setQ] = useState(qInicial);
  const { categorias, cargandoCategorias, errorCategorias, reintentarCategorias } = useCategorias();
  const [resultados, setResultados] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorBusqueda, setErrorBusqueda] = useState('');
  const [reintentosBusqueda, setReintentosBusqueda] = useState(0);

  useEffect(() => {
    setQ(qInicial);
  }, [qInicial]);

  useEffect(() => {
    const controlador = new AbortController();
    const cargar = async () => {
      setCargando(true);
      setErrorBusqueda('');
      try {
        const params = { q: qInicial.trim() };
        if (tipoInicial) params.tipo = tipoInicial;
        if (categoriaInicial) params.categoriaId = categoriaInicial;
        const data = await buscarObjetos(params, { signal: controlador.signal });
        if (controlador.signal.aborted) return;
        let lista = Array.isArray(data) ? data : data?.results || [];
        const ubicacion = ubicacionInicial.trim().toLowerCase();
        if (ubicacion) {
          lista = lista.filter((objeto) => (objeto.ubicacion || '').toLowerCase().includes(ubicacion));
        }
        setResultados(lista);
      } catch (e) {
        if (!controlador.signal.aborted) {
          setErrorBusqueda(mensajeError(e, 'No se pudo realizar la búsqueda'));
          setResultados([]);
        }
      } finally {
        if (!controlador.signal.aborted) setCargando(false);
      }
    };
    cargar();
    // Una respuesta lenta anterior no debe reemplazar el filtro actual.
    return () => controlador.abort();
  }, [qInicial, tipoInicial, categoriaInicial, ubicacionInicial, reintentosBusqueda]);

  const reintentarBusqueda = () => setReintentosBusqueda((valor) => valor + 1);

  const aplicar = (cambios) => {
    setSearchParams((anteriores) => {
      const params = new URLSearchParams(anteriores);
      Object.entries(cambios).forEach(([campo, valor]) => {
        if (valor) params.set(campo, valor);
        else params.delete(campo);
      });
      return params;
    });
  };

  const enviarBusqueda = (e) => {
    e.preventDefault();
    if (q.trim() === qInicial) reintentarBusqueda();
    else aplicar({ q: q.trim() });
  };

  const hayFiltros = qInicial || tipoInicial || categoriaInicial || ubicacionInicial;

  return (
    <div className="pagina">
      <h1 className="pagina-titulo">Buscar objetos</h1>

      <form className="buscador-card" onSubmit={enviarBusqueda} aria-label="Búsqueda de objetos">
        <div className="buscador-fila">
          <input
            type="search"
            className="input"
            placeholder="🔍 Buscar objeto..."
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Buscar objeto"
          />
          <button type="submit" className="btn btn-primario">
            Buscar
          </button>
        </div>

        <div className="filtros-chips">
          {OPCIONES_TIPO.map((op) => (
            <button
              key={op.valor}
              type="button"
              className={`chip-filtro${tipoInicial === op.valor ? ' activo' : ''}`}
              aria-pressed={tipoInicial === op.valor}
              onClick={() => aplicar({ tipo: op.valor })}
            >
              {op.texto}
            </button>
          ))}
        </div>

        <label className="campo">
          <span className="campo-label">Categoría</span>
          <select
            className="input"
            value={categoriaInicial}
            onChange={(e) => aplicar({ categoriaId: e.target.value })}
            disabled={cargandoCategorias || !!errorCategorias}
          >
            <option value="">{cargandoCategorias ? 'Cargando categorías...' : 'Todas'}</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
        </label>
        <MensajeError mensaje={errorCategorias} onReintentar={reintentarCategorias} />
      </form>

      <section aria-label="Resultados de búsqueda" aria-busy={cargando}>
        <MensajeError mensaje={errorBusqueda} onReintentar={reintentarBusqueda} />
        {cargando ? (
          <Cargando />
        ) : errorBusqueda ? null : resultados.length === 0 ? (
          <div className="estado-vacio" role="status">
            <span className="estado-vacio-icono" aria-hidden="true">
              🔎
            </span>
            <p>{hayFiltros ? 'Sin resultados para esa búsqueda.' : 'Todavía no hay publicaciones.'}</p>
          </div>
        ) : (
          <>
            <p className="resultados-contador" role="status" aria-live="polite">
              {resultados.length} {resultados.length === 1 ? 'resultado' : 'resultados'}
            </p>
            <div className="grid-objetos">
              {resultados.map((obj) => (
                <ObjetoCard key={obj.id} objeto={obj} />
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
