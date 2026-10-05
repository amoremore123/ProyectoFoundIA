import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { mensajeError, reenviarCodigo } from '../services/api';

const ESPERA_REENVIO = 60; // segundos

// H11 - Confirmación de la cuenta con el código de 6 dígitos enviado al correo
export default function Verificar() {
  const { verificar } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [correo, setCorreo] = useState(params.get('correo') || '');
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState(
    params.get('correo') ? `Te enviamos un código de 6 dígitos a ${params.get('correo')}.` : ''
  );
  const [enviando, setEnviando] = useState(false);
  const [espera, setEspera] = useState(params.get('correo') ? ESPERA_REENVIO : 0);

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
    setError('');
    setAviso('');
    if (!correo.trim()) {
      setError('Ingresa tu correo para reenviar el código.');
      return;
    }
    try {
      const data = await reenviarCodigo(correo.trim().toLowerCase());
      setAviso(data.mensaje);
      setEspera(ESPERA_REENVIO);
    } catch (err) {
      setError(mensajeError(err, 'No se pudo reenviar el código'));
    }
  };

  return (
    <div className="auth-pantalla">
      <form className="auth-tarjeta" onSubmit={enviar} noValidate>
        <div className="auth-logo">ENCUENTRA+</div>
        <h1>Verifica tu correo</h1>

        {aviso && <div className="banner-exito">✉️ {aviso}</div>}
        {error && (
          <div className="banner-error" role="alert">
            ⚠️ {error}
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
          <button type="button" className="btn-enlace" onClick={reenviar} disabled={espera > 0}>
            {espera > 0 ? `Reenviar en ${espera}s` : 'Reenviar código'}
          </button>
        </p>

        <p className="auth-enlace">
          <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </form>
    </div>
  );
}
