import { useState } from 'react';
import Icono from './Icono';
import { getObjeto, mensajeError, statusDe } from '../services/api';

const NO_DISPONIBLE = 'La publicación ya no está disponible para compartir.';

// Igual que en Android: solo se comparten objetos perdidos vigentes o ya recuperados
export function sePuedeCompartir(objeto) {
  return objeto?.tipo === 'PERDIDO' && ['ACTIVO', 'RECUPERADO'].includes(objeto?.estado);
}

export function enlacePublico(id) {
  return `${window.location.origin}/objeto/${encodeURIComponent(id)}`;
}

// H39 - Compartir la URL pública de una publicación.
// Antes de compartir se vuelve a consultar la publicación por si fue retirada.
// Solo se comparte el enlace: nada del perfil ni del contacto de quien publicó.
export default function CompartirPublicacion({ objeto }) {
  const [estado, setEstado] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [enlaceManual, setEnlaceManual] = useState('');

  if (!sePuedeCompartir(objeto)) return null;

  const compartir = async () => {
    setEstado('procesando');
    setMensaje('');
    setEnlaceManual('');

    let vigente;
    try {
      vigente = await getObjeto(objeto.id);
    } catch (err) {
      setEstado('error');
      setMensaje(statusDe(err) === 404 ? NO_DISPONIBLE : mensajeError(err, 'No se pudo comprobar la publicación'));
      return;
    }
    if (!sePuedeCompartir(vigente)) {
      setEstado('error');
      setMensaje(NO_DISPONIBLE);
      return;
    }

    const url = enlacePublico(vigente.id);

    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: 'Publicación de FoundIA', url });
        // El navegador no confirma que el mensaje se haya enviado, solo que se eligió una app
        setEstado('ok');
        setMensaje('Elegiste dónde compartir el enlace.');
      } catch (err) {
        if (err?.name === 'AbortError') {
          setEstado('');
          setMensaje('');
        } else {
          setEstado('error');
          setMensaje('No se pudo abrir el menú para compartir. Copia el enlace manualmente.');
          setEnlaceManual(url);
        }
      }
      return;
    }

    try {
      await navigator.clipboard.writeText(url);
      setEstado('ok');
      setMensaje('Enlace copiado. Pégalo donde quieras compartirlo.');
    } catch {
      setEstado('error');
      setMensaje('No se pudo copiar automáticamente. Copia el enlace manualmente.');
      setEnlaceManual(url);
    }
  };

  return (
    <div className="compartir-objeto">
      <button
        type="button"
        className="btn btn-secundario btn-bloque"
        disabled={estado === 'procesando'}
        onClick={compartir}
      >
        <Icono nombre="enviar" tamano={16} /> {estado === 'procesando' ? 'Comprobando...' : 'Compartir publicación'}
      </button>
      <p className="nota-suave">Se comparte solo el enlace público; tus datos de contacto no se incluyen.</p>

      {mensaje && estado === 'ok' && <p role="status" className="aviso-accion aviso-ok">{mensaje}</p>}
      {mensaje && estado === 'error' && <p role="alert" className="campo-error">{mensaje}</p>}
      {enlaceManual && (
        <label className="campo">
          <span className="campo-label">Enlace de la publicación</span>
          <input className="input" readOnly value={enlaceManual} onFocus={(e) => e.target.select()} />
        </label>
      )}
    </div>
  );
}
