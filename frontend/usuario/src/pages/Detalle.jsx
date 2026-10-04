import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import ChipTipo from '../components/ChipTipo';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import { getCoincidencias, getObjeto, fotoUrl, mensajeError, statusDe } from '../services/api';

function fechaLarga(valor) {
  if (!valor) return '—';
  const soloFecha = String(valor).split('T')[0];
  const [anio, mes, dia] = soloFecha.split('-');
  if (!anio || !mes || !dia) return soloFecha;
  return `${dia}/${mes}/${anio}`;
}

export default function Detalle() {
  const { id } = useParams();
  const [objeto, setObjeto] = useState(null);
  const [coincidencias, setCoincidencias] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [cargandoCoincidencias, setCargandoCoincidencias] = useState(true);
  const [error, setError] = useState('');
  const [noEncontrado, setNoEncontrado] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    setNoEncontrado(false);
    try {
      const data = await getObjeto(id);
      setObjeto(data);
    } catch (e) {
      if (statusDe(e) === 404) {
        setNoEncontrado(true);
      } else {
        setError(mensajeError(e, 'No se pudo cargar el objeto'));
      }
    } finally {
      setCargando(false);
    }
  }, [id]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  useEffect(() => {
    let vivo = true;
    setCargandoCoincidencias(true);
    (async () => {
      try {
        const data = await getCoincidencias(id);
        if (vivo) setCoincidencias(Array.isArray(data) ? data : data?.results || []);
      } catch {
        if (vivo) setCoincidencias([]);
      } finally {
        if (vivo) setCargandoCoincidencias(false);
      }
    })();
    return () => {
      vivo = false;
    };
  }, [id]);

  if (cargando) return <Cargando />;

  if (noEncontrado) {
    return (
      <div className="pagina">
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true">
            🕵️
          </span>
          <p>Objeto no encontrado</p>
        </div>
      </div>
    );
  }

  if (error || !objeto) {
    return (
      <div className="pagina">
        <MensajeError mensaje={error || 'No se pudo cargar el objeto'} onReintentar={cargar} />
      </div>
    );
  }

  const foto = objeto.fotos?.length ? fotoUrl(objeto.fotos[0]) : '';
  const autor = objeto.publicadoPor
    ? `${objeto.publicadoPor.nombre} ${objeto.publicadoPor.apellido || ''}`.trim()
    : '—';

  return (
    <div className="pagina">
      <div className="tarjeta detalle-card">
        <div className="detalle-media">
          {foto ? (
            <img src={foto} alt={objeto.nombre} />
          ) : (
            <div className="objeto-placeholder detalle-placeholder">
              <span aria-hidden="true">📦</span>
              <span>Sin foto</span>
            </div>
          )}
        </div>

        <div className="detalle-cuerpo">
          <ChipTipo tipo={objeto.tipo} />
          <h1 className="detalle-titulo">{objeto.nombre}</h1>
          <p className="detalle-descripcion">{objeto.descripcion || 'Sin descripción.'}</p>

          <div className="datos-grid">
            <div className="dato">
              <span className="dato-label">📍 Ubicación</span>
              <span className="dato-valor">{objeto.ubicacion || '—'}</span>
            </div>
            <div className="dato">
              <span className="dato-label">📅 Fecha</span>
              <span className="dato-valor">{fechaLarga(objeto.fechaObjeto)}</span>
            </div>
            <div className="dato">
              <span className="dato-label">🗂️ Categoría</span>
              <span className="dato-valor">{objeto.categoria?.nombre || '—'}</span>
            </div>
            <div className="dato">
              <span className="dato-label">👤 Publicado por</span>
              <span className="dato-valor">{autor}</span>
            </div>
          </div>

          <button type="button" className="btn btn-secundario btn-bloque" disabled>
            Contactar
          </button>
          <p className="nota-suave">Disponible próximamente</p>
        </div>
      </div>

      <section className="seccion">
        <h2>Coincidencias sugeridas</h2>

        {cargandoCoincidencias ? (
          <Cargando texto="Buscando coincidencias..." />
        ) : coincidencias.length === 0 ? (
          <div className="estado-vacio estado-vacio-sutil">
            <p>No hay coincidencias sugeridas por ahora.</p>
          </div>
        ) : (
          <ul className="lista-coincidencias">
            {coincidencias.map((c) => (
              <li key={c.objetoId} className="tarjeta coincidencia-card">
                <div className="coincidencia-top">
                  <ChipTipo tipo={c.tipo} />
                  <span
                    className={`chip-nivel ${c.nivel === 'ALTA' ? 'nivel-alta' : 'nivel-media'}`}
                  >
                    {c.nivel}
                  </span>
                </div>
                <h3 className="coincidencia-nombre">{c.nombre}</h3>
                <p className="objeto-card-linea">📍 {c.ubicacion || 'Sin ubicación'}</p>
                <p className="objeto-card-linea">📅 {fechaLarga(c.fechaObjeto)}</p>
                <div className="coincidencia-barra">
                  <div
                    className="coincidencia-barra-llenado"
                    style={{ width: `${Math.max(0, Math.min(100, Number(c.porcentaje) || 0))}%` }}
                  />
                </div>
                <span className="coincidencia-porcentaje">{Number(c.porcentaje) || 0}% de coincidencia</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
