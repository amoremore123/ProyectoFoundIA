import { useState } from 'react';
import { Link } from 'react-router-dom';
import ConfirmarDialogo from './ConfirmarDialogo';
import MensajeError from './MensajeError';
import { confirmarRecuperacion, descartarCoincidencia, mensajeError } from '../services/api';

function fechaLocal(iso) {
  if (!iso) return '';
  const texto = String(iso);
  const fecha = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(texto) ? texto : `${texto}Z`);
  return Number.isNaN(fecha.getTime()) ? texto : fecha.toLocaleString('es-PE', { dateStyle: 'long', timeStyle: 'short' });
}

const DIALOGOS = {
  confirmar: {
    titulo: '¿Recuperaste tu objeto?',
    mensaje: 'Tu publicación de objeto perdido quedará como RECUPERADO y dejará de aparecer en las sugerencias. Esto no cambia la publicación de quien lo encontró.',
    texto: 'Confirmar recuperación',
  },
  descartar: {
    titulo: '¿Descartar esta coincidencia?',
    mensaje: 'Dejará de aparecer en tus sugerencias. Las dos publicaciones no se modifican.',
    texto: 'Sí, descartar',
  },
};

// H21 (descartar) y H22 (confirmar recuperación) - solo para quien publicó el objeto perdido.
// Siempre se confirma antes de enviar y el éxito se muestra solo con la respuesta del servidor.
export default function DecisionCoincidencia({ coincidenciaId, perdidoId, onResuelta }) {
  const [accion, setAccion] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [resultado, setResultado] = useState(null);

  const ejecutar = async () => {
    const tipo = accion;
    setEnviando(true);
    setError('');
    try {
      const respuesta = tipo === 'confirmar'
        ? await confirmarRecuperacion(coincidenciaId)
        : await descartarCoincidencia(coincidenciaId);
      setResultado({ tipo, ...respuesta });
      onResuelta?.(tipo);
    } catch (e) {
      setError(mensajeError(e, tipo === 'confirmar'
        ? 'No se pudo registrar la recuperación'
        : 'No se pudo descartar la coincidencia'));
    } finally {
      setEnviando(false);
      setAccion(null);
    }
  };

  if (resultado) {
    return (
      <section className="decision-coincidencia" aria-label="Resultado de tu decisión">
        <p role="status" className="aviso-accion aviso-ok">
          {resultado.tipo === 'confirmar'
            ? `${resultado.repetido ? 'Ya habías confirmado esta recuperación' : '¡Qué bien! Registramos que recuperaste tu objeto'}${resultado.fechaRecuperacion ? ` el ${fechaLocal(resultado.fechaRecuperacion)}` : ''}.`
            : resultado.repetido
              ? 'Ya habías descartado esta coincidencia.'
              : 'Descartaste esta coincidencia. Ya no aparecerá en tus sugerencias.'}
        </p>
        <Link className="btn btn-secundario" to={`/objeto/${perdidoId}`}>Volver a mi publicación</Link>
      </section>
    );
  }

  const dialogo = accion ? DIALOGOS[accion] : null;

  return (
    <section className="decision-coincidencia" aria-label="¿Es tu objeto?">
      <h2 className="decision-titulo">¿Es tu objeto?</h2>
      <p className="nota-suave">
        Si ya lo recuperaste gracias a esta coincidencia, confírmalo. Si no es tu objeto, descártala para dejar de verla.
      </p>
      <MensajeError mensaje={error} />
      <div className="decision-botones">
        <button type="button" className="btn btn-primario" disabled={enviando} onClick={() => setAccion('confirmar')}>
          Sí, lo recuperé
        </button>
        <button type="button" className="btn btn-secundario" disabled={enviando} onClick={() => setAccion('descartar')}>
          No es mi objeto
        </button>
      </div>
      <ConfirmarDialogo
        abierto={Boolean(dialogo)}
        titulo={dialogo?.titulo}
        mensaje={dialogo?.mensaje}
        textoConfirmar={dialogo?.texto}
        peligro={accion === 'descartar'}
        procesando={enviando}
        onConfirmar={ejecutar}
        onCancelar={() => setAccion(null)}
      />
    </section>
  );
}
