import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getDashboard, getPublicaciones, getReportes, getUsuarios, mensajeError } from '../services/api';
import { useAuth } from '../context/AuthContext';
import Icono from '../components/Icono';
import { DonaRecuperacion, GraficoMensual } from '../components/GraficosPanel';
import useContador from '../hooks/useContador';
import UltimoRespaldo from '../components/UltimoRespaldo';
import {
  actividadReciente, esVisible, haceCuanto, porMes, porcentajeRecuperacion, recientes, variacionMensual,
} from '../utils/resumenAdmin';

function Indicador({ etiqueta, valor, icono, color, nota }) {
  const animado = useContador(valor);
  return (
    <div className="indicador">
      <span className={`indicador-icono ${color}`} aria-hidden="true"><Icono nombre={icono} tamano={22} /></span>
      <div className="indicador-texto">
        <p className="indicador-etiqueta">{etiqueta}</p>
        <p className="indicador-valor">{valor == null ? '—' : animado.toLocaleString('es-PE')}</p>
        {nota && <p className="indicador-nota">{nota}</p>}
      </div>
    </div>
  );
}

const TIPO = { PERDIDO: 'Perdido', ENCONTRADO: 'Encontrado' };
const ESTADO = { ACTIVO: 'Activo', RECUPERADO: 'Recuperado', OCULTO: 'Oculto', ELIMINADO: 'Eliminado' };
const ICONO_ACTIVIDAD = { perdido: 'alerta', encontrado: 'buscar', recuperado: 'check', denuncia: 'bandera', usuario: 'usuarios' };

const ACCIONES = [
  { to: '/reportes', icono: 'bandera', texto: 'Revisar denuncias', color: 'amarillo' },
  { to: '/publicaciones', icono: 'caja', texto: 'Ver objetos', color: 'azul' },
  { to: '/usuarios', icono: 'usuarios', texto: 'Gestionar usuarios', color: 'naranja' },
  { to: '/estadisticas', icono: 'grafico', texto: 'Ver estadísticas', color: 'marino' },
];

// Panel general: indicadores reales de Django y listas (objetos, denuncias, usuarios) para
// el gráfico mensual, los recientes y la actividad. Si una lista falla, el resto sigue visible.
export default function Dashboard() {
  const { user } = useAuth();
  const [resumen, setResumen] = useState(null);
  const [listas, setListas] = useState({ objetos: [], reportes: [], usuarios: [], fallidas: [] });
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const peticion = useRef(null);

  const cargar = useCallback(async () => {
    peticion.current?.abort();
    const control = new AbortController();
    peticion.current = control;
    setCargando(true);
    setError('');
    const [r, objetos, reportes, usuarios] = await Promise.allSettled([
      getDashboard({}, { signal: control.signal }), getPublicaciones(), getReportes(), getUsuarios(),
    ]);
    if (control.signal.aborted) return;
    if (r.status === 'fulfilled') setResumen(r.value);
    else { setResumen(null); setError(mensajeError(r.reason, 'No se pudo cargar el panel')); }
    const valor = (p) => (p.status === 'fulfilled' && Array.isArray(p.value) ? p.value : []);
    setListas({
      objetos: valor(objetos),
      reportes: valor(reportes),
      usuarios: valor(usuarios),
      fallidas: [['objetos', objetos], ['denuncias', reportes], ['usuarios', usuarios]].filter(([, p]) => p.status === 'rejected').map(([n]) => n),
    });
    setCargando(false);
  }, []);

  useEffect(() => {
    cargar();
    return () => peticion.current?.abort();
  }, [cargar]);

  const serie = useMemo(() => porMes(listas.objetos), [listas.objetos]);
  const variacion = variacionMensual(serie);
  const actividad = useMemo(() => actividadReciente(listas), [listas]);
  const objetosRecientes = useMemo(() => recientes(listas.objetos.filter(esVisible), 'fecha_publicacion', 5), [listas.objetos]);

  const o = resumen?.objetos;
  const reportados = o ? o.perdidos + o.encontrados : null;
  const porcentaje = o ? porcentajeRecuperacion(o.perdidos, o.recuperados) : null;
  const notaMes = listas.fallidas.includes('objetos') ? null
    : `${variacion.actual} este mes${variacion.porcentaje != null ? ` · ${variacion.porcentaje >= 0 ? '+' : ''}${variacion.porcentaje}% vs. mes anterior` : ''}`;

  const indicadores = [
    { etiqueta: 'Objetos reportados', valor: reportados, icono: 'caja', color: 'amarillo', nota: notaMes },
    { etiqueta: 'Objetos perdidos', valor: o?.perdidos, icono: 'alerta', color: 'naranja' },
    { etiqueta: 'Objetos encontrados', valor: o?.encontrados, icono: 'buscar', color: 'azul' },
    { etiqueta: 'Objetos recuperados', valor: o?.recuperados, icono: 'check', color: 'verde', nota: porcentaje != null ? `${porcentaje}% de los perdidos` : null },
    { etiqueta: 'Denuncias pendientes', valor: resumen?.reportesPendientes, icono: 'bandera', color: 'naranja', nota: 'Por revisar' },
    { etiqueta: 'Usuarios registrados', valor: resumen?.usuarios, icono: 'usuarios', color: 'azul' },
  ];

  return (
    <div className="pagina panel">
      <section className="panel-banner" aria-labelledby="panel-saludo">
        <div className="panel-banner-texto">
          <span className="panel-chip"><Icono nombre="chispa" tamano={14} /> FINDI</span>
          <h1 id="panel-saludo">Hola, <span>{user?.nombre || 'Administrador'}</span></h1>
          <p>Supervisa los objetos, las denuncias y los usuarios de FoundIA en Tecsup.</p>
        </div>
        <img className="panel-banner-findi" src="/findi/findi.webp" alt="" width="742" height="772" />
      </section>

      {error && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {error}
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : (
        <>
          {resumen && (
            <section aria-label="Indicadores generales" className="grid-indicadores">
              {indicadores.map((i) => <Indicador key={i.etiqueta} {...i} />)}
            </section>
          )}

          {listas.fallidas.length > 0 && (
            <p className="panel-aviso" role="status">No se pudieron cargar: {listas.fallidas.join(', ')}. Los demás datos están al día.</p>
          )}

          <div className="panel-fila">
            <section className="tarjeta panel-tarjeta panel-grafico" aria-labelledby="titulo-mensual">
              <div className="panel-tarjeta-cabecera">
                <h2 id="titulo-mensual"><Icono nombre="grafico" tamano={18} /> Objetos reportados por mes</h2>
                <span className="panel-etiqueta">Últimos 6 meses</span>
              </div>
              <GraficoMensual serie={serie} />
            </section>

            <section className="tarjeta panel-tarjeta" aria-labelledby="titulo-recuperacion">
              <div className="panel-tarjeta-cabecera"><h2 id="titulo-recuperacion"><Icono nombre="check" tamano={18} /> Recuperación</h2></div>
              {o ? <DonaRecuperacion porcentaje={porcentaje} recuperados={o.recuperados} pendientes={Math.max(0, o.perdidos - o.recuperados)} /> : <p className="panel-vacio">Sin datos.</p>}
            </section>

            <section className="tarjeta panel-tarjeta" aria-labelledby="titulo-acciones">
              <div className="panel-tarjeta-cabecera"><h2 id="titulo-acciones"><Icono nombre="rayo" tamano={18} /> Acciones rápidas</h2></div>
              <div className="panel-acciones">
                {ACCIONES.map((a) => (
                  <Link key={a.to} to={a.to} className={`panel-accion ${a.color}`}>
                    <Icono nombre={a.icono} tamano={18} /> {a.texto} <Icono nombre="flechaDer" tamano={16} className="panel-accion-flecha" />
                  </Link>
                ))}
              </div>
            </section>
          </div>

          <div className="panel-fila panel-fila-2">
            <section className="tarjeta panel-tarjeta" aria-labelledby="titulo-recientes">
              <div className="panel-tarjeta-cabecera">
                <h2 id="titulo-recientes"><Icono nombre="caja" tamano={18} /> Objetos recientes</h2>
                <Link to="/publicaciones" className="panel-enlace">Ver todos <Icono nombre="flechaDer" tamano={14} /></Link>
              </div>
              {objetosRecientes.length === 0 ? (
                <p className="panel-vacio">Aún no hay objetos publicados.</p>
              ) : (
                <div className="tabla-contenedor">
                  <table className="tabla panel-tabla">
                    <thead><tr><th>Objeto</th><th>Tipo</th><th>Estado</th><th>Lugar</th><th>Publicado</th></tr></thead>
                    <tbody>
                      {objetosRecientes.map((obj) => (
                        <tr key={obj.id}>
                          <td><strong>{obj.nombre}</strong><span className="panel-sub">{obj.publicado_por}</span></td>
                          <td><span className={`chip-tipo ${obj.tipo === 'PERDIDO' ? 'perdido' : 'encontrado'}`}>{TIPO[obj.tipo] || obj.tipo}</span></td>
                          <td><span className={`chip-estado ${String(obj.estado).toLowerCase()}`}>{ESTADO[obj.estado] || obj.estado}</span></td>
                          <td>{obj.ubicacion || '—'}</td>
                          <td>{haceCuanto(obj.fecha_publicacion)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <div className="panel-columna">
            <UltimoRespaldo />

            <section className="tarjeta panel-tarjeta" aria-labelledby="titulo-actividad">
              <div className="panel-tarjeta-cabecera"><h2 id="titulo-actividad"><Icono nombre="reloj" tamano={18} /> Actividad reciente</h2></div>
              {actividad.length === 0 ? (
                <p className="panel-vacio">Todavía no hay actividad.</p>
              ) : (
                <ul className="actividad">
                  {actividad.map((e) => (
                    <li key={e.clave} className="actividad-item">
                      <span className={`actividad-icono ${e.tipo}`} aria-hidden="true"><Icono nombre={ICONO_ACTIVIDAD[e.tipo]} tamano={16} /></span>
                      <div className="actividad-texto">
                        <strong>{e.titulo}</strong>
                        {e.detalle && <span>{e.detalle}</span>}
                      </div>
                      <time className="actividad-tiempo" dateTime={e.fecha.toISOString()}>{haceCuanto(e.fecha)}</time>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
