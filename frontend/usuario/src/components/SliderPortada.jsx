import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';
import { useAsistente } from '../context/AsistenteContext';

const INTERVALO = 6500;

const DIAPOSITIVAS = [
  {
    imagen: '/imagenes/tecsup-campus.png',
    posicion: 'center 45%',
    etiqueta: 'Comunidad Tecsup',
    titulo: <>¿Lo perdiste en el campus? <span>Busquémoslo juntos.</span></>,
    texto: 'Estudiantes y personal de Tecsup publican aquí lo que pierden y lo que encuentran.',
    accion: { texto: 'Ver objetos encontrados', enlace: '/buscar?tipo=ENCONTRADO' },
  },
  {
    imagen: '/imagenes/mochila-photo.jpg',
    posicion: 'center 60%',
    etiqueta: '¿Encontraste algo?',
    titulo: <>Ayuda a que vuelva <span>con su dueño.</span></>,
    texto: 'Publícalo en menos de un minuto: qué es, dónde lo encontraste y cuándo.',
    accion: { texto: 'Publicar un objeto', enlace: '/publicar' },
  },
  {
    imagen: '/imagenes/llaves-photo.jpg',
    posicion: 'center 50%',
    etiqueta: 'Asistente con IA',
    titulo: <>Cuéntanos qué perdiste <span>y te ayudamos.</span></>,
    texto: 'Descríbelo con tus palabras y el asistente busca publicaciones parecidas.',
    accion: { texto: 'Hablar con el asistente', asistente: true },
  },
];

const movimientoReducido = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// Carrusel principal de la portada: cambia solo, se pausa al pasar el mouse o al enfocar,
// y tiene controles accesibles (anterior, siguiente, pausa y puntos).
export default function SliderPortada() {
  const { abrir } = useAsistente();
  const [actual, setActual] = useState(0);
  const [pausado, setPausado] = useState(movimientoReducido);
  const [enFoco, setEnFoco] = useState(false);
  const total = DIAPOSITIVAS.length;

  const ir = useCallback((indice) => setActual((indice + total) % total), [total]);

  useEffect(() => {
    if (pausado || enFoco) return undefined;
    const t = setTimeout(() => ir(actual + 1), INTERVALO);
    return () => clearTimeout(t);
  }, [actual, pausado, enFoco, ir]);

  return (
    <section
      className={`slider-portada${pausado || enFoco ? ' detenido' : ''}`}
      aria-roledescription="carrusel"
      aria-label="Destacados de FoundIA"
      onMouseEnter={() => setEnFoco(true)}
      onMouseLeave={() => setEnFoco(false)}
      onFocus={() => setEnFoco(true)}
      onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setEnFoco(false); }}
      onKeyDown={(e) => {
        if (e.key === 'ArrowRight') ir(actual + 1);
        if (e.key === 'ArrowLeft') ir(actual - 1);
      }}
    >
      {DIAPOSITIVAS.map((d, i) => (
        <div
          key={d.imagen}
          className={`slide${i === actual ? ' activa' : ''}`}
          role="group"
          aria-roledescription="diapositiva"
          aria-label={`${i + 1} de ${total}`}
          aria-hidden={i !== actual}
        >
          <img className="slide-imagen" src={d.imagen} alt="" style={{ objectPosition: d.posicion }} loading={i === 0 ? 'eager' : 'lazy'} />
          <div className="slide-velo" />
          <div className="slide-contenido">
            <span className="slide-etiqueta">{d.etiqueta}</span>
            <p className="slide-titulo">{d.titulo}</p>
            <p className="slide-texto">{d.texto}</p>
            {d.accion.asistente ? (
              <button type="button" className="btn slide-boton" onClick={() => abrir()} tabIndex={i === actual ? 0 : -1}>
                {d.accion.texto} <Icono nombre="flechaDer" tamano={16} />
              </button>
            ) : (
              <Link to={d.accion.enlace} className="btn slide-boton" tabIndex={i === actual ? 0 : -1}>
                {d.accion.texto} <Icono nombre="flechaDer" tamano={16} />
              </Link>
            )}
          </div>
        </div>
      ))}

      <div className="slider-controles">
        <div className="slider-puntos">
          {DIAPOSITIVAS.map((d, i) => (
            <button
              key={d.imagen}
              type="button"
              className={`slider-punto${i === actual ? ' activo' : ''}`}
              aria-label={`Ir a la diapositiva ${i + 1}`}
              aria-current={i === actual}
              onClick={() => ir(i)}
            >
              <span style={{ animationDuration: `${INTERVALO}ms` }} />
            </button>
          ))}
        </div>
        <div className="slider-botones">
          <button type="button" className="slider-boton" onClick={() => setPausado((p) => !p)} aria-label={pausado ? 'Reanudar el carrusel' : 'Pausar el carrusel'}>
            {pausado ? (
              <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4l13 8-13 8z" fill="currentColor" /></svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 4h4v16H6zM14 4h4v16h-4z" fill="currentColor" /></svg>
            )}
          </button>
          <button type="button" className="slider-boton" onClick={() => ir(actual - 1)} aria-label="Diapositiva anterior">
            <Icono nombre="flechaIzq" tamano={18} />
          </button>
          <button type="button" className="slider-boton" onClick={() => ir(actual + 1)} aria-label="Diapositiva siguiente">
            <Icono nombre="flechaDer" tamano={18} />
          </button>
        </div>
      </div>
    </section>
  );
}
