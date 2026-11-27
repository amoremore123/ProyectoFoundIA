import { useEffect, useState } from 'react';
import { getFotoChat } from '../services/api';

// H62: las fotos del chat son privadas; el panel las pide con su token (Spring y Django lo comparten).
export default function FotoChatPrivada({ url, alt }) {
  const [imagen, setImagen] = useState('');
  const [estado, setEstado] = useState('cargando');

  useEffect(() => {
    let vigente = true;
    let objeto = '';
    setEstado('cargando');
    getFotoChat(url)
      .then((blob) => {
        if (!vigente) return;
        if (blob) { objeto = URL.createObjectURL(blob); setImagen(objeto); setEstado('lista'); } else setEstado('error');
      })
      .catch(() => { if (vigente) setEstado('error'); });
    return () => { vigente = false; if (objeto) URL.revokeObjectURL(objeto); };
  }, [url]);

  if (estado === 'cargando') return <span role="status">Cargando foto…</span>;
  if (estado === 'error') return <span role="alert">No se pudo cargar la foto.</span>;
  return <img src={imagen} alt={alt} />;
}
