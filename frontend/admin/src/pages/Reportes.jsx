import { useCallback, useEffect, useRef, useState } from 'react';
import { getReportes, mensajeError, resolverReporte, statusDe } from '../services/api';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';

const MAX_RESOLUCION = 1000;
const ABIERTOS = ['PENDIENTE', 'REVISADO'];

const MOTIVOS = {
  CONTENIDO_INAPROPIADO: 'Contenido inapropiado',
  INFORMACION_FALSA: 'Información falsa',
  SPAM: 'Spam',
  OTRO: 'Otro',
};

const CLASE_ESTADO = {
  PENDIENTE: 'estado-pendiente',
  REVISADO: 'estado-pendiente',
  RESUELTO: 'estado-resuelto',
  RECHAZADO: 'estado-inactivo',
};

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

function idDe(valor) {
  return valor && typeof valor === 'object' ? valor.id : valor;
}

function mensajeResolucion(err) {
  const status = statusDe(err);
  if (status === 401) return 'Sesión expirada. Vuelve a iniciar sesión.';
  if (status === 403) return 'No tienes permisos para resolver reportes.';
  if (status === 404) return 'El reporte ya no existe. Recarga la lista.';
  if (status === 409) return mensajeError(err, 'Este reporte ya fue resuelto con otra decisión.');
  return mensajeError(err, 'No se pudo guardar la resolución');
}

// H28 - Consulta y resolución auditada de reportes.
// La decisión (resuelto/rechazado) exige una explicación; el actor y la fecha los registra el servidor.
export default function Reportes() {
  const { mostrarToast } = useToast();
  const [reportes, setReportes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [filtro, setFiltro] = useState('abiertos');
  const [seleccionado, setSeleccionado] = useState(null);
  const [decision, setDecision] = useState({ estado: 'RESUELTO', resolucion: '' });
  const [errorDecision, setErrorDecision] = useState('');
  const [guardando, setGuardando] = useState(false);
  const panel = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getReportes();
      setReportes(Array.isArray(data) ? data : []);
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

  useEffect(() => {
    if (seleccionado) panel.current?.querySelector('textarea')?.focus();
  }, [seleccionado]);

  const abrir = (reporte) => {
    setSeleccionado(reporte);
    setDecision({ estado: 'RESUELTO', resolucion: '' });
    setErrorDecision('');
  };

  const cerrar = () => {
    setSeleccionado(null);
    setErrorDecision('');
  };

  const guardar = async (e) => {
    e.preventDefault();
    const resolucion = decision.resolucion.trim();
    if (!resolucion) {
      setErrorDecision('Explica la decisión antes de guardarla.');
      return;
    }
    if (resolucion.length > MAX_RESOLUCION) {
      setErrorDecision(`La explicación admite hasta ${MAX_RESOLUCION} caracteres.`);
      return;
    }
    setGuardando(true);
    setErrorDecision('');
    try {
      const respuesta = await resolverReporte(seleccionado.id, { estado: decision.estado, resolucion });
      setReportes((prev) => prev.map((r) => (r.id === seleccionado.id ? {
        ...r,
        estado: respuesta.estado,
        resolucion: respuesta.resolucion,
        fecha_resolucion: respuesta.fechaResolucion,
        resuelto_por: respuesta.resueltoPorId,
      } : r)));
      mostrarToast(respuesta.repetido
        ? 'Esa resolución ya estaba registrada.'
        : `Reporte #${seleccionado.id} ${respuesta.estado === 'RECHAZADO' ? 'rechazado' : 'resuelto'}.`, 'exito');
      setSeleccionado(null);
    } catch (err) {
      setErrorDecision(mensajeResolucion(err));
    } finally {
      setGuardando(false);
    }
  };

  const abiertos = reportes.filter((r) => ABIERTOS.includes(r.estado));
  const visibles = filtro === 'abiertos' ? abiertos : reportes;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Reportes</h1>
        <span className="contador">{abiertos.length} por atender · {reportes.length} en total</span>
      </div>

      <div className="filtro-segmentos" role="group" aria-label="Filtrar reportes">
        <button type="button" className={`btn btn-mini ${filtro === 'abiertos' ? 'btn-primario' : 'btn-fantasma'}`}
          aria-pressed={filtro === 'abiertos'} onClick={() => setFiltro('abiertos')}>Por atender</button>
        <button type="button" className={`btn btn-mini ${filtro === 'todos' ? 'btn-primario' : 'btn-fantasma'}`}
          aria-pressed={filtro === 'todos'} onClick={() => setFiltro('todos')}>Todos</button>
      </div>

      {error && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {error}
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {seleccionado && (
        <form ref={panel} className="tarjeta formulario resolver-panel" onSubmit={guardar} noValidate
          aria-label={`Resolver reporte #${seleccionado.id}`}>
          <h2>Resolver reporte #{seleccionado.id}</h2>
          <p className="nota-tabla">
            Publicación #{idDe(seleccionado.objeto)} · {MOTIVOS[seleccionado.motivo] || seleccionado.motivo}
            {seleccionado.descripcion ? ` · "${seleccionado.descripcion}"` : ''}
          </p>
          <fieldset className="resolver-opciones">
            <legend className="campo-label">Decisión</legend>
            <label><input type="radio" name="decision" value="RESUELTO" checked={decision.estado === 'RESUELTO'}
              onChange={() => setDecision((d) => ({ ...d, estado: 'RESUELTO' }))} /> Resuelto (se atendió)</label>
            <label><input type="radio" name="decision" value="RECHAZADO" checked={decision.estado === 'RECHAZADO'}
              onChange={() => setDecision((d) => ({ ...d, estado: 'RECHAZADO' }))} /> Rechazado (no procede)</label>
          </fieldset>
          <label className="campo">
            <span className="campo-label">Explicación *</span>
            <textarea
              className={`input textarea${errorDecision ? ' input-error' : ''}`}
              rows={3}
              maxLength={MAX_RESOLUCION}
              value={decision.resolucion}
              onChange={(e) => { setDecision((d) => ({ ...d, resolucion: e.target.value })); setErrorDecision(''); }}
              placeholder="Ej. Publicación revisada; el contenido cumple las normas."
            />
            <span className="nota-tabla">{decision.resolucion.trim().length}/{MAX_RESOLUCION}</span>
          </label>
          {errorDecision && <p className="campo-error" role="alert">{errorDecision}</p>}
          <p className="nota-tabla">La publicación, el motivo y la descripción del reporte se conservan. La decisión no se podrá cambiar después.</p>
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={cerrar} disabled={guardando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardando}>
              {guardando ? 'Guardando...' : 'Guardar decisión'}
            </button>
          </div>
        </form>
      )}

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : visibles.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="bandera" tamano={28} />
          </span>
          <p>{filtro === 'abiertos' ? 'No hay reportes por atender.' : 'No hay reportes.'}</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Reporte</th>
                <th>Motivo</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((r) => (
                <tr key={r.id}>
                  <td data-label="Reporte">#{r.id} · Publicación #{idDe(r.objeto)}</td>
                  <td data-label="Motivo">
                    {MOTIVOS[r.motivo] || r.motivo}
                    {r.descripcion && <span className="celda-detalle">{r.descripcion}</span>}
                  </td>
                  <td data-label="Estado">
                    <span className={`chip-estado ${CLASE_ESTADO[r.estado] || 'estado-pendiente'}`}>{r.estado}</span>
                    {r.resolucion && <span className="celda-detalle">{r.resolucion}</span>}
                  </td>
                  <td data-label="Fecha">
                    {fechaCorta(r.fecha_reporte)}
                    {r.fecha_resolucion && <span className="celda-detalle">Resuelto: {fechaCorta(r.fecha_resolucion)}</span>}
                  </td>
                  <td data-label="Acción">
                    {ABIERTOS.includes(r.estado) ? (
                      <button type="button" className="btn btn-primario btn-mini" onClick={() => abrir(r)}
                        disabled={guardando} aria-label={`Resolver reporte #${r.id}`}>
                        Resolver
                      </button>
                    ) : (
                      <span className="nota-tabla">Cerrado</span>
                    )}
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
