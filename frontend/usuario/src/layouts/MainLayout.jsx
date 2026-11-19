import { Outlet, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import BottomNav from '../components/BottomNav';
import ChatAsistente from '../components/ChatAsistente';
import PieSitio from '../components/PieSitio';
import { AsistenteProvider } from '../context/AsistenteContext';
import useRevelarEnScroll from '../hooks/useRevelarEnScroll';
import { useEmbebido } from '../utils/embebido';

export default function MainLayout() {
  useRevelarEnScroll();
  const { pathname } = useLocation();
  // H73: dentro del WebView de la app solo se muestra la página, sin menú, pie ni FINDI
  const embebido = useEmbebido();

  if (embebido) {
    return (
      <div className="app-shell app-embebida">
        <main className="contenido" id="contenido" tabIndex={-1}>
          <div className="vista" key={pathname}>
            <Outlet />
          </div>
        </main>
      </div>
    );
  }

  return (
    <AsistenteProvider>
      <div className={`app-shell${pathname === '/' ? ' portada-cristal' : ''}`}>
        {/* H51: primer elemento enfocable para saltar el menú con el teclado */}
        <a href="#contenido" className="saltar-contenido">Saltar al contenido</a>
        <Navbar />
        <main className="contenido" id="contenido" tabIndex={-1}>
          {/* La clave por ruta repite la entrada suave en cada cambio de página */}
          <div className="vista" key={pathname}>
            <Outlet />
          </div>
        </main>
        {/* El pie va fuera del contenedor centrado para ocupar todo el ancho */}
        {pathname === '/' && <PieSitio />}
        <BottomNav />
        {/* H45: el asistente es parte del Sprint 3; ya no depende del interruptor de extras */}
        <ChatAsistente />
      </div>
    </AsistenteProvider>
  );
}
