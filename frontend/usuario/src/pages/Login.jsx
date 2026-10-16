import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { mensajeError, reenviarCodigo } from '../services/api';

const MINUTOS_BLOQUEO = 15;

// El servidor indica el tiempo restante redondeado hacia arriba ("en 3 minutos", "por 15 minutos"),
// así que reactivar Entrar al cumplirse ese tiempo nunca adelanta el fin real del bloqueo.
function minutosDeBloqueo(mensaje) {
  const coincidencia = /(\d+)\s*minuto/i.exec(mensaje || '');
  return coincidencia ? Number(coincidencia[1]) : MINUTOS_BLOQUEO;
}

// H12 - Inicio de sesión con JWT, mensajes de error y bloqueo tras 5 intentos
export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [bloqueadoHasta, setBloqueadoHasta] = useState(null);
  const [sinVerificar, setSinVerificar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const bloqueado = bloqueadoHasta !== null;

  // H12: al vencer el bloqueo se reactiva Entrar sin editar el correo ni recargar
  useEffect(() => {
    if (bloqueadoHasta === null) return undefined;
    const t = setTimeout(() => {
      setBloqueadoHasta(null);
      setError('');
    }, Math.max(0, bloqueadoHasta - Date.now()));
    return () => clearTimeout(t);
  }, [bloqueadoHasta]);

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    setSinVerificar(false);
    setBloqueadoHasta(null);

    if (!correo.trim() || !password) {
      setError('Ingresa tu correo y tu contraseña.');
      return;
    }

    setEnviando(true);
    try {
      await login(correo.trim().toLowerCase(), password);
      navigate('/', { replace: true });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401) {
        // Credenciales incorrectas (incluye intentos restantes) o cuenta suspendida
        setError(mensajeError(err, 'Correo o contraseña incorrectos.'));
      } else if (status === 423) {
        const mensaje = mensajeError(err, 'Tu cuenta está bloqueada temporalmente. Intenta más tarde.');
        setBloqueadoHasta(Date.now() + minutosDeBloqueo(mensaje) * 60 * 1000);
        setError(mensaje);
      } else if (status === 403) {
        setSinVerificar(true);
        setError(mensajeError(err, 'Debes verificar tu correo antes de iniciar sesión.'));
      } else if (!err?.response) {
        setError('No hay conexión con el servidor. Revisa que la API esté encendida.');
      } else {
        setError(mensajeError(err, 'No se pudo iniciar sesión'));
      }
    } finally {
      setEnviando(false);
    }
  };

  const irAVerificar = async () => {
    const limpio = correo.trim().toLowerCase();
    try {
      await reenviarCodigo(limpio);
    } catch {
      // Si falla el reenvío, igual se puede ingresar un código anterior vigente
    }
    navigate(`/verificar?correo=${encodeURIComponent(limpio)}`);
  };

  return (
    <AuthLayout>
      <form className="auth-tarjeta" onSubmit={enviar} noValidate>
        <div className="auth-logo">
          <Logo tamano={56} />
        </div>
        <h1>Iniciar sesión</h1>
        <p className="auth-subtitulo">Bienvenido de vuelta. Ingresa con tu cuenta para continuar.</p>

        {error && (
          <div className="banner-error" role="alert">
            <Icono nombre={bloqueado ? 'candado' : 'alerta'} tamano={18} /> {error}
            {sinVerificar && (
              <button type="button" className="btn-enlace" onClick={irAVerificar}>
                Enviar código y verificar
              </button>
            )}
          </div>
        )}

        <label className="campo">
          <span className="campo-label">Correo electrónico *</span>
          <input
            type="email"
            className="input"
            value={correo}
            onChange={(e) => {
              setCorreo(e.target.value);
              setBloqueadoHasta(null);
            }}
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

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || bloqueado}>
          {enviando ? 'Entrando...' : 'Entrar'}
        </button>

        <p className="auth-enlace">
          ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
