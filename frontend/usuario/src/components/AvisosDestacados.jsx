import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';
import { getAvisosDestacados } from '../services/api';

// H71: avisos que la biblioteca o el administrador quieren que todos vean (horarios, campañas).
// La API solo devuelve los vigentes hoy; no se guardan en caché para que uno vencido no siga apareciendo.
// Cerrar un aviso lo oculta solo en esta pestaña (sessionStorage), sin afectar a nadie más.
const CLAVE_OCULTOS = 'foundia-avisos-ocultos';

function leerOcultos() {
  try { return JSON.parse(sessionStorage.getItem(CLAVE_OCULTOS) || '[]'); } catch { return []; }
}

// Solo rutas internas de la web (empiezan con una sola «/»)
export function enlaceAviso(enlace) {
  return typeof enlace === 'string' && /^\/(?!\/)[^\s]*$/.test(enlace) ? enlace : null;
}

export default function AvisosDestacados() {
  const [avisos, setAvisos] = useState([]);
  const [ocultos, setOcultos] = useState(leerOcultos);

  useEffect(() => {
    let vivo = true;
    // Si falla, la página funciona igual: los avisos son un complemento
    getAvisosDestacados().then((lista) => { if (vivo) setAvisos(lista.slice(0, 5)); }).catch(() => {});
    return () => { vivo = false; };
  }, []);

  const visibles = avisos.filter((a) => !ocultos.includes(a.id));
  if (visibles.length === 0) return null;

  const cerrar = (id) => {
    const nuevos = [...ocultos, id];
    setOcultos(nuevos);
    try { sessionStorage.setItem(CLAVE_OCULTOS, JSON.stringify(nuevos)); } catch { /* sin almacenamiento: solo en memoria */ }
  };

  return (
    <section className="avisos-destacados" aria-label="Avisos destacados">
      {visibles.map((a) => {
        const enlace = enlaceAviso(a.enlace);
        return (
          <article key={a.id} className="aviso-destacado">
            <span className="aviso-destacado-icono" aria-hidden="true"><Icono nombre="megafono" tamano={18} /></span>
            <div className="aviso-destacado-cuerpo">
              <h2 className="aviso-destacado-titulo">{a.titulo}</h2>
              <p className="aviso-destacado-texto">{a.texto}</p>
              {enlace && <Link className="aviso-destacado-enlace" to={enlace}>Ver más</Link>}
            </div>
            <button type="button" className="aviso-destacado-cerrar" onClick={() => cerrar(a.id)} aria-label={`Ocultar el aviso «${a.titulo}»`}>
              <Icono nombre="cerrar" tamano={16} />
            </button>
          </article>
        );
      })}
    </section>
  );
}
