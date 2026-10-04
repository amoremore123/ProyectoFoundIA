import { Link } from 'react-router-dom';
import ChipTipo from './ChipTipo';
import { fotoUrl } from '../services/api';

function fechaCorta(valor) {
  if (!valor) return '';
  const parte = String(valor).split('T')[0];
  const [anio, mes, dia] = parte.split('-');
  if (!anio || !mes || !dia) return parte;
  return `${dia}/${mes}`;
}

export default function ObjetoCard({ objeto }) {
  const foto = objeto?.fotos?.length ? fotoUrl(objeto.fotos[0]) : '';

  return (
    <Link to={`/objeto/${objeto.id}`} className="objeto-card">
      <div className="objeto-card-media">
        {foto ? (
          <img src={foto} alt={objeto.nombre} loading="lazy" />
        ) : (
          <div className="objeto-placeholder">
            <span aria-hidden="true">📦</span>
            <span>Sin foto</span>
          </div>
        )}
        <div className="objeto-card-chip">
          <ChipTipo tipo={objeto.tipo} />
        </div>
      </div>
      <div className="objeto-card-cuerpo">
        <h3 className="objeto-card-titulo">{objeto.nombre}</h3>
        <p className="objeto-card-linea">📍 {objeto.ubicacion || 'Sin ubicación'}</p>
        <p className="objeto-card-linea">📅 {fechaCorta(objeto.fechaObjeto)}</p>
      </div>
    </Link>
  );
}
