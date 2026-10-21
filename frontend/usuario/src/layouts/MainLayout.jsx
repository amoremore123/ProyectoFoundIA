import { Outlet } from 'react-router-dom';
import Navbar from '../components/Navbar';
import BottomNav from '../components/BottomNav';
import ChatAsistente from '../components/ChatAsistente';
import { AsistenteProvider } from '../context/AsistenteContext';
import useRevelarEnScroll from '../hooks/useRevelarEnScroll';
import { EXTRAS_HABILITADOS } from '../config/alcance';

export default function MainLayout() {
  useRevelarEnScroll();

  return (
    <AsistenteProvider>
      <div className="app-shell">
        <Navbar />
        <main className="contenido">
          <Outlet />
        </main>
        <BottomNav />
        {EXTRAS_HABILITADOS && <ChatAsistente />}
      </div>
    </AsistenteProvider>
  );
}
