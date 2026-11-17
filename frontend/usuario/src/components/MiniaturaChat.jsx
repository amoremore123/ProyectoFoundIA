import { useState } from 'react';
import Icono from './Icono';
import { fotoReferencialDe } from './ImagenObjeto';
import { fotoUrl } from '../services/api';

// Miniatura de una publicación en el chat de FINDI: la foto real si existe; si no,
// la imagen de referencia de su tipo (marcada como tal); si tampoco hay, un ícono.
export default function MiniaturaChat({ objeto }) {
  const real = objeto?.fotos?.length ? fotoUrl(objeto.fotos[0]) : '';
  const referencia = fotoReferencialDe(objeto?.nombre, objeto?.categoria?.nombre || objeto?.categoria);
  const [fallo, setFallo] = useState('');
  const src = real && fallo !== real ? real : (referencia && fallo !== referencia.src ? referencia.src : '');
  return (
    <span className={`chat-resultado-media${src && src !== real ? ' es-referencia' : ''}`}>
      {src
        ? <img src={src} alt="" loading="lazy" onError={() => setFallo(src)} />
        : <Icono nombre="caja" tamano={18} />}
      {src && src !== real && <span className="chat-referencia-etiqueta">Referencia</span>}
    </span>
  );
}
