import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTema } from '../context/TemaContext';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import useRevelarEnScroll from '../hooks/useRevelarEnScroll';

const menu = [
  { to: '/', icono: 'panel', label: 'Dashboard', exact: true },
  { to: '/usuarios', icono: 'usuarios', label: 'Usuarios' },
  { to: '/publicaciones', icono: 'caja', label: 'Publicaciones' },
  // H26/H28 son historias del Sprint 2: ya no dependen del interruptor de extras
  { to: '/reportes', icono: 'bandera', label: 'Reportes' },
  { to: '/categorias', icono: 'etiqueta', label: 'Categorías' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const { tema, alternarTema } = useTema();
  const navigate = useNavigate();
  useRevelarEnScroll();

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const oscuro = tema === 'oscuro';

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="sidebar-logo">
          <Logo tamano={32} />
          <span className="sidebar-badge">Admin · Tecsup</span>
        </div>

        <nav className="sidebar-menu">
          {menu.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.exact}
              className={({ isActive }) => `sidebar-link${isActive ? ' activo' : ''}`}
            >
              <Icono nombre={item.icono} tamano={18} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-pie">
          {user && (
            <div className="sidebar-perfil">
              <span className="sidebar-avatar" aria-hidden="true">
                {(user.nombre || '?').charAt(0).toUpperCase()}
              </span>
              <p className="sidebar-usuario">
                {user.nombre} {user.apellido}
                <span>{user.correo}</span>
              </p>
            </div>
          )}
          <button
            type="button"
            className="boton-tema"
            onClick={alternarTema}
            aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          >
            <Icono nombre={oscuro ? 'sol' : 'luna'} tamano={18} />
            {oscuro ? 'Modo claro' : 'Modo oscuro'}
          </button>
          <button type="button" className="sidebar-salir" onClick={cerrarSesion}>
            <Icono nombre="salir" tamano={16} /> Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="admin-contenido">
        <Outlet />
      </main>
    </div>
  );
}
