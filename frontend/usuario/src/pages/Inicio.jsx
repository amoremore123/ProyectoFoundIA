import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ObjetoCard from '../components/ObjetoCard';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import useCategorias from '../hooks/useCategorias';
import { listarObjetos, mensajeError } from '../services/api';

export default function Inicio() {
  const navigate = useNavigate();

  const [q, setQ] = useState('');
  const [categoriaId, setCategoriaId] = useState('');
  const [ubicacion, setUbicacion] = useState('');
  const { categorias, cargandoCategorias, errorCategorias, reintentarCategorias } = useCategorias();
  const [recientes, setRecientes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const cargarRecientes = async () => {
    setCargando(true);
    setError('');
    try {
      const data = await listarObjetos({ estado: 'ACTIVO' });
      const lista = Array.isArray(data) ? data : data?.results || [];
      setRecientes(lista.slice(0, 6));
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar los objetos'));
      setRecientes([]);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => {
    cargarRecientes();
  }, []);

  const enviarBusqueda = (e) => {
    e.preventDefault();
    const params = new URLSearchParams();
    if (q.trim()) params.set('q', q.trim());
    if (categoriaId) params.set('categoriaId', categoriaId);
    if (ubicacion.trim()) params.set('ubicacion', ubicacion.trim());
    navigate(`/buscar?${params.toString()}`);
  };

  return (
    <div className="pagina">
      <section className="hero">
        <span className="hero-etiqueta">✨ Objetos perdidos y encontrados</span>
        <h1>Perdiste algo. Encuéntralo.</h1>
        <p>Publica lo que perdiste o lo que encontraste y ayúdale a otros de la comunidad.</p>
      </section>

      <form className="buscador-card" onSubmit={enviarBusqueda}>
        <input
          type="search"
          className="input"
          placeholder="🔍 Buscar objeto..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Buscar objeto"
        />
        <div className="filtros-rapidos">
          <label className="campo">
            <span className="campo-label">Categoría</span>
            <select
              className="input"
              value={categoriaId}
              onChange={(e) => setCategoriaId(e.target.value)}
              disabled={cargandoCategorias || !!errorCategorias}
            >
              <option value="">{cargandoCategorias ? 'Cargando categorías...' : 'Todas'}</option>
              {categorias.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}{c.estado === false ? ' (desactivada)' : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            <span className="campo-label">Ubicación</span>
            <input
              type="text"
              className="input"
              placeholder="Ej. Aula 204"
              value={ubicacion}
              onChange={(e) => setUbicacion(e.target.value)}
            />
          </label>
          <button type="submit" className="btn btn-primario filtros-buscar">
            Buscar
          </button>
        </div>
        <MensajeError mensaje={errorCategorias} onReintentar={reintentarCategorias} />
      </form>

      <section className="seccion">
        <div className="seccion-header">
          <h2>Objetos recientes</h2>
          <button type="button" className="btn btn-primario" onClick={() => navigate('/publicar')}>
            + Publicar objeto
          </button>
        </div>

        <MensajeError mensaje={error} onReintentar={cargarRecientes} />

        {cargando ? (
          <Cargando />
        ) : recientes.length === 0 && !error ? (
          <div className="estado-vacio">
            <span className="estado-vacio-icono" aria-hidden="true">
              🗂️
            </span>
            <p>Todavía no hay publicaciones.</p>
            <button type="button" className="btn btn-secundario" onClick={() => navigate('/publicar')}>
              Publicar el primero
            </button>
          </div>
        ) : (
          <div className="grid-objetos">
            {recientes.map((obj) => (
              <ObjetoCard key={obj.id} objeto={obj} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
