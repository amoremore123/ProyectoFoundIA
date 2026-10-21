import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { mensajeError, reenviarCodigo } from '../services/api';

const ESPERA_REENVIO = 60; // segundos

// H11 - Confirmación de la cuenta con el código de 6 dígitos enviado al correo
export default function Verificar() {
  const { verificar } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { state } = useLocation();
  const envioConfirmado = Number.isFinite(state?.codigoEnviadoEn);

  const [correo, setCorreo] = useState(params.get('correo') || '');
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState(
    envioConfirmado ? `Te enviamos un código de 6 dígitos a ${params.get('correo')}.` : ''
  );
  const [enviando, setEnviando] = useState(false);
  const [espera, setEspera] = useState(envioConfirmado ? Math.max(0, ESPERA_REENVIO - Math.floor((Date.now() - state.codigoEnviadoEn) / 1000)) : 0);
  const [reenviando, setReenviando] = useState(false);

  useEffect(() => {
    if (espera <= 0) return undefined;
    const t = setTimeout(() => setEspera((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [espera]);

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    if (!/^\d{6}$/.test(codigo)) {
      setError('El código debe tener 6 dígitos.');
      return;
    }
    setEnviando(true);
    try {
      await verificar(correo.trim().toLowerCase(), codigo);
      navigate('/', { replace: true });
    } catch (err) {
      setError(mensajeError(err, 'No se pudo verificar el código'));
    } finally {
      setEnviando(false);
    }
  };

  const reenviar = async () => {
    if (reenviando || espera > 0) return;
    setError('');
    setAviso('');
    if (!correo.trim()) {
      setError('Ingresa tu correo para reenviar el código.');
      return;
    }
    setReenviando(true);
    try {
      const data = await reenviarCodigo(correo.trim().toLowerCase());
      setAviso(data.mensaje);
      setEspera(ESPERA_REENVIO);
    } catch (err) {
      setError(mensajeError(err, 'No se pudo reenviar el código'));
      if (err?.response?.status === 429) {
        const segundos = Number(err.response.headers?.['retry-after']);
        if (Number.isFinite(segundos) && segundos > 0) setEspera(Math.ceil(segundos));
      }
    } finally {
      setReenviando(false);
    }
  };

  return (
    <AuthLayout>
      <form className="auth-tarjeta" onSubmit={enviar} noValidate>
        <div className="auth-logo">
          <Logo tamano={56} />
        </div>
        <h1>Verifica tu correo</h1>

        {aviso && <div className="banner-exito"><Icono nombre="correo" tamano={18} /> {aviso}</div>}
        {error && (
          <div className="banner-error" role="alert">
            <Icono nombre="alerta" tamano={18} /> {error}
          </div>
        )}

        {!params.get('correo') && (
          <label className="campo">
            <span className="campo-label">Correo electrónico *</span>
            <input
              type="email"
              className="input"
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              placeholder="tu@correo.com"
              autoComplete="email"
            />
          </label>
        )}

        <label className="campo">
          <span className="campo-label">Código de verificación *</span>
          <input
            className="input input-codigo"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="000000"
            aria-label="Código de 6 dígitos"
          />
        </label>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || codigo.length !== 6}>
          {enviando ? 'Verificando...' : 'Verificar cuenta'}
        </button>

        <p className="auth-texto">
          ¿No te llegó?{' '}
          <button type="button" className="btn-enlace" onClick={reenviar} disabled={espera > 0 || reenviando}>
            {reenviando ? 'Enviando código...' : espera > 0 ? `Reenviar en ${espera}s` : 'Reenviar código'}
          </button>
        </p>

        <p className="auth-enlace">
          <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
