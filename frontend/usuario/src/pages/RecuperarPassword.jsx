import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { mensajeError, solicitarRecuperacion } from '../services/api';
import { correoEsValido } from '../utils/validaciones';

const ESPERA_REENVIO = 60; // segundos, igual que la API
const MENSAJE_NEUTRO = 'Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.';

// H35 - Solicitar el enlace para restablecer la contraseña.
// La respuesta es la misma exista o no la cuenta, para no revelar qué correos están registrados.
export default function RecuperarPassword() {
  const [params] = useSearchParams();
  const [correo, setCorreo] = useState(params.get('correo') || '');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [espera, setEspera] = useState(0);

  useEffect(() => {
    if (espera <= 0) return undefined;
    const t = setTimeout(() => setEspera((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [espera]);

  const enviar = async (e) => {
    e.preventDefault();
    if (enviando || espera > 0) return;
    setError('');
    setAviso('');

    const limpio = correo.trim().toLowerCase();
    if (!limpio) {
      setError('Ingresa el correo de tu cuenta.');
      return;
    }
    if (!correoEsValido(limpio) || limpio.length > 150) {
      setError('Ingresa un correo válido.');
      return;
    }

    setEnviando(true);
    try {
      const data = await solicitarRecuperacion(limpio);
      setAviso(data?.mensaje || MENSAJE_NEUTRO);
      setEspera(ESPERA_REENVIO);
    } catch (err) {
      if (!err?.response) {
        setError('No hay conexión con el servidor. Revisa tu conexión e inténtalo de nuevo.');
      } else {
        setError(mensajeError(err, 'No se pudo enviar la solicitud'));
      }
    } finally {
      setEnviando(false);
    }
  };

  let textoBoton = 'Enviar enlace';
  if (enviando) textoBoton = 'Enviando...';
  else if (espera > 0) textoBoton = `Reenviar en ${espera} s`;
  else if (aviso) textoBoton = 'Reenviar enlace';

  return (
    <AuthLayout>
      <form className="auth-tarjeta" onSubmit={enviar} noValidate>
        <div className="auth-logo">
          <Logo tamano={56} />
        </div>
        <h1>Recuperar contraseña</h1>
        <p className="auth-subtitulo">
          Escribe el correo de tu cuenta y te enviaremos un enlace para crear una nueva contraseña.
        </p>

        {aviso && (
          <div className="banner-exito" role="status">
            <Icono nombre="correo" tamano={18} /> {aviso} Revisa también tu carpeta de spam. El enlace vence en 30 minutos.
          </div>
        )}
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
            placeholder="tu@correo.com"
            required
            maxLength={150}
            autoComplete="email"
          />
        </label>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || espera > 0}>
          {textoBoton}
        </button>

        <p className="auth-enlace">
          ¿La recordaste? <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
