import Icono from '../Icono';
import CabeceraSeccion from './CabeceraSeccion';

const CONSEJOS = [
  ['etiqueta', 'Cómo es', 'Incluye el color, la marca y algún detalle particular.'],
  ['camara', 'Una foto clara', 'Usa una fotografía real del objeto, con buena iluminación.'],
  ['ubicacion', 'Dónde y cuándo', 'Indica el lugar y la fecha aproximada.'],
  ['escudo', 'Antes de una entrega', 'Verifica los datos y coordina un lugar seguro.'],
];

export default function ConsejosPublicacion() {
  return (
    <section className="ini-seccion ini-consejos" aria-labelledby="consejos-titulo">
      <CabeceraSeccion
        id="consejos-titulo"
        eyebrow="Una buena descripción ayuda"
        titulo="Los detalles hacen la diferencia"
        texto="Una publicación clara ayuda a que otra persona reconozca lo que estás buscando."
      />
      <ul className="ini-consejos-lista">
        {CONSEJOS.map(([icono, titulo, texto]) => (
          <li key={titulo} className="ini-tarjeta ini-consejo">
            <span className="ini-consejo-icono" aria-hidden="true"><Icono nombre={icono} tamano={22} /></span>
            <h3>{titulo}</h3>
            <p>{texto}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
