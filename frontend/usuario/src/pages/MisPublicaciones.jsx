import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import ChipTipo from '../components/ChipTipo';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { eliminarObjeto, getMisObjetos, mensajeError } from '../services/api';

function fechaCorta(valor) {
  if (!valor) return '';
  const parte = String(valor).split('T')[0];
  const [anio, mes, dia] = parte.split('-');
  if (!anio || !mes || !dia) return parte;
  return `${dia}/${mes}/${anio}`;
}

const ESTADOS = {
  ACTIVO: 'Activo',
  OCULTO: 'Oculto',
  ELIMINADO: 'Eliminado',
};

export default function MisPublicaciones() {
  const [objetos, setObjetos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [eliminando, setEliminando] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getMisObjetos();
      setObjetos(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar tus publicaciones'));
      setObjetos([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const borrar = async (objeto) => {
    const confirmar = window.confirm(`¿Eliminar "${objeto.nombre}"? Esta acción no se puede deshacer.`);
    if (!confirmar) return;
    setEliminando(objeto.id);
    setError('');
    try {
      await eliminarObjeto(objeto.id);
      setObjetos((prev) => prev.filter((o) => o.id !== objeto.id));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo eliminar la publicación'));
    } finally {
      setEliminando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Mis publicaciones</h1>
        <Link to="/publicar" className="btn btn-primario">
          + Publicar objeto
        </Link>
      </div>

      <MensajeError mensaje={error} onReintentar={cargar} />

      {cargando ? (
        <Cargando />
      ) : objetos.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            📭
          </span>
          <p>Aún no has publicado nada.</p>
          <Link to="/publicar" className="btn btn-secundario">
            Publicar mi primer objeto
          </Link>
        </div>
      ) : (
        <ul className="lista-mis-publicaciones">
          {objetos.map((obj) => (
            <li key={obj.id} className="tarjeta mis-publicacion-card">
              <div className="mis-publicacion-info">
                <div className="coincidencia-top">
                  <ChipTipo tipo={obj.tipo} />
                  <span className={`chip-estado estado-${(obj.estado || '').toLowerCase()}`}>
                    {ESTADOS[obj.estado] || obj.estado}
                  </span>
                </div>
                <Link to={`/objeto/${obj.id}`} className="mis-publicacion-titulo">
                  {obj.nombre}
                </Link>
                <p className="objeto-card-linea">📍 {obj.ubicacion || 'Sin ubicación'}</p>
                <p className="objeto-card-linea">
                  📅 {fechaCorta(obj.fechaObjeto)} · 🗂️ {obj.categoria?.nombre || '—'}
                </p>
              </div>
              <div className="mis-publicacion-acciones">
                <button
                  type="button"
                  className="btn btn-peligro"
                  onClick={() => borrar(obj)}
                  disabled={eliminando === obj.id}
                >
                  {eliminando === obj.id ? 'Eliminando...' : 'Eliminar'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
