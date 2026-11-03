// Muestra el texto de los documentos legales (Markdown simple: títulos con # y ##, párrafos).
// Se usa en el registro (H33) y en las páginas públicas de privacidad y términos (H48).
export default function TextoLegal({ texto = '' }) {
  return texto
    .split('\n')
    .map((linea) => linea.trim())
    .filter(Boolean)
    .map((linea, i) => {
      if (linea.startsWith('## ')) return <h4 key={i}>{linea.slice(3)}</h4>;
      if (linea.startsWith('# ')) return <h3 key={i}>{linea.slice(2)}</h3>;
      return <p key={i}>{linea}</p>;
    });
}
