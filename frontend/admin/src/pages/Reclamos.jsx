import { useCallback, useEffect, useRef, useState } from 'react';
import {
  abrirArchivo, getQrRetiro, getReclamosCustodia, mensajeError, registrarRetiro, resolverReclamo, verificarQrRetiro,
} from '../services/api';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import { fechaHora, Paginacion } from './Custodia';

const ESTADOS = {
  PENDIENTE: { texto: 'Pendiente', clase: 'pendiente' },
  ACEPTADO: { texto: 'Aceptado', clase: 'activo' },
  RECHAZADO: { texto: 'Rechazado', clase: 'eliminado' },
  RETIRADO: { texto: 'Retirado', clase: 'recuperado' },
};
const SEGMENTOS = [
  { valor: 'PENDIENTE', texto: 'Pendientes' },
  { valor: 'ACEPTADO', texto: 'Aceptados' },
  { valor: 'RETIRADO', texto: 'Retirados' },
  { valor: 'RECHAZADO', texto: 'Rechazados' },
  { valor: '', texto: 'Todos' },
];
const ESTADO_QR = {
  VIGENTE: 'Vigente: se puede entregar el objeto.',
  VENCIDO: 'El QR venció. El reclamante debe pedir uno nuevo.',
  USADO: 'Este QR ya se usó: el objeto ya fue retirado.',
  SIN_QR: 'El reclamo no tiene QR de retiro.',
  NO_VIGENTE: 'El reclamo ya no está aceptado.',
};
const MAX_MOTIVO = 1000;

// H43 - Bandeja de reclamos de objetos en custodia: aceptar o rechazar con motivo y registrar el retiro con QR.
export default function Reclamos() {
  const { mostrarToast } = useToast();
  const [estado, setEstado] = useState('PENDIENTE');
  const [busqueda, setBusqueda] = useState('');
  const [q, setQ] = useState('');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState({ count: 0, results: [], next: null, previous: null });
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const [seleccionado, setSeleccionado] = useState(null);
  const [decision, setDecision] = useState({ decision: 'ACEPTADO', motivo: '' });
  const [errorDecision, setErrorDecision] = useState('');
  const [confirmar, setConfirmar] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const panel = useRef(null);

  const [generandoQr, setGenerandoQr] = useState(null);
  const [errorQr, setErrorQr] = useState('');

  const [token, setToken] = useState('');
  const [verificando, setVerificando] = useState(false);
  const [verificacion, setVerificacion] = useState(null);
  const [confirmarRetiro, setConfirmarRetiro] = useState(false);
  const [retirando, setRetirando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getReclamosCustodia({ estado, q }, pagina));
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar los reclamos'));
      setLista({ count: 0, results: [], next: null, previous: null });
    } finally {
      setCargando(false);
    }
  }, [estado, q, pagina]);

  useEffect(() => { cargar(); }, [cargar]);
  useEffect(() => { if (seleccionado) panel.current?.querySelector('textarea')?.focus(); }, [seleccionado]);

  const cambiarEstado = (valor) => { setEstado(valor); setPagina(1); };
  const buscar = (e) => { e.preventDefault(); setPagina(1); setQ(busqueda.trim()); };

  const abrir = (reclamo) => {
    setSeleccionado(reclamo);
    setDecision({ decision: 'ACEPTADO', motivo: '' });
    setErrorDecision('');
  };

  const pedirConfirmacion = (e) => {
    e.preventDefault();
    const motivo = decision.motivo.trim();
    if (!motivo) { setErrorDecision('Explica el motivo de la decisión.'); return; }
    if (motivo.length > MAX_MOTIVO) { setErrorDecision(`El motivo admite hasta ${MAX_MOTIVO} caracteres.`); return; }
    setErrorDecision('');
    setConfirmar(true);
  };

  const guardar = async () => {
    setGuardando(true);
    try {
      const r = await resolverReclamo(seleccionado.id, { decision: decision.decision, motivo: decision.motivo.trim() });
      mostrarToast(r.estado === 'ACEPTADO'
        ? `Reclamo #${r.id} aceptado. Se avisó al reclamante y se generó su QR de retiro.`
        : `Reclamo #${r.id} rechazado. Se avisó al reclamante.`, 'exito');
      setSeleccionado(null);
      cargar();
    } catch (err) {
      setErrorDecision(mensajeError(err, 'No se pudo guardar la decisión'));
    } finally {
      setGuardando(false);
      setConfirmar(false);
    }
  };

  const verQr = async (reclamo) => {
    setGenerandoQr(reclamo.id);
    setErrorQr('');
    try {
      abrirArchivo(await getQrRetiro(reclamo.id));
    } catch (err) {
      setErrorQr(mensajeError(err, 'No se pudo generar el QR de retiro'));
    } finally {
      setGenerandoQr(null);
    }
  };

  const verificar = async (e) => {
    e.preventDefault();
    const valor = token.trim();
    if (!valor) { setVerificacion({ ok: false, mensaje: 'Escanea o pega el QR de retiro del reclamante.' }); return; }
    setVerificando(true);
    try {
      setVerificacion({ ok: true, datos: await verificarQrRetiro(valor), token: valor });
    } catch (err) {
      setVerificacion({ ok: false, mensaje: mensajeError(err, 'No se pudo verificar el QR') });
    } finally {
      setVerificando(false);
    }
  };

  const retirar = async () => {
    setRetirando(true);
    try {
      await registrarRetiro(verificacion.token);
      mostrarToast('Retiro registrado: el objeto quedó como recuperado.', 'exito');
      setToken('');
      setVerificacion(null);
      cargar();
    } catch (err) {
      setVerificacion((v) => ({ ...v, ok: false, mensaje: mensajeError(err, 'No se pudo registrar el retiro') }));
    } finally {
      setRetirando(false);
      setConfirmarRetiro(false);
    }
  };

  const reclamoQr = verificacion?.ok ? verificacion.datos?.reclamo : null;
  const qrVigente = verificacion?.ok && verificacion.datos?.estadoQr === 'VIGENTE';

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Reclamos</h1>
          <p className="pagina-subtitulo">Personas que dicen ser dueñas de un objeto en custodia.</p>
        </div>
        <span className="contador">{lista.count} {estado ? (ESTADOS[estado]?.texto || estado).toLowerCase() : 'en total'}</span>
      </div>

      <form className="tarjeta formulario retiro-panel" onSubmit={verificar} noValidate aria-labelledby="titulo-retiro">
        <h2 id="titulo-retiro"><Icono nombre="qr" tamano={18} /> Registrar retiro</h2>
        <div className="retiro-fila">
          <label className="campo">
            <span className="campo-label">QR de retiro</span>
            <input className="input" value={token} autoComplete="off" disabled={verificando || retirando}
              placeholder="Escanea el QR que muestra el reclamante"
              onChange={(e) => { setToken(e.target.value); setVerificacion(null); }} />
          </label>
          <button type="submit" className="btn btn-secundario" disabled={verificando || retirando}>
            {verificando ? 'Verificando...' : 'Verificar'}
          </button>
        </div>
        {verificacion && !verificacion.ok && <p className="campo-error" role="alert">{verificacion.mensaje}</p>}
        {verificacion?.ok && (
          <div className={`custodia-resultado${qrVigente ? '' : ' aviso'}`} role="status">
            <p><Icono nombre={qrVigente ? 'check' : 'alerta'} tamano={16} /> {ESTADO_QR[verificacion.datos.estadoQr] || verificacion.datos.estadoQr}</p>
            {reclamoQr && (
              <p className="nota-tabla">Reclamo #{reclamoQr.id} · {reclamoQr.objeto?.nombre} · {reclamoQr.reclamante?.nombre}</p>
            )}
            {qrVigente && (
              <button type="button" className="btn btn-primario" onClick={() => setConfirmarRetiro(true)} disabled={retirando}>
                Entregar objeto
              </button>
            )}
          </div>
        )}
      </form>

      <div className="filtro-segmentos" role="group" aria-label="Filtrar reclamos por estado">
        {SEGMENTOS.map((s) => (
          <button key={s.valor || 'todos'} type="button" className={`btn btn-mini ${estado === s.valor ? 'btn-primario' : 'btn-fantasma'}`}
            aria-pressed={estado === s.valor} onClick={() => cambiarEstado(s.valor)}>{s.texto}</button>
        ))}
      </div>
      <form className="busqueda-fila" onSubmit={buscar} role="search">
        <input className="input" value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por objeto o reclamante" aria-label="Buscar por objeto o reclamante" />
        <button type="submit" className="btn btn-fantasma btn-mini"><Icono nombre="buscar" tamano={14} /> Buscar</button>
      </form>

      {seleccionado && (
        <form ref={panel} className="tarjeta formulario resolver-panel" onSubmit={pedirConfirmacion} noValidate
          aria-label={`Resolver reclamo #${seleccionado.id}`}>
          <h2>Resolver reclamo #{seleccionado.id}</h2>
          <p className="nota-tabla">
            {seleccionado.objeto?.nombre} (#{seleccionado.objeto?.id}) · reclamado por {seleccionado.reclamante?.nombre} el {fechaHora(seleccionado.fechaReclamo)}
          </p>
          {seleccionado.objeto?.descripcion && <p className="nota-tabla">Descripción registrada: «{seleccionado.objeto.descripcion}»</p>}
          <fieldset className="resolver-opciones">
            <legend className="campo-label">Decisión</legend>
            <label><input type="radio" name="decision" value="ACEPTADO" checked={decision.decision === 'ACEPTADO'}
              onChange={() => setDecision((d) => ({ ...d, decision: 'ACEPTADO' }))} /> Aceptar (es el dueño)</label>
            <label><input type="radio" name="decision" value="RECHAZADO" checked={decision.decision === 'RECHAZADO'}
              onChange={() => setDecision((d) => ({ ...d, decision: 'RECHAZADO' }))} /> Rechazar</label>
          </fieldset>
          <label className="campo">
            <span className="campo-label">Motivo *</span>
            <textarea className={`input textarea${errorDecision ? ' input-error' : ''}`} rows={3} maxLength={MAX_MOTIVO}
              value={decision.motivo} disabled={guardando}
              onChange={(e) => { const motivo = e.target.value; setDecision((d) => ({ ...d, motivo })); setErrorDecision(''); }}
              placeholder="Ej. Documento de identidad y descripción verificados." />
          </label>
          {errorDecision && <p className="campo-error" role="alert">{errorDecision}</p>}
          <p className="nota-tabla">
            {decision.decision === 'ACEPTADO'
              ? 'Al aceptar se rechazan los otros reclamos pendientes de este objeto y el reclamante recibe un QR de retiro válido 7 días.'
              : 'El reclamante recibirá el motivo del rechazo.'} La decisión no se puede cambiar.
          </p>
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={() => setSeleccionado(null)} disabled={guardando}>Cancelar</button>
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
      {errorQr && <div className="banner-error" role="alert"><Icono nombre="alerta" tamano={18} /> {errorQr}</div>}

      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : error ? null : lista.results.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="bandeja" tamano={28} /></span>
          <p>{q ? 'Ningún reclamo coincide con la búsqueda.' : estado === 'PENDIENTE' ? 'No hay reclamos pendientes.' : 'No hay reclamos en esta vista.'}</p>
        </div>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr><th>Reclamo</th><th>Objeto</th><th>Reclamante</th><th>Fecha</th><th>Estado</th><th>Acción</th></tr>
              </thead>
              <tbody>
                {lista.results.map((r) => {
                  const e = ESTADOS[r.estado] || { texto: r.estado, clase: '' };
                  return (
                    <tr key={r.id}>
                      <td data-label="Reclamo">#{r.id}</td>
                      <td data-label="Objeto">{r.objeto?.nombre}<span className="celda-detalle">#{r.objeto?.id} · {r.objeto?.categoria || 'Sin categoría'}</span></td>
                      <td data-label="Reclamante">{r.reclamante?.nombre}</td>
                      <td data-label="Fecha">{fechaHora(r.fechaReclamo)}</td>
                      <td data-label="Estado">
                        <span className={`chip-estado ${e.clase}`}>{e.texto}</span>
                        {r.motivoResolucion && <span className="celda-detalle">{r.motivoResolucion}{r.resueltoPor ? ` · ${r.resueltoPor.nombre}` : ''}</span>}
                        {r.estado === 'ACEPTADO' && r.qrRetiroExpira && (
                          <span className="celda-detalle">QR {r.qrRetiroVigente ? `vigente hasta ${fechaHora(r.qrRetiroExpira)}` : 'vencido'}</span>
                        )}
                      </td>
                      <td data-label="Acción">
                        {r.estado === 'PENDIENTE' ? (
                          <button type="button" className="btn btn-primario btn-mini" onClick={() => abrir(r)} disabled={guardando}
                            aria-label={`Resolver reclamo #${r.id}`}>Resolver</button>
                        ) : r.qrRetiroUrl && r.qrRetiroVigente ? (
                          <button type="button" className="btn btn-fantasma btn-mini" onClick={() => verQr(r)} disabled={generandoQr != null}
                            aria-label={`Ver el QR de retiro del reclamo #${r.id}`}>
                            <Icono nombre="qr" tamano={14} /> {generandoQr === r.id ? 'Generando...' : 'QR de retiro'}
                          </button>
                        ) : (
                          <span className="nota-acciones">—</span>
                        )}
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
        titulo={decision.decision === 'ACEPTADO' ? '¿Aceptar este reclamo?' : '¿Rechazar este reclamo?'}
        mensaje={seleccionado
          ? `${seleccionado.reclamante?.nombre} · ${seleccionado.objeto?.nombre}. ${decision.decision === 'ACEPTADO' ? 'Se generará su QR de retiro y se rechazarán los demás reclamos pendientes del objeto.' : 'Se le enviará el motivo.'}`
          : ''}
        textoConfirmar={decision.decision === 'ACEPTADO' ? 'Sí, aceptar' : 'Sí, rechazar'}
        peligro={decision.decision === 'RECHAZADO'}
        procesando={guardando}
        onConfirmar={guardar}
        onCancelar={() => setConfirmar(false)}
      />
      <ConfirmarDialogo
        abierto={confirmarRetiro}
        titulo="¿Entregar el objeto?"
        mensaje={reclamoQr ? `${reclamoQr.objeto?.nombre} se entregará a ${reclamoQr.reclamante?.nombre}. El QR quedará usado y el objeto pasará a recuperado.` : ''}
        textoConfirmar="Sí, entregar"
        procesando={retirando}
        onConfirmar={retirar}
        onCancelar={() => setConfirmarRetiro(false)}
      />
    </div>
  );
}
