import { useCallback, useEffect, useState } from 'react';
import Icono from '../components/Icono';
import UltimoRespaldo, { huellaCorta, tamanoLegible } from '../components/UltimoRespaldo';
import VersionesRestaurables from '../components/VersionesRestaurables';
import { getRespaldos, mensajeError } from '../services/api';
import { fechaHora, Paginacion } from './Custodia';

// H58/H59: respaldos de la base y de las fotos, su historial y cómo recuperar todo tras una falla.
// Solo lectura: los respaldos los crea el servicio programado de Docker, nunca el panel.
const ESTADOS = {
  COMPLETADO: { texto: 'Completado', clase: 'activo' },
  EN_PROCESO: { texto: 'En proceso', clase: 'recuperado' },
  FALLIDO: { texto: 'Fallido', clase: 'eliminado' },
};
const TIPOS = { BASE: 'Base de datos', FOTOS: 'Fotos' };
const SEGMENTOS = [
  { valor: '', texto: 'Todos' },
  { valor: 'BASE', texto: 'Base de datos' },
  { valor: 'FOTOS', texto: 'Fotos' },
];
const VACIA = { count: 0, results: [], next: null, previous: null };

export default function Respaldos() {
  const [tipo, setTipo] = useState('');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getRespaldos({ tipo }, pagina));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo cargar el historial de respaldos'));
      setLista(VACIA);
    } finally {
      setCargando(false);
    }
  }, [tipo, pagina]);

  useEffect(() => { cargar(); }, [cargar]);

  const cambiarTipo = (valor) => { setTipo(valor); setPagina(1); };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Respaldos</h1>
          <p className="pagina-subtitulo">
            Copias automáticas de la base de datos y de las fotos de las publicaciones. Cada archivo se
            verifica con su huella SHA-256 antes de darlo por completado.
          </p>
        </div>
      </div>

      <div className="respaldos-ultimos">
        <UltimoRespaldo tipo="BASE" conHuella />
        <UltimoRespaldo tipo="FOTOS" conHuella />
      </div>

      <section className="respaldos-historial" aria-labelledby="titulo-historial">
        <h2 id="titulo-historial" className="seccion-titulo">Historial</h2>
        <div className="filtro-segmentos" role="group" aria-label="Filtrar respaldos por tipo">
          {SEGMENTOS.map((s) => (
            <button key={s.valor || 'todos'} type="button" className={`btn btn-mini ${tipo === s.valor ? 'btn-primario' : 'btn-fantasma'}`}
              aria-pressed={tipo === s.valor} onClick={() => cambiarTipo(s.valor)}>{s.texto}</button>
          ))}
        </div>
        {error && (
          <div className="banner-error" role="alert">
            <span><Icono nombre="alerta" tamano={18} /> {error}</span>
            <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
          </div>
        )}
        {cargando ? (
          <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
        ) : error ? null : lista.results.length === 0 ? (
          <div className="estado-vacio">
            <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="escudo" tamano={28} /></span>
            <p>{tipo ? `Todavía no hay respaldos de ${TIPOS[tipo].toLowerCase()}.` : 'Todavía no hay respaldos registrados.'}</p>
          </div>
        ) : (
          <>
            <div className="tarjeta tabla-contenedor">
              <table className="tabla">
                <thead>
                  <tr><th>Tipo</th><th>Inicio</th><th>Estado</th><th>Tamaño</th><th>Archivo y huella</th></tr>
                </thead>
                <tbody>
                  {lista.results.map((r) => {
                    const e = ESTADOS[r.estado] || { texto: r.estado, clase: '' };
                    return (
                      <tr key={r.id}>
                        <td data-label="Tipo">{TIPOS[r.tipo] || r.tipo || TIPOS.BASE}</td>
                        <td data-label="Inicio">{fechaHora(r.iniciadoEn)}</td>
                        <td data-label="Estado">
                          <span className={`chip-estado ${e.clase}`}>{e.texto}</span>
                          {r.estado === 'FALLIDO' && <span className="celda-detalle">{r.mensaje || 'Falló sin mensaje del servidor.'}</span>}
                        </td>
                        <td data-label="Tamaño">{tamanoLegible(r.tamanoBytes)}</td>
                        <td data-label="Archivo y huella">
                          <span className="respaldo-nombre">{r.nombreArchivo || '—'}</span>
                          <span className="celda-detalle"><code title={r.huellaSha256 || undefined}>{huellaCorta(r.huellaSha256)}</code></span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Paginacion pagina={pagina} lista={lista} onCambiar={setPagina} />
          </>
        )}
      </section>

      <section className="tarjeta respaldos-procedimiento" aria-labelledby="titulo-procedimiento">
        <h2 id="titulo-procedimiento"><Icono nombre="alerta" tamano={18} /> Si se pierden los datos: cómo recuperar</h2>
        <p className="nota-tabla">
          La recuperación la hace una persona con acceso al servidor, no el panel. El procedimiento completo y
          los comandos están en <code>docs/api/sprint4-h58.md</code>.
        </p>
        <ol className="respaldos-pasos">
          <li><strong>No borres nada.</strong> Nunca uses <code>docker compose down -v</code> ni borres el volumen de respaldos: sin respaldos no hay recuperación.</li>
          <li><strong>Detén la API, el panel y el servicio de respaldo</strong> para que nadie escriba mientras se recupera.</li>
          <li><strong>Levanta MySQL</strong> y espera a que termine de iniciar.</li>
          <li>
            <strong>Ejecuta <code>scripts/recuperar_todo.sh</code>.</strong> Usa el respaldo de la base y el de fotos más
            recientes, rechaza un par con más de 1 hora de diferencia y comprueba la huella de cada archivo antes de restaurarlo.
          </li>
          <li><strong>Revisa el resultado:</strong> el script compara usuarios, objetos, fotos y custodias, y avisa si alguna foto registrada no tiene su archivo.</li>
          <li><strong>Levanta el resto de los servicios</strong> y comprueba en la web que las publicaciones y sus fotos se ven.</li>
        </ol>
        <p className="nota-tabla">
          Para volver a una versión anterior concreta (H70), usa la sección «Versiones a las que se puede volver».
        </p>
      </section>

      {/* H70: versiones restaurables e historial (solo lectura) */}
      <VersionesRestaurables />
    </div>
  );
}
