import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { CLAVE_TEMA, EVENTO_CONSENTIMIENTO, puedeGuardarPreferencias } from '../utils/almacenamiento';

// Sin provider (por ejemplo en tests) el tema queda en modo claro y el
// alternar es un no-op, para no romper el render aislado de páginas.
const TemaContext = createContext({ tema: 'claro', alternarTema: () => {} });

function temaInicial() {
  try {
    // El tema guardado es una preferencia: solo se usa si la persona permitió recordarla
    const guardado = puedeGuardarPreferencias() ? localStorage.getItem(CLAVE_TEMA) : null;
    if (guardado === 'claro' || guardado === 'oscuro') return guardado;
  } catch {
    // localStorage puede no estar disponible (modo privado); seguimos con el sistema
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro';
}

function guardarTema(tema) {
  if (!puedeGuardarPreferencias()) return;
  try {
    localStorage.setItem(CLAVE_TEMA, tema);
  } catch {
    // Sin persistencia la app sigue funcionando en la pestaña actual
  }
}

export function TemaProvider({ children }) {
  const [tema, setTema] = useState(temaInicial);

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    guardarTema(tema);
  }, [tema]);

  // Si la persona acepta recordar preferencias después, se guarda el tema que ya eligió
  useEffect(() => {
    const alCambiar = () => guardarTema(document.documentElement.dataset.tema);
    window.addEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
    return () => window.removeEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
  }, []);

  const alternarTema = useCallback(() => {
    setTema((actual) => (actual === 'oscuro' ? 'claro' : 'oscuro'));
  }, []);

  return <TemaContext.Provider value={{ tema, alternarTema }}>{children}</TemaContext.Provider>;
}

export function useTema() {
  return useContext(TemaContext);
}
