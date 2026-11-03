import { useCallback, useEffect, useRef, useState } from 'react';
import { elegirAlternativa } from '../utils/voz';

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

const obtenerReconocimiento = () =>
  (typeof window !== 'undefined' && (window.SpeechRecognition || window.webkitSpeechRecognition)) || null;

export function mensajeErrorVoz(codigo) {
  if (codigo === 'aborted') return '';
  return MENSAJES_ERROR[codigo] || 'No se pudo usar el micrófono. Escribe tu mensaje.';
}

export default function useVoz({ alReconocer, alParcial } = {}) {
  const Reconocimiento = obtenerReconocimiento();
  const soportaDictado = Boolean(Reconocimiento);
  const soportaLectura = typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function';
  const [escuchando, setEscuchando] = useState(false);
  const [errorVoz, setErrorVoz] = useState('');
  const reconocimiento = useRef(null);
  const callback = useRef(alReconocer);
  callback.current = alReconocer;
  const parcial = useRef(alParcial);
  parcial.current = alParcial;
  const idioma = useRef(IDIOMA);

  const detener = useCallback(() => {
    reconocimiento.current?.stop();
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
