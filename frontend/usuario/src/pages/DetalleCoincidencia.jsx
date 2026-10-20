import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDetalleCoincidencia, iniciarContacto, mensajeError, statusDe } from '../services/api';
import ImagenObjeto from '../components/ImagenObjeto';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';

function ObjetoComparado({ objeto, titulo }) {
  const [indice, setIndice] = useState(0);
  return <article className="tarjeta comparacion-objeto" aria-label={titulo}>
    <p className="campo-label">{titulo}</p>
    <h2>{objeto.nombre}</h2>
    <div className="comparacion-foto"><ImagenObjeto objeto={objeto} indice={indice} /></div>
    {objeto.fotos?.length > 1 && <div className="galeria-controles" aria-label={`Fotos: ${titulo}`}>
      {objeto.fotos.map((foto, i) => <button className="btn btn-secundario" type="button" key={foto.id || i} aria-pressed={indice === i} onClick={() => setIndice(i)}>Foto {i + 1}</button>)}
    </div>}
    <p>{objeto.descripcion || 'Sin descripción.'}</p>
    <dl>
      <dt>Ubicación</dt><dd>{objeto.ubicacion || 'Sin ubicación'}</dd>
      <dt>Fecha del objeto</dt><dd>{objeto.fechaObjeto ? objeto.fechaObjeto.split('-').reverse().join('/') : 'Sin fecha'}</dd>
      <dt>Categoría</dt><dd>{objeto.categoria?.nombre || 'Sin categoría'}</dd>
      <dt>Publicado por</dt><dd>{objeto.publicadoPor?.nombre || 'Usuario'}</dd>
    </dl>
    <Link to={`/objeto/${objeto.id}`}>Ver publicación completa</Link>
  </article>;
}

export default function DetalleCoincidencia() {
  const { id } = useParams();
  const { user, token } = useAuth();
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [noDisponible, setNoDisponible] = useState(false);
  const [intento, setIntento] = useState(0);
  const [enviando, setEnviando] = useState(false);
  const [errorContacto, setErrorContacto] = useState('');
  const [resultado, setResultado] = useState('');

  useEffect(() => {
    const controlador = new AbortController();
    setCargando(true); setDatos(null); setError(''); setNoDisponible(false);
    getDetalleCoincidencia(id, { signal: controlador.signal }).then((respuesta) => {
      if (!controlador.signal.aborted) setDatos(respuesta);
    }).catch((fallo) => {
      if (controlador.signal.aborted) return;
      if (statusDe(fallo) === 404) setNoDisponible(true);
      else setError(mensajeError(fallo, 'No se pudo cargar la coincidencia'));
    }).finally(() => { if (!controlador.signal.aborted) setCargando(false); });
    return () => controlador.abort();
  }, [id, intento]);

  useEffect(() => { setResultado(''); setErrorContacto(''); }, [id, token, user?.id]);

  const contactar = async () => {
    setEnviando(true); setErrorContacto('');
    try {
      const respuesta = await iniciarContacto(datos.perdido.id, datos.encontrado.id);
      setResultado(respuesta.repetido ? 'Ya habías enviado esta solicitud de contacto.' : 'Solicitud enviada. Quien encontró el objeto recibió un aviso.');
    } catch (fallo) {
      setErrorContacto(mensajeError(fallo, 'No se pudo enviar la solicitud de contacto'));
    } finally { setEnviando(false); }
  };

  if (cargando) return <Cargando texto="Cargando comparación..." />;
  if (noDisponible) return <div className="pagina"><h1>Coincidencia no disponible</h1><p>La pareja no existe o alguna publicación ya no está disponible para comparar.</p><Link to="/buscar">Volver a buscar</Link></div>;
  if (error || !datos) return <div className="pagina"><MensajeError mensaje={error || 'No se pudo cargar la coincidencia'} onReintentar={() => setIntento((n) => n + 1)} /></div>;

  const puedeContactar = token && String(user?.id) === String(datos.perdido.publicadoPor?.id);
  return <div className="pagina">
    <Link to={`/objeto/${datos.perdido.id}`}>Volver a las coincidencias del objeto perdido</Link>
    <h1 className="pagina-titulo">Comparar posible coincidencia</h1>
    <p><strong>{datos.porcentaje}% de similitud · {datos.nivel}</strong></p>
    <p className="nota-suave">La similitud compara características de las publicaciones; no confirma quién es el dueño del objeto.</p>
    <ul className="comparacion-criterios" aria-label="Puntos por criterio">
      {Object.entries({ nombre: 'Nombre', categoria: 'Categoría', ubicacion: 'Ubicación', fecha: 'Fecha' }).map(([campo, etiqueta]) => <li key={campo}>{etiqueta}: {datos.criterios?.[campo] ?? 0} puntos</li>)}
    </ul>
    <div className="comparacion-objetos">
      <ObjetoComparado key={`perdido-${datos.perdido.id}`} objeto={datos.perdido} titulo="Objeto perdido" />
      <ObjetoComparado key={`encontrado-${datos.encontrado.id}`} objeto={datos.encontrado} titulo="Objeto encontrado" />
    </div>
    {puedeContactar && <section aria-label="Solicitar contacto">
      <p>Solicita contactar a quien lo encontró. Recibirá una notificación; tu correo permanece privado. Contactar no envía un reclamo.</p>
      {resultado ? <p role="status" className="aviso-accion aviso-ok">{resultado}</p> : <>
        <MensajeError mensaje={errorContacto} />
        <button className="btn btn-primario" type="button" disabled={enviando} onClick={contactar}>{enviando ? 'Enviando...' : errorContacto ? 'Reintentar contacto' : 'Contactar'}</button>
      </>}
    </section>}
    {!token && <p><Link to="/login">Inicia sesión</Link> para solicitar contacto si eres quien publicó el objeto perdido.</p>}
  </div>;
}
