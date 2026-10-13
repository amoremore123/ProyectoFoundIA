import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import { mensajeError, statusDe } from '../services/api';
import { REGLAS_PASSWORD, validarRegistro } from '../utils/validaciones';

// H11 - Registro de usuario con validaciones y envío de código al correo
export default function Registro() {
  const { registrar } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ nombre: '', apellido: '', correo: '', password: '', confirmar: '' });
  const [errores, setErrores] = useState({});
  const [tocados, setTocados] = useState({});
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (campo) => (e) => {
    const siguiente = { ...form, [campo]: e.target.value };
    setForm(siguiente);
    if (tocados[campo]) setErrores(validarRegistro(siguiente));
  };

  const salir = (campo) => () => {
    setTocados((t) => ({ ...t, [campo]: true }));
    setErrores(validarRegistro(form));
  };

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    const encontrados = validarRegistro(form);
    setErrores(encontrados);
    setTocados({ nombre: true, apellido: true, correo: true, password: true, confirmar: true });
    if (Object.keys(encontrados).length > 0) return;

    setEnviando(true);
    try {
      const correo = form.correo.trim().toLowerCase();
      await registrar({
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        correo,
        password: form.password,
      });
      navigate(`/verificar?correo=${encodeURIComponent(correo)}`, { replace: true });
    } catch (err) {
      const status = statusDe(err);
      if (status === 409) {
        setErrores((x) => ({ ...x, correo: 'Ese correo ya está registrado.' }));
        setError('Ese correo ya está registrado. Intenta con otro o inicia sesión.');
      } else if (status === 400) {
        setError(mensajeError(err, 'Revisa los datos del formulario'));
      } else if (status === 503) {
        setError(mensajeError(err, 'No pudimos enviar el correo de verificación. Intenta más tarde.'));
      } else {
        setError(mensajeError(err, 'No se pudo completar el registro'));
      }
    } finally {
      setEnviando(false);
    }
  };

  const mostrar = (campo) => tocados[campo] && errores[campo];

  const campoTexto = (campo, etiqueta, props = {}) => (
    <label className="campo">
      <span className="campo-label">{etiqueta} *</span>
      <input
        className={`input ${mostrar(campo) ? 'input-error' : ''}`}
        value={form[campo]}
        onChange={cambiar(campo)}
        onBlur={salir(campo)}
        aria-invalid={Boolean(mostrar(campo))}
        {...props}
      />
      {mostrar(campo) && <span className="campo-error">{errores[campo]}</span>}
    </label>
  );

  return (
    <AuthLayout>
      <form className="auth-tarjeta" onSubmit={enviar} noValidate>
        <div className="auth-logo">
          <Logo tamano={56} />
        </div>
        <h1>Crear cuenta</h1>
        <p className="auth-subtitulo">Únete para publicar y recuperar objetos.</p>

        {error && (
          <div className="banner-error" role="alert">
            ⚠️ {error}
          </div>
        )}

        {campoTexto('nombre', 'Nombre', { type: 'text', autoComplete: 'given-name', maxLength: 100 })}
        {campoTexto('apellido', 'Apellido', { type: 'text', autoComplete: 'family-name', maxLength: 100 })}
        {campoTexto('correo', 'Correo electrónico', {
          type: 'email',
          placeholder: 'tu@correo.com',
          autoComplete: 'email',
          maxLength: 150,
        })}
        {campoTexto('password', 'Contraseña', {
          type: 'password',
          autoComplete: 'new-password',
          maxLength: 72,
        })}

        <ul className="reglas-password" aria-label="Requisitos de la contraseña">
          {REGLAS_PASSWORD.map((r) => (
            <li key={r.id} className={r.cumple(form.password) ? 'ok' : ''}>
              {r.texto}
            </li>
          ))}
        </ul>

        {campoTexto('confirmar', 'Confirmar contraseña', {
          type: 'password',
          autoComplete: 'new-password',
          maxLength: 72,
        })}

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
          {enviando ? 'Creando cuenta...' : 'Registrarme'}
        </button>

        <p className="auth-enlace">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
