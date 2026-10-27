import { useCallback, useEffect, useRef, useState } from 'react';
import { getDashboard, mensajeError } from '../services/api';
import Icono from '../components/Icono';
import useContador from '../hooks/useContador';

function CifraValor({ valor }) {
  const animado = useContador(valor);
  return <p className="metrica-valor">{valor == null ? '—' : animado}</p>;
}

function Tarjeta({ etiqueta, valor, icono, nota }) {
  return (
    <div className="tarjeta metrica">
      <span className="metrica-icono" aria-hidden="true">
        <Icono nombre={icono} tamano={20} />
      </span>
      <div>
        <CifraValor valor={valor} />
        <p className="metrica-etiqueta">{etiqueta}</p>
        {nota && <p className="metrica-nota">{nota}</p>}
      </div>
    </div>
  );
}

const formatoNumero = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 });

// H30: hasta 48 horas se muestra en horas; desde ahí, en días.
export function formatearDuracion(horas) {
  if (horas == null) return '—';
  if (horas < 1) return 'menos de 1 h';
  if (horas < 48) return `${formatoNumero.format(horas)} h`;
  return `${formatoNumero.format(horas / 24)} días`;
}

function fechaCorta(iso) {
  const [anio, mes, dia] = String(iso).split('-');
  return `${dia}/${mes}/${anio}`;
}

function textoRango(rango) {
  if (!rango?.desde && !rango?.hasta) return 'Todo el historial';
  if (rango.desde && rango.hasta) return `Del ${fechaCorta(rango.desde)} al ${fechaCorta(rango.hasta)}`;
  if (rango.desde) return `Desde el ${fechaCorta(rango.desde)}`;
  return `Hasta el ${fechaCorta(rango.hasta)}`;
}

function plural(n, singular, varios) {
  return `${n} ${n === 1 ? singular : varios}`;
}

// H31: ranking con barra proporcional al primero de la lista
function Ranking({ id, titulo, icono, filas, vacio, pie }) {
  const maximo = Math.max(1, ...filas.map((f) => f.total));
  return (
    <section className="tarjeta ranking" aria-labelledby={id}>
      <h2 id={id} className="ranking-titulo">
        <Icono nombre={icono} tamano={18} /> {titulo}
      </h2>
      {filas.length === 0 ? (
        <p className="ranking-vacio">{vacio}</p>
      ) : (
        <ol className="ranking-lista">
          {filas.map((f, i) => (
            <li key={`${f.nombre}-${i}`} className="ranking-fila">
              <div className="ranking-cabecera">
                <span className="ranking-puesto" aria-hidden="true">{i + 1}</span>
                <span className="ranking-nombre">{f.nombre}</span>
                <span className="ranking-total">{plural(f.total, 'publicación', 'publicaciones')}</span>
              </div>
              <div className="ranking-barra" aria-hidden="true">
                <span style={{ width: `${(f.total / maximo) * 100}%` }} />
              </div>
              <p className="ranking-detalle">
                {plural(f.perdidos, 'perdido', 'perdidos')} · {plural(f.encontrados, 'encontrado', 'encontrados')}
              </p>
            </li>
          ))}
        </ol>
      )}
      {pie && <p className="ranking-pie">{pie}</p>}
    </section>
  );
}

function Recuperacion({ r }) {
  const notas = [];
  if (r.sinFecha > 0) {
    notas.push(r.sinFecha === 1
      ? '1 recuperación antigua no tiene fecha y no se incluye.'
      : `${r.sinFecha} recuperaciones antiguas no tienen fecha y no se incluyen.`);
  }
  if (r.fechasIncoherentes > 0) {
    notas.push(r.fechasIncoherentes === 1
      ? '1 registro tiene fechas incoherentes y se excluye.'
      : `${r.fechasIncoherentes} registros tienen fechas incoherentes y se excluyen.`);
  }

  return (
    <section className="tarjeta recuperacion" aria-labelledby="titulo-recuperacion">
      <div className="recuperacion-principal">
        <span className="metrica-icono" aria-hidden="true">
          <Icono nombre="calendario" tamano={20} />
        </span>
        <div>
          <h2 id="titulo-recuperacion" className="metrica-etiqueta">Tiempo promedio de recuperación</h2>
          {r.muestra > 0 ? (
            <>
              <p className="metrica-valor" data-testid="promedio-recuperacion">{formatearDuracion(r.promedioHoras)}</p>
              <p className="metrica-nota">
                Desde que se publica un objeto perdido hasta que se confirma su recuperación.
                Basado en {plural(r.muestra, 'recuperación', 'recuperaciones')}.
              </p>
            </>
          ) : (
            <p className="recuperacion-vacia">Aún no hay recuperaciones con fecha en este periodo.</p>
          )}
        </div>
      </div>
      {r.muestra > 0 && (
        <dl className="recuperacion-detalle">
          <div><dt>Mediana</dt><dd>{formatearDuracion(r.medianaHoras)}</dd></div>
          <div><dt>Más rápida</dt><dd>{formatearDuracion(r.minimoHoras)}</dd></div>
          <div><dt>Más lenta</dt><dd>{formatearDuracion(r.maximoHoras)}</dd></div>
        </dl>
      )}
      {notas.length > 0 && <p className="ranking-pie">{notas.join(' ')}</p>}
    </section>
  );
}

const SIN_FILTRO = { desde: '', hasta: '' };

// H29/H30/H31 - Panel de indicadores con filtro de fechas (UTC, por fecha de publicación)
export default function Dashboard() {
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [filtros, setFiltros] = useState(SIN_FILTRO);
  const [aplicados, setAplicados] = useState(SIN_FILTRO);
  const [errorFiltro, setErrorFiltro] = useState('');
  const peticion = useRef(null);

  const cargar = useCallback(async () => {
    peticion.current?.abort();
    const control = new AbortController();
    peticion.current = control;
    setCargando(true);
    setError('');
    try {
      const data = await getDashboard(aplicados, { signal: control.signal });
      if (control.signal.aborted) return;
      setDatos(data);
    } catch (e) {
      if (control.signal.aborted) return;
      setError(mensajeError(e, 'No se pudo cargar el panel'));
      setDatos(null);
    } finally {
      if (!control.signal.aborted) setCargando(false);
    }
  }, [aplicados]);

  useEffect(() => {
    cargar();
    return () => peticion.current?.abort();
  }, [cargar]);

  const aplicar = (e) => {
    e.preventDefault();
    if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) {
      setErrorFiltro('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    setErrorFiltro('');
    setAplicados({ ...filtros });
  };

  const limpiar = () => {
    setErrorFiltro('');
    setFiltros(SIN_FILTRO);
    setAplicados(SIN_FILTRO);
  };

  const generales = [
    { etiqueta: 'Usuarios', valor: datos?.usuarios, icono: 'usuarios' },
    { etiqueta: 'Objetos activos', valor: datos?.objetosActivos, icono: 'caja' },
    // H29: las cuatro tarjetas históricas se conservan
    { etiqueta: 'Reportes pendientes', valor: datos?.reportesPendientes, icono: 'bandera' },
    { etiqueta: 'Categorías', valor: datos?.categorias, icono: 'etiqueta' },
  ];

  const periodo = datos?.objetos ? [
    { etiqueta: 'Perdidos', valor: datos.objetos.perdidos, icono: 'alerta' },
    { etiqueta: 'Encontrados', valor: datos.objetos.encontrados, icono: 'buscar' },
    { etiqueta: 'Recuperados', valor: datos.objetos.recuperados, icono: 'check', nota: 'Parte de los perdidos' },
    { etiqueta: 'Activos', valor: datos.objetos.activos, icono: 'caja' },
  ] : [];

  const hayFiltro = Boolean(aplicados.desde || aplicados.hasta || filtros.desde || filtros.hasta);

  return (
    <div className="pagina">
      <div>
        <h1 className="pagina-titulo">Dashboard</h1>
        <p className="pagina-subtitulo">Resumen de la actividad de FoundIA en Tecsup.</p>
      </div>

      <form className="tarjeta filtro-fechas" onSubmit={aplicar} noValidate aria-label="Filtrar por fecha de publicación">
        <label className="campo">
          <span className="campo-label">Desde</span>
          <input
            type="date"
            className={`input ${errorFiltro ? 'input-error' : ''}`}
            value={filtros.desde}
            onChange={(e) => setFiltros((f) => ({ ...f, desde: e.target.value }))}
          />
        </label>
        <label className="campo">
          <span className="campo-label">Hasta</span>
          <input
            type="date"
            className={`input ${errorFiltro ? 'input-error' : ''}`}
            value={filtros.hasta}
            onChange={(e) => setFiltros((f) => ({ ...f, hasta: e.target.value }))}
          />
        </label>
        <div className="filtro-acciones">
          <button type="submit" className="btn btn-primario" disabled={cargando}>Aplicar</button>
          {hayFiltro && (
            <button type="button" className="btn btn-fantasma" onClick={limpiar} disabled={cargando}>Limpiar</button>
          )}
        </div>
        {errorFiltro && <p className="campo-error filtro-error" role="alert">{errorFiltro}</p>}
        <p className="filtro-ayuda">Se filtra por fecha de publicación (hora UTC). Ocultos y eliminados no se cuentan.</p>
      </form>

      {error && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {error}
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>
            Reintentar
          </button>
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : datos && (
        <>
          <section className="dashboard-seccion" aria-labelledby="titulo-general">
            <h2 id="titulo-general" className="seccion-titulo">General</h2>
            <div className="grid-metricas">
              {generales.map((t) => <Tarjeta key={t.etiqueta} {...t} />)}
            </div>
          </section>

          {periodo.length > 0 && (
            <section className="dashboard-seccion" aria-labelledby="titulo-periodo">
              <h2 id="titulo-periodo" className="seccion-titulo">
                Publicaciones · <span className="seccion-rango">{textoRango(datos.rango)}</span>
              </h2>
              <div className="grid-metricas">
                {periodo.map((t) => <Tarjeta key={t.etiqueta} {...t} />)}
              </div>
            </section>
          )}

          {datos.recuperacion && <Recuperacion r={datos.recuperacion} />}

          {(datos.categoriasTop || datos.ubicacionesTop) && (
            <div className="grid-rankings">
              <Ranking
                id="ranking-categorias"
                titulo="Categorías con más reportes"
                icono="etiqueta"
                filas={datos.categoriasTop || []}
                vacio="No hay publicaciones en este periodo."
              />
              <Ranking
                id="ranking-ubicaciones"
                titulo="Ubicaciones con más reportes"
                icono="ubicacion"
                filas={datos.ubicacionesTop || []}
                vacio="No hay publicaciones con ubicación en este periodo."
                pie={datos.sinUbicacion > 0
                  ? `${plural(datos.sinUbicacion, 'publicación no indica', 'publicaciones no indican')} ubicación.`
                  : ''}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
