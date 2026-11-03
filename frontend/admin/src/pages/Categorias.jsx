import { useCallback, useEffect, useState } from 'react';
import {
  crearCategoria,
  getCategorias,
  mensajeError,
  patchCategoria,
  statusDe,
  erroresDeValidacion,
} from '../services/api';
import Icono from '../components/Icono';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import { useToast } from '../context/ToastContext';

const VACIO = { nombre: '', descripcion: '' };
const MAX_NOMBRE = 100;
const MAX_DESCRIPCION = 255;

function validar({ nombre, descripcion }) {
  const errores = {};
  if (!nombre.trim()) errores.nombre = 'El nombre es obligatorio.';
  else if (nombre.trim().length > MAX_NOMBRE) errores.nombre = `Máximo ${MAX_NOMBRE} caracteres.`;
  if (descripcion.trim().length > MAX_DESCRIPCION) errores.descripcion = `Máximo ${MAX_DESCRIPCION} caracteres.`;
  return errores;
}

// Convierte la respuesta 400 de DRF en errores por campo o en un mensaje general
function erroresApi(err, fallback) {
  const campos = erroresDeValidacion(err) || {};
  const general = campos.non_field_errors;
  delete campos.non_field_errors;
  return { campos, general: general || (Object.keys(campos).length ? '' : mensajeError(err, fallback)) };
}

function mensajePorEstado(err, accion) {
  const status = statusDe(err);
  if (status === 401) return 'Sesión expirada. Vuelve a iniciar sesión.';
  if (status === 403) return `No tienes permisos para ${accion}.`;
  if (status === 404) return 'La categoría ya no existe. Recarga la lista.';
  return null;
}

// H26 - Gestión de categorías: crear, editar y desactivar/activar conservando el ID.
// Desactivar no borra la categoría ni cambia las publicaciones que ya la usan.
export default function Categorias() {
  const { mostrarToast } = useToast();
  const [categorias, setCategorias] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(VACIO);
  const [erroresCampo, setErroresCampo] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [accionando, setAccionando] = useState(null);
  const [editando, setEditando] = useState(null);
  const [formEdicion, setFormEdicion] = useState(VACIO);
  const [erroresEdicion, setErroresEdicion] = useState({});
  const [porDesactivar, setPorDesactivar] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getCategorias();
      setCategorias(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(mensajePorEstado(e, 'ver las categorías') || mensajeError(e, 'No se pudieron cargar las categorías'));
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
    const encontrados = validar(form);
    setErroresCampo(encontrados);
    if (Object.keys(encontrados).length) return;

    setEnviando(true);
    try {
      const creada = await crearCategoria({ nombre: form.nombre.trim(), descripcion: form.descripcion.trim() });
      setCategorias((prev) => [...prev, creada]);
      setForm(VACIO);
      mostrarToast(`Categoría "${creada?.nombre || form.nombre.trim()}" creada correctamente.`, 'exito');
    } catch (err) {
      const porEstado = mensajePorEstado(err, 'crear categorías');
      if (porEstado) setError(porEstado);
      else {
        const { campos, general } = erroresApi(err, 'No se pudo crear la categoría');
        setErroresCampo(campos);
        setError(general);
      }
    } finally {
      setEnviando(false);
    }
  };

  const empezarEdicion = (cat) => {
    setError('');
    setEditando(cat.id);
    setFormEdicion({ nombre: cat.nombre || '', descripcion: cat.descripcion || '' });
    setErroresEdicion({});
  };

  const guardarEdicion = async (cat) => {
    const encontrados = validar(formEdicion);
    setErroresEdicion(encontrados);
    if (Object.keys(encontrados).length) return;
    setAccionando(cat.id);
    setError('');
    try {
      const actualizada = await patchCategoria(cat.id, {
        nombre: formEdicion.nombre.trim(),
        descripcion: formEdicion.descripcion.trim(),
      });
      setCategorias((prev) => prev.map((c) => (c.id === cat.id ? { ...c, ...actualizada } : c)));
      setEditando(null);
      mostrarToast(`Categoría "${actualizada?.nombre || formEdicion.nombre.trim()}" actualizada.`, 'exito');
    } catch (err) {
      const porEstado = mensajePorEstado(err, 'modificar categorías');
      if (porEstado) setError(porEstado);
      else {
        const { campos, general } = erroresApi(err, 'No se pudo guardar la categoría');
        setErroresEdicion(campos);
        setError(general);
      }
    } finally {
      setAccionando(null);
    }
  };

  const cambiarEstado = async (cat, nuevoEstado) => {
    setAccionando(cat.id);
    setError('');
    try {
      const actualizada = await patchCategoria(cat.id, { estado: nuevoEstado });
      setCategorias((prev) => prev.map((c) => (c.id === cat.id ? { ...c, ...actualizada, estado: nuevoEstado } : c)));
      mostrarToast(
        nuevoEstado ? `"${cat.nombre}" está activa de nuevo.` : `"${cat.nombre}" quedó desactivada.`,
        'exito',
      );
    } catch (e) {
      setError(mensajePorEstado(e, 'modificar categorías') || mensajeError(e, 'No se pudo actualizar la categoría'));
    } finally {
      setAccionando(null);
      setPorDesactivar(null);
    }
  };

  const activas = categorias.filter((c) => c.estado).length;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Categorías</h1>
        <span className="contador">{activas} activas · {categorias.length} en total</span>
      </div>

      {error && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {error}
        </div>
      )}

      <form className="tarjeta formulario" onSubmit={crear} noValidate>
        <h2>Nueva categoría</h2>

        <label className="campo">
          <span className="campo-label">Nombre *</span>
          <input
            type="text"
            className={`input${erroresCampo.nombre ? ' input-error' : ''}`}
            value={form.nombre}
            maxLength={MAX_NOMBRE}
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
            maxLength={MAX_DESCRIPCION}
            onChange={(e) => {
              setForm((f) => ({ ...f, descripcion: e.target.value }));
              setErroresCampo((er) => ({ ...er, descripcion: undefined }));
            }}
            placeholder="Ej. Teléfonos inteligentes"
          />
          {erroresCampo.descripcion && <span className="campo-error">{erroresCampo.descripcion}</span>}
        </label>

        <button type="submit" className="btn btn-primario" disabled={enviando}>
          {enviando ? 'Creando...' : 'Crear categoría'}
        </button>
      </form>

      <p className="nota-tabla">
        Desactivar una categoría no la borra: las publicaciones que ya la usan la conservan y
        no se podrá elegir en publicaciones nuevas.
      </p>

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : categorias.length === 0 && !error ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="etiqueta" tamano={28} />
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
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {categorias.map((c) => (editando === c.id ? (
                <tr key={c.id} className="fila-edicion">
                  <td data-label="Nombre">
                    <input
                      className={`input${erroresEdicion.nombre ? ' input-error' : ''}`}
                      aria-label={`Nombre de ${c.nombre}`}
                      value={formEdicion.nombre}
                      maxLength={MAX_NOMBRE}
                      onChange={(e) => setFormEdicion((f) => ({ ...f, nombre: e.target.value }))}
                    />
                    {erroresEdicion.nombre && <span className="campo-error">{erroresEdicion.nombre}</span>}
                  </td>
                  <td data-label="Descripción">
                    <input
                      className={`input${erroresEdicion.descripcion ? ' input-error' : ''}`}
                      aria-label={`Descripción de ${c.nombre}`}
                      value={formEdicion.descripcion}
                      maxLength={MAX_DESCRIPCION}
                      onChange={(e) => setFormEdicion((f) => ({ ...f, descripcion: e.target.value }))}
                    />
                    {erroresEdicion.descripcion && <span className="campo-error">{erroresEdicion.descripcion}</span>}
                  </td>
                  <td data-label="Estado">
                    <span className={`chip-estado ${c.estado ? 'estado-activo' : 'estado-inactivo'}`}>
                      {c.estado ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td data-label="Acciones">
                    <div className="acciones-fila">
                      <button type="button" className="btn btn-primario btn-mini" disabled={accionando === c.id}
                        onClick={() => guardarEdicion(c)}>
                        {accionando === c.id ? 'Guardando...' : 'Guardar'}
                      </button>
                      <button type="button" className="btn btn-fantasma btn-mini" disabled={accionando === c.id}
                        onClick={() => setEditando(null)}>
                        Cancelar
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                <tr key={c.id}>
                  <td data-label="Nombre">{c.nombre}</td>
                  <td data-label="Descripción">{c.descripcion || '—'}</td>
                  <td data-label="Estado">
                    <span className={`chip-estado ${c.estado ? 'estado-activo' : 'estado-inactivo'}`}>
                      {c.estado ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td data-label="Acciones">
                    <div className="acciones-fila">
                      <button type="button" className="btn btn-fantasma btn-mini" disabled={accionando === c.id}
                        onClick={() => empezarEdicion(c)} aria-label={`Editar ${c.nombre}`}>
                        Editar
                      </button>
                      <button
                        type="button"
                        className={`btn ${c.estado ? 'btn-peligro' : 'btn-primario'} btn-mini`}
                        onClick={() => (c.estado ? setPorDesactivar(c) : cambiarEstado(c, true))}
                        disabled={accionando === c.id}
                        aria-label={`${c.estado ? 'Desactivar' : 'Activar'} ${c.nombre}`}
                      >
                        {accionando === c.id ? 'Guardando...' : c.estado ? 'Desactivar' : 'Activar'}
                      </button>
                    </div>
                  </td>
                </tr>
              )))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmarDialogo
        abierto={Boolean(porDesactivar)}
        titulo={`¿Desactivar "${porDesactivar?.nombre || ''}"?`}
        mensaje="No se podrá elegir en publicaciones nuevas. Las publicaciones que ya la usan la conservan y puedes volver a activarla cuando quieras."
        textoConfirmar="Sí, desactivar"
        peligro
        procesando={accionando === porDesactivar?.id}
        onConfirmar={() => cambiarEstado(porDesactivar, false)}
        onCancelar={() => setPorDesactivar(null)}
      />
    </div>
  );
}
