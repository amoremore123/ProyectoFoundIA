import { useState } from 'react';
import Icono from './Icono';
import { crearInvitacion, mensajeError } from '../services/api';

// H40: invitar a un amigo. El enlace lo emite el servidor y no lleva nombre, correo ni sesión.
// Copiar o compartir solo se anuncia cuando de verdad ocurrió (cancelar no cuenta como enviado).
export default function InvitarAmigo() {
  const [url, setUrl] = useState('');
  const [creando, setCreando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const puedeCompartir = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  const crear = async () => {
    setCreando(true);
    setError('');
    setAviso('');
    try {
      const data = await crearInvitacion();
      if (!data?.url) throw new Error('Respuesta sin enlace');
      setUrl(data.url);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo crear la invitación. Inténtalo de nuevo.'));
    } finally {
      setCreando(false);
    }
  };

  const copiar = async () => {
    setAviso('');
    try {
      await navigator.clipboard.writeText(url);
      setAviso('Enlace copiado. Pégalo en un mensaje para tu amigo.');
    } catch {
      setAviso('No se pudo copiar automáticamente: selecciona el enlace y cópialo.');
    }
  };

  const compartir = async () => {
    setAviso('');
    try {
      await navigator.share({ title: 'FoundIA Tecsup', text: 'Únete a FoundIA para encontrar objetos perdidos en Tecsup.', url });
      setAviso('Invitación compartida.');
    } catch (e) {
      // Cancelar el selector no es un error ni un envío: no se anuncia nada
      if (e?.name !== 'AbortError') setAviso('No se pudo abrir el menú para compartir. Copia el enlace.');
    }
  };

  return (
    <section className="tarjeta invitar-amigo" aria-labelledby="invitar-titulo">
      <h2 id="invitar-titulo" className="invitar-titulo"><Icono nombre="usuario" tamano={18} /> Invitar a un amigo</h2>
      <p className="nota-suave">
        Comparte FoundIA con tus compañeros de Tecsup. El enlace no incluye tu nombre, tu correo ni tu sesión.
      </p>
      {error && <p className="campo-error" role="alert">{error}</p>}
      {!url ? (
        <button type="button" className="btn btn-secundario" onClick={crear} disabled={creando}>
          {creando ? 'Creando enlace…' : 'Crear enlace de invitación'}
        </button>
      ) : (
        <>
          <label className="campo">
            <span className="campo-label">Tu enlace de invitación</span>
            <input className="input" value={url} readOnly onFocus={(e) => e.target.select()} />
          </label>
          <div className="invitar-acciones">
            <button type="button" className="btn btn-primario" onClick={copiar}>Copiar enlace</button>
            {puedeCompartir && <button type="button" className="btn btn-secundario" onClick={compartir}>Compartir</button>}
          </div>
        </>
      )}
      {aviso && <p className="nota-suave" role="status">{aviso}</p>}
    </section>
  );
}
