import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MensajeError from '../components/MensajeError';
import { crearObjeto, listarCategorias, mensajeError, statusDe } from '../services/api';

const VACIO = {
  nombre: '',
  descripcion: '',
  categoriaId: '',
  ubicacion: '',
  fechaObjeto: '',
};

export default function Publicar() {
  const navigate = useNavigate();

  const [tipo, setTipo] = useState('PERDIDO');
  const [form, setForm] = useState(VACIO);
  const [categorias, setCategorias] = useState([]);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let vivo = true;
    (async () => {
      try {
        const cats = await listarCategorias();
        if (vivo) setCategorias(Array.isArray(cats) ? cats : cats?.results || []);
      } catch (e) {
        if (vivo) setErrorGeneral(mensajeError(e, 'No se pudieron cargar las categorías'));
      }
    })();
    return () => {
      vivo = false;
    };
  }, []);

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setForm((f) => ({ ...f, [campo]: valor }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
  };

  const validar = () => {
    const er = {};
    if (!form.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    if (!form.categoriaId) er.categoriaId = 'Selecciona una categoría';
    if (!form.ubicacion.trim()) er.ubicacion = 'La ubicación es obligatoria';
    if (!form.fechaObjeto) er.fechaObjeto = 'La fecha es obligatoria';
    setErrores(er);
    return Object.keys(er).length === 0;
  };

  const asignarErrorDeCampo = (mensaje) => {
    const texto = (mensaje || '').toLowerCase();
    const campoDetectado = ['nombre', 'descripcion', 'ubicacion', 'fecha', 'categoria'].find((c) =>
      texto.includes(c)
    );
    if (campoDetectado) {
      const mapa = {
        nombre: 'nombre',
        descripcion: 'descripcion',
        ubicacion: 'ubicacion',
        fecha: 'fechaObjeto',
        categoria: 'categoriaId',
      };
      setErrores((er) => ({ ...er, [mapa[campoDetectado]]: mensaje }));
      return true;
    }
    return false;
  };

  const enviar = async (e) => {
    e.preventDefault();
    setErrorGeneral('');
    setErrores({});
    if (!validar()) return;

    setEnviando(true);
    try {
      const payload = {
        nombre: form.nombre.trim(),
        descripcion: form.descripcion.trim(),
        categoriaId: Number(form.categoriaId),
        ubicacion: form.ubicacion.trim(),
        fechaObjeto: form.fechaObjeto,
        tipo,
      };
      const creado = await crearObjeto(payload);
      navigate(`/objeto/${creado.id}`, { replace: true });
    } catch (err) {
      if (statusDe(err) === 400) {
        const mensaje = mensajeError(err, 'Revisa los datos del formulario');
        if (!asignarErrorDeCampo(mensaje)) setErrorGeneral(mensaje);
      } else if (statusDe(err) === 401) {
        setErrorGeneral('Tu sesión expiró. Inicia sesión nuevamente.');
      } else {
        setErrorGeneral(mensajeError(err, 'No se pudo publicar el objeto'));
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="pagina pagina-estrecha">
      <button type="button" className="btn-fantasma btn-volver" onClick={() => navigate('/')}>
        ← Volver
      </button>
      <h1 className="pagina-titulo">← Publicar objeto</h1>

      <form className="tarjeta formulario" onSubmit={enviar} noValidate>
        <MensajeError mensaje={errorGeneral} />

        <fieldset className="campo grupo-toggle">
          <legend className="campo-label">¿Qué tipo de publicación es? *</legend>
          <div className="toggle-tipos">
            <button
              type="button"
              className={`toggle-tipo${tipo === 'PERDIDO' ? ' activo-perdido' : ''}`}
              onClick={() => setTipo('PERDIDO')}
            >
              Perdí un objeto
            </button>
            <button
              type="button"
              className={`toggle-tipo${tipo === 'ENCONTRADO' ? ' activo-encontrado' : ''}`}
              onClick={() => setTipo('ENCONTRADO')}
            >
              Encontré un objeto
            </button>
          </div>
        </fieldset>

        <label className="campo">
          <span className="campo-label">Nombre *</span>
          <input
            type="text"
            className={`input${errores.nombre ? ' input-error' : ''}`}
            value={form.nombre}
            onChange={cambiar('nombre')}
            placeholder="Ej. Celular Samsung Galaxy"
          />
          {errores.nombre && <span className="campo-error">{errores.nombre}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Descripción</span>
          <textarea
            className="input textarea"
            rows={4}
            value={form.descripcion}
            onChange={cambiar('descripcion')}
            placeholder="Describe el objeto con detalle..."
          />
          {errores.descripcion && <span className="campo-error">{errores.descripcion}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Categoría *</span>
          <select
            className={`input${errores.categoriaId ? ' input-error' : ''}`}
            value={form.categoriaId}
            onChange={cambiar('categoriaId')}
          >
            <option value="">Selecciona una categoría</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
          {errores.categoriaId && <span className="campo-error">{errores.categoriaId}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Ubicación *</span>
          <input
            type="text"
            className={`input${errores.ubicacion ? ' input-error' : ''}`}
            value={form.ubicacion}
            onChange={cambiar('ubicacion')}
            placeholder="Ej. Biblioteca central"
          />
          {errores.ubicacion && <span className="campo-error">{errores.ubicacion}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Fecha *</span>
          <input
            type="date"
            className={`input${errores.fechaObjeto ? ' input-error' : ''}`}
            value={form.fechaObjeto}
            onChange={cambiar('fechaObjeto')}
          />
          {errores.fechaObjeto && <span className="campo-error">{errores.fechaObjeto}</span>}
        </label>

        <div className="proximamente">
          <span>📷 Foto — próximamente</span>
        </div>
        <div className="proximamente">
          <span>📍 Ubicación en el mapa — próximamente</span>
        </div>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
          {enviando ? 'Publicando...' : 'PUBLICAR OBJETO'}
        </button>
      </form>
    </div>
  );
}
