// H48: el panel enlaza a las páginas públicas de privacidad y términos de la web de usuario.
// Son públicas (no piden sesión), por eso se abren en la web y no se duplican en el panel.
export const WEB_URL = (import.meta.env.VITE_WEB_URL || 'http://localhost:5173').replace(/\/+$/, '');

export default function EnlacesLegales({ className = '' }) {
  return (
    <nav className={`enlaces-legales ${className}`.trim()} aria-label="Documentos legales">
      <a href={`${WEB_URL}/privacidad`} target="_blank" rel="noopener noreferrer">
        Privacidad<span className="sr-only"> (se abre en otra pestaña)</span>
      </a>
      <span aria-hidden="true">·</span>
      <a href={`${WEB_URL}/terminos`} target="_blank" rel="noopener noreferrer">
        Términos<span className="sr-only"> (se abre en otra pestaña)</span>
      </a>
    </nav>
  );
}
