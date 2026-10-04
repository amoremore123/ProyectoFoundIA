import { useCallback, useEffect, useState } from 'react';
import { getPublicaciones, mensajeError, patchPublicacion, statusDe } from '../services/api';

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

export default function Publicaciones() {
  const [publicaciones, setPublicaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [accionando, setAccionando] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getPublicaciones();
      setPublicaciones(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar las publicaciones'));
      setPublicaciones([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const cambiarEstado = async (pub) => {
    const nuevoEstado = pub.estado === 'OCULTO' ? 'ACTIVO' : 'OCULTO';
    const mensaje =
      nuevoEstado === 'OCULTO'
        ? `¿Ocultar "${pub.nombre}"?`
        : `¿Reactivar "${pub.nombre}"?`;
    if (!window.confirm(mensaje)) return;

    setAccionando(pub.id);
    setError('');
    try {
      await patchPublicacion(pub.id, { estado: nuevoEstado });
      setPublicaciones((prev) =>
        prev.map((p) => (p.id === pub.id ? { ...p, estado: nuevoEstado } : p))
      );
    } catch (e) {
      const status = statusDe(e);
      if (status === 401) setError('Sesión expirada. Vuelve a iniciar sesión.');
      else if (status === 403) setError('No tienes permisos para modificar publicaciones.');
      else if (status === 400) setError(mensajeError(e, 'Datos inválidos'));
      else setError(mensajeError(e, 'No se pudo actualizar la publicación'));
    } finally {
      setAccionando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Publicaciones</h1>
        <span className="contador">{publicaciones.length} registros</span>
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
      ) : publicaciones.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            📦
          </span>
          <p>No hay publicaciones.</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Tipo</th>
                <th>Categoría</th>
                <th>Ubicación</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {publicaciones.map((p) => (
                <tr key={p.id}>
                  <td data-label="Nombre">{p.nombre}</td>
                  <td data-label="Tipo">
                    <span
                      className={`chip ${p.tipo === 'PERDIDO' ? 'chip-perdido' : 'chip-encontrado'}`}
                    >
                      {p.tipo === 'PERDIDO' ? 'Perdido' : 'Encontrado'}
                    </span>
                  </td>
                  <td data-label="Categoría">{p.categoria?.nombre || p.categoria || '—'}</td>
                  <td data-label="Ubicación">{p.ubicacion || '—'}</td>
                  <td data-label="Estado">
                    <span
                      className={`chip-estado ${
                        p.estado === 'OCULTO' ? 'estado-oculto' : 'estado-activo'
                      }`}
                    >
                      {p.estado}
                    </span>
                  </td>
                  <td data-label="Fecha">{fechaCorta(p.fechaObjeto || p.fechaPublicacion)}</td>
                  <td data-label="Acción">
                    <button
                      type="button"
                      className={`btn ${p.estado === 'OCULTO' ? 'btn-primario' : 'btn-peligro'} btn-mini`}
                      onClick={() => cambiarEstado(p)}
                      disabled={accionando === p.id}
                    >
                      {accionando === p.id
                        ? 'Guardando...'
                        : p.estado === 'OCULTO'
                          ? 'Activar'
                          : 'Ocultar'}
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
