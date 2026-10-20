import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import ChipTipo from '../components/ChipTipo';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import ImagenObjeto from '../components/ImagenObjeto';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { eliminarObjeto, getMisObjetos, mensajeError, statusDe } from '../services/api';
import { useToast } from '../context/ToastContext';
import { ESTADOS_EDITABLES } from './Publicar';

function fechaCorta(valor) {
  if (!valor) return '';
  const parte = String(valor).split('T')[0];
  const [anio, mes, dia] = parte.split('-');
  if (!anio || !mes || !dia) return parte;
  return `${dia}/${mes}/${anio}`;
}

const ESTADOS = {
  ACTIVO: 'Activo',
  RECUPERADO: 'Recuperado',
  OCULTO: 'Oculto',
  ELIMINADO: 'Eliminado',
};

const NOTAS_ESTADO = {
  OCULTO: 'La administración la ocultó: no aparece en las búsquedas públicas. Puedes corregirla.',
  ELIMINADO: 'Se conserva en tu historial y ya no aparece en las búsquedas públicas.',
};

// Mismas reglas que la API (H5): se edita si no está eliminada y se elimina si está visible.
const puedeEditar = (o) => ESTADOS_EDITABLES.includes(o.estado);
const puedeEliminar = (o) => o.estado === 'ACTIVO' || o.estado === 'RECUPERADO';

// H14 - Publicaciones del usuario del token, con su estado y las acciones permitidas.
export default function MisPublicaciones() {
  const { mostrarToast } = useToast();
  const [objetos, setObjetos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [aEliminar, setAEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getMisObjetos();
      setObjetos(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setErrorCarga(mensajeError(e, 'No se pudieron cargar tus publicaciones'));
      setObjetos([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const confirmarEliminar = async () => {
    const objeto = aEliminar;
    setEliminando(true);
    setErrorAccion('');
    try {
      await eliminarObjeto(objeto.id);
      setObjetos((prev) => prev.map((o) => (o.id === objeto.id ? { ...o, estado: 'ELIMINADO' } : o)));
      mostrarToast(`"${objeto.nombre}" se eliminó y se conserva en tu historial.`, 'exito');
      setAEliminar(null);
    } catch (e) {
      const status = statusDe(e);
      setErrorAccion(status === 403 || status === 404
        ? 'No se pudo eliminar: la publicación no es tuya o ya fue eliminada.'
        : mensajeError(e, 'No se pudo eliminar la publicación'));
      setAEliminar(null);
    } finally {
      setEliminando(false);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Mis publicaciones</h1>
        <Link to="/publicar" className="btn btn-primario">
          <Icono nombre="mas" tamano={16} /> Publicar objeto
        </Link>
      </div>

      <MensajeError mensaje={errorCarga} onReintentar={cargar} />
      <MensajeError mensaje={errorAccion} />

      {cargando ? (
        <Cargando />
      ) : errorCarga ? null : objetos.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="bandeja" tamano={28} />
          </span>
          <p>Aún no has publicado nada.</p>
          <Link to="/publicar" className="btn btn-secundario">
            Publicar mi primer objeto
          </Link>
        </div>
      ) : (
        <>
          <p className="resultados-contador" role="status">
            {objetos.length} {objetos.length === 1 ? 'publicación' : 'publicaciones'}
          </p>
          <ul className="lista-mis-publicaciones">
            {objetos.map((obj) => (
              <li key={obj.id} className={`tarjeta mis-publicacion-card estado-card-${(obj.estado || '').toLowerCase()}`}>
                <div className="mis-publicacion-foto" aria-hidden="true">
                  <ImagenObjeto objeto={obj} />
                </div>
                <div className="mis-publicacion-info">
                  <div className="coincidencia-top">
                    <ChipTipo tipo={obj.tipo} />
                    <span className={`chip-estado estado-${(obj.estado || '').toLowerCase()}`}>
                      {ESTADOS[obj.estado] || obj.estado}
                    </span>
                  </div>
                  {obj.estado === 'ACTIVO' || obj.estado === 'RECUPERADO' ? (
                    <Link to={`/objeto/${obj.id}`} className="mis-publicacion-titulo">{obj.nombre}</Link>
                  ) : (
                    <span className="mis-publicacion-titulo">{obj.nombre}</span>
                  )}
                  <p className="objeto-card-linea"><Icono nombre="ubicacion" tamano={14} /> {obj.ubicacion || 'Sin ubicación'}</p>
                  <p className="objeto-card-linea">
                    <Icono nombre="calendario" tamano={14} /> {fechaCorta(obj.fechaObjeto)} · <Icono nombre="etiqueta" tamano={14} /> {obj.categoria?.nombre || '—'}
                  </p>
                  {NOTAS_ESTADO[obj.estado] && <p className="nota-suave" role="status">{NOTAS_ESTADO[obj.estado]}</p>}
                </div>
                {(puedeEditar(obj) || puedeEliminar(obj)) && (
                  <div className="mis-publicacion-acciones">
                    {puedeEditar(obj) && (
                      <Link
                        to={`/mis-publicaciones/${obj.id}/editar`}
                        className="btn btn-secundario"
                        aria-label={`Editar ${obj.nombre}`}
                      >
                        Editar
                      </Link>
                    )}
                    {puedeEliminar(obj) && (
                      <button
                        type="button"
                        className="btn btn-peligro"
                        onClick={() => { setErrorAccion(''); setAEliminar(obj); }}
                        aria-label={`Eliminar ${obj.nombre}`}
                      >
                        Eliminar
                      </button>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      <ConfirmarDialogo
        abierto={Boolean(aEliminar)}
        titulo="¿Eliminar esta publicación?"
        mensaje={aEliminar
          ? `"${aEliminar.nombre}" dejará de verse en las búsquedas públicas. Seguirá en tu historial como eliminada.`
          : ''}
        textoConfirmar="Sí, eliminar"
        peligro
        procesando={eliminando}
        onConfirmar={confirmarEliminar}
        onCancelar={() => setAEliminar(null)}
      />
    </div>
  );
}
