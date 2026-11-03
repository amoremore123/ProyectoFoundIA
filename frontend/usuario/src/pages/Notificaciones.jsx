import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import Icono from '../components/Icono';
import PreferenciasNotificaciones from '../components/PreferenciasNotificaciones';
import { getNotificaciones, marcarNotificacionLeida, mensajeError } from '../services/api';
import { avisarCambioNotificaciones } from '../hooks/useNotificaciones';

function fechaHora(valor) {
  if (!valor) return '';
  const [fecha, hora] = String(valor).split('T');
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}${hora ? ' · ' + hora.slice(0, 5) : ''}`;
}

// H18/H20: qué acción genera cada aviso (se muestra como etiqueta del aviso).
const TIPOS = {
  CONTACTO_RECIBIDO: { etiqueta: 'Solicitud de contacto', clase: 'tipo-contacto' },
  RECLAMO_RECIBIDO: { etiqueta: 'Reclamo', clase: 'tipo-reclamo' },
  // H23: aviso de una pareja nueva para quien perdió el objeto
  COINCIDENCIA_NUEVA: { etiqueta: 'Posible coincidencia', clase: 'tipo-coincidencia' },
};

// H23: solo se navega a rutas internas conocidas; el destino vuelve a validar la disponibilidad
export function enlaceInterno(enlace) {
  return typeof enlace === 'string' && /^\/(coincidencias|objeto)\/[A-Za-z0-9-]+$/.test(enlace) ? enlace : null;
}

// Notificaciones propias (las del usuario del token). Abrir un aviso lo marca como leído.
export default function Notificaciones() {
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorMarcar, setErrorMarcar] = useState('');
  const [marcando, setMarcando] = useState(null);
  const navigate = useNavigate();

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getNotificaciones();
      setNotificaciones(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      setErrorCarga(mensajeError(e, 'No se pudieron cargar las notificaciones'));
      setNotificaciones([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const abrir = async (noti) => {
    const destino = enlaceInterno(noti.enlace);
    if (noti.leida) {
      if (destino) navigate(destino);
      return;
    }
    setMarcando(noti.id);
    setErrorMarcar('');
    try {
      await marcarNotificacionLeida(noti.id);
      setNotificaciones((prev) => prev.map((n) => (n.id === noti.id ? { ...n, leida: true } : n)));
      avisarCambioNotificaciones(); // actualiza el contador de la campana
      if (destino) navigate(destino);
    } catch (e) {
      setErrorMarcar(mensajeError(e, 'No se pudo marcar la notificación como leída'));
    } finally {
      setMarcando(null);
    }
  };

  const sinLeer = notificaciones.filter((n) => !n.leida).length;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Notificaciones</h1>
        {!cargando && !errorCarga && notificaciones.length > 0 && (
          <span className="resultados-contador" role="status">
            {sinLeer === 0 ? 'Todas leídas' : `${sinLeer} sin leer`}
          </span>
        )}
      </div>

      <p className="nota-suave notificaciones-ayuda">
        Recibes un aviso de <strong>solicitud de contacto</strong> cuando el dueño de un objeto perdido cree que
        coincide con algo que publicaste como encontrado, y un aviso de <strong>reclamo</strong> cuando alguien
        dice que un objeto que publicaste es suyo. También te avisamos de <strong>posibles coincidencias</strong> con
        tus objetos perdidos. Tus datos de contacto nunca se comparten.
      </p>

      {/* H24 */}
      <PreferenciasNotificaciones />

      <MensajeError mensaje={errorCarga} onReintentar={cargar} />
      <MensajeError mensaje={errorMarcar} />

      {cargando ? (
        <Cargando />
      ) : errorCarga ? null : notificaciones.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="campanaOff" tamano={28} />
          </span>
          <p>No tienes notificaciones todavía.</p>
        </div>
      ) : (
        <ul className="lista-notificaciones">
          {notificaciones.map((noti) => {
            const tipo = TIPOS[noti.tipo] || { etiqueta: 'Aviso', clase: 'tipo-general' };
            return (
              <li key={noti.id}>
                <button
                  type="button"
                  className={`notificacion${noti.leida ? '' : ' no-leida'}`}
                  onClick={() => abrir(noti)}
                  disabled={marcando === noti.id}
                  aria-label={`${tipo.etiqueta}: ${noti.titulo}. ${noti.leida ? 'Leída' : 'Sin leer, pulsa para marcarla como leída'}${enlaceInterno(noti.enlace) ? '. Abre la coincidencia' : ''}`}
                >
                  <span className="notificacion-icono" aria-hidden="true">
                    <Icono nombre="campana" tamano={18} />
                  </span>
                  <span className="notificacion-cuerpo">
                    <span className={`notificacion-tipo ${tipo.clase}`}>{tipo.etiqueta}</span>
                    <span className="notificacion-titulo">{noti.titulo}</span>
                    <span className="notificacion-mensaje">{noti.mensaje}</span>
                    <span className="notificacion-fecha">
                      {fechaHora(noti.fechaCreacion)}{noti.leida ? ' · Leída' : ''}
                    </span>
                  </span>
                  {!noti.leida && <span className="notificacion-punto" aria-hidden="true" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
