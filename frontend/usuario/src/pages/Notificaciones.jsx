import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import Icono from '../components/Icono';
import PreferenciasNotificaciones from '../components/PreferenciasNotificaciones';
import {
  getHistorialNotificaciones, marcarNotificacionLeida, marcarTodasNotificacionesLeidas, mensajeError,
} from '../services/api';
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
  // H53: recordatorio de un objeto propio sin resolver; su enlace lleva a /objeto/{id}
  RECORDATORIO_OBJETO: { etiqueta: 'Recordatorio', clase: 'tipo-recordatorio' },
};

// H25: filtros del historial
const FILTROS = [
  { valor: 'TODAS', texto: 'Todas', vacio: 'No tienes notificaciones todavía.' },
  { valor: 'NO_LEIDAS', texto: 'Sin leer', vacio: 'No tienes avisos sin leer.' },
  { valor: 'LEIDAS', texto: 'Leídas', vacio: 'Aún no has leído ningún aviso.' },
];
const TAMANO = 20;

// H23: solo se navega a rutas internas conocidas; el destino vuelve a validar la disponibilidad
export function enlaceInterno(enlace) {
  return typeof enlace === 'string' && /^\/(coincidencias|objeto)\/[A-Za-z0-9-]+$/.test(enlace) ? enlace : null;
}

// Notificaciones propias (las del usuario del token). Abrir un aviso lo marca como leído.
// H25: historial con filtro leídas/sin leer, paginación y «marcar todas como leídas».
export default function Notificaciones() {
  const [filtro, setFiltro] = useState('TODAS');
  const [notificaciones, setNotificaciones] = useState([]);
  const [pagina, setPagina] = useState({ actual: 0, total: 0, elementos: 0 });
  const [noLeidas, setNoLeidas] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorMas, setErrorMas] = useState('');
  const [errorMarcar, setErrorMarcar] = useState('');
  const [marcando, setMarcando] = useState(null);
  const [marcandoTodas, setMarcandoTodas] = useState(false);
  const [aviso, setAviso] = useState('');
  const peticion = useRef(0);
  const navigate = useNavigate();

  const cargar = useCallback(async (estado = filtro) => {
    const id = ++peticion.current;
    setCargando(true);
    setErrorCarga('');
    setErrorMas('');
    try {
      const data = await getHistorialNotificaciones({ estado, pagina: 0, tamano: TAMANO });
      if (id !== peticion.current) return;
      setNotificaciones(data?.contenido || []);
      setPagina({ actual: data?.pagina ?? 0, total: data?.totalPaginas ?? 0, elementos: data?.total ?? 0 });
      setNoLeidas(data?.noLeidas ?? 0);
    } catch (e) {
      if (id !== peticion.current) return;
      setErrorCarga(mensajeError(e, 'No se pudieron cargar las notificaciones'));
      setNotificaciones([]);
    } finally {
      if (id === peticion.current) setCargando(false);
    }
  }, [filtro]);

  useEffect(() => {
    cargar(filtro);
  }, [cargar, filtro]);

  const cambiarFiltro = (valor) => {
    if (valor === filtro) return;
    setAviso('');
    setErrorMarcar('');
    setFiltro(valor);
  };

  const cargarMas = async () => {
    setCargandoMas(true);
    setErrorMas('');
    try {
      const data = await getHistorialNotificaciones({ estado: filtro, pagina: pagina.actual + 1, tamano: TAMANO });
      setNotificaciones((prev) => {
        const vistos = new Set(prev.map((n) => n.id));
        return [...prev, ...(data?.contenido || []).filter((n) => !vistos.has(n.id))];
      });
      setPagina({ actual: data?.pagina ?? pagina.actual + 1, total: data?.totalPaginas ?? pagina.total, elementos: data?.total ?? pagina.elementos });
      setNoLeidas(data?.noLeidas ?? noLeidas);
    } catch (e) {
      setErrorMas(mensajeError(e, 'No se pudieron cargar más avisos'));
    } finally {
      setCargandoMas(false);
    }
  };

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
      setNoLeidas((n) => Math.max(0, n - 1));
      avisarCambioNotificaciones(); // actualiza el contador de la campana
      if (destino) navigate(destino);
    } catch (e) {
      setErrorMarcar(mensajeError(e, 'No se pudo marcar la notificación como leída'));
    } finally {
      setMarcando(null);
    }
  };

  const marcarTodas = async () => {
    setMarcandoTodas(true);
    setErrorMarcar('');
    setAviso('');
    try {
      const r = await marcarTodasNotificacionesLeidas();
      setNoLeidas(r?.noLeidas ?? 0);
      avisarCambioNotificaciones();
      setAviso(r?.actualizadas === 1 ? 'Marcaste 1 aviso como leído.' : `Marcaste ${r?.actualizadas ?? 0} avisos como leídos.`);
      // Se vuelve a pedir la vista para que el filtro "Sin leer" quede vacío de verdad
      await cargar(filtro);
    } catch (e) {
      setErrorMarcar(mensajeError(e, 'No se pudieron marcar los avisos como leídos'));
    } finally {
      setMarcandoTodas(false);
    }
  };

  const vacio = FILTROS.find((f) => f.valor === filtro)?.vacio;
  const hayMas = pagina.actual + 1 < pagina.total;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Notificaciones</h1>
        {!cargando && !errorCarga && (pagina.elementos > 0 || noLeidas > 0) && (
          <span className="resultados-contador" role="status">
            {noLeidas === 0 ? 'Todas leídas' : `${noLeidas} sin leer`}
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

      <div className="notificaciones-barra">
        <div className="filtro-segmentos" role="group" aria-label="Filtrar notificaciones">
          {FILTROS.map((f) => (
            <button key={f.valor} type="button" aria-pressed={filtro === f.valor}
              className={`btn btn-mini ${filtro === f.valor ? 'btn-primario' : 'btn-fantasma'}`}
              onClick={() => cambiarFiltro(f.valor)}>
              {f.texto}
            </button>
          ))}
        </div>
        {noLeidas > 0 && !errorCarga && (
          <button type="button" className="btn btn-secundario btn-mini" onClick={marcarTodas} disabled={marcandoTodas || cargando}>
            <Icono nombre="check" tamano={16} /> {marcandoTodas ? 'Marcando...' : 'Marcar todas como leídas'}
          </button>
        )}
      </div>

      {aviso && <p className="aviso-accion aviso-ok" role="status">{aviso}</p>}
      <MensajeError mensaje={errorCarga} onReintentar={() => cargar(filtro)} />
      <MensajeError mensaje={errorMarcar} />

      {cargando ? (
        <Cargando />
      ) : errorCarga ? null : notificaciones.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="campanaOff" tamano={28} />
          </span>
          <p>{vacio}</p>
        </div>
      ) : (
        <>
          <ul className="lista-notificaciones">
            {notificaciones.map((noti) => {
              const tipo = TIPOS[noti.tipo] || { etiqueta: 'Aviso', clase: 'tipo-general' };
              const destino = enlaceInterno(noti.enlace);
              return (
                <li key={noti.id}>
                  <button
                    type="button"
                    className={`notificacion${noti.leida ? '' : ' no-leida'}`}
                    onClick={() => abrir(noti)}
                    disabled={marcando === noti.id}
                    aria-label={`${tipo.etiqueta}: ${noti.titulo}. ${noti.leida ? 'Leída' : 'Sin leer, pulsa para marcarla como leída'}${destino ? (destino.startsWith('/objeto/') ? '. Abre la publicación' : '. Abre la coincidencia') : ''}`}
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
          <MensajeError mensaje={errorMas} onReintentar={cargarMas} />
          {hayMas && (
            <div className="notificaciones-mas">
              <button type="button" className="btn btn-fantasma" onClick={cargarMas} disabled={cargandoMas}>
                {cargandoMas ? 'Cargando...' : 'Ver más avisos'}
              </button>
              <span className="nota-suave">Mostrando {notificaciones.length} de {pagina.elementos}</span>
            </div>
          )}
        </>
      )}
    </div>
  );
}
