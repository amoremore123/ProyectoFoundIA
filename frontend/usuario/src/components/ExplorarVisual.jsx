import { Link } from 'react-router-dom';
import { FotoReferencial } from './ImagenObjeto';
import Icono from './Icono';

export default function ExplorarVisual() {
  return <section className="explorar-visual" aria-label="Búsquedas por objeto">
    <div className="explorar-visual-cabecera"><div><span className="visual-eyebrow">EXPLORA POR OBJETO</span><h2>¿Qué estás buscando?</h2></div><Link to="/buscar">Ver todo <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link></div>
    <div className="explorar-visual-grid">
      {['Mochila', 'Celular', 'Llaves', 'Audífonos', 'Lentes', 'Billetera'].map((nombre) => <Link key={nombre} to={`/buscar?q=${encodeURIComponent(nombre)}`} className="categoria-visual"><FotoReferencial nombre={nombre}/><span>{nombre} <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></span></Link>)}
    </div>
  </section>;
}
