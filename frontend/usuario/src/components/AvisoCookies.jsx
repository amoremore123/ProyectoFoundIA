import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { EVENTO_CONSENTIMIENTO, OPCIONES, guardarConsentimiento, leerConsentimiento } from '../utils/almacenamiento';

// Aviso de cookies (Ley 29733): informa qué se guarda en el navegador y deja elegir.
// No bloquea la página ni roba el foco; desaparece cuando la persona elige una opción.
export default function AvisoCookies() {
  const [visible, setVisible] = useState(() => leerConsentimiento() === null);
  const { pathname } = useLocation();

  useEffect(() => {
    // Si la elección se hace en /cookies, el aviso también se oculta
    const alCambiar = () => setVisible(leerConsentimiento() === null);
    window.addEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
    return () => window.removeEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
  }, []);

  // En /cookies la propia página muestra la elección: el aviso flotante taparía la tabla
  if (!visible || pathname === '/cookies') return null;

  return (
    <section className="aviso-cookies" aria-labelledby="aviso-cookies-titulo">
      <h2 id="aviso-cookies-titulo" className="aviso-cookies-titulo">Tu privacidad en FoundIA</h2>
      <p className="aviso-cookies-texto">
        No usamos cookies de publicidad ni de seguimiento. Guardamos en tu navegador solo lo
        necesario para tu sesión y, si lo permites, tu modo claro u oscuro.{' '}
        <Link to="/cookies">Ver detalle</Link>
      </p>
      <div className="aviso-cookies-acciones">
        <button type="button" className="btn btn-secundario" onClick={() => guardarConsentimiento(OPCIONES.ESENCIAL)}>
          Solo lo esencial
        </button>
        <button type="button" className="btn btn-primario" onClick={() => guardarConsentimiento(OPCIONES.TODO)}>
          Recordar mis preferencias
        </button>
      </div>
    </section>
  );
}
