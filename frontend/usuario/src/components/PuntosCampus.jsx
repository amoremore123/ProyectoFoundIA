import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';
import CabeceraSeccion from './inicio/CabeceraSeccion';

// Lugares del campus donde más se pierden cosas. Al elegir uno se abre Buscar con el filtro
// de ubicación que ya existe (/buscar?ubicacion=...).
const PUNTOS = [
  ['Biblioteca', 'libro'],
  ['Cafetería', 'taza'],
  ['Laboratorios', 'matraz'],
  ['Talleres', 'llaveInglesa'],
  ['Aulas', 'grupo'],
  ['Auditorio', 'pizarra'],
];

export default function PuntosCampus() {
  const [seleccionado, setSeleccionado] = useState(PUNTOS[0][0]);
  return (
    <section className="ini-seccion ini-campus ini-panel" aria-labelledby="puntos-campus-titulo">
      <div className="ini-campus-foto">
        <img src="/imagenes/tecsup-campus.png" alt="Fachada del campus Tecsup" width="511" height="296" loading="lazy" />
      </div>
      <div className="ini-campus-contenido">
        <CabeceraSeccion
          id="puntos-campus-titulo"
          eyebrow="Recorre el campus"
          icono={<Icono nombre="ubicacion" tamano={14} />}
          titulo="¿Dónde lo viste por última vez?"
          texto="Elige un lugar y explora lo que la comunidad publicó por ahí."
        />
        <ul className="ini-lugares" aria-label="Lugares del campus">
          {PUNTOS.map(([lugar, icono]) => (
            <li key={lugar}>
              <button type="button" className="ini-lugar" aria-pressed={seleccionado === lugar} onClick={() => setSeleccionado(lugar)}>
                <Icono nombre={icono} tamano={22} />
                <span>{lugar}</span>
              </button>
            </li>
          ))}
        </ul>
        <Link to={`/buscar?ubicacion=${encodeURIComponent(seleccionado)}`} className="ini-enlace">
          Ver publicaciones en {seleccionado} <Icono nombre="flechaDer" tamano={14} />
        </Link>
      </div>
    </section>
  );
}
