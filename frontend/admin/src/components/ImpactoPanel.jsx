import { useCallback, useEffect, useState } from 'react';
import Icono from './Icono';
import { getImpacto, mensajeError } from '../services/api';

// Impacto de FoundIA: cuántos objetos volvieron a su dueño y por qué vía (biblioteca o entre personas).
// Solo conteos, sin datos de quién perdió, encontró o retiró.
export function fraseImpacto(datos) {
  if (!datos) return '';
  const n = datos.devueltosUltimos30Dias;
  if (!n) return 'Todavía no hay objetos devueltos en los últimos 30 días.';
  return `FoundIA ayudó a devolver ${n} ${n === 1 ? 'objeto' : 'objetos'} en los últimos 30 días.`;
}

function dias(valor) {
  if (valor == null) return '—';
  if (valor < 1) return 'menos de 1 día';
  return `${valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })} ${valor === 1 ? 'día' : 'días'}`;
}

export default function ImpactoPanel() {
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setError('');
    try { setDatos(await getImpacto()); } catch (e) { setError(mensajeError(e, 'No se pudo cargar el impacto')); }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  if (error) {
    return (
      <div className="banner-error" role="alert">
        <span><Icono nombre="alerta" tamano={18} /> {error}</span>
        <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
      </div>
    );
  }
  if (!datos) return <p className="panel-vacio" role="status">Calculando el impacto...</p>;

  const maximo = Math.max(1, ...datos.porMes.map((m) => m.total));
  const total6 = datos.porMes.reduce((s, m) => s + m.total, 0);
  return (
    <section className="tarjeta panel-tarjeta impacto" aria-labelledby="titulo-impacto">
      <div className="panel-tarjeta-cabecera">
        <h2 id="titulo-impacto"><Icono nombre="check" tamano={18} /> Impacto</h2>
        <span className="panel-etiqueta">Objetos devueltos a su dueño</span>
      </div>
      <p className="impacto-frase">{fraseImpacto(datos)}</p>
      <dl className="impacto-cifras">
        <div><dt>Devueltos en total</dt><dd>{datos.devueltos}</dd></div>
        <div><dt>Entregados en la biblioteca</dt><dd>{datos.enBiblioteca}</dd></div>
        <div><dt>Tiempo promedio de devolución</dt><dd>{dias(datos.tiempoPromedioDias)}</dd></div>
        <div><dt>Perdidos que se recuperaron</dt><dd>{datos.perdidosRecuperadosPorcentaje == null ? '—' : `${datos.perdidosRecuperadosPorcentaje}%`}</dd></div>
      </dl>
      <div className="impacto-grafico">
        <ol className="impacto-barras" aria-label={`Devueltos por mes en los últimos 6 meses. Total: ${total6}`}>
          {datos.porMes.map((m) => (
            <li key={m.mes} aria-label={`${m.etiqueta}: ${m.total} devueltos, ${m.biblioteca} en la biblioteca y ${m.personas} entre personas`}>
              <span className="impacto-valor" aria-hidden="true">{m.total}</span>
              <span className="impacto-pila" aria-hidden="true" style={{ '--alto': `${(m.total / maximo) * 100}%` }}>
                <span className="impacto-personas" style={{ flexGrow: m.personas }} />
                <span className="impacto-biblioteca" style={{ flexGrow: m.biblioteca }} />
              </span>
              <span className="impacto-mes" aria-hidden="true">{m.etiqueta}</span>
            </li>
          ))}
        </ol>
        <p className="impacto-leyenda" aria-hidden="true">
          <span><i className="impacto-biblioteca" /> En la biblioteca</span>
          <span><i className="impacto-personas" /> Entre personas</span>
        </p>
      </div>
      <p className="nota-tabla">
        Custodia: {datos.custodia.enCustodia} esperando a su dueño · {datos.custodia.entregadas} entregados · {datos.custodia.dispuestas} con destino final.
      </p>
    </section>
  );
}
