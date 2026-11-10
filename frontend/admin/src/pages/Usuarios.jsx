import { useCallback, useEffect, useRef, useState } from 'react';
import { cambiarRolUsuario, getCambiosRol, getUsuarios, mensajeError, patchUsuario } from '../services/api';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

// H36: roles que admite la API y su nombre visible
export const ROLES = { USUARIO: 'Usuario', BIBLIOTECA: 'Biblioteca', ADMIN: 'Administrador' };
const MAX_MOTIVO = 500;

function fechaHora(valor) {
  if (!valor) return '—';
  const d = new Date(valor);
  if (Number.isNaN(d.getTime())) return String(valor);
  return d.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
}

// H15 - Suspender o activar cuentas. H36 - Cambiar el rol con motivo, confirmación e historial.
export default function Usuarios() {
  const { mostrarToast } = useToast();
  const { user } = useAuth();
  const [usuarios, setUsuarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [pendiente, setPendiente] = useState(null); // { usuario, nuevoEstado }
  const [accionando, setAccionando] = useState(false);
  // H36
  const [rolEdicion, setRolEdicion] = useState(null); // { usuario, rol, motivo }
  const [errorRol, setErrorRol] = useState('');
  const [confirmarRol, setConfirmarRol] = useState(false);
  const [guardandoRol, setGuardandoRol] = useState(false);
  const [historial, setHistorial] = useState(null); // { usuario, cargando, error, cambios }
  const panelRol = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getUsuarios();
      setUsuarios(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setErrorCarga(mensajeError(e, 'No se pudieron cargar los usuarios'));
      setUsuarios([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const confirmar = async () => {
    const { usuario, nuevoEstado } = pendiente;
    setAccionando(true);
    setErrorAccion('');
    try {
      const actualizado = await patchUsuario(usuario.id, { estado: nuevoEstado });
      setUsuarios((prev) => prev.map((u) => (u.id === usuario.id ? { ...u, ...actualizado } : u)));
      mostrarToast(
        nuevoEstado === 'SUSPENDIDO'
          ? `${usuario.nombre} quedó suspendido: ya no puede iniciar sesión.`
          : `${usuario.nombre} fue reactivado.`,
        'exito'
      );
    } catch (e) {
      setErrorAccion(mensajeError(e, 'No se pudo actualizar el usuario'));
    } finally {
      setAccionando(false);
      setPendiente(null);
    }
  };

  const esYo = (u) => user && String(u.id) === String(user.id);

  useEffect(() => {
    if (rolEdicion?.usuario) panelRol.current?.querySelector('select')?.focus();
  }, [rolEdicion?.usuario]);

  const abrirRol = (u) => {
    setHistorial(null);
    setErrorRol('');
    const otro = Object.keys(ROLES).find((clave) => clave !== u.rol);
    setRolEdicion({ usuario: u, rol: otro, motivo: '' });
  };

  // Primero se valida el formulario; la confirmación es un paso aparte
  const pedirConfirmacionRol = (e) => {
    e.preventDefault();
    const motivo = rolEdicion.motivo.trim();
    if (rolEdicion.rol === rolEdicion.usuario.rol) { setErrorRol('Elige un rol distinto al actual.'); return; }
    if (!motivo) { setErrorRol('Explica el motivo del cambio de rol.'); return; }
    if (motivo.length > MAX_MOTIVO) { setErrorRol(`El motivo admite hasta ${MAX_MOTIVO} caracteres.`); return; }
    setErrorRol('');
    setConfirmarRol(true);
  };

  const guardarRol = async () => {
    const { usuario, rol, motivo } = rolEdicion;
    setGuardandoRol(true);
    try {
      const actualizado = await cambiarRolUsuario(usuario.id, { rol, motivo: motivo.trim() });
      setUsuarios((prev) => prev.map((u) => (u.id === usuario.id ? { ...u, rol: actualizado.rol } : u)));
      mostrarToast(`${usuario.nombre} ahora tiene el rol ${ROLES[actualizado.rol] || actualizado.rol}.`, 'exito');
      setRolEdicion(null);
    } catch (e) {
      setErrorRol(mensajeError(e, 'No se pudo cambiar el rol'));
    } finally {
      setGuardandoRol(false);
      setConfirmarRol(false);
    }
  };

  const verHistorial = async (u) => {
    setRolEdicion(null);
    setHistorial({ usuario: u, cargando: true, error: '', cambios: [] });
    try {
      const cambios = await getCambiosRol(u.id);
      setHistorial({ usuario: u, cargando: false, error: '', cambios });
    } catch (e) {
      setHistorial({ usuario: u, cargando: false, error: mensajeError(e, 'No se pudo cargar el historial'), cambios: [] });
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Usuarios</h1>
        <span className="contador">{usuarios.length} registros</span>
      </div>

      {errorCarga && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {errorCarga}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}
      {errorAccion && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {errorAccion}
        </div>
      )}

      {rolEdicion && (
        <form ref={panelRol} className="tarjeta formulario resolver-panel" onSubmit={pedirConfirmacionRol} noValidate
          aria-label={`Cambiar el rol de ${rolEdicion.usuario.nombre}`}>
          <h2>Cambiar rol de {rolEdicion.usuario.nombre} {rolEdicion.usuario.apellido}</h2>
          <p className="nota-tabla">Rol actual: <strong>{ROLES[rolEdicion.usuario.rol] || rolEdicion.usuario.rol}</strong>.
            Biblioteca atiende custodia y reclamos; Administrador gestiona todo el sistema.</p>
          <label className="campo">
            <span className="campo-label">Nuevo rol</span>
            <select className="input" value={rolEdicion.rol} disabled={guardandoRol}
              onChange={(e) => { const rol = e.target.value; setRolEdicion((r) => ({ ...r, rol })); setErrorRol(''); }}>
              {Object.entries(ROLES).filter(([clave]) => clave !== rolEdicion.usuario.rol).map(([clave, nombre]) => (
                <option key={clave} value={clave}>{nombre}</option>
              ))}
            </select>
          </label>
          <label className="campo">
            <span className="campo-label">Motivo *</span>
            <textarea className={`input textarea${errorRol ? ' input-error' : ''}`} rows={3} maxLength={MAX_MOTIVO}
              value={rolEdicion.motivo} disabled={guardandoRol}
              onChange={(e) => { const motivo = e.target.value; setRolEdicion((r) => ({ ...r, motivo })); setErrorRol(''); }}
              placeholder="Ej. Apoya la atención de custodia en la biblioteca." />
            <span className="nota-tabla">{rolEdicion.motivo.trim().length}/{MAX_MOTIVO}</span>
          </label>
          {errorRol && <p className="campo-error" role="alert">{errorRol}</p>}
          <p className="nota-tabla">El cambio queda registrado con tu correo, la fecha y el motivo.</p>
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={() => setRolEdicion(null)} disabled={guardandoRol}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardandoRol}>Continuar</button>
          </div>
        </form>
      )}

      {historial && (
        <section className="tarjeta formulario historial-roles" aria-label={`Historial de roles de ${historial.usuario.nombre}`}>
          <div className="historial-cabecera">
            <h2><Icono nombre="historial" tamano={18} /> Historial de roles · {historial.usuario.nombre} {historial.usuario.apellido}</h2>
            <button type="button" className="btn btn-fantasma btn-mini" onClick={() => setHistorial(null)}>Cerrar</button>
          </div>
          {historial.cargando ? (
            <p className="nota-tabla" role="status">Cargando historial...</p>
          ) : historial.error ? (
            <div className="banner-error" role="alert">
              <span><Icono nombre="alerta" tamano={18} /> {historial.error}</span>
              <button type="button" className="btn-fantasma btn-reintentar" onClick={() => verHistorial(historial.usuario)}>Reintentar</button>
            </div>
          ) : historial.cambios.length === 0 ? (
            <p className="nota-tabla">Esta cuenta nunca cambió de rol.</p>
          ) : (
            <ol className="historial-lista">
              {historial.cambios.map((c) => (
                <li key={c.id}>
                  <strong>{ROLES[c.rolAnterior] || c.rolAnterior} → {ROLES[c.rolNuevo] || c.rolNuevo}</strong>
                  <span className="celda-detalle">{fechaHora(c.fecha)} · por {c.cambiadoPor}</span>
                  {c.motivo && <span className="celda-detalle">Motivo: {c.motivo}</span>}
                </li>
              ))}
            </ol>
          )}
        </section>
      )}

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : errorCarga ? null : usuarios.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="usuarios" tamano={28} />
          </span>
          <p>No hay usuarios registrados.</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Apellido</th>
                <th>Correo</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Fecha de registro</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((u) => (
                <tr key={u.id}>
                  <td data-label="Nombre">
                    {u.nombre}
                    {esYo(u) && <span className="etiqueta-tu">(tú)</span>}
                  </td>
                  <td data-label="Apellido">{u.apellido}</td>
                  <td data-label="Correo">{u.correo}</td>
                  <td data-label="Rol">
                    <span className={`chip-rol rol-${String(u.rol).toLowerCase()}`}>{ROLES[u.rol] || u.rol}</span>
                    <span className="acciones-rol">
                      {!esYo(u) && (
                        <button type="button" className="btn btn-fantasma btn-mini" onClick={() => abrirRol(u)}
                          aria-label={`Cambiar el rol de ${u.nombre} ${u.apellido || ''}`.trim()}>Cambiar rol</button>
                      )}
                      <button type="button" className="btn btn-fantasma btn-mini" onClick={() => verHistorial(u)}
                        aria-label={`Ver el historial de roles de ${u.nombre} ${u.apellido || ''}`.trim()}>Historial</button>
                    </span>
                  </td>
                  <td data-label="Estado">
                    <span className={`chip-estado ${u.estado === 'SUSPENDIDO' ? 'estado-eliminado' : 'estado-activo'}`}>
                      {u.estado}
                    </span>
                  </td>
                  <td data-label="Registro">{fechaCorta(u.fecha_registro || u.fechaRegistro)}</td>
                  <td data-label="Acción">
                    {esYo(u) ? (
                      <span className="nota-acciones">Tu cuenta</span>
                    ) : (
                      <button
                        type="button"
                        className={`btn ${u.estado === 'SUSPENDIDO' ? 'btn-primario' : 'btn-peligro'} btn-mini`}
                        onClick={() => {
                          setErrorAccion('');
                          setPendiente({ usuario: u, nuevoEstado: u.estado === 'SUSPENDIDO' ? 'ACTIVO' : 'SUSPENDIDO' });
                        }}
                        aria-label={`${u.estado === 'SUSPENDIDO' ? 'Activar' : 'Suspender'} a ${u.nombre} ${u.apellido || ''}`.trim()}
                      >
                        {u.estado === 'SUSPENDIDO' ? 'Activar' : 'Suspender'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmarDialogo
        abierto={Boolean(pendiente)}
        titulo={pendiente?.nuevoEstado === 'SUSPENDIDO' ? '¿Suspender esta cuenta?' : '¿Reactivar esta cuenta?'}
        mensaje={pendiente
          ? pendiente.nuevoEstado === 'SUSPENDIDO'
            ? `${pendiente.usuario.nombre} ${pendiente.usuario.apellido || ''} no podrá iniciar sesión ni usar su sesión actual hasta que la reactives.`
            : `${pendiente.usuario.nombre} ${pendiente.usuario.apellido || ''} podrá volver a iniciar sesión.`
          : ''}
        textoConfirmar={pendiente?.nuevoEstado === 'SUSPENDIDO' ? 'Sí, suspender' : 'Sí, reactivar'}
        peligro={pendiente?.nuevoEstado === 'SUSPENDIDO'}
        procesando={accionando}
        onConfirmar={confirmar}
        onCancelar={() => setPendiente(null)}
      />

      <ConfirmarDialogo
        abierto={confirmarRol}
        titulo="¿Confirmar el cambio de rol?"
        mensaje={rolEdicion
          ? `${rolEdicion.usuario.nombre} ${rolEdicion.usuario.apellido || ''} pasará de ${ROLES[rolEdicion.usuario.rol] || rolEdicion.usuario.rol} a ${ROLES[rolEdicion.rol] || rolEdicion.rol}. Sus permisos cambian desde su próximo inicio de sesión.`
          : ''}
        textoConfirmar="Sí, cambiar rol"
        procesando={guardandoRol}
        onConfirmar={guardarRol}
        onCancelar={() => setConfirmarRol(false)}
      />
    </div>
  );
}
