import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import TextoLegal from '../components/TextoLegal';
import Icono from '../components/Icono';
import { getDocumentosLegales } from '../services/api';

const PAGINAS = {
  privacidad: { titulo: 'Política de privacidad', campo: 'privacidad', otra: { ruta: '/terminos', texto: 'Términos de uso' } },
  terminos: { titulo: 'Términos de uso', campo: 'terminos', otra: { ruta: '/privacidad', texto: 'Política de privacidad' } },
};

// H48 - Política de privacidad y términos públicos: se leen sin iniciar sesión,
// con la misma versión vigente que se acepta en el registro (H33).
export default function Legal({ tipo }) {
  const pagina = PAGINAS[tipo] ?? PAGINAS.privacidad;
  const [documentos, setDocumentos] = useState(null);
  const [estado, setEstado] = useState('cargando');
  const peticion = useRef(null);

  const cargar = useCallback(async () => {
    peticion.current?.abort();
    const control = new AbortController();
    peticion.current = control;
    setEstado('cargando');
    try {
      const data = await getDocumentosLegales({ signal: control.signal });
      if (control.signal.aborted) return;
      if (!data?.version || !data?.[pagina.campo]) throw new Error('Documento incompleto');
      setDocumentos(data);
      setEstado('ok');
    } catch (err) {
      if (control.signal.aborted || err?.name === 'CanceledError' || err?.name === 'AbortError') return;
      setDocumentos(null);
      setEstado('error');
    }
  }, [pagina.campo]);

  useEffect(() => {
    document.title = `${pagina.titulo} · FoundIA`;
    cargar();
    return () => peticion.current?.abort();
  }, [cargar, pagina.titulo]);

  return (
    <div className="pagina legal-pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">{pagina.titulo}</h1>
      </div>

      {estado === 'cargando' && (
        <p className="legal-estado" role="status">Cargando el documento vigente...</p>
      )}

      {estado === 'error' && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> No se pudo cargar el documento. Revisa tu conexión e inténtalo de nuevo.
          <button type="button" className="btn-enlace" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {estado === 'ok' && (
        <>
          <article className="legal-pagina-texto" aria-label={pagina.titulo}>
            <TextoLegal texto={documentos[pagina.campo]} nivel={2} />
          </article>
        </>
      )}

      <nav className="legal-pagina-enlaces" aria-label="Documentos legales">
        <Link to={pagina.otra.ruta}>Ver {pagina.otra.texto}</Link>
        <Link to="/cookies">Cookies y almacenamiento</Link>
        <Link to="/">Volver al inicio</Link>
      </nav>
    </div>
  );
}
