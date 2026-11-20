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

// La huella SHA-256 completa ocupa 64 caracteres: se muestra corta y entera al copiarla o leerla
export function huellaCorta(huella) {
  if (!huella) return '—';
  return huella.length > 20 ? `${huella.slice(0, 12)}…${huella.slice(-6)}` : huella;
}

const TITULOS = { BASE: 'Respaldo de la base', FOTOS: 'Respaldo de las fotos' };

// H38: estado del último respaldo automático de la base de datos (solo lectura).
// H59: con tipo="FOTOS" muestra el del volumen de fotos, con su huella.
export default function UltimoRespaldo({ tipo, titulo, conHuella = false }) {
  const [respaldo, setRespaldo] = useState(undefined); // undefined = cargando, null = sin respaldos
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setRespaldo(undefined);
    setError('');
    try {
      setRespaldo(await getUltimoRespaldo(tipo));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo consultar el último respaldo'));
      setRespaldo(null);
    }
  }, [tipo]);

  useEffect(() => { cargar(); }, [cargar]);
  const idTitulo = `titulo-respaldo${tipo ? `-${tipo.toLowerCase()}` : ''}`;
  const nombre = titulo || TITULOS[tipo] || 'Último respaldo';

  const estado = respaldo ? ESTADOS[respaldo.estado] || { texto: respaldo.estado, clase: '' } : null;

  return (
    <section className="tarjeta panel-tarjeta respaldo-tarjeta" aria-labelledby={idTitulo}>
      <div className="panel-tarjeta-cabecera">
        <h2 id={idTitulo}><Icono nombre="escudo" tamano={18} /> {nombre}</h2>
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
          {tipo === 'FOTOS'
            ? 'Todavía no hay respaldos de fotos. Se crean junto con el de la base si el servicio «respaldo» tiene RESPALDO_FOTOS=1.'
            : 'Todavía no hay respaldos registrados. Revisa que el servicio «respaldo» de Docker esté en marcha.'}
        </p>
      ) : (
        <dl className="respaldo-datos">
          <div><dt>Inicio</dt><dd>{fechaHora(respaldo.iniciadoEn)}</dd></div>
          <div><dt>Fin</dt><dd>{fechaHora(respaldo.finalizadoEn)}</dd></div>
          <div><dt>Tamaño</dt><dd>{tamanoLegible(respaldo.tamanoBytes)}</dd></div>
          <div className="respaldo-archivo"><dt>Archivo</dt><dd>{respaldo.nombreArchivo}</dd></div>
          {conHuella && (
            <div className="respaldo-archivo">
              <dt>Huella SHA-256</dt>
              <dd><code title={respaldo.huellaSha256 || undefined}>{huellaCorta(respaldo.huellaSha256)}</code></dd>
            </div>
          )}
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
