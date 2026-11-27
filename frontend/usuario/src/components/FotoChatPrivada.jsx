import { useEffect, useState } from 'react';
import { getFotoChat } from '../services/api';

// H62: las fotos del chat son privadas. Se piden con la sesión y se muestran como blob;
// un <img src> directo ya no funciona porque la API responde 401 sin el token.
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

  if (estado === 'cargando') return <span className="conv-foto" role="status">Cargando foto…</span>;
  if (estado === 'error') return <span className="conv-foto" role="alert">No se pudo cargar la foto.</span>;
  return (
    <a href={imagen} target="_blank" rel="noopener noreferrer" className="conv-foto">
      <img src={imagen} alt={alt} />
    </a>
  );
}
