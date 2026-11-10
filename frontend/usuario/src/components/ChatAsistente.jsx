import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Icono from './Icono';
import ChipTipo from './ChipTipo';
import ConfirmarDialogo from './ConfirmarDialogo';
import { useAsistente } from '../context/AsistenteContext';
import { useAuth } from '../context/AuthContext';
import { AVATAR_FINDI } from '../config/findi';
import useVoz from '../hooks/useVoz';
import { desplazarASeccion } from '../utils/navegacion';
import { buscarObjetos, fotoUrl, getMisObjetos, interpretarConIA, listarCategorias, mensajeError } from '../services/api';
import { ACCESOS_FINDI, adaptarInterpretacion, interpretar, responder, responderAcceso } from '../utils/asistente';
import { accionesValidas, datosPrellenado, tarjetasValidas } from '../utils/tarjetasFindi';

const BIENVENIDA = {
  id: 'bienvenida',
  de: 'bot',
  texto: '¡Hola! Soy FINDI, el asistente de FoundIA. Puedo ayudarte a buscar, reportar y recuperar objetos en Tecsup. ¿En qué te ayudo?',
  accesos: true,
};

// Fase 4: estados visuales de FINDI. El avatar no cambia; solo el anillo, la etiqueta y el punto.
const ETIQUETA_ESTADO = {
  'en-linea': 'En línea',
  saludando: 'Saludando',
  escuchando: 'Escuchando…',
  pensando: 'Pensando…',
  respondiendo: 'Respondiendo…',
  completada: 'Búsqueda completada',
  error: 'Sin conexión',
};

const lista = (data) => (Array.isArray(data) ? data : data?.results || []);

const ESTADO_REPORTE = { ACTIVO: 'Activo', RECUPERADO: 'Recuperado', OCULTO: 'Oculto', ELIMINADO: 'Eliminado', EN_CUSTODIA: 'En custodia' };

const AVISO_VOZ = 'Para entender tu voz, tu navegador envía el audio a su servicio de reconocimiento (Google en Chrome, Microsoft en Edge). FoundIA no guarda tu voz. Si prefieres, puedes escribir.';

// Chat flotante del asistente de búsqueda (disponible en todas las páginas del MainLayout)
export default function ChatAsistente() {
  const { abierto, abrir, cerrar, pendiente, consumirPendiente } = useAsistente();
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [saludando, setSaludando] = useState(false);
  const yaSaludo = useRef(false);
  const [completada, setCompletada] = useState(null);
  const [mensajes, setMensajes] = useState([BIENVENIDA]);
  const [texto, setTexto] = useState('');
  const [pensando, setPensando] = useState(false);
  const categoriasRef = useRef(null);
  const finRef = useRef(null);
  const inputRef = useRef(null);
  const contador = useRef(0);
  const abiertoRef = useRef(abierto);
  abiertoRef.current = abierto;
  const [leerRespuestas, setLeerRespuestas] = useState(false);
  const avisoVozMostrado = useRef(false);
  const [dictado, setDictado] = useState(false);
  // H47: acción propuesta por FINDI que espera confirmación
  const [accionPendiente, setAccionPendiente] = useState(null);
  // H46: lo dictado se escribe en la caja para revisarlo y corregirlo antes de enviar
  const voz = useVoz({
    alParcial: (parcial) => setTexto(parcial),
    alReconocer: (dicho) => {
      setTexto(dicho);
      setDictado(true);
      inputRef.current?.focus();
    },
  });

  const agregar = (mensaje) => {
    const id = ++contador.current;
    setMensajes((prev) => [...prev, { ...mensaje, id }]);
  };

  // El chat permanece montado al cerrarlo: detener también las tareas de voz.
  useEffect(() => {
    if (!abierto) {
      voz.detener();
      voz.callar();
    }
  }, [abierto, voz.detener, voz.callar]);

  // Consulta GET /api/perfil/objetos con la sesión: el backend solo devuelve las publicaciones propias
  const responderMisReportes = async () => {
    if (!auth?.token) return responderAcceso('mis-reportes', { conSesion: false });
    const propios = lista(await getMisObjetos());
    if (!propios.length) {
      return {
        texto: 'Todavía no tienes reportes publicados. Cuando reportes un objeto, aquí verás su estado.',
        acciones: [{ texto: 'Reportar un objeto', to: '/publicar' }],
      };
    }
    const n = propios.length;
    return {
      texto: `Tienes ${n} ${n === 1 ? 'reporte' : 'reportes'}. ${n > 5 ? 'Estos son los 5 más recientes:' : 'Este es su estado:'}`,
      misReportes: [...propios].sort((a, b) => String(b.fechaPublicacion).localeCompare(String(a.fechaPublicacion))).slice(0, 5),
      acciones: [{ texto: 'Ver mis reportes', to: '/mis-publicaciones' }],
    };
  };

  const preguntar = useCallback(async (pregunta, { repetir = false } = {}) => {
    const limpio = pregunta.trim();
    if (!limpio || pensando) return;
    setCompletada(null);
    // Al reintentar no se repite el mensaje del usuario
    if (!repetir) agregar({ de: 'usuario', texto: limpio });
    setTexto('');
    setDictado(false);
    setPensando(true);
    try {
      if (!categoriasRef.current) categoriasRef.current = lista(await listarCategorias());
      // El backend interpreta el mensaje; ante errores se usa el motor local de la web.
      const [datos, ia] = await Promise.all([
        buscarObjetos({ q: '' }),
        interpretarConIA(limpio).catch(() => null),
      ]);
      let consulta = adaptarInterpretacion(ia, categoriasRef.current);
      const local = interpretar(limpio, categoriasRef.current);
      // Estas acciones de la web aún no forman parte del vocabulario compartido de H45.
      if (['mis_reportes', 'como_funciona'].includes(local.intencion)
          && consulta?.intencion !== local.intencion) consulta = null;
      // Fase 5: "mis reportes" se responde con tus publicaciones reales (requiere sesión)
      const intencion = (consulta || local).intencion;
      const respuesta = intencion === 'mis_reportes'
        ? await responderMisReportes()
        : responder(limpio, { objetos: lista(datos), categorias: categoriasRef.current, consulta });
      // H45: se indica con transparencia si respondió la IA o el motor local de respaldo
      const fuente = consulta && ia?.fuente !== 'local' ? 'ia' : 'local';
      // H47: tarjetas y acciones del catálogo cerrado (se descarta lo que no se conoce)
      const tarjetas = tarjetasValidas(ia?.tarjetas);
      const accionesFindi = accionesValidas(ia?.acciones);
      agregar({ de: 'bot', fuente, ...respuesta, tarjetas, accionesFindi });
      if (respuesta.resultados?.length) setCompletada(respuesta.resultados.length);
      if (leerRespuestas && abiertoRef.current) voz.leer(respuesta.texto);
    } catch (e) {
      agregar({
        de: 'bot',
        error: true,
        reintentar: limpio,
        texto: `No pude consultar las publicaciones (${mensajeError(e, 'error de conexión')}). Revisa que la API esté encendida e inténtalo de nuevo.`,
      });
    } finally {
      setPensando(false);
    }
  }, [pensando, leerRespuestas, voz.leer, auth?.token]);

  // Mensaje enviado desde otra página (p. ej. el buscador de la portada)
  useEffect(() => {
    if (abierto && pendiente && !pensando) preguntar(consumirPendiente());
  }, [abierto, pendiente, pensando, preguntar, consumirPendiente]);

  useEffect(() => {
    finRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
  }, [mensajes, pensando, abierto]);

  useEffect(() => {
    if (abierto) inputRef.current?.focus();
  }, [abierto]);

  // FINDI saluda la primera vez que se abre el chat
  useEffect(() => {
    if (!abierto || yaSaludo.current) return undefined;
    yaSaludo.current = true;
    setSaludando(true);
    const t = setTimeout(() => setSaludando(false), 2800);
    return () => clearTimeout(t);
  }, [abierto]);

  // "Búsqueda completada" se muestra unos segundos y vuelve a "En línea"
  useEffect(() => {
    if (completada == null) return undefined;
    const t = setTimeout(() => setCompletada(null), 4500);
    return () => clearTimeout(t);
  }, [completada]);

  // Accesos rápidos: respuestas fijas, sin consultar la API
  const usarAcceso = (acceso) => {
    if (pensando) return;
    setCompletada(null);
    agregar({ de: 'usuario', texto: acceso.texto });
    const respuesta = responderAcceso(acceso.id, { conSesion: Boolean(auth?.token) });
    agregar({ de: 'bot', ...respuesta });
    if (leerRespuestas) voz.leer(respuesta.texto);
    if (respuesta.enfocar) inputRef.current?.focus();
  };

  // Las acciones con ancla (p. ej. "/#proceso-recuperacion") llevan a la sección de la portada
  const irAAccion = (event, destino) => {
    cerrar();
    if (!destino.includes('#')) return;
    event.preventDefault();
    const [ruta, ancla] = destino.split('#');
    if (location.pathname !== (ruta || '/')) navigate(ruta || '/');
    desplazarASeccion(ancla);
  };

  // H47: nada se ejecuta sin confirmar; al aceptar solo se abre la pantalla con datos prellenados
  const confirmarAccion = () => {
    const a = accionPendiente;
    setAccionPendiente(null);
    if (!a) return;
    cerrar();
    if (a.tipo === 'PRELLENAR_PUBLICAR') navigate('/publicar', { state: { prellenar: datosPrellenado(a.datos) } });
    else if (a.tipo === 'ABRIR_RECLAMO') navigate(a.destino, { state: { abrirReclamo: true } });
  };

  const ultimoBot = [...mensajes].reverse().find((m) => m.de === 'bot');
  let estado = 'en-linea';
  if (voz.escuchando) estado = 'escuchando';
  else if (pensando) estado = 'pensando';
  else if (ultimoBot?.error) estado = 'error';
  else if (voz.hablando) estado = 'respondiendo';
  else if (completada != null) estado = 'completada';
  else if (saludando) estado = 'saludando';
  const etiquetaEstado = estado === 'completada'
    ? `${ETIQUETA_ESTADO.completada} · ${completada} ${completada === 1 ? 'resultado' : 'resultados'}`
    : ETIQUETA_ESTADO[estado];

  useEffect(() => {
    if (!abierto) return undefined;
    const alPresionar = (e) => e.key === 'Escape' && cerrar();
    window.addEventListener('keydown', alPresionar);
    return () => window.removeEventListener('keydown', alPresionar);
  }, [abierto, cerrar]);

  // H46: el primer uso del micrófono muestra el aviso de privacidad de la voz
  const usarMicrofono = () => {
    if (!avisoVozMostrado.current && !voz.escuchando) {
      avisoVozMostrado.current = true;
      agregar({ de: 'bot', texto: AVISO_VOZ, avisoVoz: true });
    }
    voz.escuchar();
  };

  const alternarLectura = () => {
    if (leerRespuestas) voz.callar();
    // Fase 6: el mismo toque que activa la voz habilita el audio en celulares
    else voz.prepararAudio();
    setLeerRespuestas((v) => !v);
  };

  const enviar = (e) => {
    e.preventDefault();
    preguntar(texto);
  };

  return (
    <>
      {abierto && (
        <section className="chat-panel" role="dialog" aria-label="FINDI, asistente de FoundIA">
          <div className="chat-cabecera">
            <span className={`chat-avatar-marco estado-${estado}`} data-estado={estado}>
              <span className="chat-avatar"><img src={AVATAR_FINDI} alt="" /></span>
              {estado === 'saludando' && <span className="chat-saludo" aria-hidden="true">👋</span>}
            </span>
            <div className="chat-cabecera-texto">
              <strong>FINDI</strong>
              <span className="chat-estado-linea" data-testid="estado-findi">
                {estado === 'respondiendo'
                  ? <i className="chat-ondas" aria-hidden="true"><b /><b /><b /></i>
                  : <i className={`chat-estado estado-${estado}`} aria-hidden="true" />}
                {etiquetaEstado}
              </span>
            </div>
            {voz.soportaLectura && (
              <button
                type="button"
                className={`chat-cerrar chat-leer${leerRespuestas ? ' activo' : ''}`}
                onClick={alternarLectura}
                aria-pressed={leerRespuestas}
                aria-label={leerRespuestas ? 'Dejar de leer las respuestas en voz alta' : 'Leer las respuestas en voz alta'}
                title={leerRespuestas ? 'Lectura en voz alta activada' : 'Leer respuestas en voz alta'}
              >
                <Icono nombre={leerRespuestas ? 'volumen' : 'volumenOff'} tamano={18} />
              </button>
            )}
            <button type="button" className="chat-cerrar" onClick={cerrar} aria-label="Cerrar asistente">
              <Icono nombre="cerrar" tamano={18} />
            </button>
          </div>

          <div className="chat-mensajes" aria-live="polite">
            {mensajes.map((m) => (
              <div key={m.id} className={`chat-fila ${m.de === 'usuario' ? 'chat-fila-usuario' : ''}`}>
                <div className={`chat-burbuja ${m.de === 'usuario' ? 'chat-burbuja-usuario' : ''} ${m.error ? 'chat-burbuja-error' : ''}`}>
                  <p>{m.texto}</p>
                  {m.reintentar && (
                    <button type="button" className="chat-reintentar" onClick={() => preguntar(m.reintentar, { repetir: true })} disabled={pensando}>
                      <Icono nombre="flechaDer" tamano={14} /> Reintentar
                    </button>
                  )}
                  {m.avisoVoz && (
                    <Link to="/privacidad" className="chat-aviso-enlace" onClick={cerrar}>Ver la política de privacidad</Link>
                  )}
                  {m.fuente && (
                    <span className={`chat-fuente chat-fuente-${m.fuente}`}>
                      {m.fuente === 'ia' ? 'Interpretado con IA' : 'Respuesta del motor local'}
                    </span>
                  )}

                  {m.resultados?.length > 0 && (
                    <ul className="chat-resultados">
                      {m.resultados.map(({ objeto, porcentaje }) => {
                        const foto = objeto.fotos?.length ? fotoUrl(objeto.fotos[0]) : '';
                        return (
                          <li key={objeto.id}>
                            <Link to={`/objeto/${objeto.id}`} className="chat-resultado" onClick={cerrar}>
                              <span className="chat-resultado-media">
                                {foto ? <img src={foto} alt="" loading="lazy" /> : <Icono nombre="caja" tamano={18} />}
                              </span>
                              <span className="chat-resultado-info">
                                <span className="chat-resultado-nombre">{objeto.nombre}</span>
                                <span className="chat-resultado-linea">
                                  <Icono nombre="ubicacion" tamano={12} /> {objeto.ubicacion || 'Sin ubicación'}
                                </span>
                                <span className="chat-resultado-pie">
                                  <ChipTipo tipo={objeto.tipo} />
                                  <span className="chat-resultado-match">{porcentaje}% coincidencia</span>
                                </span>
                              </span>
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  )}

                  {m.misReportes?.length > 0 && (
                    <ul className="chat-resultados" aria-label="Mis reportes">
                      {m.misReportes.map((objeto) => (
                        <li key={objeto.id}>
                          <Link to={`/objeto/${objeto.id}`} className="chat-resultado" onClick={cerrar}>
                            <span className="chat-resultado-info">
                              <span className="chat-resultado-nombre">{objeto.nombre}</span>
                              <span className="chat-resultado-pie">
                                <ChipTipo tipo={objeto.tipo} />
                                <span className="chat-resultado-match">{ESTADO_REPORTE[objeto.estado] || objeto.estado}</span>
                              </span>
                            </span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}

                  {m.tarjetas?.length > 0 && (
                    <ul className="chat-tarjetas" aria-label="Tarjetas de FINDI">
                      {m.tarjetas.map((t, i) => (
                        <li key={`${t.tipo}-${i}`} className={`chat-tarjeta chat-tarjeta-${t.tipo.toLowerCase()}`}>
                          {t.tipo === 'OBJETO' && (
                            <Link to={t.enlace} className="chat-resultado" onClick={cerrar}>
                              <span className="chat-resultado-media"><Icono nombre="caja" tamano={18} /></span>
                              <span className="chat-resultado-info">
                                <span className="chat-resultado-nombre">{t.titulo}</span>
                                {t.datos?.ubicacion && (
                                  <span className="chat-resultado-linea"><Icono nombre="ubicacion" tamano={12} /> {t.datos.ubicacion}</span>
                                )}
                                <span className="chat-resultado-pie">
                                  {t.datos?.tipo && <ChipTipo tipo={t.datos.tipo} />}
                                  <span className="chat-resultado-match">{ESTADO_REPORTE[t.datos?.estado] || t.datos?.estado}</span>
                                </span>
                              </span>
                            </Link>
                          )}
                          {t.tipo === 'RECLAMOS' && (
                            <Link to={t.enlace} className="chat-tarjeta-cifras" onClick={cerrar}>
                              <strong><Icono nombre="bandeja" tamano={14} /> {t.titulo}</strong>
                              <span>{t.datos?.pendientes ?? 0} pendientes · {t.datos?.aceptados ?? 0} aceptados · {t.datos?.retirados ?? 0} retirados · {t.datos?.rechazados ?? 0} rechazados</span>
                            </Link>
                          )}
                          {t.tipo === 'QR_RETIRO' && (
                            <Link to={t.enlace} className="chat-tarjeta-cifras" onClick={cerrar}>
                              <strong><Icono nombre="check" tamano={14} /> {t.titulo}</strong>
                              <span>{t.datos?.vigentes === 1 ? 'Tienes 1 QR listo para recoger tu objeto.' : `Tienes ${t.datos?.vigentes} QR listos para recoger tus objetos.`}</span>
                            </Link>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}

                  {m.accionesFindi?.length > 0 && (
                    <div className="chat-acciones" role="group" aria-label="Acciones propuestas por FINDI">
                      {m.accionesFindi.map((a) => (
                        <button key={a.tipo + a.destino} type="button" className="chat-accion" onClick={() => setAccionPendiente(a)}>
                          {a.texto} <Icono nombre="flechaDer" tamano={14} />
                        </button>
                      ))}
                    </div>
                  )}

                  {m.acciones?.length > 0 && (
                    <div className="chat-acciones">
                      {m.acciones.map((a) => (
                        <Link key={a.to} to={a.to} className="chat-accion" onClick={(event) => irAAccion(event, a.to)}>
                          {a.texto} <Icono nombre="flechaDer" tamano={14} />
                        </Link>
                      ))}
                    </div>
                  )}

                  {m.accesos && (
                    <div className="chat-accesos" role="group" aria-label="Accesos rápidos">
                      {ACCESOS_FINDI.map((a) => (
                        <button key={a.id} type="button" className="chat-acceso" onClick={() => usarAcceso(a)} disabled={pensando}>
                          <span className="chat-acceso-icono" aria-hidden="true"><Icono nombre={a.icono} tamano={16} /></span>
                          {a.texto}
                        </button>
                      ))}
                    </div>
                  )}

                  {m.sugerencias?.length > 0 && (
                    <div className="chat-sugerencias">
                      {m.sugerencias.map((s) => (
                        <button key={s} type="button" className="chat-sugerencia" onClick={() => preguntar(s)} disabled={pensando}>
                          {s}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {pensando && (
              <div className="chat-fila">
                <div className="chat-burbuja chat-escribiendo" role="status" aria-label="El asistente está buscando">
                  <span /><span /><span />
                </div>
              </div>
            )}
            <div ref={finRef} />
          </div>

          <form className="chat-entrada" onSubmit={enviar}>
            <input
              ref={inputRef}
              className="chat-input"
              value={texto}
              onChange={(e) => {
                setTexto(e.target.value);
                if (!e.target.value) setDictado(false);
              }}
              placeholder="Ej. Perdí mi casaca azul en el aula 204"
              aria-label="Escribe tu mensaje al asistente"
              maxLength={300}
            />
            {voz.soportaDictado && (
              <button
                type="button"
                className={`chat-microfono${voz.escuchando ? ' escuchando' : ''}`}
                onClick={usarMicrofono}
                disabled={pensando}
                aria-pressed={voz.escuchando}
                aria-label={voz.escuchando ? 'Dejar de escuchar' : 'Hablar con el asistente'}
              >
                <Icono nombre="microfono" tamano={18} />
              </button>
            )}
            <button type="submit" className="chat-enviar" disabled={!texto.trim() || pensando} aria-label="Enviar mensaje">
              <Icono nombre="enviar" tamano={18} />
            </button>
          </form>
          {voz.escuchando && <p className="chat-voz-estado" role="status">Escuchando… habla ahora</p>}
          {!voz.escuchando && dictado && texto && (
            <p className="chat-voz-estado" role="status">Revisa el texto, corrígelo si hace falta y pulsa Enviar.</p>
          )}
          {voz.errorVoz && <p className="chat-voz-error" role="alert">{voz.errorVoz}</p>}
          {leerRespuestas && voz.avisoLectura && <p className="chat-voz-estado chat-voz-aviso">{voz.avisoLectura}</p>}
          {/* El plan gratuito de ElevenLabs pide dar crédito cuando se usa su voz */}
          {leerRespuestas && voz.proveedor === 'elevenlabs' && <p className="chat-credito">Voz de FINDI: ElevenLabs</p>}
        </section>
      )}

      <ConfirmarDialogo
        abierto={Boolean(accionPendiente)}
        titulo="¿Continuar?"
        mensaje={accionPendiente?.confirmacion || 'FINDI abrirá la pantalla con los datos que entendió. Podrás revisarlos antes de enviar nada.'}
        textoConfirmar="Sí, continuar"
        onConfirmar={confirmarAccion}
        onCancelar={() => setAccionPendiente(null)}
      />

      <button
        type="button"
        className={`chat-lanzador${abierto ? ' abierto' : ''}`}
        onClick={() => (abierto ? cerrar() : abrir())}
        aria-label={abierto ? 'Cerrar asistente' : 'Abrir asistente FINDI'}
        aria-expanded={abierto}
      >
        {abierto
          ? <Icono nombre="cerrar" tamano={24} />
          : <><img className="chat-lanzador-avatar" src={AVATAR_FINDI} alt="" /><span className={`chat-lanzador-estado estado-${estado}`} aria-hidden="true" /></>}
      </button>
    </>
  );
}
