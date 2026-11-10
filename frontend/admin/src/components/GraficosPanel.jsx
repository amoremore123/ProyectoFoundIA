// Gráficos del dashboard dibujados con HTML/SVG, sin librerías externas.

// Barras de objetos reportados por mes. El mes actual va en amarillo.
export function GraficoMensual({ serie }) {
  const maximo = Math.max(1, ...serie.map((m) => m.total));
  const total = serie.reduce((suma, m) => suma + m.total, 0);
  return (
    <div className="grafico-mensual">
      <ol className="grafico-barras" aria-label={`Objetos reportados por mes. Total: ${total}`}>
        {serie.map((m, i) => {
          const actual = i === serie.length - 1;
          return (
            <li key={m.clave} className={`grafico-columna${actual ? ' actual' : ''}`} aria-label={`${m.etiqueta}: ${m.total} ${m.total === 1 ? 'objeto' : 'objetos'}`}>
              <span className="grafico-valor" aria-hidden="true">{m.total}</span>
              <span className="grafico-barra" style={{ '--alto': `${(m.total / maximo) * 100}%` }} aria-hidden="true" />
              <span className="grafico-mes" aria-hidden="true">{m.etiqueta}</span>
            </li>
          );
        })}
      </ol>
      {total === 0 && <p className="grafico-vacio">Aún no hay objetos publicados en estos meses.</p>}
    </div>
  );
}

// Dona de recuperación: recuperados (amarillo) frente a perdidos sin recuperar (azul).
export function DonaRecuperacion({ porcentaje, recuperados, pendientes }) {
  const radio = 52;
  const circunferencia = 2 * Math.PI * radio;
  const avance = porcentaje == null ? 0 : (porcentaje / 100) * circunferencia;
  return (
    <div className="dona">
      <svg viewBox="0 0 132 132" className="dona-svg" role="img" aria-label={porcentaje == null ? 'Aún no hay objetos perdidos' : `${porcentaje}% de los objetos perdidos fueron recuperados`}>
        <circle cx="66" cy="66" r={radio} className="dona-fondo" />
        {/* Con 0 % no se dibuja el arco: el borde redondeado dejaría un punto suelto */}
        {avance > 0 && (
          <circle
            cx="66"
            cy="66"
            r={radio}
            className="dona-avance"
            strokeDasharray={`${avance} ${circunferencia}`}
            transform="rotate(-90 66 66)"
          />
        )}
        <text x="66" y="72" textAnchor="middle" className="dona-texto">{porcentaje == null ? '—' : `${porcentaje}%`}</text>
      </svg>
      <ul className="dona-leyenda">
        <li><span className="dona-punto recuperados" aria-hidden="true" /> Recuperados <strong>{recuperados}</strong></li>
        <li><span className="dona-punto pendientes" aria-hidden="true" /> Perdidos sin recuperar <strong>{pendientes}</strong></li>
      </ul>
    </div>
  );
}
