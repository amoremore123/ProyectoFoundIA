import { useRef } from 'react';
import Icono from './Icono';

// Convierte la cuadrícula interna (.grid-objetos o .explorar-visual-grid) en un carrusel
// deslizable con flechas. No toca los datos: solo envuelve al componente hijo.
export default function CarruselHorizontal({ children, etiqueta }) {
  const contenedor = useRef(null);

  const desplazar = (direccion) => {
    const pista = contenedor.current?.querySelector('.grid-objetos, .explorar-visual-grid');
    if (!pista) return;
    pista.scrollBy({ left: direccion * pista.clientWidth * 0.85, behavior: 'smooth' });
  };

  return (
    <div className="carrusel-horizontal" ref={contenedor}>
      {children}
      <div className="carrusel-flechas" role="group" aria-label={`Desplazar ${etiqueta}`}>
        <button type="button" className="slider-boton claro" onClick={() => desplazar(-1)} aria-label={`Ver ${etiqueta} anteriores`}>
          <Icono nombre="flechaIzq" tamano={18} />
        </button>
        <button type="button" className="slider-boton claro" onClick={() => desplazar(1)} aria-label={`Ver más ${etiqueta}`}>
          <Icono nombre="flechaDer" tamano={18} />
        </button>
      </div>
    </div>
  );
}
