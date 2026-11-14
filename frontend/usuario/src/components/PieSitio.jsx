import { Link } from 'react-router-dom';
import Logo from './Logo';
import { EXTRAS_HABILITADOS } from '../config/alcance';

// Pie de la portada a todo el ancho: marca a la izquierda, enlaces en columnas con título
// y una fila final con el copyright. Usa el degradado marino de la marca en ambos temas.
const GRUPOS = [
  {
    id: 'explorar',
    titulo: 'Explorar',
    enlaces: [
      { texto: EXTRAS_HABILITADOS ? 'Historial' : 'Publicaciones', to: EXTRAS_HABILITADOS ? '/historial' : '/explorar' },
      { texto: 'Buscar', to: '/buscar' },
      { texto: 'Publicar objeto', to: '/publicar' },
    ],
  },
  {
    id: 'ayuda',
    titulo: 'Ayuda',
    enlaces: [
      { texto: 'Cómo funciona', href: '#como-funciona' },
      { texto: 'Preguntas frecuentes', href: '#preguntas-frecuentes' },
    ],
  },
  {
    id: 'legal',
    titulo: 'Legal',
    enlaces: [
      { texto: 'Privacidad', to: '/privacidad' },
      { texto: 'Términos', to: '/terminos' },
      { texto: 'Cookies', to: '/cookies' },
    ],
  },
];

export default function PieSitio() {
  return (
    <footer className="pie-sitio">
      <div className="pie-sitio-interior">
        <div className="pie-sitio-marca">
          <Link to="/" className="pie-sitio-logo" aria-label="FoundIA, volver al inicio"><Logo tamano={30} claro /></Link>
          <p>Objetos perdidos y encontrados en la comunidad Tecsup.</p>
        </div>

        {GRUPOS.map((grupo) => (
          <nav key={grupo.id} className="pie-sitio-grupo" aria-labelledby={`pie-${grupo.id}`}>
            <h2 id={`pie-${grupo.id}`} className="pie-sitio-titulo">{grupo.titulo}</h2>
            <ul>
              {grupo.enlaces.map((e) => (
                <li key={e.texto}>{e.to ? <Link to={e.to}>{e.texto}</Link> : <a href={e.href}>{e.texto}</a>}</li>
              ))}
            </ul>
          </nav>
        ))}

        <div className="pie-sitio-legal">
          <span>© 2026 FoundIA</span>
          <span>Proyecto integrador Tecsup</span>
        </div>
      </div>
    </footer>
  );
}
