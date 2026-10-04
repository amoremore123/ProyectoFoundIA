import { useCallback, useEffect, useState } from 'react';
import {
  crearCategoria,
  getCategorias,
  mensajeError,
  patchCategoria,
  statusDe,
  erroresDeValidacion,
} from '../services/api';

const VACIO = { nombre: '', descripcion: '' };

export default function Categorias() {
  const [categorias, setCategorias] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(VACIO);
  const [erroresCampo, setErroresCampo] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [accionando, setAccionando] = useState(null);
  const [exito, setExito] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getCategorias();
      setCategorias(Array.isArray(data) ? data : data?.results || []);
    } catch (e) {
      const status = statusDe(e);
      if (status === 401) setError('Sesión expirada. Vuelve a iniciar sesión.');
      else if (status === 403) setError('No tienes permisos para ver las categorías.');
      else setError(mensajeError(e, 'No se pudieron cargar las categorías'));
      setCategorias([]);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const crear = async (e) => {
    e.preventDefault();
    setError('');
    setExito('');
    setErroresCampo({});

    if (!form.nombre.trim()) {
      setErroresCampo({ nombre: 'El nombre es obligatorio' });
      return;
    }

    setEnviando(true);
    try {
      const creada = await crearCategoria({
        nombre: form.nombre.trim(),
        descripcion: form.descripcion.trim(),
      });
      setCategorias((prev) => [...prev, creada]);
      setForm(VACIO);
      setExito('Categoría creada correctamente.');
    } catch (err) {
      if (statusDe(err) === 400) {
        const campos = erroresDeValidacion(err);
        if (campos) setErroresCampo(campos);
        else setError(mensajeError(err, 'Revisa los datos del formulario'));
      } else if (statusDe(err) === 401) {
        setError('Sesión expirada. Vuelve a iniciar sesión.');
      } else if (statusDe(err) === 403) {
        setError('No tienes permisos para crear categorías.');
      } else if (statusDe(err) === 409) {
        setError('Esa categoría ya existe.');
      } else {
        setError(mensajeError(err, 'No se pudo crear la categoría'));
      }
    } finally {
      setEnviando(false);
    }
  };

  const toggleEstado = async (cat) => {
    const nuevoEstado = !cat.estado;
    setAccionando(cat.id);
    setError('');
    setExito('');
    try {
      await patchCategoria(cat.id, { estado: nuevoEstado });
      setCategorias((prev) =>
        prev.map((c) => (c.id === cat.id ? { ...c, estado: nuevoEstado } : c))
      );
    } catch (e) {
      const status = statusDe(e);
      if (status === 401) setError('Sesión expirada. Vuelve a iniciar sesión.');
      else if (status === 403) setError('No tienes permisos para modificar categorías.');
      else if (status === 400) setError(mensajeError(e, 'Datos inválidos'));
      else setError(mensajeError(e, 'No se pudo actualizar la categoría'));
    } finally {
      setAccionando(null);
    }
  };

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Categorías</h1>
        <span className="contador">{categorias.length} registros</span>
      </div>

      {error && (
        <div className="banner-error" role="alert">
          ⚠️ {error}
        </div>
      )}
      {exito && <div className="banner-exito">✅ {exito}</div>}

      <form className="tarjeta formulario" onSubmit={crear} noValidate>
        <h2>Nueva categoría</h2>

        <label className="campo">
          <span className="campo-label">Nombre *</span>
          <input
            type="text"
            className={`input${erroresCampo.nombre ? ' input-error' : ''}`}
            value={form.nombre}
            onChange={(e) => {
              setForm((f) => ({ ...f, nombre: e.target.value }));
              setErroresCampo((er) => ({ ...er, nombre: undefined }));
            }}
            placeholder="Ej. Celular"
          />
          {erroresCampo.nombre && <span className="campo-error">{erroresCampo.nombre}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Descripción</span>
          <input
            type="text"
            className={`input${erroresCampo.descripcion ? ' input-error' : ''}`}
            value={form.descripcion}
            onChange={(e) => {
              setForm((f) => ({ ...f, descripcion: e.target.value }));
              setErroresCampo((er) => ({ ...er, descripcion: undefined }));
            }}
            placeholder="Ej. Teléfonos inteligentes"
          />
          {erroresCampo.descripcion && (
            <span className="campo-error">{erroresCampo.descripcion}</span>
          )}
        </label>

        <button type="submit" className="btn btn-primario" disabled={enviando}>
          {enviando ? 'Creando...' : 'Crear categoría'}
        </button>
      </form>

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : categorias.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            🗂️
          </span>
          <p>No hay categorías todavía.</p>
        </div>
      ) : (
        <div className="tarjeta tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {categorias.map((c) => (
                <tr key={c.id}>
                  <td data-label="Nombre">{c.nombre}</td>
                  <td data-label="Descripción">{c.descripcion || '—'}</td>
                  <td data-label="Estado">
                    <span
                      className={`chip-estado ${c.estado ? 'estado-activo' : 'estado-inactivo'}`}
                    >
                      {c.estado ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td data-label="Acción">
                    <button
                      type="button"
                      className={`btn ${c.estado ? 'btn-peligro' : 'btn-primario'} btn-mini`}
                      onClick={() => toggleEstado(c)}
                      disabled={accionando === c.id}
                    >
                      {accionando === c.id
                        ? 'Guardando...'
                        : c.estado
                          ? 'Desactivar'
                          : 'Activar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
