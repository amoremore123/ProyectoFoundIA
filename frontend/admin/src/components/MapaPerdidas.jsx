import { useCallback, useEffect, useRef, useState } from 'react';
import 'leaflet/dist/leaflet.css';
import Icono from './Icono';
import { getMapaPerdidas, mensajeError } from '../services/api';

// Mapa de pérdidas: dónde se pierden y encuentran más cosas en el campus, con las coordenadas que se
// guardan al publicar (M4). Los puntos se agrupan a unos 11 m: no se ve la ubicación exacta de nadie.
// La tabla de abajo muestra lo mismo para quien no puede ver el mapa.
const TIPOS = [
  { valor: '', texto: 'Perdidos y encontrados' },
  { valor: 'PERDIDO', texto: 'Solo perdidos' },
  { valor: 'ENCONTRADO', texto: 'Solo encontrados' },
];
// Contorno del campus Tecsup, sede Santa Anita (Av. Los Cascanueces 2221), tomado de OpenStreetMap
export const CAMPUS_TECSUP = [
  [-12.0429268, -76.9544983], [-12.0458969, -76.9532866], [-12.0451999, -76.9517369], [-12.0450136, -76.9516539],
  [-12.0448737, -76.9513238], [-12.0420711, -76.9524881],
];
const CENTRO_TECSUP = [-12.0439002, -76.9529147];
// Alrededor del campus (unos 300 m): lo que queda más lejos se avisa como «fuera del campus»
const MARGEN_GRADOS = 0.003;
const LIMITES = CAMPUS_TECSUP.reduce((b, [lat, lng]) => ({
  minLat: Math.min(b.minLat, lat), maxLat: Math.max(b.maxLat, lat), minLng: Math.min(b.minLng, lng), maxLng: Math.max(b.maxLng, lng),
}), { minLat: 90, maxLat: -90, minLng: 180, maxLng: -180 });

export function cercaDelCampus({ lat, lng }) {
  return lat >= LIMITES.minLat - MARGEN_GRADOS && lat <= LIMITES.maxLat + MARGEN_GRADOS
    && lng >= LIMITES.minLng - MARGEN_GRADOS && lng <= LIMITES.maxLng + MARGEN_GRADOS;
}
const COLOR_PERDIDO = '#f04438';
const COLOR_ENCONTRADO = '#1f6feb';

export function radioPunto(total, maximo) {
  return 8 + Math.round(22 * Math.sqrt(total / Math.max(1, maximo)));
}

function textoPunto(p) {
  const lugar = p.lugar || 'Lugar sin nombre';
  return `${lugar}: ${p.total} ${p.total === 1 ? 'objeto' : 'objetos'} (${p.perdidos} perdidos, ${p.encontrados} encontrados)`;
}

export default function MapaPerdidas() {
  const [tipo, setTipo] = useState('');
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  const contenedor = useRef(null);
  const mapa = useRef(null);
  const capa = useRef(null);
  const campus = useRef(null);

  const cargar = useCallback(async () => {
    setError('');
    try { setDatos(await getMapaPerdidas({ tipo })); } catch (e) { setError(mensajeError(e, 'No se pudo cargar el mapa')); setDatos({ puntos: [], conCoordenadas: 0, sinCoordenadas: 0 }); }
  }, [tipo]);
  useEffect(() => { cargar(); }, [cargar]);

  // Dibuja el mapa (Leaflet se carga solo cuando hace falta) y vuelve a pintar los puntos al cambiar los datos
  useEffect(() => {
    if (!datos || !contenedor.current) return undefined;
    let vivo = true;
    import('leaflet').then(({ default: L }) => {
      if (!vivo || !contenedor.current) return;
      if (!mapa.current) {
        mapa.current = L.map(contenedor.current, { scrollWheelZoom: false }).setView(CENTRO_TECSUP, 17);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19, attribution: '&copy; colaboradores de OpenStreetMap',
        }).addTo(mapa.current);
        // Contorno de la sede: el mapa siempre abre en el campus
        campus.current = L.polygon(CAMPUS_TECSUP, { color: '#12355b', weight: 2, dashArray: '6 4', fillColor: '#ffd60a', fillOpacity: 0.08 })
          .bindTooltip('Tecsup · sede Santa Anita').addTo(mapa.current);
      }
      capa.current?.remove();
      capa.current = L.layerGroup().addTo(mapa.current);
      const maximo = Math.max(1, ...datos.puntos.map((p) => p.total));
      datos.puntos.forEach((p) => {
        const color = p.perdidos >= p.encontrados ? COLOR_PERDIDO : COLOR_ENCONTRADO;
        L.circleMarker([p.lat, p.lng], {
          radius: radioPunto(p.total, maximo), color, weight: 2, fillColor: color, fillOpacity: 0.35,
        }).bindTooltip(textoPunto(p)).addTo(capa.current);
      });
      // Encuadre: el campus completo más los puntos cercanos (los lejanos no mueven el mapa)
      const cercanos = datos.puntos.filter(cercaDelCampus).map((p) => [p.lat, p.lng]);
      mapa.current.fitBounds(L.latLngBounds([...CAMPUS_TECSUP, ...cercanos]).pad(0.15), { maxZoom: 18 });
    }).catch(() => { if (vivo) setError('No se pudo dibujar el mapa en este navegador.'); });
    return () => { vivo = false; };
  }, [datos]);

  useEffect(() => () => { mapa.current?.remove(); mapa.current = null; }, []);

  const verSede = () => {
    if (mapa.current && campus.current) mapa.current.fitBounds(campus.current.getBounds().pad(0.15), { maxZoom: 18 });
  };
  const lejanos = datos ? datos.puntos.filter((p) => !cercaDelCampus(p)) : [];

  return (
    <section className="tarjeta mapa-perdidas" aria-labelledby="titulo-mapa">
      <div className="panel-tarjeta-cabecera">
        <h2 id="titulo-mapa"><Icono nombre="ubicacion" tamano={18} /> Mapa de pérdidas</h2>
        <select className="input mapa-filtro" value={tipo} onChange={(e) => setTipo(e.target.value)} aria-label="Qué objetos mostrar en el mapa">
          {TIPOS.map((t) => <option key={t.valor || 'todos'} value={t.valor}>{t.texto}</option>)}
        </select>
      </div>
      <p className="nota-tabla">
        Dónde se pierden y se encuentran más cosas en la sede Santa Anita. Sirve para decidir dónde poner afiches o una mesa de objetos.
        Los puntos agrupan lugares a unos 11 m: no muestran la ubicación exacta de nadie.
      </p>
      {error && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}
      <div ref={contenedor} className="mapa-lienzo" role="img"
        aria-label={datos ? `Mapa con ${datos.puntos.length} lugares marcados. El detalle está en la tabla siguiente.` : 'Cargando el mapa'} />
      <div className="mapa-acciones">
        <button type="button" className="btn btn-fantasma btn-mini" onClick={verSede}>Ver la sede completa</button>
        {lejanos.length > 0 && (
          <span className="nota-tabla" role="note">
            {lejanos.length} {lejanos.length === 1 ? 'lugar está' : 'lugares están'} fuera del campus (por ejemplo, datos de prueba) y no se ven en este encuadre.
          </span>
        )}
      </div>
      <p className="mapa-leyenda" aria-hidden="true">
        <span><i style={{ background: COLOR_PERDIDO }} /> Más perdidos</span>
        <span><i style={{ background: COLOR_ENCONTRADO }} /> Más encontrados</span>
        <span>El tamaño indica cuántos objetos hay</span>
      </p>
      {datos && (
        datos.puntos.length === 0 ? (
          <p className="panel-vacio">Todavía no hay publicaciones con ubicación del GPS para mostrar en el mapa.</p>
        ) : (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption className="nota-tabla">Lugares con más objetos ({datos.conCoordenadas} con GPS; {datos.sinCoordenadas} sin ubicación)</caption>
              <thead><tr><th>Lugar</th><th>Total</th><th>Perdidos</th><th>Encontrados</th></tr></thead>
              <tbody>
                {datos.puntos.slice(0, 10).map((p) => (
                  <tr key={`${p.lat},${p.lng}`}>
                    <td data-label="Lugar">{p.lugar || 'Lugar sin nombre'}{!cercaDelCampus(p) && <span className="celda-detalle">Fuera del campus</span>}</td>
                    <td data-label="Total">{p.total}</td>
                    <td data-label="Perdidos">{p.perdidos}</td>
                    <td data-label="Encontrados">{p.encontrados}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </section>
  );
}
