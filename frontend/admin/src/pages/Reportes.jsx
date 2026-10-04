import { useCallback, useEffect, useState } from 'react';
import { getReportes, mensajeError, patchReporte, statusDe } from '../services/api';

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

export default function Reportes() {
  const [reportes, setReportes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [accionando, setAccionando] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getReportes();
      setReportes(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar los reportes'));
      setReportes([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const resolver = async (reporte) => {
    if (!window.confirm('¿Marcar este reporte como resuelto?')) return;
    setAccionando(reporte.id);
    setError('');
    try {
      await patchReporte(reporte.id, { estado: 'RESUELTO' });
      setReportes((prev) =>
        prev.map((r) => (r.id === reporte.id ? { ...r, estado: 'RESUELTO' } : r))
      );
    } catch (e) {
      const status = statusDe(e);
      if (status === 401) setError('Sesión expirada. Vuelve a iniciar sesión.');
      else if (status === 403) setError('No tienes permisos para resolver reportes.');
      else if (status === 400) setError(mensajeError(e, 'Datos inválidos'));
      else setError(mensajeError(e, 'No se pudo actualizar el reporte'));
    } finally {
      setAccionando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Reportes</h1>
        <span className="contador">{reportes.length} registros</span>
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
      ) : reportes.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            🚩
          </span>
          <p>No hay reportes.</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Motivo</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {reportes.map((r) => (
                <tr key={r.id}>
                  <td data-label="Motivo">{r.motivo}</td>
                  <td data-label="Estado">
                    <span
                      className={`chip-estado ${
                        r.estado === 'RESUELTO' ? 'estado-resuelto' : 'estado-pendiente'
                      }`}
                    >
                      {r.estado}
                    </span>
                  </td>
                  <td data-label="Fecha">{fechaCorta(r.fecha || r.fechaCreacion)}</td>
                  <td data-label="Acción">
                    <button
                      type="button"
                      className="btn btn-primario btn-mini"
                      onClick={() => resolver(r)}
                      disabled={accionando === r.id || r.estado === 'RESUELTO'}
                    >
                      {accionando === r.id
                        ? 'Guardando...'
                        : r.estado === 'RESUELTO'
                          ? 'Resuelto'
                          : 'Resolver'}
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
