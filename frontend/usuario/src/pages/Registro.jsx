import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { mensajeError, statusDe } from '../services/api';

export default function Registro() {
  const { registrar } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ nombre: '', apellido: '', correo: '', password: '' });
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (campo) => (e) => setForm((f) => ({ ...f, [campo]: e.target.value }));

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    setEnviando(true);
    try {
      await registrar({
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        correo: form.correo.trim(),
        password: form.password,
      });
      navigate('/login', { replace: true });
    } catch (err) {
      if (statusDe(err) === 409) {
        setError('Ese correo ya está registrado. Intenta con otro.');
      } else if (statusDe(err) === 400) {
        setError(mensajeError(err, 'Revisa los datos del formulario'));
      } else {
        setError(mensajeError(err, 'No se pudo completar el registro'));
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="auth-pantalla">
      <form className="auth-tarjeta" onSubmit={enviar}>
        <div className="auth-logo">ENCUENTRA+</div>
        <h1>Crear cuenta</h1>

        {error && (
          <div className="banner-error" role="alert">
            ⚠️ {error}
          </div>
        )}

        <label className="campo">
          <span className="campo-label">Nombre *</span>
          <input type="text" className="input" value={form.nombre} onChange={cambiar('nombre')} required />
        </label>

        <label className="campo">
          <span className="campo-label">Apellido *</span>
          <input type="text" className="input" value={form.apellido} onChange={cambiar('apellido')} required />
        </label>

        <label className="campo">
          <span className="campo-label">Correo electrónico *</span>
          <input
            type="email"
            className="input"
            value={form.correo}
            onChange={cambiar('correo')}
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
            value={form.password}
            onChange={cambiar('password')}
            placeholder="Mínimo 6 caracteres"
            required
            minLength={6}
            autoComplete="new-password"
          />
        </label>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
          {enviando ? 'Creando cuenta...' : 'Registrarme'}
        </button>

        <p className="auth-enlace">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </form>
    </div>
  );
}
