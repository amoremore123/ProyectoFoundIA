import { useCallback, useEffect, useRef, useState } from 'react';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import AlcanceCampanas from '../components/AlcanceCampanas';
import PromocionCampana from '../components/PromocionCampana';
import { useToast } from '../context/ToastContext';
import {
  crearCampana, editarCampana, erroresDeValidacion, finalizarCampana, getCampanas, mensajeError, statusDe,
} from '../services/api';
import { Paginacion } from './Custodia';

// H60: campañas de difusión. Cada una tiene fechas y un enlace con ?origen= para medir (H67)
// cuántas visitas y registros llegan por ella. El enlace lo arma el servidor y no lleva datos
// personales. Las campañas no se borran: una activa se finaliza.
const ESTADOS = {
  PROGRAMADA: { texto: 'Programada', clase: 'recuperado' },
  ACTIVA: { texto: 'Activa', clase: 'activo' },
  FINALIZADA: { texto: 'Finalizada', clase: '' },
};
const SEGMENTOS = [
  { valor: '', texto: 'Todas' },
  { valor: 'ACTIVA', texto: 'Activas' },
  { valor: 'PROGRAMADA', texto: 'Programadas' },
  { valor: 'FINALIZADA', texto: 'Finalizadas' },
];
const VACIA = { count: 0, results: [], next: null, previous: null };
const ORIGEN_VALIDO = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function hoy() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const formVacio = () => ({ nombre: '', descripcion: '', origen: '', destino: '/', fechaInicio: hoy(), fechaFin: '' });

// «Semana de Objetos Perdidos 2026» → «semana-de-objetos-perdidos-2026» (sin tildes, máximo 40)
export function origenSugerido(nombre) {
  return nombre.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/, '');
}

export function validarCampana(f, editando) {
  const e = {};
  const nombre = f.nombre.trim();
  if (nombre.length < 3) e.nombre = 'Escribe un nombre de al menos 3 caracteres.';
  else if (nombre.length > 120) e.nombre = 'Máximo 120 caracteres.';
  if (f.descripcion.trim().length > 500) e.descripcion = 'Máximo 500 caracteres.';
  if (!editando) {
    if (f.origen.length < 3 || f.origen.length > 40) e.origen = 'El origen debe tener entre 3 y 40 caracteres.';
    else if (!ORIGEN_VALIDO.test(f.origen)) e.origen = 'Usa solo minúsculas, números y guiones (ej. semana-perdidos-2026).';
  }
  const destino = f.destino.trim() || '/';
  if (!destino.startsWith('/') || destino.startsWith('//')) e.destino = 'Debe ser una ruta de FoundIA que empiece con «/».';
  if (!f.fechaInicio) e.fechaInicio = 'Elige la fecha de inicio.';
  if (!f.fechaFin) e.fechaFin = 'Elige la fecha de fin.';
  else if (f.fechaInicio && f.fechaFin < f.fechaInicio) e.fechaFin = 'La fecha de fin no puede ser anterior al inicio.';
  return e;
}

function fecha(valor) {
  if (!valor) return '—';
  const [a, m, d] = valor.split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function Campanas() {
  const { mostrarToast } = useToast();
  const [estado, setEstado] = useState('');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const [abierto, setAbierto] = useState(false);
  const [editando, setEditando] = useState(null); // campaña que se edita, o null al crear
  const [form, setForm] = useState(formVacio);
  const [origenTocado, setOrigenTocado] = useState(false);
  const [errores, setErrores] = useState({});
  const [errorForm, setErrorForm] = useState('');
  const [guardando, setGuardando] = useState(false);

  const [porFinalizar, setPorFinalizar] = useState(null);
  const [finalizando, setFinalizando] = useState(false);
  const [copiado, setCopiado] = useState(null);
  // H66: campaña que se está promocionando en redes
  const [promocionando, setPromocionando] = useState(null);
  const panel = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getCampanas({ estado }, pagina));
    } catch (e) {
      setError(statusDe(e) === 403 ? 'Solo un administrador puede ver las campañas.' : mensajeError(e, 'No se pudieron cargar las campañas'));
      setLista(VACIA);
    } finally {
      setCargando(false);
    }
  }, [estado, pagina]);

  useEffect(() => { cargar(); }, [cargar]);
  useEffect(() => { if (abierto) panel.current?.querySelector('input')?.focus(); }, [abierto, editando]);

  const abrirNueva = () => {
    setEditando(null);
    setForm(formVacio());
    setOrigenTocado(false);
    setErrores({});
    setErrorForm('');
    setAbierto(true);
  };

  const abrirEdicion = (c) => {
    setEditando(c);
    setForm({
      nombre: c.nombre || '', descripcion: c.descripcion || '', origen: c.origen || '',
      destino: c.destino || '/', fechaInicio: c.fechaInicio || '', fechaFin: c.fechaFin || '',
    });
    setOrigenTocado(true);
    setErrores({});
    setErrorForm('');
    setAbierto(true);
  };

  const cerrar = () => { setAbierto(false); setEditando(null); };

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setForm((f) => {
      const nuevo = { ...f, [campo]: valor };
      // Mientras no se escriba un origen propio, se sugiere a partir del nombre
      if (campo === 'nombre' && !editando && !origenTocado) nuevo.origen = origenSugerido(valor);
      return nuevo;
    });
    if (campo === 'origen') setOrigenTocado(true);
    setErrores((er) => ({ ...er, [campo]: undefined, ...(campo === 'nombre' ? { origen: undefined } : {}) }));
    setErrorForm('');
  };

  const guardar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    const encontrados = validarCampana(form, Boolean(editando));
    setErrores(encontrados);
    if (Object.keys(encontrados).length) return;
    setGuardando(true);
    setErrorForm('');
    const datos = {
      nombre: form.nombre.trim(), descripcion: form.descripcion.trim(), destino: form.destino.trim() || '/',
      fechaInicio: form.fechaInicio, fechaFin: form.fechaFin,
    };
    try {
      if (editando) {
        await editarCampana(editando.id, datos);
        mostrarToast(`Campaña «${datos.nombre}» actualizada.`, 'exito');
      } else {
        await crearCampana({ ...datos, origen: form.origen });
        mostrarToast(`Campaña «${datos.nombre}» creada. Ya puedes copiar su enlace.`, 'exito');
      }
      cerrar();
      cargar();
    } catch (err) {
      const campos = erroresDeValidacion(err);
      if (statusDe(err) === 400 && campos) {
        const { non_field_errors: general, ...resto } = campos;
        setErrores(resto);
        setErrorForm(general || '');
      } else {
        setErrorForm(mensajeError(err, 'No se pudo guardar la campaña'));
      }
    } finally {
      setGuardando(false);
    }
  };

  const copiar = async (c) => {
    try {
      await navigator.clipboard.writeText(c.enlace);
      setCopiado(c.id);
      mostrarToast('Enlace copiado.', 'exito');
    } catch {
      mostrarToast('No se pudo copiar: selecciona el enlace y cópialo.', 'error');
    }
  };

  const finalizar = async () => {
    setFinalizando(true);
    try {
      await finalizarCampana(porFinalizar.id);
      mostrarToast(`Campaña «${porFinalizar.nombre}» finalizada.`, 'exito');
      setPorFinalizar(null);
      cargar();
    } catch (err) {
      mostrarToast(mensajeError(err, 'No se pudo finalizar la campaña'), 'error');
      setPorFinalizar(null);
    } finally {
      setFinalizando(false);
    }
  };

  const campo = (nombre, etiqueta, props = {}, ayuda = null) => (
    <div className="campo">
      <label className="campo-label" htmlFor={`campana-${nombre}`}>{etiqueta}</label>
      <input
        id={`campana-${nombre}`} className={`input${errores[nombre] ? ' input-error' : ''}`}
        value={form[nombre]} onChange={cambiar(nombre)} disabled={guardando}
        aria-invalid={Boolean(errores[nombre])}
        aria-describedby={[errores[nombre] && `campana-${nombre}-error`, ayuda && `campana-${nombre}-ayuda`].filter(Boolean).join(' ') || undefined}
        {...props}
      />
      {ayuda && <span id={`campana-${nombre}-ayuda`} className="nota-tabla">{ayuda}</span>}
      {errores[nombre] && <span id={`campana-${nombre}-error`} className="campo-error">{errores[nombre]}</span>}
    </div>
  );

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Campañas</h1>
          <p className="pagina-subtitulo">
            Difunde FoundIA con un enlace propio por campaña. El enlace no lleva datos personales: solo un
            origen para saber cuántas visitas llegan por él.
          </p>
        </div>
        {!abierto && (
          <button type="button" className="btn btn-primario" onClick={abrirNueva}>
            <Icono nombre="mas" tamano={16} /> Nueva campaña
          </button>
        )}
      </div>

      {abierto && (
        <form ref={panel} className="tarjeta formulario campana-form" onSubmit={guardar} noValidate
          aria-label={editando ? `Editar campaña ${editando.nombre}` : 'Nueva campaña'}>
          <h2>{editando ? `Editar «${editando.nombre}»` : 'Nueva campaña'}</h2>
          {campo('nombre', 'Nombre *', { maxLength: 120, placeholder: 'Ej. Semana de objetos perdidos' })}
          <div className="campo">
            <label className="campo-label" htmlFor="campana-descripcion">Descripción</label>
            <textarea id="campana-descripcion" className={`input textarea${errores.descripcion ? ' input-error' : ''}`} rows={2}
              maxLength={500} value={form.descripcion} onChange={cambiar('descripcion')} disabled={guardando}
              aria-invalid={Boolean(errores.descripcion)} aria-describedby={errores.descripcion ? 'campana-descripcion-error' : undefined}
              placeholder="Dónde y cómo se difundirá" />
            {errores.descripcion && <span id="campana-descripcion-error" className="campo-error">{errores.descripcion}</span>}
          </div>
          <div className="campana-fila">
            {campo('origen', 'Origen *', { maxLength: 40, disabled: guardando || Boolean(editando), spellCheck: false, autoCapitalize: 'none' },
              editando ? 'No se puede cambiar: el enlace ya pudo compartirse.' : 'Minúsculas, números y guiones. Va en el enlace como ?origen=…')}
            {campo('destino', 'Página de destino', { maxLength: 255, spellCheck: false, autoCapitalize: 'none' }, 'Ruta de FoundIA que abre el enlace, por ejemplo / o /explorar.')}
          </div>
          <div className="campana-fila">
            {campo('fechaInicio', 'Inicio *', { type: 'date' })}
            {campo('fechaFin', 'Fin *', { type: 'date', min: form.fechaInicio || undefined })}
          </div>
          {errorForm && <p className="campo-error" role="alert">{errorForm}</p>}
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={cerrar} disabled={guardando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardando}>
              {guardando ? 'Guardando...' : editando ? 'Guardar cambios' : 'Crear campaña'}
            </button>
          </div>
        </form>
      )}

      {promocionando && <PromocionCampana key={promocionando.id} campana={promocionando} onCerrar={() => setPromocionando(null)} />}

      <div className="filtro-segmentos" role="group" aria-label="Filtrar campañas por estado">
        {SEGMENTOS.map((s) => (
          <button key={s.valor || 'todas'} type="button" className={`btn btn-mini ${estado === s.valor ? 'btn-primario' : 'btn-fantasma'}`}
            aria-pressed={estado === s.valor} onClick={() => { setEstado(s.valor); setPagina(1); }}>{s.texto}</button>
        ))}
      </div>

      {error && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : error ? null : lista.results.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="megafono" tamano={28} /></span>
          <p>{estado ? 'No hay campañas en esta vista.' : 'Todavía no hay campañas. Crea la primera con «Nueva campaña».'}</p>
        </div>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr><th>Campaña</th><th>Fechas</th><th>Estado</th><th>Enlace</th><th>Acciones</th></tr>
              </thead>
              <tbody>
                {lista.results.map((c) => {
                  const e = ESTADOS[c.estado] || { texto: c.estado, clase: '' };
                  return (
                    <tr key={c.id}>
                      <td data-label="Campaña">
                        {c.nombre}
                        {c.descripcion && <span className="celda-detalle">{c.descripcion}</span>}
                      </td>
                      <td data-label="Fechas">{fecha(c.fechaInicio)} – {fecha(c.fechaFin)}</td>
                      <td data-label="Estado"><span className={`chip-estado ${e.clase}`}>{e.texto}</span></td>
                      <td data-label="Enlace">
                        <code className="campana-enlace">{c.enlace}</code>
                        <button type="button" className="btn btn-fantasma btn-mini" onClick={() => copiar(c)}
                          aria-label={`Copiar el enlace de ${c.nombre}`}>
                          {copiado === c.id ? 'Copiado' : 'Copiar'}
                        </button>
                      </td>
                      <td data-label="Acciones">
                        <div className="acciones-celda">
                          {c.estado !== 'FINALIZADA' && (
                            <button type="button" className="btn btn-fantasma btn-mini" onClick={() => abrirEdicion(c)}
                              aria-label={`Editar ${c.nombre}`}>Editar</button>
                          )}
                          {c.estado !== 'FINALIZADA' && (
                            <button type="button" className="btn btn-fantasma btn-mini" onClick={() => setPromocionando(c)}
                              aria-label={`Promocionar ${c.nombre} en redes`}>Promocionar</button>
                          )}
                          {c.estado === 'ACTIVA' && (
                            <button type="button" className="btn btn-peligro btn-mini" onClick={() => setPorFinalizar(c)}
                              aria-label={`Finalizar ${c.nombre}`}>Finalizar</button>
                          )}
                          {c.estado === 'FINALIZADA' && <span className="nota-tabla">Sin acciones</span>}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} lista={lista} onCambiar={setPagina} />
        </>
      )}

      {/* H67: alcance de cada campaña por rango de fechas */}
      <AlcanceCampanas />

      <ConfirmarDialogo
        abierto={Boolean(porFinalizar)}
        titulo="¿Finalizar esta campaña?"
        mensaje={porFinalizar ? `«${porFinalizar.nombre}» terminará hoy. El enlace seguirá abriendo FoundIA, pero la campaña quedará como finalizada y no se podrá editar.` : ''}
        textoConfirmar="Sí, finalizar"
        peligro
        procesando={finalizando}
        onConfirmar={finalizar}
        onCancelar={() => setPorFinalizar(null)}
      />
    </div>
  );
}
