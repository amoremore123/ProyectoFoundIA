import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CifrasComunidad from '../components/CifrasComunidad';
import Icono from '../components/Icono';
import AccesosFindi from '../components/AccesosFindi';
import SliderPortada from '../components/SliderPortada';
import PerfilComunidad from '../components/PerfilComunidad';
import ProcesoRecuperacion from '../components/ProcesoRecuperacion';
import PuntosCampus from '../components/PuntosCampus';
import { useAsistente } from '../context/AsistenteContext';
import { EXTRAS_HABILITADOS } from '../config/alcance';
import ObjetosRecientes from '../components/inicio/ObjetosRecientes';
import ConsejosPublicacion from '../components/inicio/ConsejosPublicacion';
import PreguntasFrecuentes from '../components/inicio/PreguntasFrecuentes';
import BannerPublicar from '../components/inicio/BannerPublicar';

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
    <div className="portada-escenario">
      <SliderPortada />
      <AccesosFindi />
      <CifrasComunidad />
    </div>

    <section className="buscador-portada" aria-labelledby="buscador-portada-titulo">
      <div className="buscador-portada-texto">
        <span className="visual-eyebrow">ESTUDIANTES Y PERSONAL TECSUP</span>
        <h2 id="buscador-portada-titulo">Encuentra lo que perdiste en el campus</h2>
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


    <div className="ini">
      <ProcesoRecuperacion />
      <ObjetosRecientes />
      {EXTRAS_HABILITADOS && <PerfilComunidad />}
      <PuntosCampus />
      <ConsejosPublicacion />
      <PreguntasFrecuentes />
      <BannerPublicar />
    </div>
  </div>;
}

