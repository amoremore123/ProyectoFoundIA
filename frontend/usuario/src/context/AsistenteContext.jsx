import { createContext, useCallback, useContext, useMemo, useState } from 'react';

// Permite abrir el chat del asistente desde cualquier página (p. ej. el buscador de la portada).
const AsistenteContext = createContext({ abierto: false, pendiente: '', abrir: () => {}, cerrar: () => {}, consumirPendiente: () => '' });

export function AsistenteProvider({ children }) {
  const [abierto, setAbierto] = useState(false);
  const [pendiente, setPendiente] = useState('');

  const abrir = useCallback((mensaje = '') => {
    setAbierto(true);
    if (mensaje.trim()) setPendiente(mensaje.trim());
  }, []);

  const cerrar = useCallback(() => setAbierto(false), []);

  const consumirPendiente = useCallback(() => {
    const mensaje = pendiente;
    setPendiente('');
    return mensaje;
  }, [pendiente]);

  const valor = useMemo(
    () => ({ abierto, pendiente, abrir, cerrar, consumirPendiente }),
    [abierto, pendiente, abrir, cerrar, consumirPendiente]
  );

  return <AsistenteContext.Provider value={valor}>{children}</AsistenteContext.Provider>;
}

export const useAsistente = () => useContext(AsistenteContext);
