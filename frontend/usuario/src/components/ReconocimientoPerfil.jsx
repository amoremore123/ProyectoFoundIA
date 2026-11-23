import { useEffect, useState } from 'react';
import Icono from './Icono';
import { getReconocimiento } from '../services/api';

// Reconocimiento a quien devuelve: cuántos objetos encontraste y entregaste en la biblioteca, y cuántos
// ya volvieron a su dueño. Solo cuenta lo que la biblioteca recibió en mano, así no se puede inflar.
// Es privado: solo lo ves tú (no hay ranking).
export default function ReconocimientoPerfil() {
  const [datos, setDatos] = useState(null);
  const [fallo, setFallo] = useState(false);

  useEffect(() => {
    let vivo = true;
    getReconocimiento().then((d) => { if (vivo) setDatos(d); }).catch(() => { if (vivo) setFallo(true); });
    return () => { vivo = false; };
  }, []);

  // Es un complemento del perfil: si falla, no se muestra nada
  if (fallo || !datos) return null;

  const { entregados, devueltos, nivel, siguiente } = datos;
  const total = siguiente ? entregados + siguiente.faltan : entregados;
  const progreso = total ? Math.round((entregados / total) * 100) : 0;

  return (
    <section className="tarjeta reconocimiento" aria-labelledby="reconocimiento-titulo">
      <h2 id="reconocimiento-titulo" className="reconocimiento-titulo"><Icono nombre="check" tamano={18} /> Tu aporte a la comunidad</h2>
      {nivel ? (
        <div className="reconocimiento-insignia">
          <span className="reconocimiento-medalla" aria-hidden="true"><Icono nombre="check" tamano={22} /></span>
          <div>
            <p className="reconocimiento-nivel">{nivel.nombre}</p>
            <p className="nota-suave">{nivel.descripcion}</p>
          </div>
        </div>
      ) : (
        <p className="nota-suave">
          Cuando encuentres algo, publícalo y entrégalo en la biblioteca: aquí verás cuántos objetos ayudaste a devolver.
        </p>
      )}
      <p className="reconocimiento-cifras">
        Entregaste <strong>{entregados}</strong> {entregados === 1 ? 'objeto' : 'objetos'} en la biblioteca
        {entregados > 0 && <> y <strong>{devueltos}</strong> ya {devueltos === 1 ? 'volvió' : 'volvieron'} a su dueño</>}.
      </p>
      {siguiente && (
        <div className="reconocimiento-progreso">
          <div className="reconocimiento-barra" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={entregados}
            aria-label={`Progreso hacia «${siguiente.nombre}»`}>
            <span style={{ width: `${progreso}%` }} />
          </div>
          <p className="nota-suave">
            {siguiente.faltan === 1 ? 'Te falta 1 entrega' : `Te faltan ${siguiente.faltan} entregas`} para «{siguiente.nombre}».
          </p>
        </div>
      )}
      <p className="nota-suave reconocimiento-privado">Solo tú ves esto. Solo cuentan los objetos que la biblioteca recibió.</p>
    </section>
  );
}
