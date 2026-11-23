import { useCallback, useEffect, useState } from 'react';
import Icono from './Icono';
import { getAlcanceCampana, getAlcanceCampanas, mensajeError } from '../services/api';

// H67: cuántas visitas y registros llegaron por el enlace de cada campaña. Sin datos personales:
// solo conteos por origen. Los números son exactamente los que devuelve la API.
function haceDias(n) {
  const d = new Date(Date.now() - n * 86400000);
  return d.toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
}

function formatoDia(valor) {
  const [a, m, d] = valor.split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}

function DetallePorDia({ campana, rango, onCerrar }) {
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let vivo = true;
    setDatos(null);
    setError('');
    getAlcanceCampana(campana.id, rango)
      .then((d) => { if (vivo) setDatos(d); })
      .catch((e) => { if (vivo) setError(mensajeError(e, 'No se pudo cargar el detalle')); });
    return () => { vivo = false; };
  }, [campana.id, rango]);

  const maximo = datos ? Math.max(1, ...datos.porDia.map((d) => d.visitas)) : 1;
  return (
    <div className="tarjeta alcance-detalle" role="region" aria-label={`Alcance por día de ${campana.nombre}`}>
      <div className="acciones-fila">
        <h3 className="seccion-titulo">{campana.nombre}: visitas por día</h3>
        <button type="button" className="btn btn-fantasma btn-mini" onClick={onCerrar}>Cerrar</button>
      </div>
      {error && <p className="campo-error" role="alert">{error}</p>}
      {!datos && !error && <p className="panel-vacio" role="status">Cargando...</p>}
      {datos && (
        <>
          <p className="nota-tabla">
            Del {formatoDia(datos.desde)} al {formatoDia(datos.hasta)}: {datos.visitas} visitas, {datos.registros} registros
            ({datos.conversion}% de conversión).
          </p>
          <ol className="alcance-barras" aria-label="Visitas y registros por día">
            {datos.porDia.map((d) => (
              <li key={d.fecha}>
                <span className="alcance-dia">{formatoDia(d.fecha)}</span>
                <span className="alcance-barra" aria-hidden="true"><span style={{ width: `${(d.visitas / maximo) * 100}%` }} /></span>
                <span className="alcance-cifras">{d.visitas} visitas · {d.registros} registros</span>
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  );
}

export default function AlcanceCampanas() {
  const [rango, setRango] = useState({ desde: haceDias(30), hasta: haceDias(0) });
  const [borrador, setBorrador] = useState(rango);
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  const [errorRango, setErrorRango] = useState('');
  const [elegida, setElegida] = useState(null);

  const cargar = useCallback(async () => {
    setError('');
    try {
      setDatos(await getAlcanceCampanas(rango));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo cargar el alcance de las campañas'));
      setDatos({ campanas: [], total: { visitas: 0, registros: 0 } });
    }
  }, [rango]);

  useEffect(() => { cargar(); }, [cargar]);

  const aplicar = (e) => {
    e.preventDefault();
    if (!borrador.desde || !borrador.hasta) { setErrorRango('Elige las dos fechas.'); return; }
    if (borrador.hasta < borrador.desde) { setErrorRango('La fecha final no puede ser anterior a la inicial.'); return; }
    setErrorRango('');
    setElegida(null);
    setRango({ ...borrador });
  };

  return (
    <section className="respaldos-historial" aria-labelledby="titulo-alcance">
      <h2 id="titulo-alcance" className="seccion-titulo"><Icono nombre="grafico" tamano={18} /> Alcance de las campañas</h2>
      <p className="nota-tabla">Visitas que llegaron por el enlace de cada campaña y cuentas creadas desde ella. No se guarda quién fue.</p>
      <form className="alcance-rango" onSubmit={aplicar} noValidate aria-label="Rango de fechas del alcance">
        <label className="campo"><span className="campo-label">Desde</span>
          <input type="date" className="input" value={borrador.desde} onChange={(e) => setBorrador((b) => ({ ...b, desde: e.target.value }))} />
        </label>
        <label className="campo"><span className="campo-label">Hasta</span>
          <input type="date" className="input" value={borrador.hasta} onChange={(e) => setBorrador((b) => ({ ...b, hasta: e.target.value }))} />
        </label>
        <button type="submit" className="btn btn-secundario">Ver alcance</button>
      </form>
      {errorRango && <p className="campo-error" role="alert">{errorRango}</p>}
      {error && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}
      {!datos ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : datos.campanas.length === 0 ? (
        !error && <p className="panel-vacio">No hay campañas en ese rango.</p>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead><tr><th>Campaña</th><th>Visitas</th><th>Registros</th><th>Conversión</th><th>Detalle</th></tr></thead>
            <tbody>
              {datos.campanas.map((c) => (
                <tr key={c.id}>
                  <td data-label="Campaña">{c.nombre}<span className="celda-detalle">{c.origen}</span></td>
                  <td data-label="Visitas">{c.visitas}</td>
                  <td data-label="Registros">{c.registros}</td>
                  <td data-label="Conversión">{c.conversion}%</td>
                  <td data-label="Detalle">
                    <button type="button" className="btn btn-fantasma btn-mini" onClick={() => setElegida(c)} aria-label={`Ver por día ${c.nombre}`}>Por día</button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr><th scope="row">Total</th><td>{datos.total.visitas}</td><td>{datos.total.registros}</td><td colSpan={2} /></tr>
            </tfoot>
          </table>
        </div>
      )}
      {elegida && <DetallePorDia campana={elegida} rango={rango} onCerrar={() => setElegida(null)} />}
    </section>
  );
}
