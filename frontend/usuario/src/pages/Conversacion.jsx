import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Cargando from '../components/Cargando';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import AvatarConversacion from '../components/AvatarConversacion';
import MensajeError from '../components/MensajeError';
import { useAuth } from '../context/AuthContext';
import {
  bloquearConversacion, desbloquearConversacion, enviarFotoChat, enviarMensaje, fotoUrl, getConversacion,
  getMensajes, marcarConversacionLeida, mensajeError, reportarMensaje, statusDe,
} from '../services/api';
import {
  INTERVALO_MS, MAX_TEXTO, horaChat, unirMensajes, validarFotoChat, validarTexto,
} from '../utils/chat';

// H55: conversación con envío y consulta periódica (cursor despuesDe).
// H56: bloquear y reportar. H61: solo lectura al recuperarse el objeto. H62: fotos.
export default function Conversacion() {
  const { id } = useParams();
  const { user } = useAuth();
  const [conversacion, setConversacion] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [estado, setEstado] = useState({ soloLectura: false, bloqueada: false });
  const [error, setError] = useState('');
  const [noExiste, setNoExiste] = useState(false);

  const [texto, setTexto] = useState('');
  const [foto, setFoto] = useState(null);
  const [vistaFoto, setVistaFoto] = useState('');
  const [errorEnvio, setErrorEnvio] = useState('');
  const [enviando, setEnviando] = useState(false);

  const [confirmarBloqueo, setConfirmarBloqueo] = useState(false);
  const [procesando, setProcesando] = useState(false);
  const [aviso, setAviso] = useState('');
  const [reportando, setReportando] = useState(null); // mensaje que se reporta
  const [motivo, setMotivo] = useState('');
  const [errorReporte, setErrorReporte] = useState('');

  const cursor = useRef(0);
  const lista = useRef(null);
  const entradaFoto = useRef(null);
  const miId = user?.id;

  const aplicar = useCallback((respuesta) => {
    if (!respuesta) return;
    setMensajes((actuales) => unirMensajes(actuales, respuesta.mensajes));
    if (respuesta.ultimoId > cursor.current) cursor.current = respuesta.ultimoId;
    setEstado({ soloLectura: Boolean(respuesta.soloLectura), bloqueada: Boolean(respuesta.bloqueada) });
    return respuesta.mensajes?.length || 0;
  }, []);

  // Carga inicial: la conversación y todos sus mensajes (de 50 en 50)
  const cargar = useCallback(async () => {
    setError('');
    setNoExiste(false);
    cursor.current = 0;
    setMensajes([]);
    try {
      setConversacion(await getConversacion(id));
      for (let vuelta = 0; vuelta < 20; vuelta += 1) {
        const respuesta = await getMensajes(id, cursor.current);
        if (aplicar(respuesta) < 50) break;
      }
      marcarConversacionLeida(id).catch(() => {});
    } catch (e) {
      if (statusDe(e) === 404) setNoExiste(true);
      else setError(mensajeError(e, 'No se pudo abrir la conversación'));
    }
  }, [id, aplicar]);

  useEffect(() => { cargar(); }, [cargar]);

  // Consulta periódica: solo con la pestaña visible; si llegan mensajes del otro, quedan leídos
  useEffect(() => {
    if (!conversacion) return undefined;
    let vivo = true;
    const consultar = async () => {
      if (document.hidden) return;
      try {
        const respuesta = await getMensajes(id, cursor.current);
        if (!vivo) return;
        const nuevos = respuesta?.mensajes || [];
        aplicar(respuesta);
        if (nuevos.some((m) => m.autorId !== miId)) marcarConversacionLeida(id).catch(() => {});
      } catch {
        // Un fallo puntual de red no corta la conversación: se reintenta en la próxima vuelta
      }
    };
    const temporizador = setInterval(consultar, INTERVALO_MS);
    return () => { vivo = false; clearInterval(temporizador); };
  }, [conversacion, id, aplicar, miId]);

  // Baja al último mensaje cuando llegan nuevos
  useEffect(() => {
    const nodo = lista.current;
    if (nodo) nodo.scrollTop = nodo.scrollHeight;
  }, [mensajes.length]);

  useEffect(() => () => { if (vistaFoto) URL.revokeObjectURL(vistaFoto); }, [vistaFoto]);

  const elegirFoto = (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = '';
    setErrorEnvio('');
    if (!archivo) return;
    const problema = validarFotoChat(archivo);
    if (problema) { setErrorEnvio(problema); return; }
    setFoto(archivo);
    setVistaFoto(URL.createObjectURL(archivo));
  };

  const quitarFoto = () => { setFoto(null); setVistaFoto(''); };

  const enviar = async (e) => {
    e.preventDefault();
    if (enviando) return;
    if (!foto) {
      const problema = validarTexto(texto);
      if (problema) { setErrorEnvio(problema); return; }
    } else if (texto.trim().length > MAX_TEXTO) {
      setErrorEnvio(`El mensaje no puede superar los ${MAX_TEXTO} caracteres.`);
      return;
    }
    setEnviando(true);
    setErrorEnvio('');
    try {
      const enviado = foto ? await enviarFotoChat(id, foto, texto) : await enviarMensaje(id, texto.trim());
      setMensajes((actuales) => unirMensajes(actuales, [enviado]));
      if (enviado?.id > cursor.current) cursor.current = enviado.id;
      setTexto('');
      quitarFoto();
    } catch (err) {
      const codigo = statusDe(err);
      if (codigo === 429) setErrorEnvio('Estás enviando muchos mensajes seguidos. Espera un momento e inténtalo de nuevo.');
      else setErrorEnvio(mensajeError(err, 'No se pudo enviar el mensaje'));
      // 409: la conversación terminó o hay un bloqueo; se actualiza el estado sin perder lo escrito
      if (codigo === 409) getMensajes(id, cursor.current).then(aplicar).catch(() => {});
    } finally {
      setEnviando(false);
    }
  };

  const bloquear = async () => {
    setProcesando(true);
    try {
      const c = await bloquearConversacion(id);
      setEstado((s) => ({ ...s, bloqueada: Boolean(c?.bloqueada ?? true) }));
      setAviso('Bloqueaste a esta persona: ninguno de los dos puede escribir hasta que la desbloquees.');
    } catch (err) {
      setAviso(mensajeError(err, 'No se pudo bloquear'));
    } finally {
      setProcesando(false);
      setConfirmarBloqueo(false);
    }
  };

  const desbloquear = async () => {
    setProcesando(true);
    try {
      const c = await desbloquearConversacion(id);
      const sigue = Boolean(c?.bloqueada);
      setEstado((s) => ({ ...s, bloqueada: sigue }));
      // Desbloquear quita solo el bloqueo propio: si la otra persona también bloqueó, sigue cerrada
      setAviso(sigue ? 'La otra persona también bloqueó esta conversación, así que todavía no se puede escribir.' : 'Desbloqueaste la conversación.');
    } catch (err) {
      setAviso(mensajeError(err, 'No se pudo desbloquear'));
    } finally {
      setProcesando(false);
    }
  };

  const enviarReporte = async (e) => {
    e.preventDefault();
    const limpio = motivo.trim();
    if (limpio.length < 3 || limpio.length > 255) { setErrorReporte('Explica el motivo en 3 a 255 caracteres.'); return; }
    setProcesando(true);
    setErrorReporte('');
    try {
      await reportarMensaje(id, reportando.id, limpio);
      setAviso('Gracias: el mensaje quedó reportado y lo revisará un administrador.');
      setReportando(null);
      setMotivo('');
    } catch (err) {
      setErrorReporte(mensajeError(err, 'No se pudo enviar el reporte'));
    } finally {
      setProcesando(false);
    }
  };

  if (noExiste) {
    return (
      <div className="pagina pagina-estrecha">
        <h1 className="pagina-titulo">Conversación no disponible</h1>
        <p className="nota-suave">No existe o no participas en ella.</p>
        <Link className="btn btn-secundario" to="/mensajes">Volver a mis mensajes</Link>
      </div>
    );
  }
  if (error) {
    return (
      <div className="pagina pagina-estrecha">
        <h1 className="pagina-titulo">Mensajes</h1>
        <MensajeError mensaje={error} onReintentar={cargar} />
      </div>
    );
  }
  if (!conversacion) return <Cargando texto="Abriendo la conversación..." />;

  const conBiblioteca = conversacion.tipo === 'CON_BIBLIOTECA';
  const puedeBloquear = !conBiblioteca && conversacion.otro?.id != null;
  const puedeEscribir = !estado.soloLectura && !estado.bloqueada;

  return (
    <div className="pagina pagina-estrecha conv-pagina">
      <div className="conv-cabecera">
        <Link to="/mensajes" className="btn-enlace conv-volver"><Icono nombre="flechaIzq" tamano={16} /> Mensajes</Link>
        <AvatarConversacion conversacion={{ ...conversacion, bloqueada: estado.bloqueada }} className="conv-cabecera-avatar" />
        <div className="conv-cabecera-datos">
          <h1 className="conv-titulo">{conversacion.otro?.nombre || 'Conversación'}</h1>
          <Link to={`/objeto/${conversacion.objetoId}`} className="conv-objeto">{conversacion.objetoNombre}</Link>
        </div>
        {puedeBloquear && !estado.soloLectura && (
          estado.bloqueada ? (
            <button type="button" className="btn btn-secundario btn-mini" onClick={desbloquear} disabled={procesando}>Desbloquear</button>
          ) : (
            <button type="button" className="btn btn-secundario btn-mini" onClick={() => setConfirmarBloqueo(true)} disabled={procesando}>Bloquear</button>
          )
        )}
      </div>

      {aviso && <p className="nota-suave conv-aviso" role="status">{aviso}</p>}

      <ol ref={lista} className="conv-mensajes-lista" role="log" aria-live="polite" aria-label={`Mensajes con ${conversacion.otro?.nombre || 'la otra persona'}`}>
        {mensajes.length === 0 && (
          <li className="conv-vacio">
            {conBiblioteca
              ? 'Escribe a la biblioteca: por ejemplo, para acordar cuándo retirar tu objeto o darle un detalle más.'
              : 'Saluda y coordina la entrega en un lugar seguro del campus. No compartas tu correo ni tu teléfono.'}
          </li>
        )}
        {mensajes.map((m) => {
          const propio = m.autorId === miId;
          return (
            <li key={m.id} className={`conv-burbuja ${propio ? 'conv-propio' : 'conv-ajeno'}`}>
              <span className="conv-autor">{propio ? 'Tú' : m.autorNombre}</span>
              {m.imagenUrl && (
                <a href={fotoUrl(m.imagenUrl)} target="_blank" rel="noopener noreferrer" className="conv-foto">
                  <img src={fotoUrl(m.imagenUrl)} alt={`Foto enviada por ${propio ? 'ti' : m.autorNombre}`} loading="lazy" />
                </a>
              )}
              {m.texto && <p className="conv-texto">{m.texto}</p>}
              <span className="conv-meta">
                <time dateTime={m.enviadoEn}>{horaChat(m.enviadoEn)}</time>
                {!propio && (
                  <button type="button" className="btn-enlace conv-reportar" onClick={() => { setReportando(m); setMotivo(''); setErrorReporte(''); }}
                    aria-label={`Reportar el mensaje de ${m.autorNombre}`}>Reportar</button>
                )}
              </span>
            </li>
          );
        })}
      </ol>

      {reportando && (
        <form className="tarjeta conv-reporte" onSubmit={enviarReporte} aria-label="Reportar mensaje">
          <label className="campo">
            <span className="campo-label">¿Por qué reportas este mensaje? *</span>
            <textarea className="input textarea" rows={2} maxLength={255} value={motivo} autoFocus
              onChange={(e) => { setMotivo(e.target.value); setErrorReporte(''); }}
              aria-invalid={Boolean(errorReporte)} aria-describedby={errorReporte ? 'reporte-error' : undefined}
              placeholder="Ej. Me pide mi número de teléfono" />
          </label>
          {errorReporte && <p id="reporte-error" className="campo-error" role="alert">{errorReporte}</p>}
          <div className="conv-reporte-acciones">
            <button type="button" className="btn btn-secundario" onClick={() => setReportando(null)} disabled={procesando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={procesando}>Enviar reporte</button>
          </div>
        </form>
      )}

      {estado.soloLectura ? (
        <p className="conv-cerrada" role="status">
          <Icono nombre="check" tamano={16} /> La conversación terminó: el objeto ya se resolvió. Puedes seguir leyendo los mensajes.
        </p>
      ) : estado.bloqueada ? (
        <p className="conv-cerrada" role="status">
          No se puede escribir porque hay un bloqueo en esta conversación.
        </p>
      ) : null}

      {puedeEscribir && (
        <form className="conv-redactar" onSubmit={enviar} noValidate>
          {vistaFoto && (
            <div className="conv-vista-foto">
              <img src={vistaFoto} alt="Vista previa de la foto que vas a enviar" />
              <button type="button" className="btn-enlace" onClick={quitarFoto}>Quitar foto</button>
            </div>
          )}
          <label className="conv-oculto" htmlFor="conv-texto">Mensaje</label>
          <textarea id="conv-texto" className={`input textarea conv-campo${errorEnvio ? ' input-error' : ''}`} rows={2}
            maxLength={MAX_TEXTO} value={texto} disabled={enviando}
            onChange={(e) => { setTexto(e.target.value); setErrorEnvio(''); }}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); e.currentTarget.form.requestSubmit(); } }}
            aria-invalid={Boolean(errorEnvio)} aria-describedby={`conv-contador${errorEnvio ? ' conv-error' : ''}`}
            placeholder={foto ? 'Agrega un texto a la foto (opcional)' : 'Escribe un mensaje'} />
          <div className="conv-redactar-fila">
            <input ref={entradaFoto} id="conv-foto" type="file" accept="image/jpeg,image/png" className="conv-foto-input" onChange={elegirFoto} disabled={enviando} />
            <button type="button" className="btn btn-secundario btn-mini" onClick={() => entradaFoto.current?.click()} disabled={enviando}>
              <Icono nombre="imagen" tamano={16} /> Foto
            </button>
            <span id="conv-contador" className="nota-suave conv-contador-texto">{texto.length}/{MAX_TEXTO}</span>
            <button type="submit" className="btn btn-primario" disabled={enviando}>{enviando ? 'Enviando...' : 'Enviar'}</button>
          </div>
          {errorEnvio && <p id="conv-error" className="campo-error" role="alert">{errorEnvio}</p>}
        </form>
      )}

      <ConfirmarDialogo
        abierto={confirmarBloqueo}
        titulo="¿Bloquear a esta persona?"
        mensaje="Ninguno de los dos podrá escribir en esta conversación hasta que la desbloquees. Si te pidió datos personales o te molestó, también puedes reportar su mensaje."
        textoConfirmar="Bloquear"
        peligro
        procesando={procesando}
        onConfirmar={bloquear}
        onCancelar={() => setConfirmarBloqueo(false)}
      />
    </div>
  );
}
