import { useCallback, useEffect, useState } from 'react';
import { getDashboard, mensajeError } from '../services/api';
import Icono from '../components/Icono';
import useContador from '../hooks/useContador';
import { EXTRAS_HABILITADOS } from '../config/alcance';

function CifraValor({ valor }) {
  const animado = useContador(valor);
  return <p className="metrica-valor">{valor == null ? '—' : animado}</p>;
}

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
    { etiqueta: 'Usuarios', valor: datos?.usuarios, icono: 'usuarios' },
    { etiqueta: 'Objetos activos', valor: datos?.objetosActivos, icono: 'caja' },
    ...(EXTRAS_HABILITADOS ? [
      { etiqueta: 'Reportes pendientes', valor: datos?.reportesPendientes, icono: 'bandera' },
      { etiqueta: 'Categorías', valor: datos?.categorias, icono: 'etiqueta' },
    ] : []),
  ];

  return (
    <div className="pagina">
      <div>
        <h1 className="pagina-titulo">Dashboard</h1>
        <p className="pagina-subtitulo">Resumen de la actividad de FoundIA en Tecsup.</p>
      </div>

      {error && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {error}
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
                <Icono nombre={t.icono} tamano={20} />
              </span>
              <div>
                <CifraValor valor={t.valor} />
                <p className="metrica-etiqueta">{t.etiqueta}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
