import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { mensajeError } from '../services/api';

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
      const status = err?.response?.status;
      if (status === 401) {
        setError('Correo o contraseña incorrectos, o tu cuenta está suspendida.');
      } else {
        setError(mensajeError(err, 'No se pudo iniciar sesión'));
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="auth-pantalla">
      <form className="auth-tarjeta" onSubmit={enviar}>
        <div className="auth-logo">ENCUENTRA+</div>
        <h1>Iniciar sesión</h1>

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
            placeholder="tu@correo.com"
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

        <p className="auth-enlace">
          ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
        </p>
      </form>
    </div>
  );
}
