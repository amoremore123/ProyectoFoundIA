import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Logo from '../components/Logo';
import { mensajeError, statusDe } from '../services/api';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
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
          <span className="login-panel-etiqueta">Panel administrativo</span>
          <h2>Gestiona la comunidad de FoundIA</h2>
          <p>Modera publicaciones, atiende reportes y administra usuarios y categorías desde un solo lugar.</p>
        </div>
        <div className="login-panel-stats">
          <div><strong>👥</strong>Usuarios</div>
          <div><strong>📦</strong>Publicaciones</div>
          <div><strong>🚩</strong>Reportes</div>
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
              ⚠️ {error}
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
