import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import Icono from '../components/Icono';

// Sin provider mostrarToast es un no-op para no romper renders aislados.
const ToastContext = createContext({ mostrarToast: () => {} });

const ICONOS = { exito: 'check', error: 'alerta', info: 'info' };
const ETIQUETAS = { exito: 'Éxito', error: 'Error', info: 'Aviso' };

let idSiguiente = 1;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const temporizadores = useRef({});

  const cerrarToast = useCallback((id) => {
    setToasts((lista) => lista.filter((toast) => toast.id !== id));
    clearTimeout(temporizadores.current[id]);
    delete temporizadores.current[id];
  }, []);

  const mostrarToast = useCallback((mensaje, tipo = 'info', duracion = 4000) => {
    const id = idSiguiente++;
    setToasts((lista) => [...lista.slice(-2), { id, mensaje, tipo }]);
    temporizadores.current[id] = setTimeout(() => cerrarToast(id), duracion);
  }, [cerrarToast]);

  useEffect(() => {
    const pendientes = temporizadores;
    return () => Object.values(pendientes.current).forEach(clearTimeout);
  }, []);

  return (
    <ToastContext.Provider value={{ mostrarToast, cerrarToast }}>
      {children}
      <div className="toasts" role="region" aria-label="Avisos del panel">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.tipo}`} role="status">
            <span className="toast-icono" aria-hidden="true">
              <Icono nombre={ICONOS[toast.tipo] || 'info'} tamano={18} />
            </span>
            <span className="toast-texto">
              <strong>{ETIQUETAS[toast.tipo] || 'Aviso'}</strong>
              {toast.mensaje}
            </span>
            <button
              type="button"
              className="toast-cerrar"
              onClick={() => cerrarToast(toast.id)}
              aria-label="Cerrar aviso"
            >
              <Icono nombre="cerrar" tamano={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
