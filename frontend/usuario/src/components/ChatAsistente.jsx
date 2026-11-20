import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Icono from './Icono';
import ChipTipo from './ChipTipo';
import ConfirmarDialogo from './ConfirmarDialogo';
import MiniaturaChat from './MiniaturaChat';
import CamaraEnVivo, { camaraEnVivoDisponible } from './CamaraEnVivo';
import { useAsistente } from '../context/AsistenteContext';
import { useAuth } from '../context/AuthContext';
import { AVATAR_FINDI, EXPRESIONES_FINDI, RUTAS_EXPRESIONES } from '../config/findi';
import { useNotificaciones } from '../hooks/useNotificaciones';
import { calcularEstadoFindi, desenlaceDe, etiquetaEstadoFindi, ESTADOS_FINDI } from '../utils/expresionFindi';
import useVoz from '../hooks/useVoz';
import { desplazarASeccion } from '../utils/navegacion';
import { buscarObjetos, buscarPorFoto, getMisObjetos, interpretarConIA, listarCategorias, mensajeError, statusDe } from '../services/api';
import { archivoADataUrl, reducirFoto } from '../utils/fotos';
import { ACCESOS_FINDI, SUGERENCIAS_INICIALES, adaptarInterpretacion, interpretar, responder, responderAcceso, saludoFindi } from '../utils/asistente';
import { accionesValidas, ajustarATarjetaObjeto, datosPrellenado, tarjetasValidas } from '../utils/tarjetasFindi';

// Primer mensaje: saludo personalizado (hora del día y nombre si hay sesión) con los accesos rápidos
const bienvenida = (nombre) => ({ id: 'bienvenida', de: 'bot', texto: saludoFindi(nombre), accesos: true });

// Hora corta de cada mensaje (HH:MM)
const horaActual = () => new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', hour12: false });
// La voz no lee emojis
const sinEmojis = (t = '') => String(t).replace(/[\p{Extended_Pictographic}️‍]/gu, '').replace(/\s+/g, ' ').trim();

// Avatar de FINDI con la expresión actual (imágenes 3D aprobadas, ver config/findi.js).
// "cuerpo": medio cuerpo en la bienvenida; "mini": cabeza y hombros junto a los mensajes.
// La clave hace que cada cambio de expresión entre con un fundido corto.
function AvatarFindi({ expresion = 'alegre', variante = 'mini' }) {
  const recurso = EXPRESIONES_FINDI[expresion] || EXPRESIONES_FINDI.alegre;
  return (
    <span className={`fc-avatar fc-avatar-${variante} expresion-${expresion}`} data-expresion={expresion}>
      <img key={recurso.src} src={recurso.src} alt="" decoding="async" />
    </span>
  );
}

// Duración de la presentación de una respuesta ("Escribiendo"): breve y proporcional al texto.
// Con movimiento reducido (o sin matchMedia) la respuesta aparece de inmediato.
const DURACION_ESCRITURA = (texto = '') => Math.min(650, 220 + String(texto).length * 6);
const permiteMovimiento = () => typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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
  // Resultado reciente de la última consulta (coincidencia, sin resultados o acción lista)
  const [desenlace, setDesenlace] = useState(null);
  const [buscando, setBuscando] = useState(false);
  // Respuesta que se está presentando en pantalla (expresión "Escribiendo")
  const [presentandoId, setPresentandoId] = useState(null);
  // Notificación importante: una nueva coincidencia sin leer (expresión "Alerta")
  const { notificaciones } = useNotificaciones();
  const coincidenciasNuevas = notificaciones.filter((n) => !n.leida && n.tipo === 'COINCIDENCIA_NUEVA').length;
  const coincidenciasVistas = useRef(0);
  const [alertaNotificacion, setAlertaNotificacion] = useState(false);
  const ultimaConsulta = useRef(null);
  const [enLinea, setEnLinea] = useState(() => typeof navigator === 'undefined' || navigator.onLine !== false);
  const nombreUsuario = auth?.user?.nombre || '';
  const [mensajes, setMensajes] = useState(() => [bienvenida(nombreUsuario)]);
  // El saludo se dice en voz alta una sola vez (al abrir con la lectura activada o al activarla)
  const saludoLeido = useRef(false);
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
  // Buscar con una foto: cámara en la página (o galería), aviso de privacidad la primera vez
  const [camaraAbierta, setCamaraAbierta] = useState(false);
  const [avisoFoto, setAvisoFoto] = useState(false);
  const avisoFotoAceptado = useRef(false);
  const entradaFotoChat = useRef(null);
  const [camaraVivo] = useState(camaraEnVivoDisponible);
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
    setMensajes((prev) => [...prev, { hora: horaActual(), ...mensaje, id }]);
    return id;
  };

  // Respuesta de FINDI: se agrega y, si el movimiento está permitido, se presenta con "Escribiendo"
  const responderBot = (mensaje) => {
    const id = agregar({ de: 'bot', ...mensaje });
    if (permiteMovimiento() && !mensaje.error) {
      setPresentandoId(id);
      setTimeout(() => setPresentandoId((actual) => (actual === id ? null : actual)), DURACION_ESCRITURA(mensaje.texto));
    }
    return id;
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
    setDesenlace(null);
    // Al reintentar no se repite el mensaje del usuario
    if (!repetir) agregar({ de: 'usuario', texto: limpio });
    setTexto('');
    setDictado(false);
    // 1) Groq interpreta el mensaje ("Pensando"). 2) Solo si es una búsqueda se consultan los
    // objetos ("Buscando"). Las expresiones salen de estas peticiones reales, no de palabras sueltas.
    setBuscando(false);
    setPensando(true);
    try {
      if (!categoriasRef.current) categoriasRef.current = lista(await listarCategorias());
      // El backend interpreta el mensaje; ante errores se usa el motor local de la web.
      const ia = await interpretarConIA(limpio).catch(() => null);
      let consulta = adaptarInterpretacion(ia, categoriasRef.current);
      const local = interpretar(limpio, categoriasRef.current);
      // Estas acciones de la web aún no forman parte del vocabulario compartido de H45.
      if (['mis_reportes', 'como_funciona'].includes(local.intencion)
          && consulta?.intencion !== local.intencion) consulta = null;
      // Fase 5: "mis reportes" se responde con tus publicaciones reales (requiere sesión)
      let elegida = consulta || local;
      // Seguimiento: "en el laboratorio 1500" completa la búsqueda anterior con el lugar
      if (['buscar', 'vacio'].includes(elegida.intencion) && !elegida.palabras?.length && !elegida.categoria
          && elegida.ubicacion && ultimaConsulta.current) {
        elegida = { ...ultimaConsulta.current, ubicacion: elegida.ubicacion, intencion: 'buscar' };
      }
      const intencion = elegida.intencion;
      // H47: tarjetas y acciones del catálogo cerrado (se descarta lo que no se conoce)
      const tarjetasIa = tarjetasValidas(ia?.tarjetas);
      let objetosPublicos = [];
      if (intencion === 'buscar' || tarjetasIa.some((t) => t.tipo === 'OBJETO')) {
        if (intencion === 'buscar') {
          setBuscando(true);
          if (!repetir) agregar({ de: 'bot', texto: '¡Voy a revisar los reportes! 💙', aviso: true });
        }
        objetosPublicos = lista(await buscarObjetos({ q: '' }));
      }
      const tarjetas = tarjetasIa.map((t) => (t.tipo === 'OBJETO' && t.datos?.id
        ? { ...t, objeto: objetosPublicos.find((o) => o.id === t.datos.id) }
        : t));
      const respuesta = intencion === 'mis_reportes'
        ? await responderMisReportes()
        : ajustarATarjetaObjeto(responder(limpio, { objetos: objetosPublicos, categorias: categoriasRef.current, consulta: elegida }), tarjetas);
      if (intencion === 'buscar' && respuesta.consulta) ultimaConsulta.current = respuesta.consulta;
      // H45: se indica con transparencia si respondió la IA o el motor local de respaldo
      const fuente = consulta && ia?.fuente !== 'local' ? 'ia' : 'local';
      const accionesFindi = accionesValidas(ia?.acciones);
      responderBot({ fuente, ...respuesta, tarjetas, accionesFindi });
      setDesenlace(desenlaceDe({ ...respuesta, tarjetas }, { buscaba: intencion === 'buscar' }));
      if (leerRespuestas && abiertoRef.current) voz.leer(sinEmojis(respuesta.texto));
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

  // ---------- Buscar con una foto ----------
  const abrirSelectorFoto = () => {
    if (camaraVivo) setCamaraAbierta(true);
    else entradaFotoChat.current?.click();
  };

  const pedirFoto = () => {
    if (pensando) return;
    // La foto sale del navegador hacia el proveedor de IA: se avisa antes de la primera vez
    if (!avisoFotoAceptado.current) { setAvisoFoto(true); return; }
    abrirSelectorFoto();
  };

  const buscarConFoto = async (archivo) => {
    if (!archivo || pensando) return;
    setCamaraAbierta(false);
    if (!archivo.type?.startsWith('image/')) {
      agregar({ de: 'bot', error: true, texto: 'Elige una foto (JPEG o PNG) del objeto.' });
      return;
    }
    const vista = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(archivo) : '';
    agregar({ de: 'usuario', texto: 'Busca objetos como este', foto: vista });
    setDesenlace(null);
    setBuscando(true);
    setPensando(true);
    const publicarConFoto = { texto: 'Publicar con esta foto', to: '/publicar', state: { fotoAdjunta: archivo } };
    try {
      if (!categoriasRef.current) categoriasRef.current = lista(await listarCategorias());
      // Más liviana para la IA: máx. 1024 px en JPEG
      const reducida = await reducirFoto(archivo, 1024);
      const [datos, r] = await Promise.all([buscarObjetos({ q: '' }), buscarPorFoto(await archivoADataUrl(reducida))]);
      const consulta = adaptarInterpretacion({
        intencion: 'buscar', tipoBuscado: '', categoria: r.categoria, ubicacion: '',
        palabras: r.palabras || [], colores: r.colores || [],
      }, categoriasRef.current);
      if (!consulta || consulta.intencion !== 'buscar') {
        agregar({
          de: 'bot',
          texto: 'No reconocí un objeto en la foto. Prueba con otra más cercana y con buena luz, o descríbelo con palabras.',
          acciones: [publicarConFoto],
        });
        return;
      }
      const respuesta = responder('', { objetos: lista(datos), categorias: categoriasRef.current, consulta });
      const veo = r.descripcion ? `En tu foto veo: ${r.descripcion}. ` : '';
      responderBot({
        fuente: 'ia', ...respuesta,
        texto: `${veo}${respuesta.texto}`,
        acciones: [...(respuesta.acciones || []).filter((a) => a.to !== '/publicar'), publicarConFoto],
      });
      setDesenlace(desenlaceDe(respuesta, { buscaba: true }));
    } catch (e) {
      const limite = statusDe(e) === 429;
      agregar({
        de: 'bot',
        error: true,
        texto: limite
          ? 'Hiciste varias búsquedas con foto seguidas. Espera un minuto o describe el objeto con palabras.'
          : 'No pude analizar la foto ahora. Describe el objeto con palabras o publícalo con esta foto.',
        acciones: [publicarConFoto],
      });
    } finally {
      setPensando(false);
    }
  };

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

  // Si la persona inicia o cierra sesión antes de conversar, el saludo usa su nombre
  useEffect(() => {
    setMensajes((prev) => (prev.length === 1 && prev[0].id === 'bienvenida' ? [bienvenida(nombreUsuario)] : prev));
  }, [nombreUsuario]);

  // Dice el saludo en voz alta una vez, solo si la conversación aún no empezó
  const leerSaludo = () => {
    if (saludoLeido.current || mensajes.length !== 1) return;
    saludoLeido.current = true;
    voz.leer(sinEmojis(mensajes[0].texto));
  };

  // FINDI saluda la primera vez que se abre el chat (y lo dice si la lectura en voz alta está activa)
  useEffect(() => {
    if (!abierto || yaSaludo.current) return undefined;
    yaSaludo.current = true;
    setSaludando(true);
    if (leerRespuestas) leerSaludo();
    const t = setTimeout(() => setSaludando(false), 2800);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto]);

  // El resultado de la última consulta se muestra unos segundos y vuelve a "En línea"
  useEffect(() => {
    if (!desenlace) return undefined;
    const t = setTimeout(() => setDesenlace(null), 4500);
    return () => clearTimeout(t);
  }, [desenlace]);

  // Alerta: aparece una nueva coincidencia sin leer (notificaciones reales de la cuenta)
  useEffect(() => {
    if (!abierto) return undefined;
    const hayNuevas = coincidenciasNuevas > coincidenciasVistas.current;
    coincidenciasVistas.current = coincidenciasNuevas;
    if (!hayNuevas) return undefined;
    setAlertaNotificacion(true);
    const t = setTimeout(() => setAlertaNotificacion(false), 4500);
    return () => clearTimeout(t);
  }, [abierto, coincidenciasNuevas]);

  // Precarga de las expresiones al abrir el chat por primera vez: los cambios no parpadean
  const precargadas = useRef(false);
  useEffect(() => {
    if (!abierto || precargadas.current || typeof window.Image !== 'function') return;
    precargadas.current = true;
    RUTAS_EXPRESIONES.forEach((src) => { const img = new window.Image(); img.src = src; });
  }, [abierto]);

  // Estado de conexión real del navegador
  useEffect(() => {
    const on = () => setEnLinea(true);
    const off = () => setEnLinea(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);

  // Accesos rápidos: respuestas fijas, sin consultar la API
  const usarAcceso = (acceso) => {
    if (pensando) return;
    setDesenlace(null);
    agregar({ de: 'usuario', texto: acceso.texto });
    const respuesta = responderAcceso(acceso.id, { conSesion: Boolean(auth?.token) });
    responderBot(respuesta);
    if (leerRespuestas) voz.leer(sinEmojis(respuesta.texto));
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

  const ultimoBot = [...mensajes].reverse().find((m) => m.de === 'bot' && !m.aviso);
  const estado = calcularEstadoFindi({
    escuchando: voz.escuchando, pensando, buscando, error: Boolean(ultimoBot?.error),
    preparando: voz.preparando, hablando: voz.hablando, escribiendo: presentandoId != null,
    notificacion: alertaNotificacion, desenlace, saludando,
  });
  const expresion = ESTADOS_FINDI[estado].expresion;
  const etiquetaEstado = etiquetaEstadoFindi(estado, desenlace);
  const conectado = enLinea && !ultimoBot?.error;
  const soloBienvenida = mensajes.length === 1;
  // La expresión en vivo se muestra en el último mensaje de FINDI (o en el indicador de proceso)
  const idUltimoBot = [...mensajes].reverse().find((m) => m.de === 'bot')?.id;

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
    else {
      // Fase 6: el mismo toque que activa la voz habilita el audio en celulares
      voz.prepararAudio();
      // Al activar la voz antes de conversar, FINDI se presenta hablando
      leerSaludo();
    }
    setLeerRespuestas((v) => !v);
  };

  const enviar = (e) => {
    e.preventDefault();
    preguntar(texto);
  };

  // Contenido extra de un mensaje de FINDI (resultados, tarjetas y acciones). Solo con datos reales.
  const extrasMensaje = (m) => (
    <>
      {m.resultados?.length > 0 && (
        <ul className="fc-resultados">
          {m.resultados.map(({ objeto, porcentaje }) => (
            <li key={objeto.id}>
              <Link to={`/objeto/${objeto.id}`} className="fc-objeto" onClick={cerrar}>
                <MiniaturaChat objeto={objeto} />
                <span className="fc-objeto-info">
                  <span className="fc-objeto-nombre">{objeto.nombre}</span>
                  <span className="fc-objeto-linea"><Icono nombre="ubicacion" tamano={12} /> {objeto.ubicacion || 'Sin ubicación'}</span>
                  <span className="fc-objeto-pie">
                    <ChipTipo tipo={objeto.tipo} />
                    <span className="fc-objeto-match">{porcentaje}% coincidencia</span>
                  </span>
                </span>
                <Icono nombre="flechaDer" tamano={16} className="fc-objeto-flecha" />
              </Link>
            </li>
          ))}
        </ul>
      )}

      {m.misReportes?.length > 0 && (
        <ul className="fc-resultados" aria-label="Mis reportes">
          {m.misReportes.map((objeto) => (
            <li key={objeto.id}>
              <Link to={`/objeto/${objeto.id}`} className="fc-objeto" onClick={cerrar}>
                <MiniaturaChat objeto={objeto} />
                <span className="fc-objeto-info">
                  <span className="fc-objeto-nombre">{objeto.nombre}</span>
                  <span className="fc-objeto-pie">
                    <ChipTipo tipo={objeto.tipo} />
                    <span className="fc-objeto-match">{ESTADO_REPORTE[objeto.estado] || objeto.estado}</span>
                  </span>
                </span>
                <Icono nombre="flechaDer" tamano={16} className="fc-objeto-flecha" />
              </Link>
            </li>
          ))}
        </ul>
      )}

      {m.tarjetas?.length > 0 && (
        <ul className="fc-resultados" aria-label="Tarjetas de FINDI">
          {m.tarjetas.map((t, i) => (
            <li key={`${t.tipo}-${i}`} className={`fc-tarjeta-${t.tipo.toLowerCase()}`}>
              {t.tipo === 'OBJETO' && (
                <Link to={t.enlace} className="fc-objeto" onClick={cerrar}>
                  <MiniaturaChat objeto={t.objeto || { nombre: t.titulo, categoria: t.datos?.categoria }} />
                  <span className="fc-objeto-info">
                    <span className="fc-objeto-nombre">{t.titulo}</span>
                    {t.datos?.ubicacion && <span className="fc-objeto-linea"><Icono nombre="ubicacion" tamano={12} /> {t.datos.ubicacion}</span>}
                    <span className="fc-objeto-pie">
                      {t.datos?.tipo && <ChipTipo tipo={t.datos.tipo} />}
                      <span className="fc-objeto-match">{ESTADO_REPORTE[t.datos?.estado] || t.datos?.estado}</span>
                    </span>
                  </span>
                  <Icono nombre="flechaDer" tamano={16} className="fc-objeto-flecha" />
                </Link>
              )}
              {t.tipo === 'RECLAMOS' && (
                <Link to={t.enlace} className="fc-cifras" onClick={cerrar}>
                  <strong><Icono nombre="bandeja" tamano={14} /> {t.titulo}</strong>
                  <span>{t.datos?.pendientes ?? 0} pendientes · {t.datos?.aceptados ?? 0} aceptados · {t.datos?.retirados ?? 0} retirados · {t.datos?.rechazados ?? 0} rechazados</span>
                </Link>
              )}
              {t.tipo === 'QR_RETIRO' && (
                <Link to={t.enlace} className="fc-cifras" onClick={cerrar}>
                  <strong><Icono nombre="check" tamano={14} /> {t.titulo}</strong>
                  <span>{t.datos?.vigentes === 1 ? 'Tienes 1 QR listo para recoger tu objeto.' : `Tienes ${t.datos?.vigentes} QR listos para recoger tus objetos.`}</span>
                </Link>
              )}
            </li>
          ))}
        </ul>
      )}

      {m.accionesFindi?.length > 0 && (
        <div className="fc-acciones" role="group" aria-label="Acciones propuestas por FINDI">
          {m.accionesFindi.map((a, i) => (
            <button key={a.tipo + a.destino} type="button" className={`fc-accion${i === 0 ? ' principal' : ''}`} onClick={() => setAccionPendiente(a)}>
              {a.texto} <Icono nombre="flechaDer" tamano={14} />
            </button>
          ))}
        </div>
      )}

      {m.acciones?.length > 0 && (
        <div className="fc-acciones">
          {m.acciones.map((a) => (
            <Link key={a.to} to={a.to} state={a.state} className="fc-accion" onClick={(event) => irAAccion(event, a.to)}>
              {a.texto} <Icono nombre="flechaDer" tamano={14} />
            </Link>
          ))}
        </div>
      )}

      {m.sugerencias?.length > 0 && (
        <div className="fc-sugerencias">
          {m.sugerencias.map((s) => (
            <button key={s} type="button" className="fc-sugerencia" onClick={() => preguntar(s)} disabled={pensando}>{s}</button>
          ))}
        </div>
      )}
    </>
  );

  const ondas = <i className="fc-ondas" aria-hidden="true"><b /><b /><b /></i>;

  return (
    <>
      {abierto && (
        <section className={`fc fc-estado-${estado}`} role="dialog" aria-label="FINDI, asistente de FoundIA">
          <header className="fc-cabecera">
            <span className="fc-avatar-cabecera" aria-hidden="true"><img src={AVATAR_FINDI} alt="" /></span>
            <div className="fc-cabecera-texto">
              <div className="fc-nombre">
                <strong>FINDI</strong>
                <span className={`fc-conexion${conectado ? '' : ' desconectado'}`}>
                  <i aria-hidden="true" />{conectado ? 'En línea' : enLinea ? 'Sin conexión' : 'Sin internet'}
                </span>
              </div>
              <span className="fc-subtitulo" data-testid="estado-findi">
                {estado === 'hablando' && ondas}
                {etiquetaEstado || 'Tu asistente inteligente de FoundIA ✨'}
              </span>
            </div>
            {voz.soportaLectura && (
              <button
                type="button"
                className={`fc-boton-cabecera${leerRespuestas ? ' activo' : ''}`}
                onClick={alternarLectura}
                aria-pressed={leerRespuestas}
                aria-label={leerRespuestas ? 'Dejar de leer las respuestas en voz alta' : 'Leer las respuestas en voz alta'}
                title={leerRespuestas ? 'Lectura en voz alta activada' : 'Leer respuestas en voz alta'}
              >
                <Icono nombre={leerRespuestas ? 'volumen' : 'volumenOff'} tamano={18} />
              </button>
            )}
            <button type="button" className="fc-boton-cabecera" onClick={cerrar} aria-label="Cerrar asistente" title="Cerrar (la conversación se conserva)">
              <Icono nombre="cerrar" tamano={18} />
            </button>
          </header>

          <div className="fc-mensajes" aria-live="polite">
            {mensajes.map((m) => {
              if (m.id === 'bienvenida' && soloBienvenida) {
                return (
                  <div key={m.id} className="fc-bienvenida">
                    <AvatarFindi expresion={expresion === 'alegre' ? 'saludando' : expresion} variante="cuerpo" />
                    <p className="fc-bienvenida-texto">{m.texto}</p>
                    <div className="fc-accesos" role="group" aria-label="Accesos rápidos">
                      {ACCESOS_FINDI.map((a) => (
                        <button key={a.id} type="button" className={`fc-acceso fc-acceso-${a.id}`} onClick={() => usarAcceso(a)} disabled={pensando}>
                          <span className="fc-acceso-icono" aria-hidden="true"><Icono nombre={a.icono} tamano={18} /></span>
                          {a.texto}
                        </button>
                      ))}
                    </div>
                    <div className="fc-sugerencias" role="group" aria-label="Sugerencias">
                      {SUGERENCIAS_INICIALES.slice(0, 3).map((s) => (
                        <button key={s} type="button" className="fc-sugerencia" onClick={() => preguntar(s)} disabled={pensando}>{s}</button>
                      ))}
                    </div>
                  </div>
                );
              }
              const esUsuario = m.de === 'usuario';
              return (
                <div key={m.id} className={`fc-fila ${esUsuario ? 'usuario' : 'bot'}`}>
                  {!esUsuario && (m.id === idUltimoBot && !pensando
                    ? <span aria-hidden="true"><AvatarFindi expresion={expresion} variante="mini" /></span>
                    : <span className="fc-avatar-mini" aria-hidden="true"><img src={AVATAR_FINDI} alt="" /></span>)}
                  <div className="fc-columna">
                    <div className={`fc-burbuja${m.error ? ' error' : ''}${m.id === presentandoId ? ' presentando' : ''}`}>
                      {m.foto && <img className="fc-foto-enviada" src={m.foto} alt="Foto enviada para buscar" />}
                      <p>{m.texto}</p>
                      {m.reintentar && (
                        <button type="button" className="fc-reintentar" onClick={() => preguntar(m.reintentar, { repetir: true })} disabled={pensando}>
                          <Icono nombre="flechaDer" tamano={14} /> Reintentar
                        </button>
                      )}
                      {m.avisoVoz && <Link to="/privacidad" className="fc-enlace" onClick={cerrar}>Ver la política de privacidad</Link>}
                    </div>
                    {!esUsuario && extrasMensaje(m)}
                    <span className="fc-meta">
                      {m.hora && <time>{m.hora}</time>}
                      {m.fuente && <span className={`fc-fuente fc-fuente-${m.fuente}`}>{m.fuente === 'ia' ? 'Interpretado con IA' : 'Respuesta del motor local'}</span>}
                    </span>
                  </div>
                </div>
              );
            })}

            {pensando && (
              <div className="fc-fila bot">
                <span aria-hidden="true"><AvatarFindi expresion={expresion} variante="mini" /></span>
                <div className="fc-procesando" role="status" aria-label={buscando ? 'FINDI está buscando coincidencias' : 'FINDI está pensando'}>
                  <span className="fc-giro" aria-hidden="true" />
                  <span>
                    <strong>{buscando ? 'Buscando coincidencias…' : 'Pensando…'}</strong>
                    <small>Un momento, por favor.</small>
                  </span>
                </div>
              </div>
            )}
            <div ref={finRef} />
          </div>

          <div className="fc-pie">
            {voz.escuchando && (
              <div className="fc-barra escuchando" role="status">
                <span className="fc-mic-pulso" aria-hidden="true"><Icono nombre="microfono" tamano={14} /></span>
                <span>Micrófono activo · Escuchando… habla ahora</span>
                <button type="button" onClick={voz.detener} aria-label="Cancelar dictado">Cancelar</button>
              </div>
            )}
            {!voz.escuchando && voz.hablando && (
              <div className="fc-barra hablando">
                {ondas}
                <span>FINDI está hablando</span>
                <button type="button" onClick={voz.callar} aria-label="Detener la voz de FINDI">Detener</button>
              </div>
            )}
            {!voz.escuchando && dictado && texto && (
              <p className="fc-nota" role="status">Revisa el texto, corrígelo si hace falta y pulsa Enviar.</p>
            )}
            {voz.errorVoz && <p className="fc-nota error" role="alert">{voz.errorVoz}</p>}
            {leerRespuestas && voz.avisoLectura && <p className="fc-nota">{voz.avisoLectura}</p>}

            <form className="fc-entrada" onSubmit={enviar}>
              <span className="fc-campo">
                <Icono nombre="ia" tamano={16} className="fc-campo-icono" />
                <input
                  ref={inputRef}
                  className="fc-input"
                  value={texto}
                  onChange={(e) => {
                    setTexto(e.target.value);
                    if (!e.target.value) setDictado(false);
                  }}
                  onFocus={() => setTimeout(() => finRef.current?.scrollIntoView?.({ block: 'end' }), 250)}
                  placeholder="Escribe un mensaje a FINDI…"
                  aria-label="Escribe tu mensaje al asistente"
                  maxLength={300}
                  enterKeyHint="send"
                />
              </span>
              <button type="button" className="fc-boton-redondo" onClick={pedirFoto} disabled={pensando} aria-label="Buscar con una foto" title="Buscar con una foto">
                <Icono nombre="camara" tamano={18} />
              </button>
              <input
                ref={entradaFotoChat}
                type="file"
                accept="image/jpeg,image/png"
                className="foto-entrada"
                tabIndex={-1}
                aria-hidden="true"
                onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; buscarConFoto(f); }}
              />
              {voz.soportaDictado && (
                <button
                  type="button"
                  className={`fc-boton-redondo${voz.escuchando ? ' escuchando' : ''}`}
                  onClick={usarMicrofono}
                  disabled={pensando}
                  aria-pressed={voz.escuchando}
                  aria-label={voz.escuchando ? 'Dejar de escuchar' : 'Hablar con el asistente'}
                >
                  <Icono nombre="microfono" tamano={18} />
                </button>
              )}
              <button type="submit" className="fc-enviar" disabled={!texto.trim() || pensando} aria-label="Enviar mensaje">
                <Icono nombre="enviar" tamano={18} />
              </button>
            </form>
            {/* El plan gratuito de ElevenLabs pide dar crédito cuando se usa su voz */}
            {leerRespuestas && voz.proveedor === 'elevenlabs' && <p className="fc-credito">Voz de FINDI: ElevenLabs</p>}
          </div>
        </section>
      )}

      <ConfirmarDialogo
        abierto={avisoFoto}
        titulo="Buscar con una foto"
        mensaje="FINDI enviará la foto a su proveedor de IA solo para reconocer el objeto. No se guarda ni se publica. Evita fotos con documentos, rostros u otros datos personales."
        textoConfirmar="Entendido, continuar"
        onConfirmar={() => { avisoFotoAceptado.current = true; setAvisoFoto(false); abrirSelectorFoto(); }}
        onCancelar={() => setAvisoFoto(false)}
      />
      <CamaraEnVivo
        abierto={camaraAbierta}
        titulo="Foto del objeto"
        onCapturar={buscarConFoto}
        onCerrar={() => setCamaraAbierta(false)}
        onElegirArchivo={() => { setCamaraAbierta(false); entradaFotoChat.current?.click(); }}
      />
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
