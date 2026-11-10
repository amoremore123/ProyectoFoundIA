import Icono from './Icono';

// Cómo se recupera un objeto en FoundIA. Cada paso describe una función que ya existe:
// publicar, avisos de coincidencia (H21-H24), contacto y reclamo (H20) y confirmar la recuperación (H22).
const PASOS = [
  { icono: 'caja', color: 'amarillo', titulo: 'Reporta', texto: 'Publica lo que perdiste o encontraste: qué es, dónde y cuándo.' },
  { icono: 'campana', color: 'naranja', titulo: 'Te avisamos', texto: 'FoundIA compara las publicaciones y te notifica si hay una posible coincidencia.' },
  { icono: 'escudo', color: 'azul', titulo: 'Verifica', texto: 'Contacta a quien lo encontró o haz un reclamo con un detalle que solo tú conoces.' },
  { icono: 'check', color: 'marino', titulo: 'Recupera', texto: 'Coordinen la entrega en un lugar seguro del campus y confirma que lo recuperaste.' },
];

export default function ProcesoRecuperacion() {
  return (
    <section className="proceso-recuperacion" aria-labelledby="proceso-titulo" id="proceso-recuperacion">
      <div className="proceso-cabecera">
        <span className="visual-eyebrow">CÓMO FUNCIONA</span>
        <h2 id="proceso-titulo">Así recuperas tu objeto</h2>
        <p>Cuatro pasos, de la publicación a tus manos.</p>
      </div>
      <ol className="proceso-pasos">
        {PASOS.map((paso, i) => (
          <li key={paso.titulo} className="proceso-paso">
            <span className="proceso-numero" aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
            <span className={`acceso-findi-icono ${paso.color}`} aria-hidden="true"><Icono nombre={paso.icono} tamano={22} /></span>
            <h3>{paso.titulo}</h3>
            <p>{paso.texto}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
