import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import ObjetoCard from '../components/ObjetoCard';
import Icono from '../components/Icono';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import useCategorias, { filtrarCategorias } from '../hooks/useCategorias';
import { buscarObjetos, mensajeError } from '../services/api';
import { obtenerCoordenadasActuales } from '../services/geolocalizacion';
import { CAMPOS_AVANZADOS, distanciaKm, validarFiltros } from '../utils/busqueda';

const OPCIONES_TIPO = [
  { valor: '', texto: 'Todos' },
  { valor: 'PERDIDO', texto: 'Perdidos' },
  { valor: 'ENCONTRADO', texto: 'Encontrados' },
];

export default function Buscar() {
  const [searchParams, setSearchParams] = useSearchParams();
  const qInicial = searchParams.get('q') || '';
  const categoriaInicial = searchParams.get('categoriaId') || '';
  const tipoInicial = searchParams.get('tipo') || '';
  const claveAvanzados = JSON.stringify(CAMPOS_AVANZADOS.map((campo) => searchParams.get(campo) || ''));
  const filtrosAplicados = Object.fromEntries(CAMPOS_AVANZADOS.map((campo) => [campo, searchParams.get(campo) || '']));
  const [avanzados, setAvanzados] = useState(filtrosAplicados);
  const [errorFiltros, setErrorFiltros] = useState('');
  const [ubicando, setUbicando] = useState(false);
  const solicitudGps = useRef(0);

  useEffect(() => {
    setAvanzados(Object.fromEntries(CAMPOS_AVANZADOS.map((campo, indice) => [campo, JSON.parse(claveAvanzados)[indice]])));
    setErrorFiltros('');
    solicitudGps.current += 1;
    setUbicando(false);
  }, [claveAvanzados]);
  useEffect(() => () => { solicitudGps.current += 1; }, []);

  const cambiarAvanzado = (campo, valor) => {
    solicitudGps.current += 1;
    setUbicando(false);
    setAvanzados((f) => ({ ...f, [campo]: valor }));
  };

  const [q, setQ] = useState(qInicial);
  const { categorias, cargandoCategorias, errorCategorias, reintentarCategorias } = useCategorias();
  const [resultados, setResultados] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorBusqueda, setErrorBusqueda] = useState('');
  const [reintentosBusqueda, setReintentosBusqueda] = useState(0);
  const [filtroCategoria, setFiltroCategoria] = useState('');

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
        CAMPOS_AVANZADOS.forEach((campo) => { if (filtrosAplicados[campo]) params[campo] = filtrosAplicados[campo]; });
        const data = await buscarObjetos(params, { signal: controlador.signal });
        if (controlador.signal.aborted) return;
        const lista = Array.isArray(data) ? data : data?.results || [];
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
  }, [qInicial, tipoInicial, categoriaInicial, claveAvanzados, reintentosBusqueda]);

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
    const limpios = Object.fromEntries(Object.entries(avanzados).map(([campo, valor]) => [campo, valor.trim()]));
    const error = validarFiltros(limpios);
    setErrorFiltros(error);
    if (error) return;
    if (q.trim() === qInicial && CAMPOS_AVANZADOS.every((campo) => limpios[campo] === filtrosAplicados[campo])) reintentarBusqueda();
    else aplicar({ q: q.trim(), ...limpios });
  };

  const usarUbicacion = async () => {
    const solicitud = ++solicitudGps.current;
    setUbicando(true);
    setErrorFiltros('');
    try {
      const coordenadas = await obtenerCoordenadasActuales();
      if (solicitud !== solicitudGps.current) return;
      setAvanzados((actual) => ({ ...actual, latitud: String(coordenadas.latitud), longitud: String(coordenadas.longitud), radioKm: actual.radioKm || '5' }));
    } catch (error) {
      if (solicitud === solicitudGps.current) setErrorFiltros(`${error.message}. Puedes escribir una ubicación o las coordenadas manualmente.`);
    } finally {
      if (solicitud === solicitudGps.current) setUbicando(false);
    }
  };

  const limpiarFiltros = () => {
    solicitudGps.current += 1;
    setUbicando(false);
    setQ('');
    setFiltroCategoria('');
    setAvanzados(Object.fromEntries(CAMPOS_AVANZADOS.map((campo) => [campo, ''])));
    setErrorFiltros('');
    aplicar({ q: '', categoriaId: '', tipo: '', ...Object.fromEntries(CAMPOS_AVANZADOS.map((campo) => [campo, ''])) });
  };

  const hayFiltros = qInicial || tipoInicial || categoriaInicial || CAMPOS_AVANZADOS.some((campo) => filtrosAplicados[campo]);
  // La categoría elegida sigue visible aunque no coincida con el texto del filtro
  const categoriasVisibles = filtrarCategorias(categorias, filtroCategoria);
  const seleccionada = categorias.find((c) => String(c.id) === categoriaInicial);
  if (seleccionada && !categoriasVisibles.includes(seleccionada)) categoriasVisibles.unshift(seleccionada);
  const sinCoincidenciasCategoria = filtroCategoria.trim() && filtrarCategorias(categorias, filtroCategoria).length === 0;

  const categoriaNoDisponible = categoriaInicial && !cargandoCategorias && !errorCategorias
    && !categorias.some((categoria) => String(categoria.id) === categoriaInicial);

  return (
    <div className="pagina">
      <h1 className="pagina-titulo">Buscar objetos</h1>

      <form className="buscador-card" onSubmit={enviarBusqueda} aria-label="Búsqueda de objetos" noValidate>
        <div className="buscador-fila">
          <input
            type="search"
            className="input input-busqueda"
            placeholder="Buscar objeto..."
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

        <div className="campo">
          <label className="campo-label" htmlFor="filtro-categoria">Buscar categoría</label>
          <input
            id="filtro-categoria"
            type="text"
            className="input"
            placeholder="Escribe para filtrar (ej. mo)"
            value={filtroCategoria}
            onChange={(e) => setFiltroCategoria(e.target.value)}
            disabled={cargandoCategorias || !!errorCategorias}
            aria-controls="selector-categoria"
            aria-describedby="filtro-categoria-estado"
            autoComplete="off"
          />
          <span id="filtro-categoria-estado" className="nota-suave" role="status" aria-live="polite">
            {sinCoincidenciasCategoria
              ? 'Ninguna categoría coincide con ese texto.'
              : filtroCategoria.trim()
                ? `${filtrarCategorias(categorias, filtroCategoria).length} categorías coinciden.`
                : ''}
          </span>
        </div>

        <label className="campo">
          <span className="campo-label">Categoría</span>
          <select
            id="selector-categoria"
            className="input"
            value={categoriaInicial}
            onChange={(e) => aplicar({ categoriaId: e.target.value })}
            disabled={cargandoCategorias || !!errorCategorias}
          >
            <option value="">{cargandoCategorias ? 'Cargando categorías...' : 'Todas'}</option>
            {categoriaNoDisponible && (
              <option value={categoriaInicial} disabled>Categoría no disponible</option>
            )}
            {categoriasVisibles.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}{c.estado === false ? ' (desactivada)' : ''}
              </option>
            ))}
          </select>
        </label>
        <MensajeError mensaje={errorCategorias} onReintentar={reintentarCategorias} />
        <fieldset className="filtros-avanzados">
          <legend>Ubicación y fechas</legend>
          <label className="campo"><span className="campo-label">Ubicación</span>
            <input className="input" value={avanzados.ubicacion} maxLength={255} placeholder="Ej. Biblioteca" onChange={(e) => cambiarAvanzado('ubicacion', e.target.value)} />
          </label>
          <div className="filtros-grid">
            {[['desde', 'Desde'], ['hasta', 'Hasta']].map(([campo, nombre]) => <label className="campo" key={campo}><span className="campo-label">{nombre}</span><input className="input" type="date" value={avanzados[campo]} onChange={(e) => cambiarAvanzado(campo, e.target.value)} /></label>)}
          </div>
          <p className="nota-suave">Busca por lugar o añade coordenadas para ordenar por cercanía. Las fechas incluyen ambos extremos.</p>
          <div className="filtros-grid">
            {[['latitud', 'Latitud', -90, 90], ['longitud', 'Longitud', -180, 180], ['radioKm', 'Radio en kilómetros', 0, 1000]].map(([campo, nombre, min, max]) => <label className="campo" key={campo}><span className="campo-label">{nombre}</span><input className="input" type="number" step="any" min={min} max={max} value={avanzados[campo]} onChange={(e) => cambiarAvanzado(campo, e.target.value)} /></label>)}
          </div>
          <button className="btn btn-secundario" type="button" disabled={ubicando} onClick={usarUbicacion}>{ubicando ? 'Obteniendo ubicación...' : 'Usar mi ubicación'}</button>
          <MensajeError mensaje={errorFiltros} />
          <button className="btn btn-primario" type="submit" disabled={ubicando}>Aplicar filtros</button>
        </fieldset>
        {hayFiltros && <div className="filtros-activos" aria-label="Filtros activos">
          {Object.entries({ q: qInicial, tipo: tipoInicial, categoriaId: categoriaInicial, ...filtrosAplicados }).filter(([, valor]) => valor).map(([campo, valor]) => <button className="chip-filtro" type="button" key={campo} aria-label={`Quitar filtro ${campo}`} onClick={() => aplicar(['latitud', 'longitud', 'radioKm'].includes(campo) ? { latitud: '', longitud: '', radioKm: '' } : { [campo]: '' })}>{({ q: 'Texto', tipo: 'Tipo', categoriaId: 'Categoría', ubicacion: 'Ubicación', desde: 'Desde', hasta: 'Hasta', latitud: 'Latitud', longitud: 'Longitud', radioKm: 'Radio km' })[campo]}: {campo === 'categoriaId' ? seleccionada?.nombre || valor : valor} ×</button>)}
        </div>}
        {hayFiltros && (
          <button type="button" className="btn btn-secundario" onClick={limpiarFiltros}>
            Limpiar filtros
          </button>
        )}
      </form>

      <section aria-labelledby="titulo-resultados" aria-busy={cargando}>
        <h2 id="titulo-resultados" className="solo-lector">Resultados de búsqueda</h2>
        <MensajeError mensaje={errorBusqueda} onReintentar={reintentarBusqueda} />
        {cargando ? (
          <Cargando />
        ) : errorBusqueda ? null : resultados.length === 0 ? (
          <div className="estado-vacio" role="status">
            <span className="estado-vacio-icono" aria-hidden="true">
              <Icono nombre="buscar" tamano={28} />
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
                <ObjetoCard key={obj.id} objeto={obj} distancia={distanciaKm(filtrosAplicados, obj)} />
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
