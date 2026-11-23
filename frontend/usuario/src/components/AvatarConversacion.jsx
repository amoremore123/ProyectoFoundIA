import { useEffect, useState } from 'react';
import Icono from './Icono';
import { getFotoConversacion } from '../services/api';

// H64: foto de la otra persona en el chat (solo si la tiene y no hay bloqueo); si no, sus iniciales.
// Con la biblioteca nunca hay foto: se muestra el ícono de la biblioteca.
export default function AvatarConversacion({ conversacion, className = '' }) {
  const [foto, setFoto] = useState('');
  const conBiblioteca = conversacion?.tipo === 'CON_BIBLIOTECA' && conversacion?.otro?.id == null;
  const pedir = Boolean(conversacion?.otro?.tieneFoto) && !conBiblioteca && !conversacion?.bloqueada;

  useEffect(() => {
    if (!pedir) { setFoto(''); return undefined; }
    let vivo = true;
    let url = '';
    getFotoConversacion(conversacion.id)
      .then((blob) => { if (vivo && blob) { url = URL.createObjectURL(blob); setFoto(url); } })
      .catch(() => {});
    return () => { vivo = false; if (url) URL.revokeObjectURL(url); };
  }, [pedir, conversacion?.id]);

  const nombre = conversacion?.otro?.nombre || '?';
  return (
    <span className={`conv-avatar ${className}`} aria-hidden="true">
      {foto ? <img src={foto} alt="" /> : conBiblioteca ? <Icono nombre="caja" tamano={18} /> : nombre.charAt(0).toUpperCase()}
    </span>
  );
}
