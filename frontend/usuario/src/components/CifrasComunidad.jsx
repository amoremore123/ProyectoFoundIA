import { useEffect, useRef, useState } from 'react';
import Icono from './Icono';
import useContador from '../hooks/useContador';
import { listarObjetos } from '../services/api';

function Cifra({ valor, etiqueta, icono, color, visible }) {
  // El contador empieza recién cuando la franja entra en pantalla
  const animado = useContador(visible ? valor : 0, 1100);
  return (
    <div className="cifra-findi">
      <span className={`cifra-findi-icono ${color}`} aria-hidden="true"><Icono nombre={icono} tamano={22} /></span>
      <div>
        <strong>{valor == null ? '—' : animado.toLocaleString('es-PE')}</strong>
        <span>{etiqueta}</span>
      </div>
    </div>
  );
}

// Actividad de la comunidad en números, calculada con las publicaciones reales.
// Si la API no responde, la franja simplemente no se muestra.
export default function CifrasComunidad() {
  const [conteos, setConteos] = useState(null);
  const [visible, setVisible] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    let vigente = true;
    listarObjetos()
      .then((data) => {
        if (!vigente) return;
        const lista = Array.isArray(data) ? data : data?.results || [];
        setConteos({
          total: lista.length,
          esperando: lista.filter((objeto) => objeto.tipo === 'ENCONTRADO' && objeto.estado !== 'RECUPERADO').length,
          recuperados: lista.filter((objeto) => objeto.estado === 'RECUPERADO').length,
        });
      })
      .catch(() => {
        // Las cifras son un complemento: sin datos no se muestran
      });
    return () => {
      vigente = false;
    };
  }, []);

  useEffect(() => {
    const nodo = ref.current;
    if (!nodo) return undefined;
    if (!('IntersectionObserver' in window)) { setVisible(true); return undefined; }
    const observador = new IntersectionObserver(([entrada]) => {
      if (entrada.isIntersecting) { setVisible(true); observador.disconnect(); }
    }, { threshold: 0.4 });
    observador.observe(nodo);
    return () => observador.disconnect();
  }, [conteos]);

  if (!conteos) return null;

  return (
    <section className="cifras-findi" aria-label="Actividad de la comunidad" ref={ref}>
      <Cifra visible={visible} valor={conteos.total} etiqueta="Objetos registrados" icono="caja" color="naranja" />
      <Cifra visible={visible} valor={conteos.esperando} etiqueta="Esperando a su dueño" icono="buscar" color="azul" />
      <Cifra visible={visible} valor={conteos.recuperados} etiqueta="Objetos recuperados" icono="check" color="marino" />
    </section>
  );
}
