import { useId, useState } from 'react';
import Icono from '../Icono';

// Respuestas vigentes de FoundIA (mismas reglas que antes en la portada).
const PREGUNTAS = [
  ['¿Necesito una cuenta para buscar?', 'Puedes consultar las publicaciones y buscar objetos sin iniciar sesión. Para publicar, necesitas una cuenta verificada.'],
  ['¿Qué hago si perdí algo?', 'Revisa las publicaciones de objetos encontrados. Puedes buscar por nombre, categoría o ubicación. Si no aparece, publica tu objeto como perdido con la fecha y el último lugar donde lo viste.'],
  ['¿Qué hago si encontré un objeto?', 'Publica el objeto como encontrado e indica dónde y cuándo lo encontraste. Describe sus características sin mostrar datos personales de su dueño.'],
  ['¿La fotografía siempre corresponde al objeto?', 'Las fotos subidas por quien publica tienen prioridad. Cuando no hay una foto disponible, podemos mostrar una fotografía de referencia identificada como tal. Revisa siempre la descripción.'],
  ['¿Dónde consulto mis publicaciones?', 'Entra a tu cuenta y abre “Mis reportes”. Para consultar las publicaciones de toda la comunidad, utiliza Buscar.'],
];

function IconoPreguntas() {
  return (
    <svg className="ini-faq-icono" viewBox="0 0 96 80" width="96" height="80" aria-hidden="true" focusable="false">
      <path className="burbuja-atras" d="M14 10h38a10 10 0 0 1 10 10v20a10 10 0 0 1-10 10H30l-12 10v-10h-4A10 10 0 0 1 4 40V20a10 10 0 0 1 10-10Z" />
      <path className="burbuja-frente" d="M44 22h38a10 10 0 0 1 10 10v22a10 10 0 0 1-10 10h-4v10l-12-10H44a10 10 0 0 1-10-10V32a10 10 0 0 1 10-10Z" />
      <path className="signo" d="M57 37a6 6 0 1 1 8.5 5.4c-1.6.8-2.5 2-2.5 3.6v1" />
      <circle className="signo-punto" cx="63" cy="54" r="2.2" />
    </svg>
  );
}

export default function PreguntasFrecuentes() {
  const base = useId();
  const [abiertas, setAbiertas] = useState(() => new Set());
  const alternar = (i) => setAbiertas((prev) => {
    const siguiente = new Set(prev);
    if (siguiente.has(i)) siguiente.delete(i); else siguiente.add(i);
    return siguiente;
  });

  return (
    <section className="ini-seccion ini-faq ini-panel" aria-labelledby="faq-titulo" id="preguntas-frecuentes">
      <div className="ini-faq-intro">
        <IconoPreguntas />
        <div>
          <h2 id="faq-titulo">Preguntas frecuentes</h2>
          <p>Encuentra respuestas rápidas sobre el uso de FoundIA.</p>
        </div>
      </div>
      <ul className="ini-faq-lista">
        {PREGUNTAS.map(([pregunta, respuesta], i) => {
          const abierta = abiertas.has(i);
          const idBoton = `${base}-p${i}`;
          const idPanel = `${base}-r${i}`;
          return (
            <li key={pregunta} className={`ini-faq-item${abierta ? ' abierta' : ''}`}>
              <h3>
                <button type="button" id={idBoton} aria-expanded={abierta} aria-controls={idPanel} onClick={() => alternar(i)}>
                  <span>{pregunta}</span>
                  <Icono nombre="chevronAbajo" tamano={18} className="ini-faq-chevron" />
                </button>
              </h3>
              <div id={idPanel} role="region" aria-labelledby={idBoton} className="ini-faq-respuesta" inert={abierta ? undefined : ''}>
                <div><p>{respuesta}</p></div>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
