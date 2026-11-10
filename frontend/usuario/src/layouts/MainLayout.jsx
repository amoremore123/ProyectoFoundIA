import { Outlet, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import BottomNav from '../components/BottomNav';
import ChatAsistente from '../components/ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import useRevelarEnScroll from '../hooks/useRevelarEnScroll';

export default function MainLayout() {
  useRevelarEnScroll();
  const { pathname } = useLocation();

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
        <BottomNav />
        {/* H45: el asistente es parte del Sprint 3; ya no depende del interruptor de extras */}
        <ChatAsistente />
      </div>
    </AsistenteProvider>
  );
}
