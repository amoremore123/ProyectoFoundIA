import { useCallback, useEffect, useState } from 'react';
import { getPublicaciones, mensajeError, patchPublicacion } from '../services/api';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';

function fechaCorta(valor) {
  if (!valor) return '—';
  const [fecha] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

const CLASE_ESTADO = {
  ACTIVO: 'estado-activo',
  RECUPERADO: 'estado-activo',
  OCULTO: 'estado-oculto',
  ELIMINADO: 'estado-eliminado',
};

// Textos de cada acción del panel (H15). Eliminar es lógico: la fila se conserva.
const ACCIONES = {
  OCULTO: {
    boton: 'Ocultar', titulo: '¿Ocultar esta publicación?', confirmar: 'Sí, ocultar', peligro: false,
    mensaje: (p) => `"${p.nombre}" dejará de verse en búsquedas, detalle y coincidencias. Podrás volver a mostrarla.`,
    exito: (p) => `"${p.nombre}" quedó oculta para la comunidad.`,
  },
  ACTIVO: {
    boton: 'Mostrar', titulo: '¿Volver a mostrar esta publicación?', confirmar: 'Sí, mostrar', peligro: false,
    mensaje: (p) => `"${p.nombre}" volverá a verse en las búsquedas públicas.`,
    exito: (p) => `"${p.nombre}" volvió a estar visible.`,
  },
  ELIMINADO: {
    boton: 'Eliminar', titulo: '¿Eliminar esta publicación?', confirmar: 'Sí, eliminar', peligro: true,
    mensaje: (p) => `"${p.nombre}" se eliminará de forma lógica: deja de verse para siempre en la comunidad, pero el registro se conserva. No se puede deshacer desde el panel.`,
    exito: (p) => `"${p.nombre}" fue eliminada.`,
  },
};

function accionesPara(estado) {
  if (estado === 'ELIMINADO') return [];
  return estado === 'OCULTO' ? ['ACTIVO', 'ELIMINADO'] : ['OCULTO', 'ELIMINADO'];
}

export default function Publicaciones() {
  const { mostrarToast } = useToast();
  const [publicaciones, setPublicaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [pendiente, setPendiente] = useState(null); // { pub, nuevoEstado }
  const [accionando, setAccionando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getPublicaciones();
      setPublicaciones(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setErrorCarga(mensajeError(e, 'No se pudieron cargar las publicaciones'));
      setPublicaciones([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const confirmar = async () => {
    const { pub, nuevoEstado } = pendiente;
    setAccionando(true);
    setErrorAccion('');
    try {
      const actualizada = await patchPublicacion(pub.id, { estado: nuevoEstado });
      setPublicaciones((prev) => prev.map((p) => (p.id === pub.id ? { ...p, ...actualizada } : p)));
      mostrarToast(ACCIONES[nuevoEstado].exito(pub), 'exito');
    } catch (e) {
      setErrorAccion(mensajeError(e, 'No se pudo actualizar la publicación'));
    } finally {
      setAccionando(false);
      setPendiente(null);
    }
  };

  const accion = pendiente ? ACCIONES[pendiente.nuevoEstado] : null;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Publicaciones</h1>
        <span className="contador">{publicaciones.length} registros</span>
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
      ) : errorCarga ? null : publicaciones.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="caja" tamano={28} />
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
                <th>Publicado por</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {publicaciones.map((p) => (
                <tr key={p.id}>
                  <td data-label="Nombre">{p.nombre}</td>
                  <td data-label="Tipo">
                    <span className={`chip ${p.tipo === 'PERDIDO' ? 'chip-perdido' : 'chip-encontrado'}`}>
                      {p.tipo === 'PERDIDO' ? 'Perdido' : 'Encontrado'}
                    </span>
                  </td>
                  <td data-label="Categoría">{p.categoria_nombre || p.categoria?.nombre || '—'}</td>
                  <td data-label="Publicado por">{p.publicado_por || '—'}</td>
                  <td data-label="Estado">
                    <span className={`chip-estado ${CLASE_ESTADO[p.estado] || 'estado-activo'}`}>{p.estado}</span>
                  </td>
                  <td data-label="Fecha">{fechaCorta(p.fecha_objeto || p.fecha_publicacion)}</td>
                  <td data-label="Acciones">
                    {accionesPara(p.estado).length === 0 ? (
                      <span className="nota-acciones">Sin acciones</span>
                    ) : (
                      <div className="acciones-fila">
                        {accionesPara(p.estado).map((estado) => (
                          <button
                            key={estado}
                            type="button"
                            className={`btn ${ACCIONES[estado].peligro ? 'btn-peligro' : 'btn-secundario'} btn-mini`}
                            onClick={() => { setErrorAccion(''); setPendiente({ pub: p, nuevoEstado: estado }); }}
                            aria-label={`${ACCIONES[estado].boton} ${p.nombre}`}
                          >
                            {ACCIONES[estado].boton}
                          </button>
                        ))}
                      </div>
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
        titulo={accion?.titulo || ''}
        mensaje={pendiente ? accion.mensaje(pendiente.pub) : ''}
        textoConfirmar={accion?.confirmar}
        peligro={accion?.peligro}
        procesando={accionando}
        onConfirmar={confirmar}
        onCancelar={() => setPendiente(null)}
      />
    </div>
  );
}
