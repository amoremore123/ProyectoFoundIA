import { useCallback, useEffect, useRef, useState } from 'react';
import FotoChatPrivada from '../components/FotoChatPrivada';
import Icono from '../components/Icono';
import { useAuth } from '../context/AuthContext';
import {
  getBandejaChat, getMensajesChat, marcarChatLeido, mensajeError, responderChat, statusDe,
} from '../services/api';

// H63: bandeja de la biblioteca. Quien reclamó un objeto en custodia escribe desde la web o la app;
// el personal (BIBLIOTECA o ADMIN) responde aquí. Solo se ven conversaciones de reclamos.
const MAX = 1000;
const INTERVALO_MS = 5000;

function hora(valor) {
  if (!valor) return '';
  const d = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(valor) ? valor : `${valor}Z`);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
}

function unir(actuales, nuevos) {
  if (!nuevos?.length) return actuales;
  const vistos = new Set(actuales.map((m) => m.id));
  return [...actuales, ...nuevos.filter((m) => !vistos.has(m.id))].sort((a, b) => a.id - b.id);
}

export default function MensajesBiblioteca() {
  const { user } = useAuth();
  const [bandeja, setBandeja] = useState(null);
  const [errorBandeja, setErrorBandeja] = useState('');
  const [abierta, setAbierta] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [estado, setEstado] = useState({ soloLectura: false, bloqueada: false });
  const [errorChat, setErrorChat] = useState('');
  const [texto, setTexto] = useState('');
  const [errorEnvio, setErrorEnvio] = useState('');
  const [enviando, setEnviando] = useState(false);
  const cursor = useRef(0);
  const lista = useRef(null);

  const cargarBandeja = useCallback(async () => {
    try {
      setBandeja(await getBandejaChat());
      setErrorBandeja('');
    } catch (e) {
      setErrorBandeja(mensajeError(e, 'No se pudo cargar la bandeja de mensajes'));
      setBandeja((b) => b || []);
    }
  }, []);

  useEffect(() => {
    cargarBandeja();
    const t = setInterval(cargarBandeja, INTERVALO_MS * 3);
    return () => clearInterval(t);
  }, [cargarBandeja]);

  const aplicar = (r) => {
    if (!r) return 0;
    setMensajes((a) => unir(a, r.mensajes));
    if (r.ultimoId > cursor.current) cursor.current = r.ultimoId;
    setEstado({ soloLectura: Boolean(r.soloLectura), bloqueada: Boolean(r.bloqueada) });
    return r.mensajes?.length || 0;
  };

  const abrir = async (c) => {
    setAbierta(c);
    setMensajes([]);
    setTexto('');
    setErrorEnvio('');
    setErrorChat('');
    cursor.current = 0;
    try {
      for (let vuelta = 0; vuelta < 20; vuelta += 1) {
        if (aplicar(await getMensajesChat(c.id, cursor.current)) < 50) break;
      }
      await marcarChatLeido(c.id).catch(() => {});
      setBandeja((b) => (b || []).map((x) => (x.id === c.id ? { ...x, sinLeer: 0 } : x)));
    } catch (e) {
      setErrorChat(statusDe(e) === 404 ? 'La conversación ya no está disponible.' : mensajeError(e, 'No se pudo abrir la conversación'));
    }
  };

  // Consulta periódica de la conversación abierta
  useEffect(() => {
    if (!abierta) return undefined;
    let vivo = true;
    const t = setInterval(async () => {
      if (document.hidden) return;
      try {
        const r = await getMensajesChat(abierta.id, cursor.current);
        if (!vivo) return;
        if (aplicar(r) > 0) marcarChatLeido(abierta.id).catch(() => {});
      } catch {
        // se reintenta en la próxima vuelta
      }
    }, INTERVALO_MS);
    return () => { vivo = false; clearInterval(t); };
  }, [abierta]);

  useEffect(() => {
    if (lista.current) lista.current.scrollTop = lista.current.scrollHeight;
  }, [mensajes.length]);

  const responder = async (e) => {
    e.preventDefault();
    const limpio = texto.trim();
    if (!limpio) { setErrorEnvio('Escribe una respuesta.'); return; }
    if (limpio.length > MAX) { setErrorEnvio(`Máximo ${MAX} caracteres.`); return; }
    setEnviando(true);
    setErrorEnvio('');
    try {
      const m = await responderChat(abierta.id, limpio);
      setMensajes((a) => unir(a, [m]));
      if (m?.id > cursor.current) cursor.current = m.id;
      setTexto('');
    } catch (err) {
      setErrorEnvio(statusDe(err) === 429
        ? 'Se enviaron muchos mensajes seguidos. Espera un momento.'
        : mensajeError(err, 'No se pudo enviar la respuesta'));
    } finally {
      setEnviando(false);
    }
  };

  const pendientes = (bandeja || []).reduce((n, c) => n + (c.sinLeer || 0), 0);

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Mensajes de reclamos</h1>
          <p className="pagina-subtitulo">
            Conversaciones de quienes reclamaron un objeto en custodia: pide un detalle para verificar o acuerda el retiro.
            {pendientes > 0 && <strong> · {pendientes} sin leer</strong>}
          </p>
        </div>
      </div>

      {errorBandeja && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {errorBandeja}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargarBandeja}>Reintentar</button>
        </div>
      )}

      <div className="bandeja-chat">
        <section className="tarjeta bandeja-chat-lista" aria-labelledby="titulo-bandeja">
          <h2 id="titulo-bandeja" className="seccion-titulo">Conversaciones</h2>
          {bandeja === null ? (
            <p className="panel-vacio" role="status">Cargando...</p>
          ) : bandeja.length === 0 ? (
            <p className="panel-vacio">Todavía nadie escribió a la biblioteca.</p>
          ) : (
            <ul>
              {bandeja.map((c) => (
                <li key={c.id}>
                  <button type="button" className={`bandeja-chat-item${abierta?.id === c.id ? ' activo' : ''}`}
                    aria-pressed={abierta?.id === c.id} onClick={() => abrir(c)}>
                    <span className="bandeja-chat-fila">
                      <strong>{c.otro?.nombre || 'Reclamante'}</strong>
                      {c.sinLeer > 0 && <span className="chip-estado activo">{c.sinLeer} sin leer</span>}
                      {c.soloLectura && <span className="chip-estado">Terminada</span>}
                    </span>
                    <span className="celda-detalle">{c.objetoNombre} · #{c.objetoId}</span>
                    {c.ultimoMensaje && (
                      <span className="celda-detalle bandeja-chat-previa">{c.ultimoMensaje.texto || 'Foto'} · {hora(c.ultimoMensaje.enviadoEn)}</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="tarjeta bandeja-chat-conversacion" aria-label={abierta ? `Conversación con ${abierta.otro?.nombre}` : 'Conversación'}>
          {!abierta ? (
            <p className="panel-vacio">Elige una conversación para leerla y responder.</p>
          ) : errorChat ? (
            <div className="banner-error" role="alert"><span>{errorChat}</span></div>
          ) : (
            <>
              <div className="bandeja-chat-cabecera">
                <h2>{abierta.otro?.nombre || 'Reclamante'}</h2>
                <span className="celda-detalle">Reclamo del objeto «{abierta.objetoNombre}» (#{abierta.objetoId})</span>
              </div>
              <ol ref={lista} className="bandeja-chat-mensajes" role="log" aria-live="polite" aria-label="Mensajes de la conversación">
                {mensajes.length === 0 && <li className="panel-vacio">Sin mensajes todavía.</li>}
                {mensajes.map((m) => {
                  const personal = m.autorId !== abierta.otro?.id;
                  return (
                    <li key={m.id} className={`bandeja-chat-burbuja ${personal ? 'personal' : 'reclamante'}`}>
                      <span className="bandeja-chat-autor">{m.autorId === user?.id ? 'Tú' : m.autorNombre}</span>
                      {m.imagenUrl && <FotoChatPrivada url={m.imagenUrl} alt={`Foto enviada por ${m.autorNombre}`} />}
                      {m.texto && <p>{m.texto}</p>}
                      <time dateTime={m.enviadoEn}>{hora(m.enviadoEn)}</time>
                    </li>
                  );
                })}
              </ol>
              {estado.soloLectura ? (
                <p className="panel-aviso" role="status">La conversación terminó (el reclamo se cerró o el objeto se entregó). Solo se puede leer.</p>
              ) : (
                <form className="bandeja-chat-responder" onSubmit={responder} noValidate>
                  <label className="campo">
                    <span className="campo-label">Respuesta</span>
                    <textarea className={`input textarea${errorEnvio ? ' input-error' : ''}`} rows={2} maxLength={MAX}
                      value={texto} disabled={enviando} onChange={(e) => { setTexto(e.target.value); setErrorEnvio(''); }}
                      aria-invalid={Boolean(errorEnvio)} aria-describedby={errorEnvio ? 'respuesta-error' : undefined}
                      placeholder="Ej. Para confirmar, ¿de qué color es el estuche?" />
                  </label>
                  {errorEnvio && <p id="respuesta-error" className="campo-error" role="alert">{errorEnvio}</p>}
                  <div className="acciones-fila">
                    <span className="nota-tabla">La persona recibe un aviso «Mensaje nuevo» sin el texto.</span>
                    <button type="submit" className="btn btn-primario" disabled={enviando}>{enviando ? 'Enviando...' : 'Responder'}</button>
                  </div>
                </form>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
