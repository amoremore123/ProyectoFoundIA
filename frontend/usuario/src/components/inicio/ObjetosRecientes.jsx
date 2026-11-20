import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listarObjetos, mensajeError } from '../../services/api';
import Icono from '../Icono';
import ImagenObjeto from '../ImagenObjeto';
import MensajeError from '../MensajeError';
import CabeceraSeccion from './CabeceraSeccion';

const CANTIDAD = 4;

// Más reciente primero: por fecha de publicación y, si falta, por id.
function porReciente(a, b) {
  const fa = Date.parse(a.fechaPublicacion || '') || 0;
  const fb = Date.parse(b.fechaPublicacion || '') || 0;
  return fb - fa || (b.id ?? 0) - (a.id ?? 0);
}

// Objetos encontrados y activos publicados por la comunidad (GET /api/objetos).
export default function ObjetosRecientes() {
  const [objetos, setObjetos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError('');
    listarObjetos({ tipo: 'ENCONTRADO', estado: 'ACTIVO' })
      .then((data) => {
        const lista = Array.isArray(data) ? data : data?.results || [];
        if (vigente) setObjetos([...lista].sort(porReciente).slice(0, CANTIDAD));
      })
      .catch((e) => { if (vigente) setError(mensajeError(e, 'No pudimos cargar los objetos recientes.')); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [intento]);

  return (
    <section className="ini-seccion ini-recientes" aria-labelledby="recientes-titulo" aria-busy={cargando}>
      <CabeceraSeccion
        id="recientes-titulo"
        eyebrow="Objetos recientes"
        titulo="Lo último que encontró la comunidad"
        texto="Explora los objetos publicados recientemente en Tecsup."
      />
      <div className="ini-recientes-cuerpo">
        <Link to="/buscar?tipo=ENCONTRADO" className="ini-ver-todos">Ver todos <Icono nombre="flechaDer" tamano={14} /></Link>
        {cargando ? (
          <ul className="ini-recientes-lista" aria-label="Cargando objetos recientes">
            {Array.from({ length: CANTIDAD }, (_, i) => <li key={i} className="ini-tarjeta ini-objeto ini-esqueleto" aria-hidden="true"><span /><span /><span /></li>)}
          </ul>
        ) : error ? (
          <MensajeError mensaje={error} onReintentar={() => setIntento((n) => n + 1)} />
        ) : objetos.length === 0 ? (
          <div className="ini-tarjeta ini-vacio">
            <Icono nombre="bandeja" tamano={24} />
            <p>Todavía no hay objetos encontrados publicados.</p>
            <Link to="/publicar" className="btn btn-secundario">Publicar un objeto</Link>
          </div>
        ) : (
          <ul className="ini-recientes-lista">
            {objetos.map((objeto) => (
              <li key={objeto.id}>
                <Link to={`/objeto/${objeto.id}`} className="ini-tarjeta ini-objeto">
                  <div className="ini-objeto-foto"><ImagenObjeto objeto={objeto} /></div>
                  <div className="ini-objeto-texto">
                    <h3>{objeto.nombre}</h3>
                    <p><Icono nombre="ubicacion" tamano={14} /> <span>{objeto.ubicacion || 'Sin ubicación'}</span></p>
                  </div>
                  <span className="ini-objeto-flecha" aria-hidden="true"><Icono nombre="flechaDer" tamano={16} /></span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
