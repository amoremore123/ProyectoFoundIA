import { useCallback, useEffect, useRef, useState } from 'react';

// H46 - Voz del asistente en la web (Web Speech API, español de Perú).
// El reconocimiento lo procesa el servicio del navegador (Google en Chrome, Microsoft en Edge);
// por eso se avisa antes del primer uso. Si el navegador no lo soporta, el botón no se muestra.
const IDIOMA = 'es-PE';

const MENSAJES_ERROR = {
  'not-allowed': 'No diste permiso para usar el micrófono. Puedes activarlo en el candado de la barra de direcciones o escribir tu mensaje.',
  'service-not-allowed': 'Tu navegador no permite el reconocimiento de voz en este sitio. Escribe tu mensaje.',
  'no-speech': 'No te escuché. Toca el micrófono e inténtalo otra vez.',
  'audio-capture': 'No se encontró un micrófono. Revisa que esté conectado o escribe tu mensaje.',
  network: 'El reconocimiento de voz necesita conexión a internet. Escribe tu mensaje.',
};

const obtenerReconocimiento = () =>
  (typeof window !== 'undefined' && (window.SpeechRecognition || window.webkitSpeechRecognition)) || null;

export function mensajeErrorVoz(codigo) {
  if (codigo === 'aborted') return '';
  return MENSAJES_ERROR[codigo] || 'No se pudo usar el micrófono. Escribe tu mensaje.';
}

export default function useVoz({ alReconocer } = {}) {
  const Reconocimiento = obtenerReconocimiento();
  const soportaDictado = Boolean(Reconocimiento);
  const soportaLectura = typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function';
  const [escuchando, setEscuchando] = useState(false);
  const [errorVoz, setErrorVoz] = useState('');
  const reconocimiento = useRef(null);
  const callback = useRef(alReconocer);
  callback.current = alReconocer;

  const detener = useCallback(() => {
    reconocimiento.current?.stop();
  }, []);

  const escuchar = useCallback(() => {
    if (!Reconocimiento) return;
    if (reconocimiento.current) {
      reconocimiento.current.stop();
      return;
    }
    const rec = new Reconocimiento();
    rec.lang = IDIOMA;
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    rec.continuous = false;
    rec.onresult = (evento) => {
      const texto = Array.from(evento.results || [])
        .map((r) => r[0]?.transcript || '')
        .join(' ')
        .trim();
      if (texto) callback.current?.(texto);
    };
    rec.onerror = (evento) => setErrorVoz(mensajeErrorVoz(evento?.error));
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

  const leer = useCallback((texto) => {
    if (!soportaLectura || !texto) return;
    window.speechSynthesis.cancel();
    const frase = new window.SpeechSynthesisUtterance(texto);
    frase.lang = IDIOMA;
    window.speechSynthesis.speak(frase);
  }, [soportaLectura]);

  const callar = useCallback(() => {
    if (soportaLectura) window.speechSynthesis.cancel();
  }, [soportaLectura]);

  // Al cerrar el asistente o salir de la página se libera el micrófono y se deja de hablar
  useEffect(() => () => {
    reconocimiento.current?.abort?.();
    if (soportaLectura) window.speechSynthesis.cancel();
  }, [soportaLectura]);

  return { soportaDictado, soportaLectura, escuchando, errorVoz, escuchar, detener, leer, callar };
}
