// Cabecera común de las secciones de la portada: rótulo con raya amarilla, título y texto.
export default function CabeceraSeccion({ id, eyebrow, icono = null, titulo, texto, children }) {
  return (
    <div className="ini-cabecera">
      <span className={`ini-eyebrow${icono ? ' con-icono' : ''}`}>{icono}{eyebrow}</span>
      <h2 id={id}>{titulo}</h2>
      {texto && <p>{texto}</p>}
      {children}
    </div>
  );
}
