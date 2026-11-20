import { Link } from 'react-router-dom';
import Icono from '../Icono';
import { IMAGEN_FINDI } from '../../config/findi';

// Llamado final antes del pie. /publicar está protegida: sin sesión, la ruta lleva a /login.
export default function BannerPublicar() {
  return (
    <section className="ini-banner" aria-labelledby="banner-titulo">
      <img className="ini-banner-findi" src={IMAGEN_FINDI} alt="" width="740" height="760" loading="lazy" />
      <span className="ini-banner-destello" aria-hidden="true" />
      <div className="ini-banner-texto">
        <h2 id="banner-titulo">¿Encontraste <span className="ini-resalte">algo</span>?</h2>
        <p>Publica dónde lo encontraste y cómo es. Alguien en Tecsup podría estar buscándolo.</p>
      </div>
      <Link to="/publicar" className="btn btn-amarillo ini-banner-boton">Publicar objeto <Icono nombre="flechaDer" tamano={16} /></Link>
    </section>
  );
}
