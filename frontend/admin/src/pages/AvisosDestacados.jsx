import { useCallback, useEffect, useRef, useState } from 'react';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import {
  crearAvisoDestacado, editarAvisoDestacado, erroresDeValidacion, getAvisosDestacados, getCampanas, mensajeError,
  retirarAvisoDestacado, statusDe,
} from '../services/api';
import { Paginacion } from './Custodia';

// H71: avisos que se muestran arriba de la web y de la app mientras estén vigentes (fechas de Lima).
// No se borran: se retiran, y queda el historial. Pueden abrir una ruta de la web o una campaña (H60).
const ESTADOS = {
  PROGRAMADO: { texto: 'Programado', clase: 'recuperado' },
  VIGENTE: { texto: 'Vigente', clase: 'activo' },
  VENCIDO: { texto: 'Vencido', clase: '' },
  RETIRADO: { texto: 'Retirado', clase: 'eliminado' },
};
const SEGMENTOS = [
  { valor: '', texto: 'Todos' },
  { valor: 'VIGENTE', texto: 'Vigentes' },
  { valor: 'PROGRAMADO', texto: 'Programados' },
  { valor: 'VENCIDO', texto: 'Vencidos' },
  { valor: 'RETIRADO', texto: 'Retirados' },
];
const VACIA = { count: 0, results: [], next: null, previous: null };

function hoy() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
const formVacio = () => ({ titulo: '', texto: '', abre: 'nada', destino: '', campanaId: '', fechaInicio: hoy(), fechaFin: '' });

export function validarAviso(f) {
  const e = {};
  const titulo = f.titulo.trim();
  const texto = f.texto.trim();
  if (titulo.length < 3 || titulo.length > 80) e.titulo = 'El título debe tener entre 3 y 80 caracteres.';
  if (texto.length < 3 || texto.length > 300) e.texto = 'El texto debe tener entre 3 y 300 caracteres.';
  if (f.abre === 'ruta') {
    const d = f.destino.trim();
    if (!d.startsWith('/') || d.startsWith('//') || d.includes('?') || /\s/.test(d)) e.destino = 'Usa una ruta de FoundIA que empiece con «/», sin dominio ni parámetros (ej. /explorar).';
  }
  if (f.abre === 'campana' && !f.campanaId) e.campanaId = 'Elige la campaña que abrirá el aviso.';
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

export default function AvisosDestacados() {
  const { mostrarToast } = useToast();
  const [estado, setEstado] = useState('');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(VACIA);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [campanas, setCampanas] = useState([]);

  const [abierto, setAbierto] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(formVacio);
  const [errores, setErrores] = useState({});
  const [errorForm, setErrorForm] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [porRetirar, setPorRetirar] = useState(null);
  const [retirando, setRetirando] = useState(false);
  const panel = useRef(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getAvisosDestacados({ estado }, pagina));
    } catch (e) {
      setError(statusDe(e) === 403 ? 'Solo un administrador puede ver los avisos destacados.' : mensajeError(e, 'No se pudieron cargar los avisos'));
      setLista(VACIA);
    } finally {
      setCargando(false);
    }
  }, [estado, pagina]);

  useEffect(() => { cargar(); }, [cargar]);
  // Campañas para enlazar el aviso (las finalizadas no tienen sentido)
  useEffect(() => {
    getCampanas({}, 1).then((r) => setCampanas((r.results || []).filter((c) => c.estado !== 'FINALIZADA'))).catch(() => setCampanas([]));
  }, []);
  useEffect(() => { if (abierto) panel.current?.querySelector('input')?.focus(); }, [abierto, editando]);

  const abrirNuevo = () => {
    setEditando(null); setForm(formVacio()); setErrores({}); setErrorForm(''); setAbierto(true);
  };
  const abrirEdicion = (a) => {
    setEditando(a);
    setForm({
      titulo: a.titulo || '', texto: a.texto || '', abre: a.campanaId ? 'campana' : a.destino ? 'ruta' : 'nada',
      destino: a.destino || '', campanaId: a.campanaId ? String(a.campanaId) : '', fechaInicio: a.fechaInicio || '', fechaFin: a.fechaFin || '',
    });
    setErrores({}); setErrorForm(''); setAbierto(true);
  };
  const cerrar = () => { setAbierto(false); setEditando(null); };
  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setForm((f) => ({ ...f, [campo]: valor }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
    setErrorForm('');
  };

  const guardar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    const encontrados = validarAviso(form);
    setErrores(encontrados);
    if (Object.keys(encontrados).length) return;
    const datos = {
      titulo: form.titulo.trim(), texto: form.texto.trim(),
      destino: form.abre === 'ruta' ? form.destino.trim() : null,
      campanaId: form.abre === 'campana' ? Number(form.campanaId) : null,
      fechaInicio: form.fechaInicio, fechaFin: form.fechaFin,
    };
    setGuardando(true);
    setErrorForm('');
    try {
      if (editando) await editarAvisoDestacado(editando.id, datos);
      else await crearAvisoDestacado(datos);
      mostrarToast(editando ? `Aviso «${datos.titulo}» actualizado.` : `Aviso «${datos.titulo}» creado.`, 'exito');
      cerrar();
      cargar();
    } catch (err) {
      const campos = erroresDeValidacion(err);
      if (statusDe(err) === 400 && campos) {
        const { non_field_errors: general, ...resto } = campos;
        setErrores(resto);
        setErrorForm(general || '');
      } else {
        setErrorForm(mensajeError(err, 'No se pudo guardar el aviso'));
      }
    } finally {
      setGuardando(false);
    }
  };

  const retirar = async () => {
    setRetirando(true);
    try {
      await retirarAvisoDestacado(porRetirar.id);
      mostrarToast(`Aviso «${porRetirar.titulo}» retirado: ya no se muestra.`, 'exito');
      setPorRetirar(null);
      cargar();
    } catch (err) {
      mostrarToast(mensajeError(err, 'No se pudo retirar el aviso'), 'error');
      setPorRetirar(null);
    } finally {
      setRetirando(false);
    }
  };

  const error_ = (campo) => errores[campo] && <span id={`aviso-${campo}-error`} className="campo-error">{errores[campo]}</span>;
  const desc = (campo) => (errores[campo] ? `aviso-${campo}-error` : undefined);

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Avisos destacados</h1>
          <p className="pagina-subtitulo">
            Mensajes cortos que aparecen arriba de la web y de la app solo entre sus fechas, por ejemplo un horario
            especial de la biblioteca o una campaña.
          </p>
        </div>
        {!abierto && (
          <button type="button" className="btn btn-primario" onClick={abrirNuevo}><Icono nombre="mas" tamano={16} /> Nuevo aviso</button>
        )}
      </div>

      {abierto && (
        <form ref={panel} className="tarjeta formulario campana-form" onSubmit={guardar} noValidate
          aria-label={editando ? `Editar aviso ${editando.titulo}` : 'Nuevo aviso'}>
          <h2>{editando ? `Editar «${editando.titulo}»` : 'Nuevo aviso'}</h2>
          <div className="campo">
            <label className="campo-label" htmlFor="aviso-titulo">Título *</label>
            <input id="aviso-titulo" className={`input${errores.titulo ? ' input-error' : ''}`} maxLength={80} value={form.titulo}
              onChange={cambiar('titulo')} disabled={guardando} aria-invalid={Boolean(errores.titulo)} aria-describedby={desc('titulo')}
              placeholder="Ej. Semana de objetos perdidos" />
            {error_('titulo')}
          </div>
          <div className="campo">
            <label className="campo-label" htmlFor="aviso-texto">Texto *</label>
            <textarea id="aviso-texto" className={`input textarea${errores.texto ? ' input-error' : ''}`} rows={2} maxLength={300}
              value={form.texto} onChange={cambiar('texto')} disabled={guardando} aria-invalid={Boolean(errores.texto)} aria-describedby={desc('texto')}
              placeholder="Ej. Del 24 al 30 de noviembre revisa la mesa de objetos en la biblioteca." />
            <span className="nota-tabla">{form.texto.length}/300</span>
            {error_('texto')}
          </div>
          <fieldset className="resolver-opciones">
            <legend className="campo-label">Al tocar el aviso</legend>
            <label><input type="radio" name="abre" value="nada" checked={form.abre === 'nada'} onChange={cambiar('abre')} /> No abre nada</label>
            <label><input type="radio" name="abre" value="ruta" checked={form.abre === 'ruta'} onChange={cambiar('abre')} /> Abre una página de FoundIA</label>
            <label><input type="radio" name="abre" value="campana" checked={form.abre === 'campana'} onChange={cambiar('abre')} /> Abre una campaña</label>
          </fieldset>
          {form.abre === 'ruta' && (
            <div className="campo">
              <label className="campo-label" htmlFor="aviso-destino">Página *</label>
              <input id="aviso-destino" className={`input${errores.destino ? ' input-error' : ''}`} value={form.destino} onChange={cambiar('destino')}
                disabled={guardando} spellCheck={false} aria-invalid={Boolean(errores.destino)} aria-describedby={desc('destino')} placeholder="/explorar" />
              {error_('destino')}
            </div>
          )}
          {form.abre === 'campana' && (
            <div className="campo">
              <label className="campo-label" htmlFor="aviso-campana">Campaña *</label>
              <select id="aviso-campana" className={`input${errores.campanaId ? ' input-error' : ''}`} value={form.campanaId} onChange={cambiar('campanaId')}
                disabled={guardando} aria-invalid={Boolean(errores.campanaId)} aria-describedby={desc('campanaId')}>
                <option value="">{campanas.length ? 'Elige una campaña' : 'No hay campañas activas o programadas'}</option>
                {campanas.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
              </select>
              <span className="nota-tabla">El aviso abre el enlace de la campaña, así se cuentan las visitas que llegan por él.</span>
              {error_('campanaId')}
            </div>
          )}
          <div className="campana-fila">
            <div className="campo">
              <label className="campo-label" htmlFor="aviso-fechaInicio">Desde *</label>
              <input id="aviso-fechaInicio" type="date" className={`input${errores.fechaInicio ? ' input-error' : ''}`} value={form.fechaInicio}
                onChange={cambiar('fechaInicio')} disabled={guardando} aria-invalid={Boolean(errores.fechaInicio)} aria-describedby={desc('fechaInicio')} />
              {error_('fechaInicio')}
            </div>
            <div className="campo">
              <label className="campo-label" htmlFor="aviso-fechaFin">Hasta *</label>
              <input id="aviso-fechaFin" type="date" min={form.fechaInicio || undefined} className={`input${errores.fechaFin ? ' input-error' : ''}`}
                value={form.fechaFin} onChange={cambiar('fechaFin')} disabled={guardando} aria-invalid={Boolean(errores.fechaFin)} aria-describedby={desc('fechaFin')} />
              {error_('fechaFin')}
            </div>
          </div>
          {errorForm && <p className="campo-error" role="alert">{errorForm}</p>}
          <div className="acciones-fila">
            <button type="button" className="btn btn-fantasma" onClick={cerrar} disabled={guardando}>Cancelar</button>
            <button type="submit" className="btn btn-primario" disabled={guardando}>{guardando ? 'Guardando...' : editando ? 'Guardar cambios' : 'Crear aviso'}</button>
          </div>
        </form>
      )}

      <div className="filtro-segmentos" role="group" aria-label="Filtrar avisos por estado">
        {SEGMENTOS.map((s) => (
          <button key={s.valor || 'todos'} type="button" className={`btn btn-mini ${estado === s.valor ? 'btn-primario' : 'btn-fantasma'}`}
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
          <p>{estado ? 'No hay avisos en esta vista.' : 'Todavía no hay avisos destacados.'}</p>
        </div>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead><tr><th>Aviso</th><th>Vigencia</th><th>Estado</th><th>Abre</th><th>Acciones</th></tr></thead>
              <tbody>
                {lista.results.map((a) => {
                  const e = ESTADOS[a.estado] || { texto: a.estado, clase: '' };
                  return (
                    <tr key={a.id}>
                      <td data-label="Aviso">{a.titulo}<span className="celda-detalle">{a.texto}</span></td>
                      <td data-label="Vigencia">{fecha(a.fechaInicio)} – {fecha(a.fechaFin)}</td>
                      <td data-label="Estado"><span className={`chip-estado ${e.clase}`}>{e.texto}</span></td>
                      <td data-label="Abre"><code className="campana-enlace">{a.enlace || '—'}</code></td>
                      <td data-label="Acciones">
                        <div className="acciones-celda">
                          {a.estado !== 'RETIRADO' && (
                            <button type="button" className="btn btn-fantasma btn-mini" onClick={() => abrirEdicion(a)} aria-label={`Editar ${a.titulo}`}>Editar</button>
                          )}
                          {['VIGENTE', 'PROGRAMADO'].includes(a.estado) && (
                            <button type="button" className="btn btn-peligro btn-mini" onClick={() => setPorRetirar(a)} aria-label={`Retirar ${a.titulo}`}>Retirar</button>
                          )}
                          {a.estado === 'RETIRADO' && <span className="nota-tabla">Sin acciones</span>}
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

      <ConfirmarDialogo
        abierto={Boolean(porRetirar)}
        titulo="¿Retirar este aviso?"
        mensaje={porRetirar ? `«${porRetirar.titulo}» dejará de mostrarse ahora en la web y en la app. Queda en el historial y ya no se podrá editar.` : ''}
        textoConfirmar="Sí, retirar"
        peligro
        procesando={retirando}
        onConfirmar={retirar}
        onCancelar={() => setPorRetirar(null)}
      />
    </div>
  );
}
