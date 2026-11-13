// Muestra el texto de los documentos legales (Markdown simple: títulos con # y ##, párrafos).
// Se usa en el registro (H33) y en las páginas públicas de privacidad y términos (H48).
// H51: `nivel` es el nivel del título «#»; «##» va un nivel debajo. Así los títulos siguen
// el orden de la página donde se muestran (en la página pública van después del h1).
export default function TextoLegal({ texto = '', nivel = 3 }) {
  const Titulo = `h${Math.min(Math.max(nivel, 2), 5)}`;
  const Subtitulo = `h${Math.min(Math.max(nivel, 2), 5) + 1}`;
  return texto
    .split('\n')
    .map((linea) => linea.trim())
    .filter(Boolean)
    .map((linea, i) => {
      if (linea.startsWith('## ')) return <Subtitulo key={i}>{linea.slice(3)}</Subtitulo>;
      if (linea.startsWith('# ')) return <Titulo key={i}>{linea.slice(2)}</Titulo>;
      return <p key={i}>{linea}</p>;
    });
}
