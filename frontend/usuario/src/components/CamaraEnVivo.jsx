import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icono from './Icono';
import { LADO_MAXIMO } from '../utils/fotos';

// H52: tomar la foto dentro de la página. Abrir la app de cámara del celular hace que
// Android cierre la pestaña del navegador para liberar memoria y, al volver, la foto se
// pierde. Con la cámara en vivo el navegador nunca sale de la página.
// Requiere un contexto seguro (HTTPS o localhost); si no lo hay, Publicar usa la entrada
// de archivo con la cámara trasera, como antes.
export function camaraEnVivoDisponible() {
  return typeof window !== 'undefined'
    && window.isSecureContext === true
    && typeof navigator !== 'undefined'
    && typeof navigator.mediaDevices?.getUserMedia === 'function';
}

const MENSAJES = {
  NotAllowedError: 'No diste permiso para usar la cámara. Puedes permitirlo en la configuración del navegador o elegir una foto de tu galería.',
  NotFoundError: 'No encontramos una cámara en este dispositivo. Elige una foto de tu galería.',
  NotReadableError: 'Otra aplicación está usando la cámara. Ciérrala e inténtalo de nuevo, o elige una foto de tu galería.',
};

// titulo: encabezado del visor. onElegirArchivo: si llega, muestra «Galería» para elegir una foto guardada.
export default function CamaraEnVivo({ abierto, onCapturar, onCerrar, titulo = 'Tomar foto', onElegirArchivo }) {
  const video = useRef(null);
  const flujo = useRef(null);
  const caja = useRef(null);
  const botonCerrar = useRef(null);
  const [estado, setEstado] = useState('iniciando'); // iniciando | lista | procesando | capturada | error
  const [aviso, setAviso] = useState('');
  const [error, setError] = useState('');
  const [captura, setCaptura] = useState(null); // { archivo, url }

  const detener = () => {
    flujo.current?.getTracks().forEach((pista) => pista.stop());
    flujo.current = null;
  };

  useEffect(() => {
    if (!abierto) return undefined;
    let cancelado = false;
    const anterior = document.activeElement;
    setEstado('iniciando');
    setError('');
    setCaptura(null);
    botonCerrar.current?.focus();
    navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      audio: false,
    }).then((stream) => {
      if (cancelado) { stream.getTracks().forEach((p) => p.stop()); return; }
      flujo.current = stream;
      if (video.current) {
        video.current.srcObject = stream;
        video.current.play?.().catch(() => {});
      }
      setEstado('lista');
    }).catch((err) => {
      if (cancelado) return;
      setError(MENSAJES[err?.name] || 'No se pudo abrir la cámara. Elige una foto de tu galería.');
      setEstado('error');
    });
    return () => {
      cancelado = true;
      detener();
      anterior?.focus?.();
    };
  }, [abierto]);

  // Libera la vista previa de la captura al descartarla o al cerrar
  useEffect(() => () => { if (captura?.url) URL.revokeObjectURL(captura.url); }, [captura]);

  if (!abierto) return null;

  const tomar = () => {
    const v = video.current;
    setAviso('');
    if (!v || !v.videoWidth) {
      // Nunca se ignora un toque en silencio
      setAviso('La cámara todavía se está preparando. Espera un momento y vuelve a intentarlo.');
      return;
    }
    // Respuesta inmediata: preparar el JPEG puede tardar un segundo en el celular
    setEstado('procesando');
    const escala = Math.min(1, LADO_MAXIMO / Math.max(v.videoWidth, v.videoHeight));
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.round(v.videoWidth * escala);
    lienzo.height = Math.round(v.videoHeight * escala);
    lienzo.getContext('2d')?.drawImage(v, 0, 0, lienzo.width, lienzo.height);
    lienzo.toBlob((blob) => {
      if (!blob) { setAviso('No se pudo tomar la foto. Inténtalo de nuevo.'); setEstado('lista'); return; }
      const archivo = new File([blob], `foto-${Date.now()}.jpg`, { type: 'image/jpeg', lastModified: Date.now() });
      setCaptura({ archivo, url: URL.createObjectURL(blob) });
      setEstado('capturada');
    }, 'image/jpeg', 0.85);
  };

  const repetir = () => {
    setCaptura(null);
    setAviso('');
    setEstado('lista');
  };

  const usar = () => {
    if (!captura) return;
    detener();
    onCapturar(captura.archivo);
  };

  const teclas = (e) => {
    if (e.key === 'Escape') { e.stopPropagation(); onCerrar(); }
    if (e.key === 'Tab') {
      const botones = [...caja.current.querySelectorAll('button:not([disabled])')];
      if (!botones.length) return;
      const [primero, ultimo] = [botones[0], botones[botones.length - 1]];
      if (e.shiftKey && document.activeElement === primero) { e.preventDefault(); ultimo.focus(); }
      else if (!e.shiftKey && document.activeElement === ultimo) { e.preventDefault(); primero.focus(); }
    }
  };

  return createPortal(
    <div className="dialogo-fondo camara-fondo">
      <div ref={caja} className="camara-vivo" role="dialog" aria-modal="true" aria-labelledby="camara-titulo" onKeyDown={teclas}>
        <div className="camara-cabecera">
          <h2 id="camara-titulo">{estado === 'capturada' ? 'Revisa la foto' : titulo}</h2>
          <button ref={botonCerrar} type="button" className="camara-cerrar" onClick={onCerrar} aria-label="Cerrar la cámara">
            <Icono nombre="cerrar" tamano={20} />
          </button>
        </div>

        <div className="camara-visor">
          {/* El video sigue montado mientras se revisa la captura para poder repetir sin pedir permiso otra vez */}
          <video ref={video} className="camara-video" playsInline muted autoPlay hidden={estado === 'capturada'} aria-label="Vista de la cámara" />
          {captura && <img className="camara-captura" src={captura.url} alt="Foto tomada, lista para revisar" />}
          {estado === 'procesando' && <span className="camara-destello" aria-hidden="true" />}
          {estado === 'iniciando' && <p className="camara-estado" role="status">Abriendo la cámara…</p>}
          {estado === 'procesando' && <p className="camara-estado" role="status">Procesando la foto…</p>}
          {aviso && estado !== 'error' && <p className="camara-estado camara-error" role="alert">{aviso}</p>}
          {estado === 'error' && <p className="camara-estado camara-error" role="alert">{error}</p>}
        </div>

        <div className="camara-acciones">
          {estado === 'capturada' ? (
            <>
              <button type="button" className="btn btn-secundario" onClick={repetir}>Repetir</button>
              <button type="button" className="btn btn-primario" onClick={usar}>Usar esta foto</button>
            </>
          ) : estado === 'error' ? (
            <>
              {onElegirArchivo && <button type="button" className="btn btn-primario" onClick={() => { detener(); onElegirArchivo(); }}>Elegir de la galería</button>}
              <button type="button" className="btn btn-secundario" onClick={onCerrar}>{onElegirArchivo ? 'Cancelar' : 'Volver al formulario'}</button>
            </>
          ) : (
            <>
            {onElegirArchivo && (
              <button type="button" className="camara-galeria" onClick={() => { detener(); onElegirArchivo(); }} aria-label="Elegir de la galería">
                <Icono nombre="imagen" tamano={22} />
              </button>
            )}
            <button type="button" className="camara-disparador" onClick={tomar} disabled={estado !== 'lista'} aria-label={estado === 'procesando' ? 'Procesando la foto' : 'Capturar foto'}>
              <span aria-hidden="true" />
            </button>
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
