import { Outlet } from 'react-router-dom';
import Navbar from '../components/Navbar';
import BottomNav from '../components/BottomNav';

export default function MainLayout() {
  return (
    <div className="app-shell">
      <Navbar />
      <main className="contenido">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}
