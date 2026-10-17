import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTema } from '../context/TemaContext';
import { useNotificaciones } from '../hooks/useNotificaciones';
import Logo from './Logo';
import Icono from './Icono';

function BotonTema() {
  const { tema, alternarTema } = useTema();
  const oscuro = tema === 'oscuro';
  return (
    <button
      type="button"
      className="icono-btn"
      onClick={alternarTema}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      title={oscuro ? 'Modo claro' : 'Modo oscuro'}
    >
      <Icono nombre={oscuro ? 'sol' : 'luna'} tamano={20} />
    </button>
  );
}

export default function Navbar() {
  const { token } = useAuth();
  const { noLeidas } = useNotificaciones();

  return (
    <header className="navbar">
      <div className="navbar-inner">
        <Link to="/" className="logo" aria-label="FoundIA inicio">
          <Logo tamano={34} />
          <span className="logo-sello">Tecsup</span>
        </Link>

        <nav className="navbar-links">
          <NavLink to="/" end>
            Inicio
          </NavLink>
          <NavLink to="/historial">Historial</NavLink>
          <NavLink to="/buscar">Buscar</NavLink>
          <NavLink to="/publicar">Publicar</NavLink>
          {token ? <NavLink to="/perfil">Perfil</NavLink> : <NavLink to="/login">Iniciar sesión</NavLink>}
        </nav>

        <div className="navbar-iconos">
          <BotonTema />
          {token && (
            <Link to="/notificaciones" className="icono-btn" aria-label="Notificaciones">
              <Icono nombre="campana" tamano={20} />
              {noLeidas > 0 && <span className="punto-rojo" data-testid="notif-dot">{noLeidas}</span>}
            </Link>
          )}
          <Link to={token ? '/perfil' : '/login'} className="icono-btn" aria-label={token ? 'Perfil' : 'Iniciar sesión'}>
            <Icono nombre="usuario" tamano={20} />
          </Link>
        </div>
      </div>
    </header>
  );
}
