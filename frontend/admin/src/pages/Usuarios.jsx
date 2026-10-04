import { useCallback, useEffect, useState } from 'react';
import { getUsuarios, mensajeError, patchUsuario, statusDe } from '../services/api';

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

export default function Usuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [accionando, setAccionando] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getUsuarios();
      setUsuarios(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar los usuarios'));
      setUsuarios([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const cambiarEstado = async (usuario) => {
    const nuevoEstado = usuario.estado === 'SUSPENDIDO' ? 'ACTIVO' : 'SUSPENDIDO';
    const mensaje =
      nuevoEstado === 'SUSPENDIDO'
        ? `¿Suspender a ${usuario.nombre} ${usuario.apellido || ''}?`
        : `¿Reactivar a ${usuario.nombre} ${usuario.apellido || ''}?`;
    if (!window.confirm(mensaje)) return;

    setAccionando(usuario.id);
    setError('');
    try {
      await patchUsuario(usuario.id, { estado: nuevoEstado });
      setUsuarios((prev) =>
        prev.map((u) => (u.id === usuario.id ? { ...u, estado: nuevoEstado } : u))
      );
    } catch (e) {
      const status = statusDe(e);
      if (status === 401) setError('Sesión expirada. Vuelve a iniciar sesión.');
      else if (status === 403) setError('No tienes permisos para modificar usuarios.');
      else if (status === 400) setError(mensajeError(e, 'Datos inválidos'));
      else setError(mensajeError(e, 'No se pudo actualizar el usuario'));
    } finally {
      setAccionando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Usuarios</h1>
        <span className="contador">{usuarios.length} registros</span>
      </div>

      {error && (
        <div className="banner-error" role="alert">
          ⚠️ {error}
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : usuarios.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            👥
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
                  <td data-label="Nombre">{u.nombre}</td>
                  <td data-label="Apellido">{u.apellido}</td>
                  <td data-label="Correo">{u.correo}</td>
                  <td data-label="Rol">{u.rol}</td>
                  <td data-label="Estado">
                    <span
                      className={`chip-estado ${
                        u.estado === 'SUSPENDIDO' ? 'estado-eliminado' : 'estado-activo'
                      }`}
                    >
                      {u.estado}
                    </span>
                  </td>
                  <td data-label="Registro">{fechaCorta(u.fechaRegistro)}</td>
                  <td data-label="Acción">
                    <button
                      type="button"
                      className={`btn ${u.estado === 'SUSPENDIDO' ? 'btn-primario' : 'btn-peligro'} btn-mini`}
                      onClick={() => cambiarEstado(u)}
                      disabled={accionando === u.id}
                    >
                      {accionando === u.id
                        ? 'Guardando...'
                        : u.estado === 'SUSPENDIDO'
                          ? 'Activar'
                          : 'Suspender'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
