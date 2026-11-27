import { useEffect, useState } from 'react';
import { getRestauracion, mensajeError, solicitarRestauracion, statusDe } from '../services/api';

// H70: restaurar una versión desde el panel con doble confirmación (escribir la versión y RESTAURAR).
// El servicio de respaldo la ejecuta; mientras tanto la API entra en modo mantenimiento (503 en escrituras).
const ESTADOS = {
  SOLICITADA: 'Solicitada, esperando al servicio de respaldo',
  EN_PROCESO: 'Restaurando…',
  COMPLETADA: 'Completada',
  FALLIDA: 'Fallida',
};

export default function RestaurarVersion({ version, onCerrar, onTerminada, intervaloMs = 5000 }) {
  const [versionEscrita, setVersionEscrita] = useState('');
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [restauracion, setRestauracion] = useState(null);

  const listo = versionEscrita.trim() === version && texto.trim() === 'RESTAURAR';

  const pedir = async (e) => {
    e.preventDefault();
    if (!listo || enviando) return;
    setEnviando(true);
    setError('');
    try {
      setRestauracion(await solicitarRestauracion(version, {
        confirmacionVersion: versionEscrita.trim(), confirmacionTexto: texto.trim(),
      }));
    } catch (err) {
      setError(mensajeError(err, 'No se pudo pedir la restauración. Intenta de nuevo.'));
    } finally { setEnviando(false); }
  };

  // Sigue el avance. Al terminar se cierran todas las sesiones: un 401 lleva al inicio de sesión (lo hace la API).
  const id = restauracion?.id;
  const estado = restauracion?.estado;
  useEffect(() => {
    if (!id || estado === 'COMPLETADA' || estado === 'FALLIDA') return undefined;
    const t = setInterval(async () => {
      try {
        setRestauracion(await getRestauracion(id));
      } catch (err) {
        if (statusDe(err) !== 503) setError(mensajeError(err, 'No se pudo consultar el avance.'));
      }
    }, intervaloMs);
    return () => clearInterval(t);
  }, [id, estado, intervaloMs]);
  useEffect(() => {
    if (estado === 'COMPLETADA' || estado === 'FALLIDA') onTerminada?.(restauracion);
  }, [estado]); // eslint-disable-line react-hooks/exhaustive-deps

  if (restauracion) {
    return (
      <div className="tarjeta respaldos-procedimiento" role="region" aria-label={`Restauración de la versión ${version}`}>
        <h3 className="seccion-titulo">Restauración de la versión {version}</h3>
        <p role="status">Estado: <strong>{ESTADOS[restauracion.estado] || restauracion.estado}</strong></p>
        {restauracion.mensaje && <p className="nota-tabla">{restauracion.mensaje}</p>}
        {restauracion.respaldoSeguridad && (
          <p className="nota-tabla">Para deshacerla, restaura la versión de seguridad <code>{restauracion.respaldoSeguridad}</code>.</p>
        )}
        {restauracion.estado === 'COMPLETADA' && <p className="nota-tabla">Se cerraron todas las sesiones: vuelve a iniciar sesión.</p>}
        {error && <p className="banner-error" role="alert">{error}</p>}
        <button type="button" className="btn btn-fantasma btn-mini" onClick={onCerrar}>Cerrar</button>
      </div>
    );
  }

  return (
    <form className="tarjeta respaldos-procedimiento formulario" onSubmit={pedir} noValidate aria-label={`Restaurar la versión ${version}`}>
      <h3 className="seccion-titulo">Restaurar la versión {version}</h3>
      <ul className="respaldos-pasos">
        <li>Se perderá todo lo posterior a esta versión.</li>
        <li>Se cerrarán todas las sesiones, también la tuya.</li>
        <li>Antes se crea un respaldo de seguridad: con él se puede deshacer.</li>
        <li>Mientras se restaura, la API no acepta escrituras (modo mantenimiento).</li>
      </ul>
      <label htmlFor="restaurar-version">Escribe la versión para confirmar</label>
      <input id="restaurar-version" value={versionEscrita} onChange={(e) => setVersionEscrita(e.target.value)} autoComplete="off" />
      <label htmlFor="restaurar-texto">Escribe RESTAURAR</label>
      <input id="restaurar-texto" value={texto} onChange={(e) => setTexto(e.target.value)} autoComplete="off" />
      {error && <p className="banner-error" role="alert">{error}</p>}
      <div className="acciones-fila">
        <button type="submit" className="btn btn-peligro btn-mini" disabled={!listo || enviando}>{enviando ? 'Pidiendo…' : 'Restaurar'}</button>
        <button type="button" className="btn btn-fantasma btn-mini" onClick={onCerrar}>Cancelar</button>
      </div>
    </form>
  );
}
