import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { mensajeError, statusDe } from '../services/api';

// H15: motivo por el que el panel cerró la sesión (lo envía el interceptor de la API).
const MOTIVOS = {
  'sin-permiso': 'Tu cuenta ya no tiene permisos de administrador o fue suspendida. Se cerró la sesión del panel.',
  'sesion-vencida': 'Tu sesión venció. Vuelve a iniciar sesión.',
};

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(MOTIVOS[params.get('motivo')] || '');
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    setEnviando(true);
    try {
      await login(correo.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      const status = statusDe(err);
      if (status === 401) {
        setError('Correo o contraseña incorrectos.');
      } else if (status === 403) {
        setError('No tienes permisos de administrador.');
      } else {
        setError(mensajeError(err, 'No se pudo iniciar sesión'));
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="login-pantalla">
      <aside className="login-panel" aria-hidden="true">
        <Logo claro tamano={52} />
        <div className="login-panel-texto">
          <span className="login-panel-etiqueta">Panel administrativo · Tecsup</span>
          <h2>Gestiona los objetos perdidos del campus</h2>
          <p>Modera publicaciones, atiende reportes y administra usuarios y categorías desde un solo lugar.</p>
        </div>
        <div className="login-panel-stats">
          <div><strong><Icono nombre="usuarios" tamano={18} /></strong>Usuarios</div>
          <div><strong><Icono nombre="caja" tamano={18} /></strong>Publicaciones</div>
          <div><strong><Icono nombre="bandera" tamano={18} /></strong>Reportes</div>
        </div>
      </aside>

      <div className="login-contenido">
        <form className="login-tarjeta" onSubmit={enviar}>
          <div className="login-logo">
            <Logo tamano={56} />
          </div>
          <h1>Iniciar sesión</h1>
          <p className="login-subtitulo">Acceso exclusivo para administradores.</p>

          {error && (
            <div className="banner-error" role="alert">
              <Icono nombre="alerta" tamano={18} /> {error}
            </div>
          )}

          <label className="campo">
            <span className="campo-label">Correo electrónico *</span>
            <input
              type="email"
              className="input"
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              placeholder="admin@correo.com"
              required
              autoComplete="email"
            />
          </label>

          <label className="campo">
            <span className="campo-label">Contraseña *</span>
            <input
              type="password"
              className="input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
            />
          </label>

          <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
            {enviando ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  );
}
