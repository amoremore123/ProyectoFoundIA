import { useCallback, useEffect, useState } from 'react';
import QRCode from 'qrcode';
import Icono from './Icono';
import ConfirmarDialogo from './ConfirmarDialogo';
import { crearEtiqueta, desactivarEtiqueta, getEtiquetas, mensajeError } from '../services/api';

// Etiqueta QR preventiva: registro algo mío (calculadora, casaca, USB), descargo su QR y lo pego.
// Quien lo encuentre lo escanea, ve solo el nombre y la descripción, y me escribe por el chat.
export function urlEtiqueta(codigo) {
  return `${window.location.origin}/etiqueta/${codigo}`;
}

// Etiqueta lista para imprimir: el QR con el nombre y una instrucción corta
async function descargarEtiqueta(etiqueta) {
  const lienzo = document.createElement('canvas');
  lienzo.width = 600;
  lienzo.height = 760;
  const g = lienzo.getContext('2d');
  g.fillStyle = '#ffffff';
  g.fillRect(0, 0, 600, 760);
  g.strokeStyle = '#12355b';
  g.lineWidth = 6;
  g.strokeRect(12, 12, 576, 736);
  g.fillStyle = '#12355b';
  g.textAlign = 'center';
  g.font = 'bold 40px sans-serif';
  g.fillText('¿Me encontraste?', 300, 80);
  g.font = '26px sans-serif';
  g.fillText('Escanéame para avisar a mi dueño', 300, 122);
  const qr = document.createElement('canvas');
  await QRCode.toCanvas(qr, urlEtiqueta(etiqueta.codigo), { width: 440, margin: 1, errorCorrectionLevel: 'M' });
  g.drawImage(qr, 80, 150, 440, 440);
  g.font = 'bold 30px sans-serif';
  g.fillText(etiqueta.nombre.length > 30 ? `${etiqueta.nombre.slice(0, 29)}…` : etiqueta.nombre, 300, 640);
  g.font = '24px sans-serif';
  g.fillText('FoundIA · Tecsup', 300, 690);
  const enlace = document.createElement('a');
  enlace.href = lienzo.toDataURL('image/png');
  enlace.download = `etiqueta-${etiqueta.nombre.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'foundia'}.png`;
  enlace.click();
}

function TarjetaEtiqueta({ etiqueta, onDesactivar }) {
  const [imagen, setImagen] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let vivo = true;
    QRCode.toDataURL(urlEtiqueta(etiqueta.codigo), { width: 160, margin: 1, errorCorrectionLevel: 'M' })
      .then((url) => { if (vivo) setImagen(url); })
      .catch(() => { if (vivo) setImagen(''); });
    return () => { vivo = false; };
  }, [etiqueta.codigo]);

  const descargar = async () => {
    setError('');
    try { await descargarEtiqueta(etiqueta); } catch { setError('No se pudo generar la imagen en este navegador.'); }
  };

  return (
    <li className="etiqueta-item">
      {imagen ? <img src={imagen} alt={`Código QR de ${etiqueta.nombre}`} className="etiqueta-qr" /> : <span className="etiqueta-qr" aria-hidden="true" />}
      <div className="etiqueta-datos">
        <p className="etiqueta-nombre">{etiqueta.nombre}</p>
        {etiqueta.descripcion && <p className="nota-suave">{etiqueta.descripcion}</p>}
        <div className="etiqueta-acciones">
          <button type="button" className="btn btn-secundario btn-mini" onClick={descargar}>
            <Icono nombre="descargar" tamano={14} /> Descargar etiqueta
          </button>
          <button type="button" className="btn btn-fantasma btn-mini" onClick={() => onDesactivar(etiqueta)}>Desactivar</button>
        </div>
        {error && <p role="alert" className="campo-error">{error}</p>}
      </div>
    </li>
  );
}

export default function MisEtiquetas() {
  const [etiquetas, setEtiquetas] = useState(null);
  const [errorCarga, setErrorCarga] = useState('');
  const [nombre, setNombre] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [creando, setCreando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [porDesactivar, setPorDesactivar] = useState(null);
  const [desactivando, setDesactivando] = useState(false);

  const cargar = useCallback(async () => {
    setErrorCarga('');
    try { setEtiquetas(await getEtiquetas()); } catch (e) { setErrorCarga(mensajeError(e, 'No se pudieron cargar tus etiquetas')); setEtiquetas([]); }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  const crear = async (e) => {
    e.preventDefault();
    setError('');
    setAviso('');
    if (!nombre.trim()) { setError('Escribe qué cosa quieres etiquetar.'); return; }
    setCreando(true);
    try {
      const nueva = await crearEtiqueta({ nombre: nombre.trim(), descripcion: descripcion.trim() });
      setEtiquetas((lista) => [nueva, ...(lista || [])]);
      setNombre('');
      setDescripcion('');
      setAviso(`Etiqueta de «${nueva.nombre}» lista. Descárgala, imprímela y pégala.`);
    } catch (err) {
      setError(mensajeError(err, 'No se pudo crear la etiqueta'));
    } finally {
      setCreando(false);
    }
  };

  const desactivar = async () => {
    setDesactivando(true);
    try {
      await desactivarEtiqueta(porDesactivar.id);
      setEtiquetas((lista) => lista.filter((x) => x.id !== porDesactivar.id));
      setAviso(`Desactivaste la etiqueta de «${porDesactivar.nombre}». Su QR ya no funciona.`);
      setPorDesactivar(null);
    } catch (err) {
      setError(mensajeError(err, 'No se pudo desactivar la etiqueta'));
      setPorDesactivar(null);
    } finally {
      setDesactivando(false);
    }
  };

  return (
    <section className="tarjeta mis-etiquetas" aria-labelledby="etiquetas-titulo">
      <h2 id="etiquetas-titulo" className="reconocimiento-titulo"><Icono nombre="etiqueta" tamano={18} /> Etiquetas QR de mis cosas</h2>
      <p className="nota-suave">
        Pega una etiqueta en tu calculadora, casaca o USB. Si alguien la encuentra, escanea el QR y te escribe por el chat
        de FoundIA: no verá tu nombre completo, tu correo ni tu teléfono.
      </p>
      <form className="etiqueta-form" onSubmit={crear} noValidate>
        <div className="campo">
          <label className="campo-label" htmlFor="etiqueta-nombre">¿Qué cosa es?</label>
          <input id="etiqueta-nombre" className="input" value={nombre} maxLength={80} onChange={(e) => setNombre(e.target.value)} placeholder="Calculadora Casio fx-991" />
        </div>
        <div className="campo">
          <label className="campo-label" htmlFor="etiqueta-descripcion">Cómo reconocerla (opcional)</label>
          <input id="etiqueta-descripcion" className="input" value={descripcion} maxLength={200} onChange={(e) => setDescripcion(e.target.value)}
            placeholder="Tiene un sticker de gato atrás" aria-describedby="etiqueta-descripcion-ayuda" />
          <small id="etiqueta-descripcion-ayuda" className="nota-suave">Lo verá quien la escanee: no pongas tu nombre ni tu teléfono.</small>
        </div>
        <button type="submit" className="btn btn-primario" disabled={creando}>{creando ? 'Creando...' : 'Crear etiqueta'}</button>
      </form>
      {error && <p role="alert" className="campo-error">{error}</p>}
      {aviso && <p role="status" className="aviso-accion aviso-ok">{aviso}</p>}
      {errorCarga && (
        <p role="alert" className="campo-error">{errorCarga} <button type="button" className="btn-enlace" onClick={cargar}>Reintentar</button></p>
      )}
      {etiquetas && etiquetas.length > 0 && (
        <ul className="etiqueta-lista">
          {etiquetas.map((x) => <TarjetaEtiqueta key={x.id} etiqueta={x} onDesactivar={setPorDesactivar} />)}
        </ul>
      )}
      <ConfirmarDialogo
        abierto={Boolean(porDesactivar)}
        titulo="¿Desactivar esta etiqueta?"
        mensaje={porDesactivar ? `El QR de «${porDesactivar.nombre}» dejará de funcionar y sus conversaciones quedarán solo para leer.` : ''}
        textoConfirmar="Sí, desactivar"
        procesando={desactivando}
        onConfirmar={desactivar}
        onCancelar={() => setPorDesactivar(null)}
      />
    </section>
  );
}
