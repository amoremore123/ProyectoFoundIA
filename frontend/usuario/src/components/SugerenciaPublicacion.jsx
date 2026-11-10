import { useEffect, useState } from 'react';
import Icono from './Icono';
import { mensajeError, sugerirPublicacion } from '../services/api';
import { MAX_DESCRIPCION } from '../utils/validaciones';

// H49: FINDI sugiere la categoría y una descripción más clara. Solo sugiere:
// nada cambia en el formulario hasta que la persona pulsa «Usar sugerencia», y nada se publica desde aquí.
export default function SugerenciaPublicacion({ nombre, descripcion, tipo, categorias, onAplicar }) {
  const [estado, setEstado] = useState('inicial'); // inicial | cargando | lista | error
  const [error, setError] = useState('');
  const [sugerencia, setSugerencia] = useState(null);
  const [texto, setTexto] = useState('');

  const suficiente = `${nombre} ${descripcion}`.replace(/\s+/g, '').length >= 3;

  // Si la persona cambia lo que escribió, la sugerencia anterior deja de valer
  useEffect(() => {
    if (estado === 'lista' || estado === 'error') {
      setEstado('inicial');
      setSugerencia(null);
      setError('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nombre, tipo]);

  const pedir = async () => {
    setEstado('cargando');
    setError('');
    try {
      const r = await sugerirPublicacion({ nombre: nombre.trim(), descripcion: descripcion.trim(), tipo });
      // La categoría debe seguir activa en el catálogo que ve la web
      const activa = r?.categoria && categorias.some((c) => c.id === r.categoria.id && c.estado !== false);
      const limpia = { ...r, categoria: activa ? r.categoria : null };
      if (!limpia.categoria && !limpia.descripcion) {
        setError('FINDI no encontró nada que mejorar. Puedes publicar tal como está.');
        setEstado('error');
        return;
      }
      setSugerencia(limpia);
      setTexto(limpia.descripcion || '');
      setEstado('lista');
    } catch (e) {
      setError(mensajeError(e, 'No se pudo obtener la sugerencia. Puedes publicar sin ella'));
      setEstado('error');
    }
  };

  const usar = () => {
    onAplicar({
      categoriaId: sugerencia.categoria ? String(sugerencia.categoria.id) : null,
      descripcion: sugerencia.descripcion ? texto.trim() : null,
    });
    setEstado('inicial');
    setSugerencia(null);
  };

  const descartar = () => {
    setEstado('inicial');
    setSugerencia(null);
    setError('');
  };

  return (
    <div className="sugerencia-ia" aria-live="polite">
      {estado !== 'lista' && (
        <div className="sugerencia-ia-fila">
          <button type="button" className="btn btn-secundario btn-mini" onClick={pedir}
            disabled={!suficiente || estado === 'cargando'}>
            <Icono nombre="chispa" tamano={16} /> {estado === 'cargando' ? 'FINDI está pensando...' : 'Sugerir categoría y descripción'}
          </button>
          <span className="nota-suave">{suficiente ? 'Opcional: FINDI solo sugiere, tú decides.' : 'Escribe el nombre del objeto para pedir una sugerencia.'}</span>
        </div>
      )}
      {estado === 'error' && <p className="campo-error" role="alert">{error}</p>}

      {estado === 'lista' && sugerencia && (
        <section className="sugerencia-ia-tarjeta" aria-labelledby="sugerencia-titulo">
          <h3 id="sugerencia-titulo"><Icono nombre="chispa" tamano={16} /> Sugerencia de FINDI</h3>
          {sugerencia.categoria && (
            <p>Categoría: <strong>{sugerencia.categoria.nombre}</strong></p>
          )}
          {sugerencia.descripcion && (
            <label className="campo">
              <span className="campo-label">Descripción sugerida (puedes editarla)</span>
              <textarea className="input textarea" rows={3} maxLength={MAX_DESCRIPCION} value={texto}
                onChange={(e) => setTexto(e.target.value)} />
            </label>
          )}
          <p className="nota-suave">{sugerencia.aviso || 'Es solo una sugerencia: revísala antes de publicar.'}</p>
          <div className="sugerencia-ia-acciones">
            <button type="button" className="btn btn-primario btn-mini" onClick={usar}
              disabled={Boolean(sugerencia.descripcion) && texto.trim().length < 10}>
              Usar sugerencia
            </button>
            <button type="button" className="btn btn-fantasma btn-mini" onClick={descartar}>Descartar</button>
          </div>
        </section>
      )}
    </div>
  );
}
