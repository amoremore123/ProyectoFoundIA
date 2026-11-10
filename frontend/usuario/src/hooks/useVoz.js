import { useCallback, useEffect, useRef, useState } from 'react';
import { elegirAlternativa } from '../utils/voz';
import { obtenerVozFindi } from '../services/api';

// H46 - Voz del asistente en la web (Web Speech API, español de Perú).
// El reconocimiento lo procesa el servicio del navegador (Google en Chrome, Microsoft en Edge);
// por eso se avisa antes del primer uso. Si el navegador no lo soporta, el botón no se muestra.
const IDIOMA = 'es-PE';
const IDIOMA_RESPALDO = 'es-419'; // español latinoamericano, si el navegador no tiene es-PE

const MENSAJES_ERROR = {
  'not-allowed': 'No diste permiso para usar el micrófono. Puedes activarlo en el candado de la barra de direcciones o escribir tu mensaje.',
  'service-not-allowed': 'Tu navegador no permite el reconocimiento de voz en este sitio. Escribe tu mensaje.',
  'no-speech': 'No te escuché. Toca el micrófono e inténtalo otra vez.',
  'audio-capture': 'No se encontró un micrófono. Revisa que esté conectado o escribe tu mensaje.',
  network: 'El reconocimiento de voz necesita conexión a internet. Escribe tu mensaje.',
};

// Algunos navegadores basados en Chromium (p. ej. navegadores integrados en otras apps) dicen
// soportar el reconocimiento de voz pero no tienen acceso al servicio: fallan con "network"
// aunque haya internet. En ese caso se explica y el micrófono se oculta durante la visita.
const NO_DISPONIBLE = 'El dictado por voz no está disponible en este navegador. Ábrelo en Google Chrome o Microsoft Edge, o escribe tu mensaje.';

const hayConexion = () => typeof navigator === 'undefined' || navigator.onLine !== false;

// Errores que no se arreglan reintentando: el micrófono deja de ofrecerse en esta visita
export function dictadoBloqueado(codigo, enLinea = hayConexion()) {
  return codigo === 'service-not-allowed' || (codigo === 'network' && enLinea);
}

// Fase 6: voz natural de FINDI (ElevenLabs vía backend) con la voz del navegador como respaldo
const AVISO_RESPALDO = 'La voz natural de FINDI no está disponible ahora; se usa la voz del navegador.';
// WAV vacío de 44 bytes: reproducirlo con el toque del usuario "desbloquea" el audio en iPhone y Android
const SILENCIO = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=';

const reproducir = (audio) => {
  try {
    return Promise.resolve(audio.play());
  } catch (e) {
    return Promise.reject(e);
  }
};

const obtenerReconocimiento = () =>
  (typeof window !== 'undefined' && (window.SpeechRecognition || window.webkitSpeechRecognition)) || null;

export function mensajeErrorVoz(codigo, enLinea = hayConexion()) {
  if (codigo === 'aborted') return '';
  if (codigo === 'network' && enLinea) return NO_DISPONIBLE;
  return MENSAJES_ERROR[codigo] || 'No se pudo usar el micrófono. Escribe tu mensaje.';
}

export default function useVoz({ alReconocer, alParcial } = {}) {
  const Reconocimiento = obtenerReconocimiento();
  const [disponible, setDisponible] = useState(true);
  const soportaDictado = Boolean(Reconocimiento) && disponible;
  const soportaNavegador = typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function';
  const soportaAudio = typeof window !== 'undefined' && typeof window.Audio === 'function';
  const soportaLectura = soportaNavegador || soportaAudio;
  const [escuchando, setEscuchando] = useState(false);
  // Fase 4: FINDI muestra "Respondiendo…" mientras lee una respuesta en voz alta
  const [hablando, setHablando] = useState(false);
  const [errorVoz, setErrorVoz] = useState('');
  const reconocimiento = useRef(null);
  const callback = useRef(alReconocer);
  callback.current = alReconocer;
  const parcial = useRef(alParcial);
  parcial.current = alParcial;
  const idioma = useRef(IDIOMA);
  // Fase 6: quién está hablando ('elevenlabs' o 'navegador') y aviso si se pasó al respaldo
  const [proveedor, setProveedor] = useState(null);
  const [avisoLectura, setAvisoLectura] = useState('');
  const audio = useRef(null);
  const urlAudio = useRef(null);
  const peticionVoz = useRef(null);
  const vozNatural = useRef(true);

  const detener = useCallback(() => {
    const rec = reconocimiento.current;
    reconocimiento.current = null;
    if (rec) {
      // Al cerrar no se entrega un dictado tardío ni se reintenta otro idioma.
      rec.onresult = null;
      rec.onerror = null;
      rec.onend = null;
      rec.abort?.();
    }
    setEscuchando(false);
  }, []);

  const escuchar = useCallback(function iniciar() {
    if (!Reconocimiento) return;
    if (reconocimiento.current) {
      reconocimiento.current.stop();
      return;
    }
    const rec = new Reconocimiento();
    rec.lang = idioma.current;
    // Se muestra el texto mientras se habla y se piden varias alternativas para elegir la mejor
    rec.interimResults = true;
    rec.maxAlternatives = 5;
    rec.continuous = false;
    rec.onresult = (evento) => {
      const resultados = Array.from(evento.results || []);
      const finales = resultados.filter((r) => r.isFinal !== false);
      const enCurso = resultados.filter((r) => r.isFinal === false);
      if (enCurso.length) {
        const vista = [...finales, ...enCurso].map((r) => r[0]?.transcript || '').join(' ').trim();
        if (vista) parcial.current?.(vista);
        return;
      }
      const texto = finales.map((r) => elegirAlternativa(Array.from(r))).join(' ').trim();
      if (texto) callback.current?.(texto);
    };
    rec.onerror = (evento) => {
      // Si el navegador no tiene español de Perú, se reintenta con español latinoamericano
      if (evento?.error === 'language-not-supported' && idioma.current !== IDIOMA_RESPALDO) {
        idioma.current = IDIOMA_RESPALDO;
        rec.onend = () => {
          reconocimiento.current = null;
          iniciar();
        };
        return;
      }
      setErrorVoz(mensajeErrorVoz(evento?.error));
      if (dictadoBloqueado(evento?.error)) setDisponible(false);
    };
    rec.onend = () => {
      reconocimiento.current = null;
      setEscuchando(false);
    };
    setErrorVoz('');
    reconocimiento.current = rec;
    setEscuchando(true);
    try {
      rec.start();
    } catch {
      reconocimiento.current = null;
      setEscuchando(false);
      setErrorVoz(mensajeErrorVoz());
    }
  }, [Reconocimiento]);

  const leerConNavegador = useCallback((texto) => {
    if (!soportaNavegador) { setHablando(false); return; }
    window.speechSynthesis.cancel();
    const frase = new window.SpeechSynthesisUtterance(texto);
    frase.lang = IDIOMA;
    frase.onstart = () => setHablando(true);
    frase.onend = () => setHablando(false);
    frase.onerror = () => setHablando(false);
    setProveedor('navegador');
    window.speechSynthesis.speak(frase);
  }, [soportaNavegador]);

  const soltarAudio = useCallback(() => {
    peticionVoz.current?.abort();
    peticionVoz.current = null;
    if (audio.current) {
      audio.current.onplaying = null;
      audio.current.onended = null;
      audio.current.onerror = null;
      audio.current.pause?.();
    }
    if (urlAudio.current) {
      URL.revokeObjectURL?.(urlAudio.current);
      urlAudio.current = null;
    }
  }, []);

  const respaldo = useCallback((texto) => {
    setAvisoLectura(AVISO_RESPALDO);
    leerConNavegador(texto);
  }, [leerConNavegador]);

  const leer = useCallback((texto) => {
    if (!soportaLectura || !texto) return;
    soltarAudio();
    window.speechSynthesis?.cancel();
    if (!soportaAudio || !vozNatural.current) { leerConNavegador(texto); return; }

    const control = new AbortController();
    peticionVoz.current = control;
    obtenerVozFindi(texto, { signal: control.signal })
      .then((mp3) => {
        if (control.signal.aborted) return;
        if (!audio.current) audio.current = new window.Audio();
        const reproductor = audio.current;
        urlAudio.current = URL.createObjectURL(mp3);
        reproductor.onplaying = () => { setHablando(true); setProveedor('elevenlabs'); };
        reproductor.onended = () => setHablando(false);
        reproductor.onerror = () => { if (!control.signal.aborted) { setHablando(false); respaldo(texto); } };
        reproductor.src = urlAudio.current;
        return reproducir(reproductor).catch(() => {
          if (!control.signal.aborted) respaldo(texto);
        });
      })
      .catch((e) => {
        if (control.signal.aborted) return;
        // Sin voz configurada (503) o con un backend que aún no tiene /voz (404) no se reintenta en esta visita
        if ([404, 503].includes(e?.response?.status)) vozNatural.current = false;
        respaldo(texto);
      });
  }, [soportaLectura, soportaAudio, soportaNavegador, soltarAudio, leerConNavegador, respaldo]);

  // Se llama con el toque que activa la voz: así el navegador permite reproducir después
  const prepararAudio = useCallback(() => {
    if (!soportaAudio) return;
    if (!audio.current) audio.current = new window.Audio();
    audio.current.src = SILENCIO;
    reproducir(audio.current).catch(() => {});
  }, [soportaAudio]);

  const callar = useCallback(() => {
    soltarAudio();
    window.speechSynthesis?.cancel();
    setHablando(false);
  }, [soltarAudio, soportaNavegador]);

  // Al cerrar el asistente o salir de la página se libera el micrófono y se deja de hablar
  useEffect(() => () => {
    reconocimiento.current?.abort?.();
    soltarAudio();
    window.speechSynthesis?.cancel();
  }, [soportaNavegador, soltarAudio]);

  return {
    soportaDictado, soportaLectura, escuchando, hablando, errorVoz, proveedor, avisoLectura,
    escuchar, detener, leer, callar, prepararAudio,
  };
}
