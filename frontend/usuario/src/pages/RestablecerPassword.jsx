import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { mensajeError, restablecerPassword, statusDe } from '../services/api';
import { REGLAS_PASSWORD, TOKEN_RECUPERACION, validarNuevaPassword } from '../utils/validaciones';

const ENLACE_INVALIDO = 'El enlace no es válido o ya venció. Solicita uno nuevo.';

// H35 - Crear una nueva contraseña desde el enlace del correo (/restablecer-password?token=...)
export default function RestablecerPassword() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { search, pathname } = useLocation();

  // El token se lee una sola vez y se quita de la barra de direcciones
  // para que no quede en el historial ni se comparta por accidente.
  const [token] = useState(() => new URLSearchParams(search).get('token') || '');
  const tokenConFormato = TOKEN_RECUPERACION.test(token);

  const [form, setForm] = useState({ password: '', confirmar: '' });
  const [errores, setErrores] = useState({});
  const [tocados, setTocados] = useState({});
  const [error, setError] = useState(tokenConFormato ? '' : ENLACE_INVALIDO);
  const [enlaceInvalido, setEnlaceInvalido] = useState(!tokenConFormato);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (search) navigate(pathname, { replace: true });
  }, []);

  const cambiar = (campo) => (e) => {
    const siguiente = { ...form, [campo]: e.target.value };
    setForm(siguiente);
    if (tocados[campo]) setErrores(validarNuevaPassword(siguiente));
  };

  const salir = (campo) => () => {
    setTocados((t) => ({ ...t, [campo]: true }));
    setErrores(validarNuevaPassword(form));
  };

  const enviar = async (e) => {
    e.preventDefault();
    if (enviando || enlaceInvalido) return;
    setError('');
    const encontrados = validarNuevaPassword(form);
    setErrores(encontrados);
    setTocados({ password: true, confirmar: true });
    if (Object.keys(encontrados).length > 0) return;

    setEnviando(true);
    try {
      const data = await restablecerPassword(token, form.password);
      // Las sesiones anteriores dejan de valer: se cierra también la de este navegador
      logout();
      navigate('/login', {
        replace: true,
        state: { aviso: data?.mensaje || 'Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña.' },
      });
    } catch (err) {
      const mensaje = mensajeError(err, 'No se pudo actualizar la contraseña');
      if (statusDe(err) === 400 && /enlace/i.test(mensaje)) {
        setEnlaceInvalido(true);
        setForm({ password: '', confirmar: '' });
      }
      if (!err?.response) {
        setError('No hay conexión con el servidor. Revisa tu conexión e inténtalo de nuevo.');
      } else {
        setError(mensaje);
      }
    } finally {
      setEnviando(false);
    }
  };

  const mostrar = (campo) => tocados[campo] && errores[campo];

  const campoPassword = (campo, etiqueta) => (
    <label className="campo">
      <span className="campo-label">{etiqueta} *</span>
      <input
        type="password"
        className={`input ${mostrar(campo) ? 'input-error' : ''}`}
        value={form[campo]}
        onChange={cambiar(campo)}
        onBlur={salir(campo)}
        aria-invalid={Boolean(mostrar(campo))}
        autoComplete="new-password"
        maxLength={72}
        disabled={enlaceInvalido}
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
        <h1>Nueva contraseña</h1>
        <p className="auth-subtitulo">Crea una contraseña segura. Al guardarla se cerrarán tus sesiones abiertas.</p>

        {error && (
          <div className="banner-error" role="alert">
            <Icono nombre={enlaceInvalido ? 'candado' : 'alerta'} tamano={18} /> {error}
            {enlaceInvalido && (
              <Link className="btn-enlace" to="/recuperar-password">Solicitar un nuevo enlace</Link>
            )}
          </div>
        )}

        {campoPassword('password', 'Nueva contraseña')}

        <ul className="reglas-password" aria-label="Requisitos de la contraseña">
          {REGLAS_PASSWORD.map((r) => (
            <li key={r.id} className={r.cumple(form.password) ? 'ok' : ''}>
              {r.texto}
            </li>
          ))}
        </ul>

        {campoPassword('confirmar', 'Confirmar contraseña')}

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || enlaceInvalido}>
          {enviando ? 'Guardando...' : 'Guardar contraseña'}
        </button>

        <p className="auth-enlace">
          <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
