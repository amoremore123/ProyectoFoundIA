import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import QRCode from 'qrcode';
import Icono from './Icono';
import { getQrRetiro, iniciarConversacion, mensajeError } from '../services/api';

// H42/H44: estado de mi reclamo de un objeto en custodia y, si fue aceptado, el QR de retiro.
// El QR solo contiene el token firmado por el servidor (sin nombre, correo ni teléfono).
const ESTADOS = {
  PENDIENTE: {
    titulo: 'Reclamo en revisión',
    texto: 'La biblioteca está comparando tu respuesta con el objeto. Te avisaremos cuando lo resuelva.',
    tono: 'pendiente',
  },
  ACEPTADO: {
    titulo: '¡Reclamo aceptado!',
    texto: 'Muestra este QR en la biblioteca para retirar tu objeto. Sirve una sola vez.',
    tono: 'aceptado',
  },
  RECHAZADO: {
    titulo: 'Reclamo rechazado',
    texto: 'La biblioteca no pudo confirmar que el objeto es tuyo. Si crees que es un error, acércate a la biblioteca.',
    tono: 'rechazado',
  },
  ASIGNADO_A_OTRO: {
    titulo: 'Este objeto ya tiene dueño confirmado',
    texto: 'La biblioteca ya aceptó el reclamo de otra persona. Si crees que es tuyo, acércate a la biblioteca con algo que lo demuestre.',
    tono: 'pendiente',
  },
  RETIRADO: {
    titulo: 'Objeto retirado',
    texto: 'Ya retiraste este objeto en la biblioteca.',
    tono: 'aceptado',
  },
};

// La API envía la fecha de vencimiento en UTC sin zona: se interpreta como UTC
function fechaVence(expira) {
  if (!expira) return '';
  const fecha = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(expira) ? expira : `${expira}Z`);
  if (Number.isNaN(fecha.getTime())) return '';
  return fecha.toLocaleString('es-PE', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
}

function QrRetiro({ objetoId, nombre }) {
  const [estado, setEstado] = useState('cargando');
  const [imagen, setImagen] = useState('');
  const [vence, setVence] = useState('');
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setEstado('cargando');
    setError('');
    try {
      const { token, expira } = await getQrRetiro(objetoId);
      setImagen(await QRCode.toDataURL(token, { width: 240, margin: 2, errorCorrectionLevel: 'M' }));
      setVence(fechaVence(expira));
      setEstado('listo');
    } catch (e) {
      setError(mensajeError(e, 'El QR de retiro ya no está disponible.'));
      setEstado('error');
    }
  }, [objetoId]);

  useEffect(() => { cargar(); }, [cargar]);

  if (estado === 'cargando') return <p className="nota-suave" role="status">Preparando tu QR de retiro…</p>;
  if (estado === 'error') {
    return (
      <div className="qr-retiro-error" role="alert">
        <p>{error} Si ya venció, consulta en la biblioteca.</p>
        <button type="button" className="btn-enlace" onClick={cargar}>Reintentar</button>
      </div>
    );
  }
  return (
    <figure className="qr-retiro">
      <img src={imagen} alt={`QR de retiro de ${nombre}`} width={240} height={240} />
      <figcaption>
        {vence && <span>Válido hasta el {vence}</span>}
        <a className="btn btn-secundario" href={imagen} download={`qr-retiro-${objetoId}.png`}>
          <Icono nombre="descargar" tamano={16} /> Descargar QR
        </a>
      </figcaption>
    </figure>
  );
}

// H63: quien reclamó puede escribir a la biblioteca (pedir un detalle o acordar el retiro)
function EscribirBiblioteca({ reclamoId }) {
  const navigate = useNavigate();
  const [abriendo, setAbriendo] = useState(false);
  const [error, setError] = useState('');
  const abrir = async () => {
    setAbriendo(true);
    setError('');
    try {
      const conversacion = await iniciarConversacion({ reclamoId });
      navigate(`/mensajes/${conversacion.id}`);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo abrir la conversación con la biblioteca'));
      setAbriendo(false);
    }
  };
  return (
    <div className="estado-reclamo-chat">
      <button type="button" className="btn btn-secundario" onClick={abrir} disabled={abriendo}>
        <Icono nombre="chat" tamano={16} /> {abriendo ? 'Abriendo...' : 'Escribir a la biblioteca'}
      </button>
      {error && <p className="campo-error" role="alert">{error}</p>}
    </div>
  );
}

export default function EstadoReclamoCustodia({ estado, objetoId, nombre, reclamoId }) {
  const info = ESTADOS[estado];
  if (!info) return null;
  return (
    <section className={`estado-reclamo estado-reclamo-${info.tono}`} aria-labelledby="estado-reclamo-titulo">
      <h2 id="estado-reclamo-titulo" className="estado-reclamo-titulo">{info.titulo}</h2>
      <p className="estado-reclamo-texto">{info.texto}</p>
      {estado === 'ACEPTADO' && <QrRetiro objetoId={objetoId} nombre={nombre} />}
      {reclamoId && ['PENDIENTE', 'ACEPTADO'].includes(estado) && <EscribirBiblioteca reclamoId={reclamoId} />}
    </section>
  );
}
