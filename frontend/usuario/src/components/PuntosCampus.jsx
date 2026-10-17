import { Link } from 'react-router-dom';
import Icono from './Icono';

// Lugares del campus donde más se pierden cosas. Cada uno abre Buscar con ese lugar.
const PUNTOS = [
  ['Biblioteca', 'Mesas de estudio y salas grupales'],
  ['Cafetería', 'Mesas, barra y zona de microondas'],
  ['Laboratorios', 'Cómputo, electrónica y química'],
  ['Talleres', 'Mecánica, soldadura y mantenimiento'],
  ['Aulas', 'Pabellones de clases teóricas'],
  ['Auditorio', 'Charlas, ceremonias y eventos'],
];

export default function PuntosCampus() {
  return (
    <section className="puntos-campus" aria-labelledby="puntos-campus-titulo">
      <div className="puntos-campus-cabecera">
        <span className="visual-eyebrow">RECORRE EL CAMPUS</span>
        <h2 id="puntos-campus-titulo">¿Dónde lo viste por última vez?</h2>
        <p>Elige un lugar y te mostramos lo que la comunidad publicó por ahí.</p>
      </div>
      <ul className="puntos-campus-lista">
        {PUNTOS.map(([lugar, detalle], i) => (
          <li key={lugar} style={{ '--i': i }}>
            <Link to={`/buscar?ubicacion=${encodeURIComponent(lugar)}`} className="punto-campus">
              <span className="punto-campus-pin" aria-hidden="true"><Icono nombre="ubicacion" tamano={18} /></span>
              <span className="punto-campus-texto">
                <strong>{lugar}</strong>
                <small>{detalle}</small>
              </span>
              <Icono nombre="flechaDer" tamano={16} className="punto-campus-flecha" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
