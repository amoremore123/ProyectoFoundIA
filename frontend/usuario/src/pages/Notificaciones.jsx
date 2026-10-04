import { useCallback, useEffect, useState } from 'react';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { getNotificaciones, marcarNotificacionLeida, mensajeError } from '../services/api';

function fechaHora(valor) {
  if (!valor) return '';
  const [fecha, hora] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}${hora ? ' · ' + hora.slice(0, 5) : ''}`;
}

export default function Notificaciones() {
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [marcando, setMarcando] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getNotificaciones();
      setNotificaciones(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar las notificaciones'));
      setNotificaciones([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const abrir = async (noti) => {
    if (noti.leida) return;
    setMarcando(noti.id);
    try {
      await marcarNotificacionLeida(noti.id);
      setNotificaciones((prev) => prev.map((n) => (n.id === noti.id ? { ...n, leida: true } : n)));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo marcar la notificación como leída'));
    } finally {
      setMarcando(null);
    }
  };

  return (
    <div className="pagina">
      <h1 className="pagina-titulo">Notificaciones</h1>

      <MensajeError mensaje={error} onReintentar={cargar} />

      {cargando ? (
        <Cargando />
      ) : notificaciones.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            🔕
          </span>
          <p>No tienes notificaciones todavía.</p>
        </div>
      ) : (
        <ul className="lista-notificaciones">
          {notificaciones.map((noti) => (
            <li key={noti.id}>
              <button
                type="button"
                className={`notificacion${noti.leida ? '' : ' no-leida'}`}
                onClick={() => abrir(noti)}
                disabled={marcando === noti.id}
              >
                <span className="notificacion-icono" aria-hidden="true">
                  🔔
                </span>
                <span className="notificacion-cuerpo">
                  <span className="notificacion-titulo">{noti.titulo}</span>
                  <span className="notificacion-mensaje">{noti.mensaje}</span>
                  <span className="notificacion-fecha">{fechaHora(noti.fechaCreacion)}</span>
                </span>
                {!noti.leida && <span className="notificacion-punto" aria-label="Sin leer" />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
