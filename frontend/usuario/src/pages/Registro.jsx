import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthLayout from '../layouts/AuthLayout';
import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { getDocumentosLegales, mensajeError, statusDe } from '../services/api';
import { REGLAS_PASSWORD, validarRegistro } from '../utils/validaciones';

// Muestra el texto de los documentos (Markdown simple: títulos con # y ##, párrafos)
function TextoLegal({ texto }) {
  return texto
    .split('\n')
    .map((linea) => linea.trim())
    .filter(Boolean)
    .map((linea, i) => {
      if (linea.startsWith('## ')) return <h4 key={i}>{linea.slice(3)}</h4>;
      if (linea.startsWith('# ')) return <h3 key={i}>{linea.slice(2)}</h3>;
      return <p key={i}>{linea}</p>;
    });
}

// H11 - Registro de usuario con validaciones y envío de código al correo
// H33 - Aceptación expresa de los términos y la política de privacidad vigentes
export default function Registro() {
  const { registrar } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ nombre: '', apellido: '', correo: '', password: '', confirmar: '' });
  const [errores, setErrores] = useState({});
  const [tocados, setTocados] = useState({});
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  // H33: documentos legales vigentes y aceptación expresa (la casilla empieza desmarcada)
  const [documentos, setDocumentos] = useState(null);
  const [estadoDocumentos, setEstadoDocumentos] = useState('cargando');
  const [acepta, setAcepta] = useState(false);
  const [errorTerminos, setErrorTerminos] = useState('');
  const peticionDocumentos = useRef(null);

  const cargarDocumentos = useCallback(async () => {
    peticionDocumentos.current?.abort();
    const control = new AbortController();
    peticionDocumentos.current = control;
    setEstadoDocumentos('cargando');
    setAcepta(false); // al recargar los documentos se vuelve a pedir la aceptación
    try {
      const data = await getDocumentosLegales({ signal: control.signal });
      if (control.signal.aborted) return;
      if (!data?.version || !data?.terminos || !data?.privacidad) throw new Error('Documentos incompletos');
      setDocumentos(data);
      setEstadoDocumentos('ok');
    } catch {
      if (control.signal.aborted) return;
      setDocumentos(null);
      setEstadoDocumentos('error');
    }
  }, []);

  useEffect(() => {
    cargarDocumentos();
    return () => peticionDocumentos.current?.abort();
  }, [cargarDocumentos]);

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
    if (estadoDocumentos !== 'ok') {
      setError('No se pudieron cargar los términos y la política de privacidad. Reintenta antes de registrarte.');
      return;
    }
    const faltaAceptar = !acepta;
    if (faltaAceptar) setErrorTerminos('Debes aceptar los términos y la política de privacidad para crear tu cuenta.');
    if (Object.keys(encontrados).length > 0 || faltaAceptar) return;

    setEnviando(true);
    try {
      const correo = form.correo.trim().toLowerCase();
      await registrar({
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        correo,
        password: form.password,
        aceptaTerminos: true,
        versionLegal: documentos.version,
      });
      navigate(`/verificar?correo=${encodeURIComponent(correo)}`, { replace: true, state: { codigoEnviadoEn: Date.now() } });
    } catch (err) {
      const status = statusDe(err);
      if (status === 409) {
        setErrores((x) => ({ ...x, correo: 'Ese correo ya está registrado.' }));
        setError('Ese correo ya está registrado. Intenta con otro o inicia sesión.');
      } else if (status === 400) {
        const mensaje = mensajeError(err, 'Revisa los datos del formulario');
        setError(mensaje);
        // H33: si la versión cambió, se cargan los documentos nuevos y se pide aceptar otra vez
        if (/versi[oó]n/i.test(mensaje)) cargarDocumentos();
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
            <Icono nombre="alerta" tamano={18} /> {error}
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

        <section className="legal" aria-labelledby="legal-titulo">
          <h2 id="legal-titulo" className="legal-titulo">Términos y privacidad</h2>

          {estadoDocumentos === 'cargando' && (
            <p className="legal-estado" role="status">Cargando los términos y la política de privacidad...</p>
          )}

          {estadoDocumentos === 'error' && (
            <div className="banner-error" role="alert">
              <Icono nombre="alerta" tamano={18} /> No se pudieron cargar los documentos. No puedes registrarte sin leerlos.
              <button type="button" className="btn-enlace" onClick={cargarDocumentos}>
                Reintentar
              </button>
            </div>
          )}

          {estadoDocumentos === 'ok' && (
            <>
              <p className="legal-version">
                Versión <strong>{documentos.version}</strong>
                {documentos.estado?.includes('PENDIENTE') && ' · texto del proyecto pendiente de revisión institucional'}
              </p>
              <details className="legal-doc">
                <summary>Leer los Términos de uso</summary>
                <div className="legal-texto" tabIndex={0} aria-label="Términos de uso">
                  <TextoLegal texto={documentos.terminos} />
                </div>
              </details>
              <details className="legal-doc">
                <summary>Leer la Política de privacidad</summary>
                <div className="legal-texto" tabIndex={0} aria-label="Política de privacidad">
                  <TextoLegal texto={documentos.privacidad} />
                </div>
              </details>
              <label className="legal-casilla">
                <input
                  type="checkbox"
                  checked={acepta}
                  onChange={(e) => {
                    setAcepta(e.target.checked);
                    setErrorTerminos('');
                  }}
                  aria-invalid={Boolean(errorTerminos)}
                  aria-describedby={errorTerminos ? 'error-terminos' : undefined}
                />
                <span>
                  He leído y acepto los Términos de uso y la Política de privacidad (versión {documentos.version}).
                </span>
              </label>
              {errorTerminos && (
                <span id="error-terminos" className="campo-error">
                  {errorTerminos}
                </span>
              )}
            </>
          )}
        </section>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || estadoDocumentos !== 'ok'}>
          {enviando ? 'Creando cuenta...' : 'Registrarme'}
        </button>

        <p className="auth-enlace">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
