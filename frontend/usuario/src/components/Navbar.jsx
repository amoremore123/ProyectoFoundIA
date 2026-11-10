import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTema } from '../context/TemaContext';
import { useNotificaciones } from '../hooks/useNotificaciones';
import Logo from './Logo';
import Icono from './Icono';
import { EXTRAS_HABILITADOS } from '../config/alcance';
import { desplazarASeccion } from '../utils/navegacion';

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

const iniciales = (usuario) => {
  const texto = `${usuario?.nombre || ''} ${usuario?.apellido || ''}`.trim();
  if (!texto) return '';
  return texto.split(/\s+/).slice(0, 2).map((parte) => parte[0]).join('').toUpperCase();
};

export default function Navbar() {
  const { token, user } = useAuth();
  const { noLeidas } = useNotificaciones();
  const location = useLocation();
  const navigate = useNavigate();
  const enlacesRef = useRef(null);
  const [indicador, setIndicador] = useState(null);
  const [conScroll, setConScroll] = useState(false);

  // La píldora azul se desliza hasta el enlace activo (y desaparece si ninguno lo está)
  useLayoutEffect(() => {
    const activo = enlacesRef.current?.querySelector('a.active');
    setIndicador(activo ? { x: activo.offsetLeft, ancho: activo.offsetWidth } : null);
  }, [location.pathname, token]);

  // Con scroll, la barra se vuelve más translúcida y gana una sombra suave
  useEffect(() => {
    const alDesplazar = () => setConScroll(window.scrollY > 8);
    alDesplazar();
    window.addEventListener('scroll', alDesplazar, { passive: true });
    return () => window.removeEventListener('scroll', alDesplazar);
  }, []);

  // "Ayuda" lleva a "Así recuperas tu objeto" en la portada desde cualquier página
  const irAAyuda = (event) => {
    event.preventDefault();
    if (location.pathname !== '/') navigate('/');
    desplazarASeccion('proceso-recuperacion');
  };

  const siglas = iniciales(user);

  return (
    <header className={`navbar${conScroll ? ' con-scroll' : ''}`}>
      <div className="navbar-inner">
        <Link to="/" className="logo" aria-label="FoundIA inicio">
          <Logo claro={location.pathname === '/'} tamano={34} />
        </Link>

        <nav
          className="navbar-links"
          ref={enlacesRef}
          style={indicador ? { '--pildora-x': `${indicador.x}px`, '--pildora-ancho': `${indicador.ancho}px` } : undefined}
          data-indicador={indicador ? 'si' : 'no'}
        >
          <span className="navbar-pildora" aria-hidden="true" />
          <NavLink to="/" end>Inicio</NavLink>
          <NavLink to="/publicar">Reportar objeto</NavLink>
          <NavLink to="/buscar">Buscar objeto</NavLink>
          {EXTRAS_HABILITADOS ? <NavLink to="/historial">Historial</NavLink> : <NavLink to="/explorar">Explorar</NavLink>}
          {token && <NavLink to="/mis-publicaciones">Mis reportes</NavLink>}
          <a href="/#proceso-recuperacion" onClick={irAAyuda}>Ayuda</a>
        </nav>

        <div className="navbar-iconos">
          <BotonTema />
          {token && (
            <Link to="/notificaciones" className="icono-btn" aria-label={`Notificaciones${noLeidas > 0 ? ` ${noLeidas} sin leer` : ''}`}>
              <Icono nombre="campana" tamano={20} />
              {noLeidas > 0 && <span className="punto-rojo" data-testid="notif-dot" aria-hidden="true">{noLeidas}</span>}
            </Link>
          )}
          {token ? (
            <NavLink to="/perfil" className={({ isActive }) => `navbar-avatar${isActive ? ' activo' : ''}`} aria-label={`Perfil${siglas ? ` ${siglas}` : ''}`} title="Mi perfil">
              <span aria-hidden="true">{siglas || <Icono nombre="usuario" tamano={18} />}</span>
            </NavLink>
          ) : (
            <Link to="/login" className="btn btn-primario navbar-entrar">Iniciar sesión</Link>
          )}
        </div>
      </div>
    </header>
  );
}
