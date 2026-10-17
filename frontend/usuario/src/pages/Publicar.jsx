import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MensajeError from '../components/MensajeError';
import Icono from '../components/Icono';
import { crearObjeto, mensajeError, statusDe } from '../services/api';
import useCategorias from '../hooks/useCategorias';
import { MAX_DESCRIPCION, largoEnCaracteres } from '../utils/validaciones';
import { obtenerUbicacionActual } from '../services/geolocalizacion';
import { useToast } from '../context/ToastContext';

const VACIO = {
  nombre: '',
  descripcion: '',
  categoriaId: '',
  ubicacion: '',
  fechaObjeto: '',
};

const hoyISO = () => {
  const f = new Date();
  return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;
};

export default function Publicar() {
  const navigate = useNavigate();
  const { mostrarToast } = useToast();

  const [tipo, setTipo] = useState('PERDIDO');
  const [form, setForm] = useState(VACIO);
  const { categorias, cargandoCategorias, errorCategorias, reintentarCategorias } = useCategorias();
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [coordenadas, setCoordenadas] = useState({ latitud: null, longitud: null });
  const [ubicando, setUbicando] = useState(false);
  const [avisoUbicacion, setAvisoUbicacion] = useState('');
  const solicitudUbicacion = useRef(0);

  useEffect(() => () => {
    solicitudUbicacion.current += 1;
  }, []);

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setForm((f) => ({ ...f, [campo]: valor }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
    if (campo === 'ubicacion') {
      // Una dirección manual ya no representa las coordenadas obtenidas por GPS.
      solicitudUbicacion.current += 1;
      setCoordenadas({ latitud: null, longitud: null });
      setAvisoUbicacion('');
      setUbicando(false);
    }
  };

  const usarMiUbicacion = async () => {
    const solicitud = ++solicitudUbicacion.current;
    setAvisoUbicacion('');
    setUbicando(true);
    try {
      const { latitud, longitud, direccion } = await obtenerUbicacionActual();
      if (solicitud !== solicitudUbicacion.current) return;
      setCoordenadas({ latitud, longitud });
      setForm((f) => ({
        ...f,
        ubicacion: direccion || `${latitud}, ${longitud}`,
      }));
      setErrores((er) => ({ ...er, ubicacion: undefined }));
    } catch (e) {
      if (solicitud === solicitudUbicacion.current) setAvisoUbicacion(e.message);
    } finally {
      if (solicitud === solicitudUbicacion.current) setUbicando(false);
    }
  };

  const validar = () => {
    const er = {};
    if (!form.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    else if (form.nombre.trim().length > 150) er.nombre = 'El nombre no debe superar los 150 caracteres';
    if (!form.descripcion.trim()) er.descripcion = 'La descripción es obligatoria';
    else if (largoEnCaracteres(form.descripcion) > MAX_DESCRIPCION) {
      er.descripcion = `La descripción no debe superar los ${MAX_DESCRIPCION} caracteres`;
    }
    if (!form.categoriaId) er.categoriaId = 'Selecciona una categoría';
    if (!form.ubicacion.trim()) er.ubicacion = 'La ubicación es obligatoria';
    else if (form.ubicacion.trim().length > 255) er.ubicacion = 'La ubicación no debe superar los 255 caracteres';
    if (!form.fechaObjeto) er.fechaObjeto = 'La fecha es obligatoria';
    else if (form.fechaObjeto > hoyISO()) er.fechaObjeto = 'La fecha no puede ser futura';
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
    if (enviando || ubicando) return;
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
        latitud: coordenadas.latitud,
        longitud: coordenadas.longitud,
      };
      const creado = await crearObjeto(payload);
      mostrarToast(
        tipo === 'PERDIDO'
          ? 'Tu objeto perdido ya está publicado. Te avisaremos si aparece una coincidencia.'
          : '¡Gracias por reportarlo! Tu hallazgo ya es visible para la comunidad.',
        'exito',
      );
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
        <Icono nombre="flechaIzq" tamano={16} /> Volver
      </button>
      <h1 className="pagina-titulo">Publicar objeto</h1>
      <p className="pagina-subtitulo">Completa los datos para que la comunidad Tecsup pueda ayudarte.</p>

      <form className="tarjeta formulario" onSubmit={enviar} noValidate>
        <MensajeError mensaje={errorGeneral} />

        <fieldset className="campo grupo-toggle">
          <legend className="campo-label">¿Qué tipo de publicación es? *</legend>
          <div className="toggle-tipos">
            <button
              type="button"
              className={`toggle-tipo${tipo === 'PERDIDO' ? ' activo-perdido' : ''}`}
              aria-pressed={tipo === 'PERDIDO'}
              onClick={() => setTipo('PERDIDO')}
            >
              Perdí un objeto
            </button>
            <button
              type="button"
              className={`toggle-tipo${tipo === 'ENCONTRADO' ? ' activo-encontrado' : ''}`}
              aria-pressed={tipo === 'ENCONTRADO'}
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
            maxLength={150}
          />
          {errores.nombre && <span className="campo-error">{errores.nombre}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Descripción *</span>
          <textarea
            className={`input textarea${errores.descripcion ? ' input-error' : ''}`}
            rows={4}
            value={form.descripcion}
            onChange={cambiar('descripcion')}
            placeholder="Describe el objeto con detalle..."
            maxLength={MAX_DESCRIPCION}
            aria-invalid={Boolean(errores.descripcion)}
            aria-describedby="descripcion-contador"
          />
          <span
            id="descripcion-contador"
            className={`nota-suave contador-caracteres${largoEnCaracteres(form.descripcion) > MAX_DESCRIPCION ? ' excedido' : ''}`}
          >
            {largoEnCaracteres(form.descripcion)} / {MAX_DESCRIPCION} caracteres
          </span>
          {errores.descripcion && <span className="campo-error">{errores.descripcion}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Categoría *</span>
          <select
            className={`input${errores.categoriaId ? ' input-error' : ''}`}
            value={form.categoriaId}
            onChange={cambiar('categoriaId')}
            disabled={cargandoCategorias || Boolean(errorCategorias)}
          >
            <option value="">{cargandoCategorias ? 'Cargando categorías...' : 'Selecciona una categoría'}</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id} disabled={c.estado === false}>
                {c.nombre}{c.estado === false ? ' (desactivada)' : ''}
              </option>
            ))}
          </select>
          {errores.categoriaId && <span className="campo-error">{errores.categoriaId}</span>}
        </label>
        <MensajeError mensaje={errorCategorias} onReintentar={reintentarCategorias} />

        <label className="campo">
          <span className="campo-label">Ubicación *</span>
          <input
            type="text"
            className={`input${errores.ubicacion ? ' input-error' : ''}`}
            value={form.ubicacion}
            onChange={cambiar('ubicacion')}
            placeholder="Ej. Biblioteca central"
            maxLength={255}
          />
          {errores.ubicacion && <span className="campo-error">{errores.ubicacion}</span>}
        </label>

        <label className="campo">
          <span className="campo-label">Fecha *</span>
          <input
            type="date"
            className={`input${errores.fechaObjeto ? ' input-error' : ''}`}
            value={form.fechaObjeto}
            max={hoyISO()}
            onChange={cambiar('fechaObjeto')}
          />
          {errores.fechaObjeto && <span className="campo-error">{errores.fechaObjeto}</span>}
        </label>

        <div className="campo">
          <button
            type="button"
            className="btn btn-secundario"
            onClick={usarMiUbicacion}
            disabled={ubicando || enviando}
          >
            <Icono nombre="ubicacion" tamano={16} />
            {ubicando ? 'Ubicando...' : 'Usar mi ubicación'}
          </button>
          {coordenadas.latitud != null && (
            <span className="nota-suave">
              Ubicación obtenida: {coordenadas.latitud}, {coordenadas.longitud}
            </span>
          )}
          {avisoUbicacion && <span className="campo-error">{avisoUbicacion}</span>}
        </div>

        <div className="proximamente">
          <Icono nombre="camara" tamano={20} />
          <span>Foto — próximamente</span>
        </div>

        <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || ubicando}>
          {enviando ? 'Publicando...' : 'PUBLICAR OBJETO'}
        </button>
      </form>
    </div>
  );
}
