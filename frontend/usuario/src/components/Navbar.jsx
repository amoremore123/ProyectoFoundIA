import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotificaciones } from '../hooks/useNotificaciones';
import Logo from './Logo';

export default function Navbar() {
  const { token } = useAuth();
  const { noLeidas } = useNotificaciones();

  return (
    <header className="navbar">
      <div className="navbar-inner">
        <Link to="/" className="logo" aria-label="FoundIA inicio">
          <Logo tamano={38} />
        </Link>

        <nav className="navbar-links">
          <NavLink to="/" end>
            Inicio
          </NavLink>
          <NavLink to="/buscar">Buscar</NavLink>
          <NavLink to="/publicar">Publicar</NavLink>
          <NavLink to="/perfil">Perfil</NavLink>
        </nav>

        <div className="navbar-iconos">
          {token && (
            <Link to="/notificaciones" className="icono-btn" aria-label="Notificaciones">
              <span aria-hidden="true">🔔</span>
              {noLeidas > 0 && <span className="punto-rojo" data-testid="notif-dot">{noLeidas}</span>}
            </Link>
          )}
          <Link to={token ? '/perfil' : '/login'} className="icono-btn" aria-label="Perfil">
            <span aria-hidden="true">👤</span>
          </Link>
        </div>
      </div>
    </header>
  );
}
