import Icono from './Icono';
import CabeceraSeccion from './inicio/CabeceraSeccion';

// Cómo se recupera un objeto en FoundIA. Cada paso describe una función que ya existe:
// publicar, avisos de coincidencia (H21-H24), contacto y reclamo (H20) y confirmar la recuperación (H22).
const PASOS = [
  { icono: 'caja', titulo: 'Reporta', texto: 'Publica lo que perdiste o encontraste.' },
  { icono: 'campana', titulo: 'Te avisamos', texto: 'Te notificamos si hay una posible coincidencia.' },
  { icono: 'escudo', titulo: 'Verifica', texto: 'Confirma los detalles del objeto.' },
  { icono: 'check', titulo: 'Recupera', texto: 'Coordina la entrega en un lugar seguro del campus.' },
];

// El id "proceso-recuperacion" lo usan la barra de navegación, los accesos del hero y el asistente;
// "como-funciona" lo usa el pie de página.
export default function ProcesoRecuperacion() {
  return (
    <section className="ini-seccion ini-pasos" aria-labelledby="como-funciona" id="proceso-recuperacion">
      <CabeceraSeccion
        id="como-funciona"
        eyebrow="Cómo funciona"
        titulo="Así recuperas tu objeto"
        texto="Cuatro pasos simples, de la publicación a tus manos."
      />
      <ol className="ini-pasos-lista">
        {PASOS.map((paso, i) => (
          <li key={paso.titulo} className={`ini-tarjeta ini-paso${i === PASOS.length - 1 ? ' destacado' : ''}`}>
            <span className="ini-paso-numero" aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
            <span className="ini-paso-icono" aria-hidden="true"><Icono nombre={paso.icono} tamano={26} /></span>
            <h3>{paso.titulo}</h3>
            <p>{paso.texto}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
