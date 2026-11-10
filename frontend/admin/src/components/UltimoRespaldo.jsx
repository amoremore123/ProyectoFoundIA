import { useCallback, useEffect, useState } from 'react';
import { getUltimoRespaldo, mensajeError } from '../services/api';
import Icono from './Icono';

const ESTADOS = {
  COMPLETADO: { texto: 'Completado', clase: 'activo' },
  EN_PROCESO: { texto: 'En proceso', clase: 'recuperado' },
  FALLIDO: { texto: 'Fallido', clase: 'eliminado' },
};

export function tamanoLegible(bytes) {
  if (bytes == null || Number.isNaN(Number(bytes))) return '—';
  const n = Number(bytes);
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function fechaHora(valor) {
  if (!valor) return '—';
  const d = new Date(valor);
  if (Number.isNaN(d.getTime())) return String(valor);
  return d.toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

// H38: estado del último respaldo automático de la base de datos (solo lectura)
export default function UltimoRespaldo() {
  const [respaldo, setRespaldo] = useState(undefined); // undefined = cargando, null = sin respaldos
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setRespaldo(undefined);
    setError('');
    try {
      setRespaldo(await getUltimoRespaldo());
    } catch (e) {
      setError(mensajeError(e, 'No se pudo consultar el último respaldo'));
      setRespaldo(null);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const estado = respaldo ? ESTADOS[respaldo.estado] || { texto: respaldo.estado, clase: '' } : null;

  return (
    <section className="tarjeta panel-tarjeta respaldo-tarjeta" aria-labelledby="titulo-respaldo">
      <div className="panel-tarjeta-cabecera">
        <h2 id="titulo-respaldo"><Icono nombre="escudo" tamano={18} /> Último respaldo</h2>
        {estado && <span className={`chip-estado ${estado.clase}`}>{estado.texto}</span>}
      </div>
      {respaldo === undefined && !error ? (
        <p className="panel-vacio" role="status">Consultando respaldo...</p>
      ) : error ? (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      ) : respaldo === null ? (
        <p className="panel-aviso" role="status">
          Todavía no hay respaldos registrados. Revisa que el servicio «respaldo» de Docker esté en marcha.
        </p>
      ) : (
        <dl className="respaldo-datos">
          <div><dt>Inicio</dt><dd>{fechaHora(respaldo.iniciadoEn)}</dd></div>
          <div><dt>Fin</dt><dd>{fechaHora(respaldo.finalizadoEn)}</dd></div>
          <div><dt>Tamaño</dt><dd>{tamanoLegible(respaldo.tamanoBytes)}</dd></div>
          <div className="respaldo-archivo"><dt>Archivo</dt><dd>{respaldo.nombreArchivo}</dd></div>
          {respaldo.estado === 'FALLIDO' && (
            <div className="respaldo-error" role="alert">
              <dt>Error</dt><dd>{respaldo.mensaje || 'El respaldo falló sin mensaje del servidor.'}</dd>
            </div>
          )}
        </dl>
      )}
    </section>
  );
}
