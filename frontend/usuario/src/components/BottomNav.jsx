import { NavLink } from 'react-router-dom';

const items = [
  { to: '/', icono: '🏠', label: 'Inicio', exact: true },
  { to: '/buscar', icono: '🔍', label: 'Buscar' },
  { to: '/publicar', icono: '➕', label: 'Publicar' },
  { to: '/perfil', icono: '👤', label: 'Perfil' },
];

export default function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.exact}
          className={({ isActive }) => `bottom-nav-item${isActive ? ' activo' : ''}`}
        >
          <span className="bottom-nav-icono" aria-hidden="true">
            {item.icono}
          </span>
          <span className="bottom-nav-label">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
