import { Link } from 'react-router-dom';
import ChipTipo from './ChipTipo';
import Icono from './Icono';
import ImagenObjeto from './ImagenObjeto';

function fechaCorta(valor) {
  if (!valor) return '';
  const parte = String(valor).split('T')[0];
  const [anio, mes, dia] = parte.split('-');
  if (!anio || !mes || !dia) return parte;
  return `${dia}/${mes}`;
}

export default function ObjetoCard({ objeto, distancia = null }) {

  return (
    <Link to={`/objeto/${objeto.id}`} className="objeto-card">
      <div className="objeto-card-media">
        <ImagenObjeto objeto={objeto} />
        <div className="objeto-card-chip">
          <ChipTipo tipo={objeto.tipo} />
        </div>
      </div>
      <div className="objeto-card-cuerpo">
        <h3 className="objeto-card-titulo">{objeto.nombre}</h3>
        {distancia !== null && <p className="objeto-card-linea">A {distancia.toLocaleString('es-PE', { maximumFractionDigits: 2 })} km de tu ubicación</p>}
        {objeto.estado === 'RECUPERADO' && <span className="estado-recuperado">Recuperado · caso cerrado</span>}
        <p className="objeto-card-linea">
          <Icono nombre="ubicacion" tamano={14} /> {objeto.ubicacion || 'Sin ubicación'}
        </p>
        <p className="objeto-card-linea">
          <Icono nombre="calendario" tamano={14} /> {fechaCorta(objeto.fechaObjeto)}
        </p>
        <span className="objeto-card-ver">Ver publicación <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></span>
      </div>
    </Link>
  );
}
