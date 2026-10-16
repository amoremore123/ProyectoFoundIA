import { NavLink } from 'react-router-dom';
import Icono from './Icono';
import { useAuth } from '../context/AuthContext';

const items = [
  { to: '/', icono: 'inicio', label: 'Inicio', exact: true },
  { to: '/buscar', icono: 'buscar', label: 'Buscar' },
  { to: '/publicar', icono: 'mas', label: 'Publicar' },
];

// H12: sin sesión no se muestra el enlace privado de Perfil, sino el acceso para entrar
const itemCuenta = (token) => (token
  ? { to: '/perfil', icono: 'usuario', label: 'Perfil' }
  : { to: '/login', icono: 'usuario', label: 'Entrar' });

export default function BottomNav() {
  const { token } = useAuth();
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {[...items, itemCuenta(token)].map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.exact}
          className={({ isActive }) => `bottom-nav-item${isActive ? ' activo' : ''}`}
        >
          <span className="bottom-nav-icono" aria-hidden="true">
            <Icono nombre={item.icono} tamano={22} />
          </span>
          <span className="bottom-nav-label">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
