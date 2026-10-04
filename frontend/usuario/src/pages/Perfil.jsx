import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { useAuth } from '../context/AuthContext';
import { actualizarPerfil, getPerfil, mensajeError, statusDe } from '../services/api';

export default function Perfil() {
  const { logout, actualizarUsuario } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ nombre: '', apellido: '', password: '' });
  const [correo, setCorreo] = useState('');
  const [rol, setRol] = useState('');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [guardado, setGuardado] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cargar = async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getPerfil();
      setForm({ nombre: data.nombre || '', apellido: data.apellido || '', password: '' });
      setCorreo(data.correo || '');
      setRol(data.rol || '');
    } catch (e) {
      setError(mensajeError(e, 'No se pudo cargar tu perfil'));
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => {
    cargar();
  }, []);

  const cambiar = (campo) => (e) => {
    setForm((f) => ({ ...f, [campo]: e.target.value }));
    setGuardado('');
  };

  const guardar = async (e) => {
    e.preventDefault();
    setError('');
    setGuardado('');
    setEnviando(true);
    try {
      const payload = { nombre: form.nombre.trim(), apellido: form.apellido.trim() };
      if (form.password) payload.password = form.password;
      const data = await actualizarPerfil(payload);
      actualizarUsuario(data);
      setForm((f) => ({ ...f, password: '' }));
      setGuardado('Perfil actualizado correctamente.');
    } catch (err) {
      if (statusDe(err) === 400) {
        setError(mensajeError(err, 'Revisa los datos del formulario'));
      } else {
        setError(mensajeError(err, 'No se pudo actualizar el perfil'));
      }
    } finally {
      setEnviando(false);
    }
  };

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (cargando) return <Cargando />;

  return (
    <div className="pagina pagina-estrecha">
      <h1 className="pagina-titulo">Mi perfil</h1>

      <MensajeError mensaje={error} onReintentar={cargar} />
      {guardado && <div className="banner-exito">✅ {guardado}</div>}

      <form className="tarjeta formulario" onSubmit={guardar} noValidate>
        <div className="perfil-resumen">
          <span className="perfil-avatar" aria-hidden="true">
            👤
          </span>
          <div>
            <p className="perfil-correo">{correo}</p>
            {rol && <span className="perfil-rol">{rol}</span>}
          </div>
        </div>

        <label className="campo">
          <span className="campo-label">Nombre *</span>
          <input type="text" className="input" value={form.nombre} onChange={cambiar('nombre')} required />
        </label>

        <label className="campo">
          <span className="campo-label">Apellido *</span>
          <input type="text" className="input" value={form.apellido} onChange={cambiar('apellido')} required />
        </label>

        <label className="campo">
          <span className="campo-label">Nueva contraseña (opcional)</span>
          <input
            type="password"
            className="input"
            value={form.password}
            onChange={cambiar('password')}
            placeholder="Déjalo vacío para no cambiarla"
            autoComplete="new-password"
          />
        </label>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
          {enviando ? 'Guardando...' : 'Guardar cambios'}
        </button>
      </form>

      <div className="perfil-acciones">
        <Link to="/mis-publicaciones" className="btn btn-secundario btn-bloque">
          Mis publicaciones
        </Link>
        <Link to="/notificaciones" className="btn btn-secundario btn-bloque">
          Notificaciones
        </Link>
        <button type="button" className="btn btn-peligro btn-bloque" onClick={cerrarSesion}>
          Cerrar sesión
        </button>
      </div>
    </div>
  );
}
