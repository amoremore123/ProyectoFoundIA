import { useCallback, useEffect, useState } from 'react';
import { getUsuarios, mensajeError, patchUsuario } from '../services/api';
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

// H15 - Suspender o activar cuentas. La API solo permite cambiar el estado.
export default function Usuarios() {
  const { mostrarToast } = useToast();
  const { user } = useAuth();
  const [usuarios, setUsuarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [pendiente, setPendiente] = useState(null); // { usuario, nuevoEstado }
  const [accionando, setAccionando] = useState(false);

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
                  <td data-label="Rol">{u.rol}</td>
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
    </div>
  );
}
