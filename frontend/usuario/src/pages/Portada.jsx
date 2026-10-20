import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CifrasComunidad from '../components/CifrasComunidad';
import ExplorarVisual from '../components/ExplorarVisual';
import HistorialObjetos from '../components/HistorialObjetos';
import GuiaCampus from '../components/GuiaCampus';
import Icono from '../components/Icono';
import Logo from '../components/Logo';
import SliderPortada from '../components/SliderPortada';
import CarruselHorizontal from '../components/CarruselHorizontal';
import PerfilComunidad from '../components/PerfilComunidad';
import PuntosCampus from '../components/PuntosCampus';
import { useAsistente } from '../context/AsistenteContext';
import { EXTRAS_HABILITADOS } from '../config/alcance';

export default function Portada() {
  const { abrir } = useAsistente();
  const navigate = useNavigate();
  const [pregunta, setPregunta] = useState('');
  const buscar = (event) => {
    event.preventDefault();
    if (EXTRAS_HABILITADOS) abrir(pregunta);
    else navigate(`/buscar?q=${encodeURIComponent(pregunta.trim())}`);
    setPregunta('');
  };

  return <div className="portada">
    <SliderPortada />

    <section className="buscador-portada" aria-labelledby="buscador-portada-titulo">
      <div className="buscador-portada-texto">
        <span className="visual-eyebrow">ESTUDIANTES Y PERSONAL TECSUP</span>
        <h1 id="buscador-portada-titulo">Encuentra lo que perdiste en el campus</h1>
        <p>Tu mochila, tus apuntes o las llaves. Revisa las publicaciones de la comunidad o cuéntanos qué estás buscando.</p>
      </div>
      <form className="portada-pregunta" onSubmit={buscar}>
        <Icono nombre="buscar" tamano={20} className="portada-pregunta-icono" />
        <input className="portada-pregunta-input" value={pregunta} onChange={(event) => setPregunta(event.target.value)} placeholder={EXTRAS_HABILITADOS ? 'Ej. Dejé mi mochila en la biblioteca' : 'Ej. mochila'} aria-label={EXTRAS_HABILITADOS ? 'Describe lo que perdiste para el asistente' : 'Buscar objeto por palabras clave'} />
        <button type="submit" className="btn btn-primario">{EXTRAS_HABILITADOS ? 'Ayúdame a buscar' : 'Buscar objetos'}</button>
      </form>
      <div className="buscador-portada-pie">
        <ul className="buscador-rapidos" aria-label="Búsquedas frecuentes">
          {['Mochila', 'Celular', 'Llaves', 'Audífonos', 'Carné'].map((objeto) => (
            <li key={objeto}><Link to={`/buscar?q=${encodeURIComponent(objeto)}`}>{objeto}</Link></li>
          ))}
        </ul>
        <div className="portada-acciones">
          <Link to={EXTRAS_HABILITADOS ? '/historial' : '/explorar'}>{EXTRAS_HABILITADOS ? 'Ver historial de objetos' : 'Ver publicaciones'} <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link>
          <Link to="/publicar">Publicar un objeto <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link>
        </div>
      </div>
    </section>

    {EXTRAS_HABILITADOS && <CifrasComunidad />}

    {EXTRAS_HABILITADOS && <CarruselHorizontal etiqueta="publicaciones">
      <HistorialObjetos resumen />
    </CarruselHorizontal>}

    <CarruselHorizontal etiqueta="objetos">
      <ExplorarVisual />
    </CarruselHorizontal>

    {EXTRAS_HABILITADOS && <PerfilComunidad />}
    <PuntosCampus />
    <GuiaCampus />

    <section className="ayuda-campus" aria-labelledby="titulo-ayuda">
      <div><h2 id="titulo-ayuda">¿Encontraste algo?</h2><p>Publica dónde lo encontraste y cómo es. Alguien en Tecsup podría estar buscándolo.</p></div>
      <Link to="/publicar" className="btn btn-primario">Publicar objeto <Icono nombre="flechaDer" tamano={16} /></Link>
    </section>

    <footer className="portada-footer">
      <div className="portada-footer-marca"><Logo tamano={28} /><p>Objetos perdidos y encontrados en la comunidad Tecsup.</p></div>
      <nav className="portada-footer-links" aria-label="Enlaces del sitio"><Link to={EXTRAS_HABILITADOS ? '/historial' : '/explorar'}>{EXTRAS_HABILITADOS ? 'Historial' : 'Publicaciones'}</Link><Link to="/buscar">Buscar</Link><a href="#como-funciona">Cómo funciona</a><a href="#preguntas-frecuentes">Preguntas frecuentes</a></nav>
      <p className="portada-footer-legal">© 2026 FoundIA · Proyecto integrador Tecsup</p>
    </footer>
  </div>;
}

