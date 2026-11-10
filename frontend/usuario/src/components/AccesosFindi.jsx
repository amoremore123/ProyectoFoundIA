import { Link } from 'react-router-dom';
import Icono from './Icono';

const ACCESOS = [
  { to: '/publicar', icono: 'caja', color: 'amarillo', titulo: 'Reporta tu objeto', texto: 'Registra lo que perdiste o encontraste.' },
  { to: '/buscar', icono: 'buscar', color: 'azul', titulo: 'Busca objetos', texto: 'Explora lo que publicó la comunidad.' },
  { to: '/notificaciones', icono: 'campana', color: 'naranja', titulo: 'Recibe avisos', texto: 'Te avisamos si hay una coincidencia.' },
  { href: '#proceso-recuperacion', icono: 'escudo', color: 'marino', titulo: 'Recupera seguro', texto: 'Así coordinamos la entrega en Tecsup.' },
];

// Las cuatro acciones principales de FoundIA, debajo del carrusel de la portada.
export default function AccesosFindi() {
  return (
    <nav className="accesos-findi" aria-label="Acciones principales">
      {ACCESOS.map((a) => {
        const contenido = (
          <>
            <span className={`acceso-findi-icono ${a.color}`}><Icono nombre={a.icono} tamano={22} /></span>
            <span className="acceso-findi-texto">
              <strong>{a.titulo}</strong>
              <span>{a.texto}</span>
            </span>
            <Icono nombre="flechaDer" tamano={16} className="acceso-findi-flecha" />
          </>
        );
        // Las anclas de la misma página usan <a> para que el navegador haga el desplazamiento
        return a.href
          ? <a key={a.titulo} href={a.href} className="acceso-findi">{contenido}</a>
          : <Link key={a.titulo} to={a.to} className="acceso-findi">{contenido}</Link>;
      })}
    </nav>
  );
}
