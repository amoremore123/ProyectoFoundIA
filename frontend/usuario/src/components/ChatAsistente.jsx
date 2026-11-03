import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';
import ChipTipo from './ChipTipo';
import { useAsistente } from '../context/AsistenteContext';
import useVoz from '../hooks/useVoz';
import { buscarObjetos, fotoUrl, interpretarConIA, listarCategorias, mensajeError } from '../services/api';
import { adaptarInterpretacion, responder, SUGERENCIAS_INICIALES } from '../utils/asistente';

const BIENVENIDA = {
  id: 'bienvenida',
  de: 'bot',
  texto: '¡Hola! Soy el asistente de FoundIA. Cuéntame qué perdiste o encontraste en el campus y lo busco por ti entre todas las publicaciones.',
  sugerencias: SUGERENCIAS_INICIALES,
};

const lista = (data) => (Array.isArray(data) ? data : data?.results || []);

const AVISO_VOZ = 'Para entender tu voz, tu navegador envía el audio a su servicio de reconocimiento (Google en Chrome, Microsoft en Edge). FoundIA no guarda tu voz. Si prefieres, puedes escribir.';

// Chat flotante del asistente de búsqueda (disponible en todas las páginas del MainLayout)
export default function ChatAsistente() {
  const { abierto, abrir, cerrar, pendiente, consumirPendiente } = useAsistente();
  const [mensajes, setMensajes] = useState([BIENVENIDA]);
  const [texto, setTexto] = useState('');
  const [pensando, setPensando] = useState(false);
  const categoriasRef = useRef(null);
  const finRef = useRef(null);
  const inputRef = useRef(null);
  const contador = useRef(0);
  const [leerRespuestas, setLeerRespuestas] = useState(false);
  const avisoVozMostrado = useRef(false);
  const preguntarRef = useRef(null);
  const voz = useVoz({ alReconocer: (dicho) => preguntarRef.current?.(dicho) });

  const agregar = (mensaje) => {
    contador.current += 1;
    setMensajes((prev) => [...prev, { id: contador.current, ...mensaje }]);
  };

  const preguntar = useCallback(async (pregunta) => {
    const limpio = pregunta.trim();
    if (!limpio || pensando) return;
    agregar({ de: 'usuario', texto: limpio });
    setTexto('');
    setPensando(true);
    try {
      if (!categoriasRef.current) categoriasRef.current = lista(await listarCategorias());
      // Claude interpreta el mensaje desde el backend; si falla o no está configurado, se usa el motor local
      const [datos, ia] = await Promise.all([
        buscarObjetos({ q: '' }),
        interpretarConIA(limpio).catch(() => null),
      ]);
      const consulta = adaptarInterpretacion(ia, categoriasRef.current);
      const respuesta = responder(limpio, { objetos: lista(datos), categorias: categoriasRef.current, consulta });
      // H45: se indica con transparencia si respondió la IA o el motor local de respaldo
      agregar({ de: 'bot', fuente: consulta ? 'ia' : 'local', ...respuesta });
      if (leerRespuestas) voz.leer(respuesta.texto);
    } catch (e) {
      agregar({
        de: 'bot',
        error: true,
        texto: `No pude consultar las publicaciones (${mensajeError(e, 'error de conexión')}). Revisa que la API esté encendida e inténtalo de nuevo.`,
      });
    } finally {
      setPensando(false);
    }
  }, [pensando, leerRespuestas, voz.leer]);
  preguntarRef.current = preguntar;

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
    setLeerRespuestas((v) => !v);
  };

  const enviar = (e) => {
    e.preventDefault();
    preguntar(texto);
  };

  return (
    <>
      {abierto && (
        <section className="chat-panel" role="dialog" aria-label="Asistente de búsqueda FoundIA">
          <header className="chat-cabecera">
            <span className="chat-avatar"><Icono nombre="ia" tamano={18} /></span>
            <div className="chat-cabecera-texto">
              <strong>Asistente FoundIA</strong>
              <span><i className="chat-estado" /> Búsqueda inteligente · Tecsup</span>
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
          </header>

          <div className="chat-mensajes" aria-live="polite">
            {mensajes.map((m) => (
              <div key={m.id} className={`chat-fila ${m.de === 'usuario' ? 'chat-fila-usuario' : ''}`}>
                <div className={`chat-burbuja ${m.de === 'usuario' ? 'chat-burbuja-usuario' : ''} ${m.error ? 'chat-burbuja-error' : ''}`}>
                  <p>{m.texto}</p>
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

                  {m.acciones?.length > 0 && (
                    <div className="chat-acciones">
                      {m.acciones.map((a) => (
                        <Link key={a.to} to={a.to} className="chat-accion" onClick={cerrar}>
                          {a.texto} <Icono nombre="flechaDer" tamano={14} />
                        </Link>
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
              onChange={(e) => setTexto(e.target.value)}
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
          {voz.errorVoz && <p className="chat-voz-error" role="alert">{voz.errorVoz}</p>}
        </section>
      )}

      <button
        type="button"
        className={`chat-lanzador${abierto ? ' abierto' : ''}`}
        onClick={() => (abierto ? cerrar() : abrir())}
        aria-label={abierto ? 'Cerrar asistente' : 'Abrir asistente de búsqueda con IA'}
        aria-expanded={abierto}
      >
        <Icono nombre={abierto ? 'cerrar' : 'chat'} tamano={24} />
        {!abierto && <span className="chat-lanzador-texto">Pregúntale a la IA</span>}
      </button>
    </>
  );
}
