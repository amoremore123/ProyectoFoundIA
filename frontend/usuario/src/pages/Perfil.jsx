import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import Icono from '../components/Icono';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { actualizarPerfil, getPerfil, mensajeError, statusDe } from '../services/api';
import { REGLAS_PASSWORD, validarPerfil } from '../utils/validaciones';

const SIN_PASSWORD = { password: '', confirmar: '', passwordActual: '' };

// La API responde "El campo nombre ..." o "La contraseña actual ...": se ubica el error junto a su campo.
function campoDelError(mensaje = '') {
  const texto = mensaje.toLowerCase();
  if (texto.includes('contraseña actual')) return 'passwordActual';
  const campo = /el campo (\w+)/.exec(texto)?.[1];
  return ['nombre', 'apellido', 'password'].includes(campo) ? campo : null;
}

// H13 - Edición del perfil propio. Las contraseñas solo viven en el formulario:
// se borran al guardar o cancelar y nunca se guardan en el navegador.
export default function Perfil() {
  const { logout, actualizarUsuario } = useAuth();
  const { mostrarToast } = useToast();
  const navigate = useNavigate();

  const [original, setOriginal] = useState({ nombre: '', apellido: '' });
  const [form, setForm] = useState({ nombre: '', apellido: '', ...SIN_PASSWORD });
  const [correo, setCorreo] = useState('');
  const [rol, setRol] = useState('');
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorGuardar, setErrorGuardar] = useState('');
  const [errores, setErrores] = useState({});
  const [enviando, setEnviando] = useState(false);

  const cargar = async () => {
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getPerfil();
      const datos = { nombre: data.nombre || '', apellido: data.apellido || '' };
      setOriginal(datos);
      setForm({ ...datos, ...SIN_PASSWORD });
      setCorreo(data.correo || '');
      setRol(data.rol || '');
    } catch (e) {
      setErrorCarga(mensajeError(e, 'No se pudo cargar tu perfil'));
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => {
    cargar();
  }, []);

  const cambiar = (campo) => (e) => {
    setForm((f) => ({ ...f, [campo]: e.target.value }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
    setErrorGuardar('');
  };

  const hayCambios = form.nombre !== original.nombre || form.apellido !== original.apellido
    || Boolean(form.password || form.confirmar || form.passwordActual);

  const cancelar = () => {
    setForm({ ...original, ...SIN_PASSWORD });
    setErrores({});
    setErrorGuardar('');
  };

  const guardar = async (e) => {
    e.preventDefault();
    if (enviando) return;
    setErrorGuardar('');
    const encontrados = validarPerfil(form);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) return;

    setEnviando(true);
    try {
      const payload = { nombre: form.nombre.trim(), apellido: form.apellido.trim() };
      if (form.password) {
        payload.password = form.password;
        payload.passwordActual = form.passwordActual;
      }
      const data = await actualizarPerfil(payload);
      actualizarUsuario(data);
      const datos = { nombre: data.nombre || '', apellido: data.apellido || '' };
      setOriginal(datos);
      setForm({ ...datos, ...SIN_PASSWORD });
      mostrarToast(form.password ? 'Perfil y contraseña actualizados.' : 'Perfil actualizado correctamente.', 'exito');
    } catch (err) {
      const mensaje = mensajeError(err, 'No se pudo actualizar el perfil');
      const campo = statusDe(err) === 400 ? campoDelError(mensaje) : null;
      if (campo) setErrores((er) => ({ ...er, [campo]: mensaje }));
      else setErrorGuardar(mensaje);
    } finally {
      setEnviando(false);
    }
  };

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (cargando) return <Cargando />;

  if (errorCarga) {
    return (
      <div className="pagina pagina-estrecha">
        <h1 className="pagina-titulo">Mi perfil</h1>
        <MensajeError mensaje={errorCarga} onReintentar={cargar} />
      </div>
    );
  }

  // El error va fuera de la etiqueta (enlazado con aria-describedby) para que no cambie el nombre del campo.
  const campo = (nombre, etiqueta, props = {}) => (
    <div className="campo">
      <label className="campo-label" htmlFor={`perfil-${nombre}`}>{etiqueta}</label>
      <input
        id={`perfil-${nombre}`}
        className={`input${errores[nombre] ? ' input-error' : ''}`}
        value={form[nombre]}
        onChange={cambiar(nombre)}
        aria-invalid={Boolean(errores[nombre])}
        aria-describedby={errores[nombre] ? `error-${nombre}` : undefined}
        {...props}
      />
      {errores[nombre] && <span id={`error-${nombre}`} className="campo-error">{errores[nombre]}</span>}
    </div>
  );

  return (
    <div className="pagina pagina-estrecha">
      <h1 className="pagina-titulo">Mi perfil</h1>

      <form className="tarjeta formulario" onSubmit={guardar} noValidate>
        <div className="perfil-resumen">
          <span className="perfil-avatar" aria-hidden="true">
            {(original.nombre || correo || '?').charAt(0).toUpperCase()}
          </span>
          <div>
            <p className="perfil-correo">{correo}</p>
            {rol && <span className="perfil-rol">{rol}</span>}
          </div>
        </div>

        <MensajeError mensaje={errorGuardar} />

        {campo('nombre', 'Nombre *', { type: 'text', autoComplete: 'given-name', maxLength: 100 })}
        {campo('apellido', 'Apellido *', { type: 'text', autoComplete: 'family-name', maxLength: 100 })}

        <fieldset className="perfil-password">
          <legend className="campo-label">Cambiar contraseña (opcional)</legend>
          <p className="nota-suave">Déjalo vacío si no quieres cambiarla.</p>

          {campo('password', 'Nueva contraseña', { type: 'password', autoComplete: 'new-password', maxLength: 72 })}
          {form.password && (
            <ul className="reglas-password" aria-label="Requisitos de la nueva contraseña">
              {REGLAS_PASSWORD.map((r) => (
                <li key={r.id} className={r.cumple(form.password) ? 'ok' : ''}>{r.texto}</li>
              ))}
            </ul>
          )}
          {campo('confirmar', 'Confirmar nueva contraseña', { type: 'password', autoComplete: 'new-password', maxLength: 72 })}
          {campo('passwordActual', 'Contraseña actual', { type: 'password', autoComplete: 'current-password', maxLength: 72 })}
        </fieldset>

        <div className="perfil-botones">
          <button type="button" className="btn btn-secundario" onClick={cancelar} disabled={enviando || !hayCambios}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={enviando || !hayCambios}>
            {enviando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>

      <div className="perfil-acciones">
        <Link to="/mis-publicaciones" className="btn btn-secundario btn-bloque">
          <Icono nombre="lista" tamano={16} /> Mis publicaciones
        </Link>
        <Link to="/notificaciones" className="btn btn-secundario btn-bloque">
          <Icono nombre="campana" tamano={16} /> Notificaciones
        </Link>
        <button type="button" className="btn btn-peligro btn-bloque" onClick={cerrarSesion}>
          <Icono nombre="salir" tamano={16} /> Cerrar sesión
        </button>
      </div>
    </div>
  );
}
