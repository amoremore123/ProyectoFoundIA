import { useCallback, useEffect, useState } from 'react';
import { getDashboard, mensajeError } from '../services/api';

export default function Dashboard() {
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const data = await getDashboard();
      setDatos(data);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo cargar el panel'));
      setDatos(null);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const tarjetas = [
    { etiqueta: 'Usuarios', valor: datos?.usuarios, icono: '👥' },
    { etiqueta: 'Objetos activos', valor: datos?.objetosActivos, icono: '📦' },
    { etiqueta: 'Reportes pendientes', valor: datos?.reportesPendientes, icono: '🚩' },
    { etiqueta: 'Categorías', valor: datos?.categorias, icono: '🗂️' },
  ];

  return (
    <div className="pagina">
      <h1 className="pagina-titulo">Dashboard</h1>

      {error && (
        <div className="banner-error" role="alert">
          ⚠️ {error}
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>
            Reintentar
          </button>
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>Cargando...</span>
        </div>
      ) : (
        <div className="grid-metricas">
          {tarjetas.map((t) => (
            <div key={t.etiqueta} className="tarjeta metrica">
              <span className="metrica-icono" aria-hidden="true">
                {t.icono}
              </span>
              <div>
                <p className="metrica-valor">{t.valor ?? '—'}</p>
                <p className="metrica-etiqueta">{t.etiqueta}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
