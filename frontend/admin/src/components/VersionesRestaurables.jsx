import { useCallback, useEffect, useState } from 'react';
import Icono from './Icono';
import RestaurarVersion from './RestaurarVersion';
import { tamanoLegible } from './UltimoRespaldo';
import { getRestauraciones, getVersionesRespaldo, mensajeError } from '../services/api';
import { fechaHora, Paginacion } from '../pages/Custodia';

// H70: versiones a las que se puede volver y el historial de restauraciones.
// El panel no restaura (reemplazar la base exige detener la API): muestra el comando para hacerlo en el servidor.
const VACIA = { count: 0, results: [], next: null, previous: null };
const MODOS = { ENSAYO: 'Ensayo', APLICADA: 'Aplicada' };
const ESTADOS = {
  COMPLETADA: { texto: 'Completada', clase: 'activo' },
  EN_PROCESO: { texto: 'En proceso', clase: 'recuperado' },
  FALLIDA: { texto: 'Fallida', clase: 'eliminado' },
};

export function comandoEnsayo(version) {
  return 'MSYS_NO_PATHCONV=1 docker run --rm --network proyectofoundia_default --entrypoint bash '
    + '-v proyectofoundia_respaldos_data:/respaldos -v proyectofoundia_uploads_data:/fotos-en-uso '
    + '-v "$PWD/scripts:/s:ro" -e DB_HOST=mysql -e DB_PASSWORD=<contraseña> -e FOTOS_EN_USO=/fotos-en-uso '
    + `-e VERSION=${version} proyectofoundia-respaldo /s/restaurar_version.sh`;
}

function useLista(cargarPagina, fallo) {
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try { setLista(await cargarPagina(pagina)); } catch (e) { setError(mensajeError(e, fallo)); setLista(VACIA); } finally { setCargando(false); }
  }, [cargarPagina, pagina, fallo]);
  useEffect(() => { cargar(); }, [cargar]);
  return { pagina, setPagina, lista, cargando, error, cargar };
}

function ErrorLista({ error, onReintentar }) {
  return (
    <div className="banner-error" role="alert">
      <span><Icono nombre="alerta" tamano={18} /> {error}</span>
      <button type="button" className="btn-fantasma btn-reintentar" onClick={onReintentar}>Reintentar</button>
    </div>
  );
}

export default function VersionesRestaurables() {
  const versiones = useLista(getVersionesRespaldo, 'No se pudieron cargar las versiones');
  const historial = useLista(getRestauraciones, 'No se pudo cargar el historial de restauraciones');
  const [elegida, setElegida] = useState(null);
  const [copiado, setCopiado] = useState(false);
  const [aRestaurar, setARestaurar] = useState(null);

  const copiar = async () => {
    try { await navigator.clipboard.writeText(comandoEnsayo(elegida)); setCopiado(true); } catch { setCopiado(false); }
  };

  return (
    <>
      <section className="respaldos-historial" aria-labelledby="titulo-versiones">
        <h2 id="titulo-versiones" className="seccion-titulo">Versiones a las que se puede volver</h2>
        <p className="nota-tabla">
          Una versión es el respaldo de la base y el de las fotos de la misma ejecución. Restaurarla reemplaza los datos
          actuales, crea antes un respaldo de seguridad para deshacerlo y cierra todas las sesiones.
        </p>
        {versiones.error && <ErrorLista error={versiones.error} onReintentar={versiones.cargar} />}
        {versiones.cargando ? (
          <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
        ) : versiones.error ? null : versiones.lista.results.length === 0 ? (
          <p className="panel-vacio">Todavía no hay versiones completas de base y fotos.</p>
        ) : (
          <>
            <div className="tarjeta tabla-contenedor">
              <table className="tabla">
                <thead><tr><th>Versión</th><th>Fecha</th><th>Disponible</th><th>Archivos</th><th>Acción</th></tr></thead>
                <tbody>
                  {versiones.lista.results.map((v) => (
                    <tr key={v.version}>
                      <td data-label="Versión"><code>{v.version}</code></td>
                      <td data-label="Fecha">{fechaHora(v.fecha)}</td>
                      <td data-label="Disponible">
                        <span className={`chip-estado ${v.disponible ? 'activo' : 'eliminado'}`}>{v.disponible ? 'Sí' : 'No'}</span>
                        {!v.disponible && v.motivo && <span className="celda-detalle">{v.motivo}</span>}
                      </td>
                      <td data-label="Archivos">
                        <span className="celda-detalle">Base: {v.base ? tamanoLegible(v.base.tamanoBytes) : '—'}</span>
                        <span className="celda-detalle">Fotos: {v.fotos ? tamanoLegible(v.fotos.tamanoBytes) : '—'}</span>
                      </td>
                      <td data-label="Acción">
                        {v.disponible ? (
                          <button type="button" className="btn btn-fantasma btn-mini" onClick={() => { setElegida(v.version); setCopiado(false); }}
                            aria-label={`Ver cómo restaurar la versión ${v.version}`}>Cómo restaurar</button>
                        ) : <span className="nota-tabla">No se puede</span>}
                        {v.disponible && (
                          <button type="button" className="btn btn-peligro btn-mini" onClick={() => setARestaurar(v.version)}
                            aria-label={`Restaurar la versión ${v.version}`}>Restaurar</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacion pagina={versiones.pagina} lista={versiones.lista} onCambiar={versiones.setPagina} />
          </>
        )}

        {aRestaurar && (
          <RestaurarVersion key={aRestaurar} version={aRestaurar} onCerrar={() => setARestaurar(null)}
            onTerminada={() => historial.cargar()} />
        )}

        {elegida && (
          <div className="tarjeta respaldos-procedimiento" role="region" aria-label={`Cómo restaurar la versión ${elegida}`}>
            <h3 className="seccion-titulo">Restaurar la versión {elegida}</h3>
            <ol className="respaldos-pasos">
              <li><strong>Primero ensaya:</strong> este comando restaura la versión en una base aislada y compara los datos. No cambia nada en uso.</li>
              <li><strong>Para aplicarla,</strong> detén la API, el panel y el respaldo (<code>docker compose stop api admin-api respaldo</code>) y repite el comando agregando <code>-e RESTAURACION_APLICAR=si -e RESTAURACION_RESPONSABLE="Tu nombre"</code>.</li>
              <li><strong>Al terminar,</strong> levanta todo con <code>docker compose up -d</code>. El resultado aparece abajo en el historial.</li>
            </ol>
            <pre className="comando-restaurar"><code>{comandoEnsayo(elegida)}</code></pre>
            <div className="acciones-fila">
              <span className="nota-tabla">Cambia <code>&lt;contraseña&gt;</code> por la de la base y revisa los nombres con <code>docker volume ls</code>.</span>
              <button type="button" className="btn btn-secundario btn-mini" onClick={copiar}>{copiado ? 'Copiado' : 'Copiar comando'}</button>
              <button type="button" className="btn btn-fantasma btn-mini" onClick={() => setElegida(null)}>Cerrar</button>
            </div>
          </div>
        )}
      </section>

      <section className="respaldos-historial" aria-labelledby="titulo-restauraciones">
        <h2 id="titulo-restauraciones" className="seccion-titulo">Historial de restauraciones</h2>
        {historial.error && <ErrorLista error={historial.error} onReintentar={historial.cargar} />}
        {historial.cargando ? (
          <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
        ) : historial.error ? null : historial.lista.results.length === 0 ? (
          <p className="panel-vacio">Todavía no se ensayó ni se aplicó ninguna restauración.</p>
        ) : (
          <>
            <div className="tarjeta tabla-contenedor">
              <table className="tabla">
                <thead><tr><th>Versión</th><th>Modo</th><th>Estado</th><th>Responsable</th><th>Resultado</th></tr></thead>
                <tbody>
                  {historial.lista.results.map((r) => {
                    const e = ESTADOS[r.estado] || { texto: r.estado, clase: '' };
                    return (
                      <tr key={r.id}>
                        <td data-label="Versión"><code>{r.version}</code><span className="celda-detalle">{fechaHora(r.iniciadoEn)}</span></td>
                        <td data-label="Modo">{MODOS[r.modo] || r.modo}</td>
                        <td data-label="Estado"><span className={`chip-estado ${e.clase}`}>{e.texto}</span></td>
                        <td data-label="Responsable">{r.responsable || '—'}</td>
                        <td data-label="Resultado">
                          <span className="celda-detalle">{r.mensaje || '—'}</span>
                          {r.respaldoSeguridad && <span className="celda-detalle">Respaldo de seguridad: <code>{r.respaldoSeguridad}</code></span>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Paginacion pagina={historial.pagina} lista={historial.lista} onCambiar={historial.setPagina} />
          </>
        )}
      </section>
    </>
  );
}
