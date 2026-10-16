import { useState } from 'react';
import { fotoUrl } from '../services/api';

const fotografias = [
  [/mochila|bolso|maleta/, 'mochila', 'Mochila de tela sobre una banca'],
  [/celular|telefono|smartphone|samsung|iphone/, 'celular', 'Teléfono móvil sobre una superficie clara'],
  [/llave|llavero/, 'llaves', 'Mano sosteniendo un juego de llaves'],
  [/lente|gafa|anteojo/, 'lentes', 'Anteojos de montura oscura'],
  [/audifono|auricular|headphone|airpod/, 'audifonos', 'Audífonos sobre un escritorio'],
  [/billetera|monedero|tarjetero/, 'documentos', 'Billetera de cuero con tarjetas'],
];

export function FotoReferencial({ nombre = '', categoria = '' }) {
  const texto = `${nombre} ${categoria}`.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const fotografia = fotografias.find(([patron]) => patron.test(texto));
  const [fallida, setFallida] = useState(null);
  if (!fotografia || fallida === fotografia[1]) return <div className="foto-pendiente"><span>Sin foto</span><small>Consulta la descripción del objeto</small></div>;
  return <div className={`foto-referencial foto-${fotografia[1]}`}><img src={`/imagenes/${fotografia[1]}-photo.jpg`} alt={`Foto referencial: ${fotografia[2]}`} width="800" height="800" loading="lazy" onError={() => setFallida(fotografia[1])} /></div>;
}

export default function ImagenObjeto({ objeto, indice = 0 }) {
  const src = fotoUrl(objeto.fotos?.[indice]);
  const [fallida, setFallida] = useState(null);
  return src && fallida !== src
    ? <img src={src} alt={objeto.nombre} loading="lazy" onError={() => setFallida(src)} />
    : <div className="imagen-referencial"><FotoReferencial nombre={objeto.nombre} categoria={objeto.categoria?.nombre} /><span className="imagen-referencial-etiqueta"><strong>Sin foto</strong> · imagen de referencia</span></div>;
}
