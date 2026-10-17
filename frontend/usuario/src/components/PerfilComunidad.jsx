import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';

const PERFILES = {
  estudiante: {
    etiqueta: 'Soy estudiante',
    titulo: 'Entre clases, talleres y laboratorios',
    texto: 'Si dejaste algo en el aula o en el taller, revisa primero lo que otros ya encontraron. Publica lo antes posible: los primeros días son clave.',
    pasos: [
      ['buscar', 'Busca por lugar: aula, biblioteca, laboratorio o cafetería.', '/buscar?tipo=ENCONTRADO'],
      ['mas', 'Publica tu objeto perdido con la fecha y un detalle único.', '/publicar'],
      ['campana', 'Activa tus notificaciones para enterarte de coincidencias.', '/notificaciones'],
    ],
  },
  personal: {
    etiqueta: 'Soy personal Tecsup',
    titulo: 'Docentes, seguridad y administrativos',
    texto: 'Si alguien te entregó un objeto o lo encontraste en tu área, publícalo como encontrado. Así su dueño lo ubica sin recorrer todo el campus.',
    pasos: [
      ['caja', 'Registra el objeto como encontrado e indica dónde está guardado.', '/publicar'],
      ['escudo', 'No muestres documentos ni datos personales en la foto.', '/publicar'],
      ['lista', 'Revisa tus publicaciones y márcalas al entregarlas.', '/mis-publicaciones'],
    ],
  },
};

// Pestañas accesibles: el contenido cambia según quién usa FoundIA.
export default function PerfilComunidad() {
  const [activo, setActivo] = useState('estudiante');
  const perfil = PERFILES[activo];
  const claves = Object.keys(PERFILES);

  const teclas = (e) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    const siguiente = claves[(claves.indexOf(activo) + (e.key === 'ArrowRight' ? 1 : claves.length - 1)) % claves.length];
    setActivo(siguiente);
    document.getElementById(`pestana-${siguiente}`)?.focus();
  };

  return (
    <section className="perfil-comunidad" aria-labelledby="perfil-comunidad-titulo">
      <div className="perfil-comunidad-cabecera">
        <span className="visual-eyebrow">PARA TODA LA COMUNIDAD</span>
        <h2 id="perfil-comunidad-titulo">¿Cómo te ayuda FoundIA?</h2>
      </div>

      <div className="perfil-pestanas" role="tablist" aria-label="Elige tu perfil" onKeyDown={teclas}>
        {claves.map((clave) => (
          <button
            key={clave}
            id={`pestana-${clave}`}
            type="button"
            role="tab"
            aria-selected={activo === clave}
            aria-controls="panel-perfil"
            tabIndex={activo === clave ? 0 : -1}
            className={`perfil-pestana${activo === clave ? ' activa' : ''}`}
            onClick={() => setActivo(clave)}
          >
            <Icono nombre={clave === 'estudiante' ? 'usuario' : 'escudo'} tamano={18} />
            {PERFILES[clave].etiqueta}
          </button>
        ))}
      </div>

      <div id="panel-perfil" role="tabpanel" aria-labelledby={`pestana-${activo}`} className="perfil-panel" key={activo}>
        <div>
          <h3>{perfil.titulo}</h3>
          <p>{perfil.texto}</p>
        </div>
        <ul className="perfil-pasos">
          {perfil.pasos.map(([icono, texto, enlace]) => (
            <li key={texto}>
              <Link to={enlace}>
                <span className="perfil-paso-icono" aria-hidden="true"><Icono nombre={icono} tamano={20} /></span>
                <span>{texto}</span>
                <Icono nombre="flechaDer" tamano={16} className="perfil-paso-flecha" />
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
