import { useEffect, useState } from 'react';
import useContador from '../hooks/useContador';
import { listarObjetos } from '../services/api';

function Cifra({ valor, etiqueta }) {
  const animado = useContador(valor);
  return (
    <div>
      <strong>{valor == null ? '—' : animado}</strong>
      <span>{etiqueta}</span>
    </div>
  );
}

// Actividad de la comunidad en números, con contadores animados.
// Si la API no responde, la sección simplemente no se muestra.
export default function CifrasComunidad() {
  const [conteos, setConteos] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarObjetos()
      .then((data) => {
        if (!vigente) return;
        const lista = Array.isArray(data) ? data : data?.results || [];
        const porTipo = (tipo) => lista.filter((objeto) => objeto.tipo === tipo).length;
        setConteos({
          total: lista.length,
          perdidos: porTipo('PERDIDO'),
          encontrados: porTipo('ENCONTRADO'),
          recuperados: lista.filter((objeto) => objeto.estado === 'RECUPERADO').length,
        });
      })
      .catch(() => {
        // Las cifras son decorativas: sin datos no se muestran
      });
    return () => {
      vigente = false;
    };
  }, []);

  if (!conteos) return null;

  return (
    <section className="portada-cifras" aria-label="Actividad de la comunidad">
      <Cifra valor={conteos.total} etiqueta="Publicaciones de la comunidad" />
      <Cifra valor={conteos.perdidos} etiqueta="Objetos reportados perdidos" />
      <Cifra valor={conteos.encontrados} etiqueta="Objetos esperando a su dueño" />
      <Cifra valor={conteos.recuperados} etiqueta="Casos ya recuperados" />
    </section>
  );
}
