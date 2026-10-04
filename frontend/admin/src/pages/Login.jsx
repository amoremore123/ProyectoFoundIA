import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
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
      <form className="login-tarjeta" onSubmit={enviar}>
        <div className="login-logo">ENCUENTRA+ ADMIN</div>
        <h1>Panel administrativo</h1>

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
  );
}
