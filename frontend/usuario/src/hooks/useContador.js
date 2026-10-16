import { useEffect, useRef, useState } from 'react';

const movimientoReducido = () =>
  typeof window !== 'undefined'
  && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// Cuenta desde el valor anterior hasta el nuevo con una suavización cúbica.
// Con "reducir movimiento" activado muestra el valor final de inmediato.
export default function useContador(objetivo, duracion = 900) {
  const destino = Number(objetivo) || 0;
  const [valor, setValor] = useState(() => (movimientoReducido() ? destino : 0));
  const anterior = useRef(0);

  useEffect(() => {
    const desde = anterior.current;
    if (desde === destino) return undefined;
    if (movimientoReducido()) {
      anterior.current = destino;
      setValor(destino);
      return undefined;
    }

    let cuadro;
    let terminado = false;
    const inicio = performance.now();

    const fijarFinal = () => {
      if (terminado) return;
      terminado = true;
      anterior.current = destino;
      setValor(destino);
    };

    const avanzar = (ahora) => {
      if (terminado) return;
      const t = Math.min((ahora - inicio) / duracion, 1);
      const suave = 1 - Math.pow(1 - t, 3);
      setValor(Math.round(desde + (destino - desde) * suave));
      if (t < 1) cuadro = requestAnimationFrame(avanzar);
      else fijarFinal();
    };

    cuadro = requestAnimationFrame(avanzar);
    // Respaldo: garantiza el valor final aunque requestAnimationFrame esté
    // pausado (p. ej. pestaña en segundo plano), donde setTimeout sí se ejecuta.
    const respaldo = setTimeout(fijarFinal, duracion + 100);

    return () => {
      cancelAnimationFrame(cuadro);
      clearTimeout(respaldo);
    };
  }, [destino, duracion]);

  return valor;
}
