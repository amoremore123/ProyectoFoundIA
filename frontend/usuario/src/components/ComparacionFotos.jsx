import { useState } from 'react';
import Icono from './Icono';
import { compararFotosCoincidencias, mensajeError } from '../services/api';

// Coincidencias por imagen: quien publicó puede pedir que la IA compare su foto con las fotos de sus
// coincidencias. Se pide a mano (usa el cupo de la IA) y es solo una ayuda: no confirma nada.
export function CompararFotosIa({ objetoId, onResultados }) {
  const [estado, setEstado] = useState('inicial'); // inicial | cargando | listo
  const [error, setError] = useState('');
  const [resumen, setResumen] = useState('');

  const comparar = async () => {
    setEstado('cargando');
    setError('');
    try {
      const { resultados = [], sinFoto = 0 } = await compararFotosCoincidencias(objetoId);
      const porObjeto = Object.fromEntries(resultados.map((r) => [r.objetoId, r]));
      onResultados(porObjeto);
      const partes = [];
      partes.push(resultados.length === 0
        ? 'Ninguna coincidencia tiene foto para comparar.'
        : `Se compararon ${resultados.length} ${resultados.length === 1 ? 'foto' : 'fotos'}.`);
      if (sinFoto > 0 && resultados.length > 0) partes.push(`${sinFoto} sin foto no se compararon.`);
      setResumen(partes.join(' '));
      setEstado('listo');
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron comparar las fotos'));
      setEstado('inicial');
    }
  };

  return (
    <div className="comparar-fotos">
      <button type="button" className="btn btn-secundario" onClick={comparar} disabled={estado === 'cargando'}>
        <Icono nombre="camara" tamano={16} /> {estado === 'cargando' ? 'Comparando fotos...' : estado === 'listo' ? 'Volver a comparar' : 'Comparar fotos con IA'}
      </button>
      <p className="nota-suave">
        La IA mira tu foto junto a la de cada coincidencia. Es una ayuda: confirma siempre con detalles que solo tú conoces.
      </p>
      {error && <p role="alert" className="campo-error">{error}</p>}
      {estado === 'listo' && <p role="status" className="nota-suave">{resumen}</p>}
    </div>
  );
}

const TEXTO_NIVEL = { ALTO: 'muy parecida', MEDIO: 'algo parecida', BAJO: 'poco parecida' };

export function ParecidoFoto({ resultado }) {
  if (!resultado) return null;
  if (resultado.parecido == null) {
    return <p className="parecido-foto parecido-sin">La IA no pudo comparar esta foto.</p>;
  }
  return (
    <div className={`parecido-foto parecido-${resultado.nivel.toLowerCase()}`}>
      <p><strong>Foto {TEXTO_NIVEL[resultado.nivel] || ''}</strong> según la IA ({resultado.parecido}%)</p>
      {resultado.motivo && <p className="parecido-motivo">{resultado.motivo}</p>}
    </div>
  );
}
