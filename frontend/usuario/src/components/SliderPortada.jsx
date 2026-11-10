import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icono from './Icono';
import { useAsistente } from '../context/AsistenteContext';
const IMAGEN_FINDI_HERO = '/findi/findi-hero.webp';

const INTERVALO = 7000;

// FINDI abre el carrusel; las siguientes escenas presentan la comunidad y sus objetos.
const DIAPOSITIVAS = [
  {
    id: 'findi',
    findi: true,
    etiqueta: 'FINDI',
    titulo: <>Tu asistente<br /> inteligente<br /> en <span>TECSUP</span></>,
    texto: <>Reporta, busca y recupera tus objetos perdidos con la ayuda de la IA de <strong>FoundIA</strong>.</>,
  },
  {
    id: 'campus',
    imagen: '/imagenes/campus.jpg',
    posicion: 'center 50%',
    etiqueta: 'Comunidad Tecsup',
    titulo: <>¿Lo perdiste en el campus? <span>Busquémoslo juntos.</span></>,
    texto: 'Estudiantes y personal de Tecsup publican aquí lo que pierden y lo que encuentran.',
    accion: { texto: 'Ver objetos encontrados', enlace: '/buscar?tipo=ENCONTRADO' },
  },
  {
    id: 'mochila',
    imagen: '/imagenes/mochila-photo.jpg',
    posicion: 'center 60%',
    etiqueta: '¿Encontraste algo?',
    titulo: <>Ayuda a que vuelva <span>con su dueño.</span></>,
    texto: 'Publícalo en menos de un minuto: qué es, dónde lo encontraste y cuándo.',
    accion: { texto: 'Publicar un objeto', enlace: '/publicar' },
  },
  {
    id: 'llaves',
    imagen: '/imagenes/llaves-photo.jpg',
    posicion: 'center 50%',
    etiqueta: 'Asistente FINDI',
    titulo: <>Cuéntale a FINDI qué perdiste <span>y te ayuda.</span></>,
    texto: 'Descríbelo con tus palabras y FINDI busca publicaciones parecidas.',
    accion: { texto: 'Hablar con FINDI', asistente: true },
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
  const figuraRef = useRef(null);

  // FINDI sigue al cursor con un desplazamiento suave (parallax). Cada cuadro avanza desde
  // la posición actual hacia el objetivo, así el movimiento nunca salta aunque el cursor cambie.
  useEffect(() => {
    const figura = figuraRef.current;
    if (!figura || movimientoReducido() || !window.matchMedia?.('(pointer: fine)').matches) return undefined;
    const carrusel = figura.closest('.slider-portada');
    let objetivo = { x: 0, y: 0 };
    const actualPos = { x: 0, y: 0 };
    let cuadro = 0;
    const animar = () => {
      actualPos.x += (objetivo.x - actualPos.x) * 0.08;
      actualPos.y += (objetivo.y - actualPos.y) * 0.08;
      figura.style.setProperty('--findi-x', `${actualPos.x.toFixed(2)}px`);
      figura.style.setProperty('--findi-y', `${actualPos.y.toFixed(2)}px`);
      cuadro = Math.abs(objetivo.x - actualPos.x) + Math.abs(objetivo.y - actualPos.y) > 0.05 ? requestAnimationFrame(animar) : 0;
    };
    const mover = (event) => {
      const caja = carrusel.getBoundingClientRect();
      objetivo = {
        x: ((event.clientX - caja.left) / caja.width - 0.5) * 18,
        y: ((event.clientY - caja.top) / caja.height - 0.5) * 12,
      };
      if (!cuadro) cuadro = requestAnimationFrame(animar);
    };
    const salir = () => { objetivo = { x: 0, y: 0 }; if (!cuadro) cuadro = requestAnimationFrame(animar); };
    carrusel.addEventListener('pointermove', mover);
    carrusel.addEventListener('pointerleave', salir);
    return () => {
      carrusel.removeEventListener('pointermove', mover);
      carrusel.removeEventListener('pointerleave', salir);
      cancelAnimationFrame(cuadro);
    };
  }, []);

  useEffect(() => {
    if (pausado || enFoco) return undefined;
    const t = setTimeout(() => ir(actual + 1), INTERVALO);
    return () => clearTimeout(t);
  }, [actual, pausado, enFoco, ir]);

  return (
    <section
      className={`slider-portada${actual === 0 ? ' mostrando-findi' : ''}${pausado || enFoco ? ' detenido' : ''}`}
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
      {DIAPOSITIVAS.map((d, i) => {
        const activa = i === actual;
        const foco = activa ? 0 : -1;
        return (
          <div
            key={d.id}
            className={`slide${d.findi ? ' slide-findi' : ''}${activa ? ' activa' : ''}`}
            role="group"
            aria-roledescription="diapositiva"
            aria-label={`${i + 1} de ${total}`}
            aria-hidden={!activa}
          >
            {d.imagen && (
              <>
                <img className="slide-imagen" src={d.imagen} alt="" style={{ objectPosition: d.posicion }} loading="lazy" />
                <div className="slide-velo" />
              </>
            )}

            <div className="slide-contenido">
              <span className={d.findi ? 'chip-findi' : 'slide-etiqueta'}>
                {d.findi && <Icono nombre="chispa" tamano={14} />} {d.etiqueta}
              </span>
              {d.findi ? <h1 className="slide-titulo">{d.titulo}</h1> : <p className="slide-titulo">{d.titulo}</p>}
              <p className="slide-texto">{d.texto}</p>

              {d.findi ? (
                <div className="hero-findi-acciones">
                  <Link to="/publicar" className="btn btn-amarillo" tabIndex={foco}><Icono nombre="mas" tamano={18} /> Reportar objeto</Link>
                  <Link to="/buscar" className="btn btn-marino" tabIndex={foco}><Icono nombre="buscar" tamano={18} /> Buscar objeto</Link>
                </div>
              ) : d.accion.asistente ? (
                <button type="button" className="btn slide-boton" onClick={() => abrir()} tabIndex={foco}>
                  {d.accion.texto} <Icono nombre="flechaDer" tamano={16} />
                </button>
              ) : (
                <Link to={d.accion.enlace} className="btn slide-boton" tabIndex={foco}>
                  {d.accion.texto} <Icono nombre="flechaDer" tamano={16} />
                </Link>
              )}
              {d.findi && <p className="hero-findi-nota"><Icono nombre="escudo" tamano={15} /> Hecho para estudiantes y personal de Tecsup</p>}
            </div>

            {d.findi && (
              <figure className="slide-findi-figura" ref={figuraRef}>
                <div className="findi-escenario">
                  <span className="findi-estela" aria-hidden="true" />
                  <img className="findi-imagen" src={IMAGEN_FINDI_HERO} alt="FINDI, el asistente de FoundIA, presentando una búsqueda" width="900" height="952" />
                  <button type="button" className="findi-globo" onClick={() => abrir()} tabIndex={foco}>
                    <span className="findi-globo-saludo">¡Hola! <span aria-hidden="true">👋</span></span>
                    <span>Soy <strong>FINDI</strong>, tu asistente de FoundIA. ¿En qué puedo ayudarte?</span>
                  </button>
                  <div className="findi-objetos" aria-label="Buscar por objeto">
                    <span className="findi-objetos-etiqueta">¿Qué estás buscando?</span>
                    <div>
                      <Link to="/buscar?q=Audífonos" tabIndex={foco} aria-label="Buscar audífonos"><img src="/imagenes/audifonos.jpg" alt="" /><span>Audífonos</span></Link>
                      <Link to="/buscar?q=Celular" tabIndex={foco} aria-label="Buscar celular"><img src="/imagenes/celular.jpg" alt="" /><span>Celular</span></Link>
                      <Link to="/buscar?q=Mochila" tabIndex={foco} aria-label="Buscar mochila"><img src="/imagenes/mochila.jpg" alt="" /><span>Mochila</span></Link>
                    </div>
                  </div>
                </div>
              </figure>
            )}
          </div>
        );
      })}

      <div className="slider-controles">
        <div className="slider-puntos">
          {DIAPOSITIVAS.map((d, i) => (
            <button
              key={d.id}
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
