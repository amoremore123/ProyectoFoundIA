import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTema } from '../context/TemaContext';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import useRevelarEnScroll from '../hooks/useRevelarEnScroll';
import { puedeVer } from '../config/permisos';

// Menú en el orden del diseño aprobado. Solo módulos que existen: no hay pantallas de
// notificaciones ni configuración en el panel (el modo claro/oscuro está al pie del menú).
const menu = [
  { to: '/', icono: 'panel', label: 'Dashboard', exact: true },
  // H26/H28: denuncias de publicaciones
  { to: '/reportes', icono: 'bandera', label: 'Reportes' },
  { to: '/publicaciones', icono: 'caja', label: 'Objetos' },
  { to: '/usuarios', icono: 'usuarios', label: 'Usuarios' },
  { to: '/categorias', icono: 'etiqueta', label: 'Categorías' },
  { to: '/estadisticas', icono: 'grafico', label: 'Estadísticas' },
  // H41/H43: biblioteca
  { to: '/custodia', icono: 'qr', label: 'Custodia' },
  { to: '/reclamos', icono: 'bandeja', label: 'Reclamos' },
  // H54: objetos no reclamados
  { to: '/vencidos', icono: 'reloj', label: 'Vencidos' },
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
          {/* El menú es marino en ambos modos: siempre la versión clara del logo */}
          <Logo tamano={32} claro />
          <span className="sidebar-badge">{user?.rol === 'BIBLIOTECA' ? 'Biblioteca' : 'Admin'} · Tecsup</span>
        </div>

        <nav className="sidebar-menu">
          {menu.filter((item) => puedeVer(user, item.to)).map((item) => (
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
