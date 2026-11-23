import { useCallback, useEffect, useRef, useState } from 'react';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import { CHAT_URL, getReportesMensajes, mensajeError, resolverReporteMensaje, statusDe } from '../services/api';
import { fechaHora, Paginacion } from './Custodia';

// H56: mensajes del chat que alguien reportó. El administrador ve el texto y el autor (sin correos ni
// teléfonos) y decide: descartar, advertir al autor (sin decir quién reportó) o suspender su cuenta.
const ESTADOS = {
  PENDIENTE: { texto: 'Pendiente', clase: 'pendiente' },
  RESUELTO: { texto: 'Resuelto', clase: 'activo' },
  RECHAZADO: { texto: 'Descartado', clase: '' },
};
const SEGMENTOS = [
  { valor: 'PENDIENTE', texto: 'Pendientes' },
  { valor: 'RESUELTO', texto: 'Resueltos' },
  { valor: 'RECHAZADO', texto: 'Descartados' },
  { valor: '', texto: 'Todos' },
];
const ACCIONES = {
  DESCARTAR: { texto: 'Descartar (el reporte no procede)', confirmar: 'Sí, descartar', aviso: 'El reporte quedará descartado.' },
  ADVERTIR: { texto: 'Advertir al autor', confirmar: 'Sí, advertir', aviso: 'El autor recibirá un aviso con tu motivo. No se le dice quién reportó.' },
  SUSPENDER: { texto: 'Suspender la cuenta del autor', confirmar: 'Sí, suspender', aviso: 'La cuenta quedará suspendida y su sesión dejará de valer en la web, la app y el panel.' },
};
const VACIA = { count: 0, results: [], next: null, previous: null };

function fotoUrl(url) {
  if (!url) return '';
  return /^https?:\/\//.test(url) ? url : `${CHAT_URL}${url.startsWith('/') ? '' : '/'}${url}`;
}

export default function ReportesChat() {
  const { mostrarToast } = useToast();
  const [estado, setEstado] = useState('PENDIENTE');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [seleccionado, setSeleccionado] = useState(null);
  const [accion, setAccion] = useState('ADVERTIR');
  const [resolucion, setResolucion] = useState('');
  const [errorResolver, setErrorResolver] = useState('');
  const [confirmar, setConfirmar] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const panel = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getReportesMensajes({ estado }, pagina));
    } catch (e) {
      setError(statusDe(e) === 403 ? 'Solo un administrador puede ver los reportes del chat.' : mensajeError(e, 'No se pudieron cargar los reportes'));
      setLista(VACIA);
    } finally {
      setCargando(false);
    }
  }, [estado, pagina]);

  useEffect(() => { cargar(); }, [cargar]);
  useEffect(() => { if (seleccionado) panel.current?.querySelector('textarea')?.focus(); }, [seleccionado]);

  const abrir = (r) => { setSeleccionado(r); setAccion('ADVERTIR'); setResolucion(''); setErrorResolver(''); };

  const pedirConfirmacion = (e) => {
    e.preventDefault();
    const limpio = resolucion.trim();
    if (limpio.length < 3 || limpio.length > 1000) { setErrorResolver('Explica la decisión en 3 a 1000 caracteres.'); return; }
    setConfirmar(true);
  };

  const resolver = async () => {
    setGuardando(true);
    try {
      const r = await resolverReporteMensaje(seleccionado.id, { accion, resolucion: resolucion.trim() });
      mostrarToast(r?.repetido ? 'Ese reporte ya tenía esta misma resolución.' : 'Reporte resuelto.', 'exito');
      setSeleccionado(null);
      cargar();
    } catch (err) {
      setErrorResolver(statusDe(err) === 409 ? 'Este reporte ya se resolvió con otra decisión. Recarga la bandeja.' : mensajeError(err, 'No se pudo resolver el reporte'));
    } finally {
      setGuardando(false);
      setConfirmar(false);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Reportes del chat</h1>
          <p className="pagina-subtitulo">Mensajes que alguien reportó. Se muestran el texto y el autor; nunca correos ni teléfonos.</p>
        </div>
      </div>

      {seleccionado && (
        <form ref={panel} className="tarjeta formulario resolver-panel" onSubmit={pedirConfirmacion} noValidate aria-label={`Resolver reporte #${seleccionado.id}`}>
          <h2>Resolver reporte #{seleccionado.id}</h2>
          <blockquote className="reporte-chat-mensaje">
            {seleccionado.mensaje?.imagenUrl && <img src={fotoUrl(seleccionado.mensaje.imagenUrl)} alt="Foto del mensaje reportado" />}
            {seleccionado.mensaje?.texto && <p>{seleccionado.mensaje.texto}</p>}
            <footer className="nota-tabla">{seleccionado.autor?.nombre} · {fechaHora(seleccionado.mensaje?.enviadoEn)}</footer>
          </blockquote>
          <p className="nota-tabla">Motivo de quien reportó: «{seleccionado.motivo}»</p>
          <fieldset className="resolver-opciones resolver-opciones-columna">
            <legend className="campo-label">Decisión</legend>
            {Object.entries(ACCIONES).map(([valor, a]) => (
              <label key={valor}>
                <input type="radio" name="accion" value={valor} checked={accion === valor} onChange={() => setAccion(valor)} disabled={guardando} /> {a.texto}
              </label>
            ))}
          </fieldset>
          <label className="campo">
            <span className="campo-label">Motivo de la decisión *</span>
            <textarea className={`input textarea${errorResolver ? ' input-error' : ''}`} rows={3} maxLength={1000} value={resolucion}
              disabled={guardando} onChange={(e) => { setResolucion(e.target.value); setErrorResolver(''); }}
              aria-invalid={Boolean(errorResolver)} placeholder="Ej. Lenguaje ofensivo en el chat" />
          </label>
          {errorResolver && <p className="campo-error" role="alert">{errorResolver}</p>}
          <p className="nota-tabla">{ACCIONES[accion].aviso} La decisión no se puede cambiar.</p>
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={() => setSeleccionado(null)} disabled={guardando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardando}>Continuar</button>
          </div>
        </form>
      )}

      <div className="filtro-segmentos" role="group" aria-label="Filtrar reportes por estado">
        {SEGMENTOS.map((s) => (
          <button key={s.valor || 'todos'} type="button" className={`btn btn-mini ${estado === s.valor ? 'btn-primario' : 'btn-fantasma'}`}
            aria-pressed={estado === s.valor} onClick={() => { setEstado(s.valor); setPagina(1); }}>{s.texto}</button>
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
          <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="check" tamano={28} /></span>
          <p>{estado === 'PENDIENTE' ? 'No hay reportes pendientes.' : 'No hay reportes en esta vista.'}</p>
        </div>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead><tr><th>Mensaje</th><th>Autor</th><th>Reportado por</th><th>Estado</th><th>Acción</th></tr></thead>
              <tbody>
                {lista.results.map((r) => {
                  const e = ESTADOS[r.estado] || { texto: r.estado, clase: '' };
                  return (
                    <tr key={r.id}>
                      <td data-label="Mensaje">
                        {r.mensaje?.texto || (r.mensaje?.imagenUrl ? 'Foto' : '—')}
                        <span className="celda-detalle">Motivo: {r.motivo} · {fechaHora(r.fechaReporte)}</span>
                      </td>
                      <td data-label="Autor">{r.autor?.nombre}{r.autor?.estado === 'SUSPENDIDO' && <span className="celda-detalle">Cuenta suspendida</span>}</td>
                      <td data-label="Reportado por">{r.reportadoPor?.nombre}</td>
                      <td data-label="Estado">
                        <span className={`chip-estado ${e.clase}`}>{e.texto}</span>
                        {r.resolucion && <span className="celda-detalle">{r.resolucion}{r.resueltoPor ? ` · ${r.resueltoPor.nombre}` : ''}</span>}
                      </td>
                      <td data-label="Acción">
                        {r.estado === 'PENDIENTE' ? (
                          <button type="button" className="btn btn-primario btn-mini" onClick={() => abrir(r)} aria-label={`Resolver reporte #${r.id}`}>Resolver</button>
                        ) : <span className="nota-tabla">{r.accion ? r.accion.charAt(0) + r.accion.slice(1).toLowerCase() : '—'}</span>}
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

      <ConfirmarDialogo
        abierto={confirmar}
        titulo={accion === 'SUSPENDER' ? '¿Suspender la cuenta del autor?' : accion === 'ADVERTIR' ? '¿Advertir al autor?' : '¿Descartar este reporte?'}
        mensaje={seleccionado ? `${seleccionado.autor?.nombre}: ${ACCIONES[accion].aviso}` : ''}
        textoConfirmar={ACCIONES[accion].confirmar}
        peligro={accion === 'SUSPENDER'}
        procesando={guardando}
        onConfirmar={resolver}
        onCancelar={() => setConfirmar(false)}
      />
    </div>
  );
}
