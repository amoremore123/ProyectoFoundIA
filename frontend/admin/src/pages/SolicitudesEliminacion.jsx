import { useCallback, useEffect, useState } from 'react';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import {
  atenderEliminacion, getEliminacion, getSolicitudesEliminacion, mensajeError, rechazarEliminacion, reaplicarEliminaciones,
} from '../services/api';
import { fechaHora, Paginacion } from './Custodia';

// H34 - Eliminación real de la cuenta (Ley 29733, derecho de cancelación). Solo ADMIN.
// Lista con fecha límite (10 días hábiles), vista previa de lo que se borra y confirmación escribiendo el correo.
const ESTADOS = ['PENDIENTE', 'ATENDIDA', 'RECHAZADA'];
const CHIP = { PENDIENTE: 'recuperado', ATENDIDA: 'activo', RECHAZADA: 'eliminado' };
const VACIA = { count: 0, results: [], next: null, previous: null };
const MOTIVO_MIN = 3;
const MOTIVO_MAX = 500;

function Efecto({ efecto }) {
  if (!efecto) return null;
  const filas = [
    ['Publicaciones que se eliminan', efecto.publicacionesQueSeEliminan],
    ['Publicaciones que se conservan (autor anonimizado)', efecto.publicacionesQueSeConservan],
    ['Fotos de publicaciones', efecto.fotosDePublicaciones],
    ['Foto de perfil', efecto.fotoDePerfil ? 'Sí' : 'No'],
    ['Mensajes del chat (se reemplazan por «Mensaje eliminado»)', efecto.mensajesDelChat],
    ['Fotos del chat', efecto.fotosDelChat],
    ['Avisos', efecto.avisos],
    ['Dispositivos', efecto.dispositivos],
    ['Reclamos abiertos que se retiran', efecto.reclamosQueSeRetiran],
    ['Reclamos que se conservan', efecto.reclamosQueSeConservan],
    ['Contactos', efecto.contactos],
    ['Reportes', efecto.reportes],
  ];
  return (
    <dl className="detalle-lista" aria-label="Qué se hará con la cuenta">
      {filas.map(([etiqueta, valor]) => (<div key={etiqueta}><dt>{etiqueta}</dt><dd>{valor}</dd></div>))}
    </dl>
  );
}

export default function SolicitudesEliminacion() {
  const { mostrarToast } = useToast();
  const [estado, setEstado] = useState('PENDIENTE');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [abierta, setAbierta] = useState(null);
  const [confirmacion, setConfirmacion] = useState('');
  const [motivo, setMotivo] = useState('');
  const [modo, setModo] = useState(null); // 'atender' | 'rechazar'
  const [pidiendo, setPidiendo] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try { setLista(await getSolicitudesEliminacion({ estado }, pagina)); } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar las solicitudes de eliminación')); setLista(VACIA);
    } finally { setCargando(false); }
  }, [estado, pagina]);
  useEffect(() => { cargar(); }, [cargar]);

  const abrir = async (id) => {
    setErrorAccion(''); setConfirmacion(''); setMotivo(''); setModo(null);
    try { setAbierta(await getEliminacion(id)); } catch (e) { setErrorAccion(mensajeError(e, 'No se pudo abrir la solicitud')); }
  };
  const cerrar = () => { setAbierta(null); setModo(null); setErrorAccion(''); };

  const atender = async () => {
    setPidiendo(true); setErrorAccion('');
    try {
      const r = await atenderEliminacion(abierta.id, confirmacion.trim());
      setModo(null);
      mostrarToast(r.correoEnviado === false ? 'Cuenta eliminada; el correo de confirmación no se pudo enviar.' : 'Cuenta eliminada.', 'exito');
      cerrar(); cargar();
    } catch (e) { setModo(null); setErrorAccion(mensajeError(e, 'No se pudo atender la solicitud')); } finally { setPidiendo(false); }
  };
  const rechazar = async () => {
    setPidiendo(true); setErrorAccion('');
    try { await rechazarEliminacion(abierta.id, motivo.trim()); mostrarToast('Solicitud rechazada.', 'exito'); cerrar(); cargar(); } catch (e) {
      setErrorAccion(mensajeError(e, 'No se pudo rechazar la solicitud'));
    } finally { setPidiendo(false); }
  };
  const reaplicar = async () => {
    try {
      const r = await reaplicarEliminaciones();
      setAviso(`Cuentas anonimizadas de nuevo: ${r.cuentasAnonimizadasDeNuevo}.`);
      cargar();
    } catch (e) { setError(mensajeError(e, 'No se pudo volver a aplicar las eliminaciones')); }
  };

  const correoOk = abierta && confirmacion.trim().toLowerCase() === abierta.usuario.correo.toLowerCase();
  const motivoOk = motivo.trim().length >= MOTIVO_MIN && motivo.trim().length <= MOTIVO_MAX;
  const hayQueReaplicar = lista.results.some((s) => s.requiereReaplicar);

  return (
    <div className="pagina">
      <h1>Eliminación de cuentas</h1>
      <p className="nota-tabla">
        Derecho de cancelación (Ley 29733): plazo de 10 días hábiles. Atender anonimiza la cuenta y borra lo que solo le sirve a la persona; no se puede deshacer.
      </p>
      <div className="filtros" role="group" aria-label="Estado de la solicitud">
        {ESTADOS.map((e) => (
          <button key={e} type="button" className={`btn btn-mini ${estado === e ? 'btn-primario' : 'btn-fantasma'}`} aria-pressed={estado === e}
            onClick={() => { setEstado(e); setPagina(1); cerrar(); }}>{e.charAt(0) + e.slice(1).toLowerCase()}s</button>
        ))}
      </div>
      {hayQueReaplicar && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> Una restauración trajo de vuelta cuentas ya eliminadas.</span>
          <button type="button" className="btn btn-secundario btn-mini" onClick={reaplicar}>Volver a anonimizarlas</button>
        </div>
      )}
      {aviso && <p role="status" className="nota-tabla">{aviso}</p>}
      {error && <div className="banner-error" role="alert"><span>{error}</span><button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button></div>}
      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : error ? null : lista.results.length === 0 ? (
        <p className="panel-vacio">No hay solicitudes {estado.toLowerCase()}s.</p>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead><tr><th>Persona</th><th>Solicitada</th><th>Fecha límite</th><th>Estado</th><th>Acción</th></tr></thead>
              <tbody>
                {lista.results.map((s) => (
                  <tr key={s.id}>
                    <td data-label="Persona">{s.usuario.nombre}<span className="celda-detalle">{s.usuario.correo}</span></td>
                    <td data-label="Solicitada">{fechaHora(s.fechaSolicitud)}</td>
                    <td data-label="Fecha límite">{s.fechaLimite}{s.vencida && <span className="chip-estado eliminado"> Vencida</span>}</td>
                    <td data-label="Estado">
                      <span className={`chip-estado ${CHIP[s.estado] || ''}`}>{s.estado}</span>
                      {s.motivoRechazo && <span className="celda-detalle">{s.motivoRechazo}</span>}
                    </td>
                    <td data-label="Acción">
                      <button type="button" className="btn btn-fantasma btn-mini" onClick={() => abrir(s.id)} aria-label={`Abrir la solicitud de ${s.usuario.nombre}`}>
                        {s.estado === 'PENDIENTE' ? 'Revisar' : 'Ver'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} lista={lista} onCambiar={setPagina} />
        </>
      )}

      {abierta && (
        <section className="tarjeta formulario" aria-label={`Solicitud de ${abierta.usuario.nombre}`}>
          <h2>{abierta.usuario.nombre}</h2>
          <p className="nota-tabla">{abierta.usuario.correo} · {abierta.usuario.rol} · fecha límite {abierta.fechaLimite}</p>
          <Efecto efecto={abierta.efecto} />
          {errorAccion && <p className="banner-error" role="alert">{errorAccion}</p>}
          {abierta.estado === 'PENDIENTE' ? (
            <>
              <label htmlFor="eliminar-correo">Para atender, escribe el correo de la cuenta</label>
              <input id="eliminar-correo" value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} autoComplete="off" />
              <label htmlFor="eliminar-motivo">Para rechazar, escribe el motivo ({MOTIVO_MIN} a {MOTIVO_MAX} caracteres)</label>
              <textarea id="eliminar-motivo" value={motivo} maxLength={MOTIVO_MAX} onChange={(e) => setMotivo(e.target.value)} />
              <div className="acciones-fila">
                <button type="button" className="btn btn-peligro btn-mini" disabled={!correoOk || pidiendo} onClick={() => setModo('atender')}>Atender y eliminar</button>
                <button type="button" className="btn btn-secundario btn-mini" disabled={!motivoOk || pidiendo} onClick={() => setModo('rechazar')}>Rechazar</button>
                <button type="button" className="btn btn-fantasma btn-mini" onClick={cerrar}>Cerrar</button>
              </div>
            </>
          ) : (
            <div className="acciones-fila"><button type="button" className="btn btn-fantasma btn-mini" onClick={cerrar}>Cerrar</button></div>
          )}
        </section>
      )}

      <ConfirmarDialogo abierto={modo === 'atender'} titulo="Eliminar la cuenta" peligro procesando={pidiendo}
        mensaje={`Se anonimizará la cuenta de ${abierta?.usuario.correo} y se borrarán sus datos personales. No se puede deshacer.`}
        textoConfirmar="Eliminar la cuenta" onConfirmar={atender} onCancelar={() => setModo(null)} />
      <ConfirmarDialogo abierto={modo === 'rechazar'} titulo="Rechazar la solicitud" procesando={pidiendo}
        mensaje="La cuenta no cambia y la persona verá el motivo."
        textoConfirmar="Rechazar" onConfirmar={rechazar} onCancelar={() => setModo(null)} />
    </div>
  );
}
