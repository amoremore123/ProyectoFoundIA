import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import ObjetoCard from '../components/ObjetoCard';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { buscarObjetos, listarCategorias, mensajeError } from '../services/api';

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
  const [categoriaId, setCategoriaId] = useState(categoriaInicial);
  const [categorias, setCategorias] = useState([]);
  const [resultados, setResultados] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setQ(qInicial);
    setCategoriaId(categoriaInicial);
  }, [qInicial, categoriaInicial]);

  useEffect(() => {
    let vivo = true;
    (async () => {
      try {
        const cats = await listarCategorias();
        if (vivo) setCategorias(Array.isArray(cats) ? cats : cats?.results || []);
      } catch (e) {
        if (vivo) setError(mensajeError(e, 'No se pudieron cargar las categorías'));
      }
    })();
    return () => {
      vivo = false;
    };
  }, []);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const params = { q: searchParams.get('q') || '' };
      if (searchParams.get('tipo')) params.tipo = searchParams.get('tipo');
      if (searchParams.get('categoriaId')) params.categoriaId = searchParams.get('categoriaId');
      const data = await buscarObjetos(params);
      let lista = Array.isArray(data) ? data : data?.results || [];
      const ubi = (searchParams.get('ubicacion') || '').trim().toLowerCase();
      if (ubi) {
        lista = lista.filter((o) => (o.ubicacion || '').toLowerCase().includes(ubi));
      }
      setResultados(lista);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo realizar la búsqueda'));
      setResultados([]);
    } finally {
      setCargando(false);
    }
  }, [searchParams]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const aplicar = (cambios) => {
    const params = new URLSearchParams(searchParams);
    Object.entries(cambios).forEach(([k, v]) => {
      if (v) params.set(k, v);
      else params.delete(k);
    });
    setSearchParams(params);
  };

  const enviarBusqueda = (e) => {
    e.preventDefault();
    aplicar({ q: q.trim() });
  };

  const hayFiltros = qInicial || tipoInicial || categoriaInicial || ubicacionInicial;

  return (
    <div className="pagina">
      <h1 className="pagina-titulo">Buscar objetos</h1>

      <form className="buscador-card" onSubmit={enviarBusqueda}>
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
            value={categoriaId}
            onChange={(e) => {
              setCategoriaId(e.target.value);
              aplicar({ categoriaId: e.target.value });
            }}
          >
            <option value="">Todas</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
        </label>
      </form>

      <MensajeError mensaje={error} onReintentar={cargar} />

      {cargando ? (
        <Cargando />
      ) : resultados.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            🔎
          </span>
          <p>{hayFiltros ? 'Sin resultados para esa búsqueda.' : 'Escribe algo para buscar objetos.'}</p>
        </div>
      ) : (
        <>
          <p className="resultados-contador">
            {resultados.length} {resultados.length === 1 ? 'resultado' : 'resultados'}
          </p>
          <div className="grid-objetos">
            {resultados.map((obj) => (
              <ObjetoCard key={obj.id} objeto={obj} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
