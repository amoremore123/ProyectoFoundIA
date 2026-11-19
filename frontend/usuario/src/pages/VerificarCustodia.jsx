import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import Icono from '../components/Icono';
import { fotoUrl, verificarCustodia } from '../services/api';
import { useEmbebido } from '../utils/embebido';

// H41: página que abre el QR de la etiqueta de custodia. Cualquiera que lo escanee ve qué objeto es
// y si sigue en la biblioteca; nunca quién lo dejó ni quién lo reclamó.
const ESTADOS = {
  EN_CUSTODIA: {
    titulo: 'Objeto en custodia en la biblioteca',
    texto: 'Este objeto está guardado en la biblioteca de Tecsup esperando a su dueño. Si es tuyo, ábrelo en FoundIA y reclámalo respondiendo una pregunta de verificación.',
    tono: 'aceptado',
  },
  RETIRADO: {
    titulo: 'Objeto ya devuelto',
    texto: 'Este objeto ya fue retirado por su dueño en la biblioteca.',
    tono: 'pendiente',
  },
  DISPUESTO: {
    titulo: 'Custodia cerrada',
    texto: 'El plazo de custodia de este objeto terminó y la biblioteca le dio su destino final.',
    tono: 'pendiente',
  },
};

function fechaRecepcion(valor) {
  if (!valor) return '';
  const fecha = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(valor) ? valor : `${valor}Z`);
  if (Number.isNaN(fecha.getTime())) return '';
  return fecha.toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
}

export default function VerificarCustodia() {
  const [params] = useSearchParams();
  const t = params.get('t') || '';
  // H73: dentro de la app no hay sesión web; reclamar se hace desde la propia app
  const embebido = useEmbebido();
  const [estado, setEstado] = useState(t ? 'cargando' : 'invalida');
  const [custodia, setCustodia] = useState(null);

  useEffect(() => {
    if (!t) return undefined;
    let vigente = true;
    verificarCustodia(t)
      .then((data) => { if (vigente) { setCustodia(data); setEstado('lista'); } })
      .catch((e) => { if (vigente) setEstado(e?.response?.status === 404 ? 'invalida' : 'error'); });
    return () => { vigente = false; };
  }, [t]);

  if (estado === 'cargando') {
    return <div className="pagina"><p className="nota-suave" role="status">Verificando la etiqueta…</p></div>;
  }
  if (estado !== 'lista') {
    return (
      <div className="pagina">
        <section className="tarjeta verificar-custodia" role="alert">
          <h1 className="detalle-titulo">{estado === 'invalida' ? 'Etiqueta no válida' : 'No se pudo verificar'}</h1>
          <p className="detalle-descripcion">
            {estado === 'invalida'
              ? 'Este QR no corresponde a una etiqueta vigente de FoundIA. Consulta en la biblioteca.'
              : 'Revisa tu conexión e inténtalo de nuevo.'}
          </p>
          {!embebido && <Link className="btn btn-secundario" to="/explorar">Ver objetos encontrados</Link>}
        </section>
      </div>
    );
  }

  const info = ESTADOS[custodia.estado] || ESTADOS.EN_CUSTODIA;
  const { objeto } = custodia;
  const foto = fotoUrl(objeto?.fotoUrl);
  const recibido = fechaRecepcion(custodia.fechaRecepcion);
  return (
    <div className="pagina">
      <section className="tarjeta verificar-custodia" aria-labelledby="custodia-titulo">
        <p className="verificar-custodia-codigo"><Icono nombre="caja" tamano={16} /> Etiqueta {custodia.codigo}</p>
        <div className={`estado-reclamo estado-reclamo-${info.tono}`}>
          <h1 id="custodia-titulo" className="estado-reclamo-titulo">{info.titulo}</h1>
          <p className="estado-reclamo-texto">{info.texto}</p>
        </div>
        <div className="verificar-custodia-objeto">
          {foto && <img src={foto} alt={`Foto de ${objeto.nombre}`} className="verificar-custodia-foto" />}
          <div>
            <h2 className="detalle-titulo">{objeto.nombre}</h2>
            {objeto.categoria && <p className="nota-suave">{objeto.categoria}</p>}
            {recibido && <p className="nota-suave">Recibido en la biblioteca el {recibido}</p>}
          </div>
        </div>
        {custodia.estado === 'EN_CUSTODIA' && (embebido ? (
          <p className="nota-suave">Si es tuyo, búscalo en la app de FoundIA y reclámalo desde su detalle.</p>
        ) : (
          <Link className="btn btn-primario" to={`/objeto/${objeto.id}`}>Ver el objeto y reclamarlo</Link>
        ))}
      </section>
    </div>
  );
}
