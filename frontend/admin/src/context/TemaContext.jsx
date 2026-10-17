import { createContext, useCallback, useContext, useEffect, useState } from 'react';

const CLAVE = 'foundia-tema-admin';

// Sin provider el tema queda en modo claro y alternar es un no-op.
const TemaContext = createContext({ tema: 'claro', alternarTema: () => {} });

function temaInicial() {
  try {
    const guardado = localStorage.getItem(CLAVE);
    if (guardado === 'claro' || guardado === 'oscuro') return guardado;
  } catch {
    // localStorage puede no estar disponible; seguimos con el sistema
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro';
}

export function TemaProvider({ children }) {
  const [tema, setTema] = useState(temaInicial);

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    try {
      localStorage.setItem(CLAVE, tema);
    } catch {
      // Sin persistencia la app sigue funcionando en la pestaña actual
    }
  }, [tema]);

  const alternarTema = useCallback(() => {
    setTema((actual) => (actual === 'oscuro' ? 'claro' : 'oscuro'));
  }, []);

  return <TemaContext.Provider value={{ tema, alternarTema }}>{children}</TemaContext.Provider>;
}

export function useTema() {
  return useContext(TemaContext);
}
