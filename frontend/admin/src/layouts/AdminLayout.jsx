import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Logo from '../components/Logo';

const menu = [
  { to: '/', icono: '📊', label: 'Dashboard', exact: true },
  { to: '/usuarios', icono: '👥', label: 'Usuarios' },
  { to: '/publicaciones', icono: '📦', label: 'Publicaciones' },
  { to: '/reportes', icono: '🚩', label: 'Reportes' },
  { to: '/categorias', icono: '🗂️', label: 'Categorías' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="sidebar-logo">
          <Logo claro tamano={34} />
          <span className="sidebar-badge">Admin</span>
        </div>

        <nav className="sidebar-menu">
          {menu.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.exact}
              className={({ isActive }) => `sidebar-link${isActive ? ' activo' : ''}`}
            >
              <span aria-hidden="true">{item.icono}</span>
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
          <button type="button" className="sidebar-salir" onClick={cerrarSesion}>
            Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="admin-contenido">
        <Outlet />
      </main>
    </div>
  );
}
