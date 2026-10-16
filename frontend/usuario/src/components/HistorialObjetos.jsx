import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listarObjetos } from '../services/api';
import ObjetoCard from './ObjetoCard';
import Cargando from './Cargando';
import MensajeError from './MensajeError';
import Icono from './Icono';

const filtros = [['', 'Todos'], ['PERDIDO', 'Perdidos'], ['ENCONTRADO', 'Encontrados'], ['RECUPERADO', 'Recuperados']];

export default function HistorialObjetos({ resumen = false }) {
  const [objetos, setObjetos] = useState([]);
  const [filtro, setFiltro] = useState('');
  const [limite, setLimite] = useState(12);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError('');
    listarObjetos().then((data) => {
      if (vigente) setObjetos(Array.isArray(data) ? data : data?.results || []);
    }).catch(() => {
      if (vigente) setError('No pudimos cargar las publicaciones. Revisa tu conexión e inténtalo de nuevo.');
    }).finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [intento]);

  const filtrados = objetos.filter((objeto) => !filtro || (filtro === 'RECUPERADO' ? objeto.estado === filtro : objeto.tipo === filtro));
  const visibles = filtrados.slice(0, resumen ? 6 : limite);
  const Titulo = resumen ? 'h2' : 'h1';

  return <section className="historial-objetos" aria-label="Historial de objetos" aria-busy={cargando}>
    <div className="seccion-header">
      <div><span className="visual-eyebrow">PUBLICACIONES DE LA COMUNIDAD</span><Titulo>Historial de objetos</Titulo><p>Revisa lo que se perdió, se encontró o ya volvió con su dueño.</p></div>
      {resumen && <Link to="/historial" className="historial-enlace">Ver historial completo <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link>}
    </div>
    <div className="historial-filtros" aria-label="Filtrar historial">
      {filtros.map(([valor, texto]) => <button type="button" key={valor} aria-pressed={filtro === valor} onClick={() => { setFiltro(valor); setLimite(12); }}>{texto}</button>)}
    </div>
    {cargando ? <Cargando texto="Cargando publicaciones..." /> : error ? <MensajeError mensaje={error} onReintentar={() => setIntento((valor) => valor + 1)} /> : <>
      <p className="historial-contador" role="status">{filtrados.length} {filtrados.length === 1 ? 'publicación' : 'publicaciones'}</p>
      {filtrados.length === 0 ? <div className="estado-vacio"><p>{filtro ? 'Todavía no hay publicaciones en esta sección.' : 'Todavía no hay publicaciones en la comunidad.'}</p><Link to="/publicar" className="btn btn-secundario">Publicar un objeto</Link></div> : <div className="grid-objetos">{visibles.map((objeto) => <ObjetoCard key={objeto.id} objeto={objeto} />)}</div>}
      {!resumen && filtrados.length > limite && <button className="btn btn-secundario historial-mas" onClick={() => setLimite((valor) => valor + 12)}>Ver más publicaciones</button>}
    </>}
  </section>;
}
