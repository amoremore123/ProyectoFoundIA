import { useState } from 'react';
import { Link } from 'react-router-dom';
import { mensajeError, reportarPublicacion, statusDe } from '../services/api';

export const MOTIVOS_REPORTE = [
  { valor: 'CONTENIDO_INAPROPIADO', texto: 'Contenido inapropiado' },
  { valor: 'INFORMACION_FALSA', texto: 'Información falsa' },
  { valor: 'SPAM', texto: 'Spam o publicidad' },
  { valor: 'OTRO', texto: 'Otro motivo' },
];
const MAX_DESCRIPCION = 2000;

// H27 - Denunciar una publicación inapropiada. Cancelar no envía nada y
// la confirmación se muestra solo con la respuesta del servidor.
export default function ReportarPublicacion({ objetoId, conSesion, esPropia }) {
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState('');

  if (esPropia) return null;

  if (!conSesion) {
    return (
      <p className="nota-suave reportar-sin-sesion">
        ¿Esta publicación es inapropiada? <Link to="/login">Inicia sesión para reportarla</Link>.
      </p>
    );
  }

  if (resultado) {
    return <p role="status" className="aviso-accion aviso-ok">{resultado}</p>;
  }

  const cancelar = () => {
    setAbierto(false);
    setMotivo('');
    setDescripcion('');
    setError('');
  };

  const enviar = async (e) => {
    e.preventDefault();
    setError('');
    const texto = descripcion.trim();
    if (!motivo) { setError('Elige un motivo.'); return; }
    if (motivo === 'OTRO' && !texto) { setError('Cuéntanos el motivo cuando eliges "Otro".'); return; }
    if (texto.length > MAX_DESCRIPCION) { setError(`La explicación admite hasta ${MAX_DESCRIPCION} caracteres.`); return; }

    setEnviando(true);
    try {
      const respuesta = await reportarPublicacion(objetoId, { motivo, ...(texto ? { descripcion: texto } : {}) });
      setResultado(respuesta?.repetido
        ? 'Ya habías reportado esta publicación. La administración la revisará.'
        : 'Gracias. Tu reporte quedó registrado y la administración lo revisará.');
    } catch (err) {
      setError(statusDe(err) === 404
        ? 'Esta publicación ya no está disponible.'
        : mensajeError(err, 'No se pudo enviar el reporte'));
    } finally {
      setEnviando(false);
    }
  };

  if (!abierto) {
    return (
      <button type="button" className="btn-enlace reportar-abrir" onClick={() => setAbierto(true)}>
        Reportar esta publicación
      </button>
    );
  }

  return (
    <form className="reportar" onSubmit={enviar} noValidate aria-label="Reportar publicación">
      <h2 className="reportar-titulo">Reportar publicación</h2>
      <p className="nota-suave">Tu reporte es privado: quien publicó no sabrá quién lo envió.</p>
      <fieldset className="reportar-motivos">
        <legend className="campo-label">Motivo *</legend>
        {MOTIVOS_REPORTE.map((m) => (
          <label key={m.valor} className="reportar-motivo">
            <input type="radio" name="motivo" value={m.valor} checked={motivo === m.valor}
              onChange={() => { setMotivo(m.valor); setError(''); }} disabled={enviando} />
            {m.texto}
          </label>
        ))}
      </fieldset>
      <label className="campo">
        <span className="campo-label">Explicación {motivo === 'OTRO' ? '*' : '(opcional)'}</span>
        <textarea className="input" rows={3} maxLength={MAX_DESCRIPCION} value={descripcion}
          onChange={(e) => { setDescripcion(e.target.value); setError(''); }} disabled={enviando} />
      </label>
      {error && <p role="alert" className="campo-error">{error}</p>}
      <div className="reportar-botones">
        <button type="button" className="btn btn-secundario" onClick={cancelar} disabled={enviando}>Cancelar</button>
        <button type="submit" className="btn btn-primario" disabled={enviando}>
          {enviando ? 'Enviando...' : 'Enviar reporte'}
        </button>
      </div>
    </form>
  );
}
