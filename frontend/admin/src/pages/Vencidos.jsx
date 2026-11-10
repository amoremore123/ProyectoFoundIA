import { useCallback, useEffect, useRef, useState } from 'react';
import { disponerCustodia, getActaDisposicion, getCustodiasVencidas, guardarArchivo, mensajeError } from '../services/api';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import { fechaHora } from './Custodia';

const DESTINOS = { DONADO: 'Donar', DESCARTADO: 'Descartar' };
const MOTIVO_MIN = 10;
const MOTIVO_MAX = 1000;

// H54 - Objetos que cumplieron el plazo de custodia sin ser reclamados: disposición final con acta.
// Nada se borra: la custodia queda como «dispuesta», el objeto se oculta y el acta registra responsable y fecha.
export default function Vencidos() {
  const { mostrarToast } = useToast();
  const [datos, setDatos] = useState({ plazoDias: null, resultados: [] });
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [seleccionada, setSeleccionada] = useState(null);
  const [form, setForm] = useState({ destino: 'DONADO', motivo: '' });
  const [errorForm, setErrorForm] = useState('');
  const [confirmar, setConfirmar] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [dispuestas, setDispuestas] = useState([]); // actas generadas en esta sesión
  const [descargando, setDescargando] = useState(null);
  const [errorActa, setErrorActa] = useState('');
  const panel = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const r = await getCustodiasVencidas();
      setDatos({ plazoDias: r?.plazoDias ?? null, resultados: r?.resultados || [] });
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar los objetos vencidos'));
      setDatos({ plazoDias: null, resultados: [] });
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);
  useEffect(() => { if (seleccionada) panel.current?.querySelector('textarea')?.focus(); }, [seleccionada]);

  const abrir = (c) => {
    setSeleccionada(c);
    setForm({ destino: 'DONADO', motivo: '' });
    setErrorForm('');
  };

  const pedirConfirmacion = (e) => {
    e.preventDefault();
    const motivo = form.motivo.trim();
    if (motivo.length < MOTIVO_MIN) { setErrorForm(`Explica el motivo (al menos ${MOTIVO_MIN} caracteres).`); return; }
    if (motivo.length > MOTIVO_MAX) { setErrorForm(`El motivo admite hasta ${MOTIVO_MAX} caracteres.`); return; }
    setErrorForm('');
    setConfirmar(true);
  };

  const disponer = async () => {
    setGuardando(true);
    try {
      const r = await disponerCustodia(seleccionada.id, { destino: form.destino, motivo: form.motivo.trim() });
      setDispuestas((prev) => [{ id: r.id, codigo: r.codigo, nombre: r.objeto?.nombre, disposicion: r.disposicion }, ...prev]);
      mostrarToast(`${r.codigo} quedó como ${form.destino === 'DONADO' ? 'donado' : 'descartado'}. Descarga el acta.`, 'exito');
      setSeleccionada(null);
      cargar();
    } catch (err) {
      setErrorForm(mensajeError(err, 'No se pudo registrar la disposición'));
    } finally {
      setGuardando(false);
      setConfirmar(false);
    }
  };

  const descargarActa = async (d) => {
    setDescargando(d.id);
    setErrorActa('');
    try {
      guardarArchivo(await getActaDisposicion(d.id), `${d.disposicion?.codigoActa || `acta-${d.codigo}`}.pdf`);
    } catch (err) {
      setErrorActa(mensajeError(err, 'No se pudo descargar el acta'));
    } finally {
      setDescargando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Objetos vencidos</h1>
          <p className="pagina-subtitulo">
            {datos.plazoDias
              ? `Objetos en custodia hace más de ${datos.plazoDias} días sin que nadie los retire.`
              : 'Objetos que superaron el plazo de custodia sin ser retirados.'}
          </p>
        </div>
        <span className="contador">{datos.resultados.length} vencidos</span>
      </div>

      {dispuestas.length > 0 && (
        <section className="tarjeta formulario" aria-labelledby="titulo-actas">
          <h2 id="titulo-actas"><Icono nombre="archivo" tamano={18} /> Actas generadas</h2>
          <ul className="historial-lista">
            {dispuestas.map((d) => (
              <li key={d.id}>
                <strong>{d.disposicion?.codigoActa || d.codigo}</strong> · {d.nombre}
                <button type="button" className="btn btn-fantasma btn-mini acta-boton" onClick={() => descargarActa(d)}
                  disabled={descargando != null} aria-label={`Descargar el acta de ${d.codigo}`}>
                  <Icono nombre="descargar" tamano={14} /> {descargando === d.id ? 'Generando...' : 'Descargar acta'}
                </button>
              </li>
            ))}
          </ul>
          {errorActa && <p className="campo-error" role="alert">{errorActa}</p>}
        </section>
      )}

      {seleccionada && (
        <form ref={panel} className="tarjeta formulario resolver-panel" onSubmit={pedirConfirmacion} noValidate
          aria-label={`Disponer de ${seleccionada.codigo}`}>
          <h2>Disponer de {seleccionada.codigo}</h2>
          <p className="nota-tabla">
            {seleccionada.objeto?.nombre} · {seleccionada.diasEnCustodia} días en custodia
            {seleccionada.reclamosPendientes > 0 && ` · ${seleccionada.reclamosPendientes} reclamo(s) pendiente(s) se cerrarán con aviso`}
          </p>
          <fieldset className="resolver-opciones">
            <legend className="campo-label">Destino</legend>
            {Object.entries(DESTINOS).map(([valor, texto]) => (
              <label key={valor}>
                <input type="radio" name="destino" value={valor} checked={form.destino === valor}
                  onChange={() => setForm((f) => ({ ...f, destino: valor }))} /> {texto}
              </label>
            ))}
          </fieldset>
          <label className="campo">
            <span className="campo-label">Motivo *</span>
            <textarea className={`input textarea${errorForm ? ' input-error' : ''}`} rows={3} maxLength={MOTIVO_MAX}
              value={form.motivo} disabled={guardando}
              onChange={(e) => { const motivo = e.target.value; setForm((f) => ({ ...f, motivo })); setErrorForm(''); }}
              placeholder="Ej. Sin reclamos tras el plazo; se dona a la campaña de la biblioteca." />
          </label>
          {errorForm && <p className="campo-error" role="alert">{errorForm}</p>}
          <p className="nota-tabla">Se generará un acta con tu nombre y la fecha. El objeto no se borra: deja de mostrarse en la búsqueda.</p>
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={() => setSeleccionada(null)} disabled={guardando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardando}>Continuar</button>
          </div>
        </form>
      )}

      {error && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : error ? null : datos.resultados.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="reloj" tamano={28} /></span>
          <p>No hay objetos vencidos. Todos siguen dentro del plazo de custodia.</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr><th>Código</th><th>Objeto</th><th>Recibido</th><th>Días</th><th>Reclamos</th><th>Acción</th></tr>
            </thead>
            <tbody>
              {datos.resultados.map((c) => (
                <tr key={c.id}>
                  <td data-label="Código"><strong>{c.codigo}</strong></td>
                  <td data-label="Objeto">{c.objeto?.nombre}<span className="celda-detalle">#{c.objeto?.id} · {c.objeto?.categoria || 'Sin categoría'}</span></td>
                  <td data-label="Recibido">{fechaHora(c.fechaRecepcion)}</td>
                  <td data-label="Días">{c.diasEnCustodia}</td>
                  <td data-label="Reclamos">{c.bloqueada ? 'Aceptado (por retirar)' : c.reclamosPendientes > 0 ? `${c.reclamosPendientes} pendiente(s)` : 'Ninguno'}</td>
                  <td data-label="Acción">
                    {c.bloqueada ? (
                      <span className="nota-acciones">Esperando retiro</span>
                    ) : (
                      <button type="button" className="btn btn-primario btn-mini" onClick={() => abrir(c)} disabled={guardando}
                        aria-label={`Disponer de ${c.codigo}`}>Disponer</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmarDialogo
        abierto={confirmar}
        titulo={form.destino === 'DONADO' ? '¿Registrar la donación?' : '¿Registrar el descarte?'}
        mensaje={seleccionada ? `${seleccionada.objeto?.nombre} (${seleccionada.codigo}) quedará como ${form.destino === 'DONADO' ? 'donado' : 'descartado'}. Esta decisión no se puede deshacer.` : ''}
        textoConfirmar={form.destino === 'DONADO' ? 'Sí, donar' : 'Sí, descartar'}
        peligro={form.destino === 'DESCARTADO'}
        procesando={guardando}
        onConfirmar={disponer}
        onCancelar={() => setConfirmar(false)}
      />
    </div>
  );
}
