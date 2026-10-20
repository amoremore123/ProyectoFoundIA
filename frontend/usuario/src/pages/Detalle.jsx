import ImagenObjeto from '../components/ImagenObjeto';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import ChipTipo from '../components/ChipTipo';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { getCoincidencias, getObjeto, iniciarContacto, mensajeError, reclamarObjeto, statusDe } from '../services/api';
import { useAuth } from '../context/AuthContext';

function fechaLarga(valor) {
  if (!valor) return '—';
  const soloFecha = String(valor).split('T')[0];
  const [anio, mes, dia] = soloFecha.split('-');
  if (!anio || !mes || !dia) return soloFecha;
  return `${dia}/${mes}/${anio}`;
}

export default function Detalle() {
  const { id } = useParams();
  const sesion = useAuth() || {};
  const { user, token } = sesion;
  const [indiceFoto, setIndiceFoto] = useState(0);
  const [objeto, setObjeto] = useState(null);
  const [coincidencias, setCoincidencias] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [cargandoCoincidencias, setCargandoCoincidencias] = useState(true);
  const [error, setError] = useState('');
  const [noEncontrado, setNoEncontrado] = useState(false);
  const [errorCoincidencias, setErrorCoincidencias] = useState('');
  const [reintentosCoincidencias, setReintentosCoincidencias] = useState(0);
  const [contactos, setContactos] = useState({});
  const [erroresContacto, setErroresContacto] = useState({});
  const [contactandoId, setContactandoId] = useState(null);
  const [reclamo, setReclamo] = useState('');
  const [reclamoError, setReclamoError] = useState('');
  const [reclamando, setReclamando] = useState(false);
  const [confirmarReclamo, setConfirmarReclamo] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    setNoEncontrado(false);
    try {
      const data = await getObjeto(id);
      setObjeto(data);
    } catch (e) {
      if (statusDe(e) === 404) {
        setNoEncontrado(true);
      } else {
        setError(mensajeError(e, 'No se pudo cargar el objeto'));
      }
    } finally {
      setCargando(false);
    }
  }, [id]);

  // H18: el error se muestra en la tarjeta de esa coincidencia y el botón queda para reintentar.
  const contactar = async (coincidencia) => {
    setErroresContacto((e) => ({ ...e, [coincidencia.objetoId]: '' }));
    setContactandoId(coincidencia.objetoId);
    try {
      const respuesta = await iniciarContacto(objeto.id, coincidencia.objetoId);
      setContactos((anteriores) => ({
        ...anteriores,
        [coincidencia.objetoId]: respuesta.repetido ? 'repetido' : 'enviado',
      }));
    } catch (errorContacto) {
      setErroresContacto((e) => ({
        ...e,
        [coincidencia.objetoId]: mensajeError(errorContacto, 'No se pudo enviar la solicitud de contacto'),
      }));
    } finally {
      setContactandoId(null);
    }
  };

  // H20: el reclamo avisa a otra persona, por eso se confirma antes de enviarlo.
  const reclamar = async () => {
    setReclamoError('');
    setReclamando(true);
    try {
      const respuesta = await reclamarObjeto(objeto.id);
      setReclamo(respuesta.repetido ? 'repetido' : 'enviado');
    } catch (errorReclamo) {
      setReclamoError(mensajeError(errorReclamo, 'No se pudo enviar el reclamo'));
    } finally {
      setReclamando(false);
      setConfirmarReclamo(false);
    }
  };

  useEffect(() => {
    setIndiceFoto(0);
    cargar();
  }, [cargar]);

  useEffect(() => {
    let vivo = true;
    setCargandoCoincidencias(true);
    setErrorCoincidencias('');
    (async () => {
      try {
        const data = await getCoincidencias(id);
        if (vivo) setCoincidencias(Array.isArray(data) ? data : data?.results || []);
      } catch (e) {
        // Un fallo no es lo mismo que "no hay coincidencias"
        if (vivo) {
          setCoincidencias([]);
          setErrorCoincidencias(mensajeError(e, 'No se pudieron cargar las coincidencias'));
        }
      } finally {
        if (vivo) setCargandoCoincidencias(false);
      }
    })();
    return () => {
      vivo = false;
    };
  }, [id, reintentosCoincidencias]);

  if (cargando) return <Cargando />;

  if (noEncontrado) {
    return (
      <div className="pagina">
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            <Icono nombre="buscar" tamano={28} />
          </span>
          <p>Objeto no encontrado</p>
        </div>
      </div>
    );
  }

  if (error || !objeto) {
    return (
      <div className="pagina">
        <MensajeError mensaje={error || 'No se pudo cargar el objeto'} onReintentar={cargar} />
      </div>
    );
  }

  const autor = objeto.publicadoPor
    ? `${objeto.publicadoPor.nombre} ${objeto.publicadoPor.apellido || ''}`.trim()
    : '—';
  const puedeContactar = Boolean(token && user && objeto.tipo === 'PERDIDO'
    && String(user.id) === String(objeto.publicadoPor?.id));
  const esReclamable = objeto.tipo === 'ENCONTRADO' && objeto.estado === 'ACTIVO';
  const puedeReclamar = Boolean(token && user && esReclamable && String(user.id) !== String(objeto.publicadoPor?.id));
  const reclamoSinSesion = esReclamable && !token;

  return (
    <div className="pagina">
      <div className="tarjeta detalle-card">
        <div className="detalle-media">
          <div className="detalle-foto-principal"><ImagenObjeto objeto={objeto} indice={indiceFoto} /></div>
          {objeto.fotos?.length > 1 && <div className="galeria-controles" aria-label="Fotos del objeto">
            {objeto.fotos.map((_, indice) => <button key={indice} type="button" aria-label={`Ver foto ${indice + 1}`} aria-pressed={indiceFoto === indice} onClick={() => setIndiceFoto(indice)}><ImagenObjeto objeto={objeto} indice={indice} /></button>)}
            <span aria-live="polite">Foto {indiceFoto + 1} de {objeto.fotos.length}</span>
          </div>}
        </div>

        <div className="detalle-cuerpo">
          <ChipTipo tipo={objeto.tipo} />
          <h1 className="detalle-titulo">{objeto.nombre}</h1>
          <p className="detalle-descripcion">{objeto.descripcion || 'Sin descripción.'}</p>

          <div className="datos-grid">
            <div className="dato">
              <span className="dato-label"><Icono nombre="ubicacion" tamano={14} /> Ubicación</span>
              <span className="dato-valor">{objeto.ubicacion || '—'}</span>
            </div>
            <div className="dato">
              <span className="dato-label"><Icono nombre="calendario" tamano={14} /> Fecha</span>
              <span className="dato-valor">{fechaLarga(objeto.fechaObjeto)}</span>
            </div>
            <div className="dato">
              <span className="dato-label"><Icono nombre="etiqueta" tamano={14} /> Categoría</span>
              <span className="dato-valor">{objeto.categoria?.nombre || '—'}</span>
            </div>
            <div className="dato">
              <span className="dato-label"><Icono nombre="usuario" tamano={14} /> Publicado por</span>
              <span className="dato-valor">{autor}</span>
            </div>
          </div>

          {puedeReclamar && (reclamo ? (
            <p role="status" className="aviso-accion aviso-ok">
              {reclamo === 'repetido'
                ? 'Ya habías enviado este reclamo. Quien lo publicó ya fue avisado.'
                : 'Reclamo enviado. Quien lo publicó recibió un aviso para coordinar la entrega contigo.'}
            </p>
          ) : (
            <div className="reclamo-objeto">
              <p className="nota-suave">
                ¿Es tuyo? Al reclamarlo, avisamos a quien lo encontró para que coordine la entrega contigo.
                Tu correo no se comparte.
              </p>
              {reclamoError && <MensajeError mensaje={reclamoError} />}
              <button
                type="button"
                className="btn btn-secundario btn-bloque"
                disabled={reclamando}
                onClick={() => { setReclamoError(''); setConfirmarReclamo(true); }}
              >
                Reclamar este objeto
              </button>
            </div>
          ))}

          {reclamoSinSesion && (
            <p className="nota-suave">
              ¿Es tuyo? <Link to="/login">Inicia sesión para reclamar este objeto</Link> y avisaremos a quien lo encontró.
            </p>
          )}

        </div>
      </div>

      <section className="seccion">
        <h2>Coincidencias sugeridas</h2>
        {puedeContactar && (
          <p className="nota-suave">
            Si una coincidencia parece tu objeto, pulsa <strong>Contactar</strong>: quien lo encontró recibirá un aviso
            de solicitud de contacto. Tu correo no se comparte.
          </p>
        )}

        {cargandoCoincidencias ? (
          <Cargando texto="Buscando coincidencias..." />
        ) : errorCoincidencias ? (
          <MensajeError
            mensaje={errorCoincidencias}
            onReintentar={() => setReintentosCoincidencias((n) => n + 1)}
          />
        ) : coincidencias.length === 0 ? (
          <div className="estado-vacio estado-vacio-sutil">
            <p>No hay coincidencias sugeridas por ahora.</p>
          </div>
        ) : (
          <ul className="lista-coincidencias">
            {coincidencias.map((c) => (
              <li key={c.objetoId} className="tarjeta coincidencia-card">
                <div className="coincidencia-top">
                  <ChipTipo tipo={c.tipo} />
                  <span
                    className={`chip-nivel ${c.nivel === 'ALTA' ? 'nivel-alta' : 'nivel-media'}`}
                  >
                    {c.nivel}
                  </span>
                </div>
                <h3 className="coincidencia-nombre">{c.nombre}</h3>
                <p className="objeto-card-linea"><Icono nombre="ubicacion" tamano={14} /> {c.ubicacion || 'Sin ubicación'}</p>
                <p className="objeto-card-linea"><Icono nombre="calendario" tamano={14} /> {fechaLarga(c.fechaObjeto)}</p>
                <div className="coincidencia-barra">
                  <div
                    className="coincidencia-barra-llenado"
                    style={{ width: `${Math.max(0, Math.min(100, Number(c.porcentaje) || 0))}%` }}
                  />
                </div>
                <span className="coincidencia-porcentaje">{Number(c.porcentaje) || 0}% de coincidencia</span>
                {puedeContactar && (
                  <div className="coincidencia-contacto">
                    {contactos[c.objetoId] ? (
                      <p role="status" className="aviso-accion aviso-ok">
                        {contactos[c.objetoId] === 'repetido'
                          ? 'Ya habías enviado una solicitud de contacto.'
                          : 'Solicitud de contacto enviada.'}
                      </p>
                    ) : (
                      <>
                        {erroresContacto[c.objetoId] && (
                          <p role="alert" className="campo-error">{erroresContacto[c.objetoId]}</p>
                        )}
                        <button
                          type="button"
                          className="btn btn-primario"
                          disabled={contactandoId === c.objetoId}
                          onClick={() => contactar(c)}
                        >
                          {contactandoId === c.objetoId
                            ? 'Enviando...'
                            : erroresContacto[c.objetoId] ? 'Reintentar contacto' : 'Contactar'}
                        </button>
                      </>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <ConfirmarDialogo
        abierto={confirmarReclamo}
        titulo="¿Reclamar este objeto?"
        mensaje={`Avisaremos a quien publicó "${objeto.nombre}" que crees que es tuyo, para que coordine la entrega contigo. Tu correo no se comparte.`}
        textoConfirmar="Sí, reclamar"
        procesando={reclamando}
        onConfirmar={reclamar}
        onCancelar={() => setConfirmarReclamo(false)}
      />
    </div>
  );
}
