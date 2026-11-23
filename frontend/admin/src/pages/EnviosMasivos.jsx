import { useCallback, useEffect, useState } from 'react';
import ConfirmarDialogo from '../components/ConfirmarDialogo';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';
import {
  enviarEnvioMasivo, erroresDeValidacion, getDestinatariosEnvio, getEnviosMasivos, mensajeError, statusDe, vistaPreviaEnvioMasivo,
} from '../services/api';
import { fechaHora, Paginacion } from './Custodia';

// H69: aviso para muchas cuentas a la vez. Primero una vista previa (cuántos lo recibirán y un código de
// confirmación), luego el envío con ese código. Máximo 1000 destinatarios y 3 envíos al día. Queda registrado.
const GRUPOS = [
  { valor: 'TODOS', texto: 'Todas las cuentas activas' },
  { valor: 'USUARIOS', texto: 'Solo usuarios' },
  { valor: 'BIBLIOTECA', texto: 'Solo el personal de biblioteca' },
];
const nombreGrupo = (v) => GRUPOS.find((g) => g.valor === v)?.texto || v;
const VACIA = { count: 0, results: [], next: null, previous: null };
const VACIO = { titulo: '', mensaje: '', enlace: '', destinatarios: 'TODOS' };

export function validarEnvio(f) {
  const e = {};
  const titulo = f.titulo.trim();
  const mensaje = f.mensaje.trim();
  const enlace = f.enlace.trim();
  if (titulo.length < 3 || titulo.length > 150) e.titulo = 'El título debe tener entre 3 y 150 caracteres.';
  if (mensaje.length < 3 || mensaje.length > 1000) e.mensaje = 'El mensaje debe tener entre 3 y 1000 caracteres.';
  if (enlace && (!enlace.startsWith('/') || enlace.startsWith('//') || enlace.includes('?') || /\s/.test(enlace))) {
    e.enlace = 'Usa una ruta de FoundIA que empiece con «/», sin dominio ni parámetros (ej. /explorar).';
  }
  return e;
}

function Destinatarios({ envio, onCerrar }) {
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let vivo = true;
    setLista(null);
    getDestinatariosEnvio(envio.id, pagina)
      .then((r) => { if (vivo) setLista(r); })
      .catch((e) => { if (vivo) { setError(mensajeError(e, 'No se pudieron cargar los destinatarios')); setLista(VACIA); } });
    return () => { vivo = false; };
  }, [envio.id, pagina]);
  return (
    <div className="tarjeta alcance-detalle" role="region" aria-label={`Destinatarios de «${envio.titulo}»`}>
      <div className="acciones-fila">
        <h3 className="seccion-titulo">Destinatarios de «{envio.titulo}»</h3>
        <button type="button" className="btn btn-fantasma btn-mini" onClick={onCerrar}>Cerrar</button>
      </div>
      {error && <p className="campo-error" role="alert">{error}</p>}
      {!lista ? <p className="panel-vacio" role="status">Cargando...</p> : (
        <>
          <ul className="promocion-lista">
            {lista.results.map((d) => (
              <li key={d.id || d.usuario?.id}>
                {d.nombre || d.usuario?.nombre}
                <span className="celda-detalle">{d.leida || d.leidaEn ? 'Leído' : 'Sin leer'}</span>
              </li>
            ))}
          </ul>
          <Paginacion pagina={pagina} lista={lista} onCambiar={setPagina} />
        </>
      )}
    </div>
  );
}

export default function EnviosMasivos() {
  const { mostrarToast } = useToast();
  const [form, setForm] = useState(VACIO);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [vista, setVista] = useState(null); // { destinatarios, muestra, confirmacion, expiraEn }
  const [pidiendo, setPidiendo] = useState(false);
  const [confirmar, setConfirmar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [pagina, setPagina] = useState(1);
  const [historial, setHistorial] = useState(VACIA);
  const [errorHistorial, setErrorHistorial] = useState('');
  const [detalle, setDetalle] = useState(null);

  const cargarHistorial = useCallback(async () => {
    setErrorHistorial('');
    try { setHistorial(await getEnviosMasivos(pagina)); } catch (e) { setErrorHistorial(mensajeError(e, 'No se pudo cargar el historial')); }
  }, [pagina]);
  useEffect(() => { cargarHistorial(); }, [cargarHistorial]);

  const cambiar = (campo) => (e) => {
    const valor = e.target.value;
    setForm((f) => ({ ...f, [campo]: valor }));
    setErrores((x) => ({ ...x, [campo]: undefined }));
    setErrorGeneral('');
    setVista(null); // cualquier cambio invalida la vista previa y su código
  };

  const datos = () => ({
    titulo: form.titulo.trim(), mensaje: form.mensaje.trim(), destinatarios: form.destinatarios,
    ...(form.enlace.trim() ? { enlace: form.enlace.trim() } : {}),
  });

  const mostrarErrores = (err, fallback) => {
    const campos = erroresDeValidacion(err);
    if (statusDe(err) === 400 && campos) {
      const { non_field_errors: general, detail, ...resto } = campos;
      setErrores(resto);
      setErrorGeneral(general || detail || (Object.keys(resto).length ? '' : mensajeError(err, fallback)));
    } else if (statusDe(err) === 429) {
      setErrorGeneral('Ya se hicieron los 3 envíos masivos permitidos hoy. Intenta mañana.');
    } else {
      setErrorGeneral(mensajeError(err, fallback));
    }
  };

  const pedirVista = async (e) => {
    e?.preventDefault();
    const encontrados = validarEnvio(form);
    setErrores(encontrados);
    if (Object.keys(encontrados).length) return;
    setPidiendo(true);
    setErrorGeneral('');
    try {
      setVista(await vistaPreviaEnvioMasivo(datos()));
    } catch (err) {
      mostrarErrores(err, 'No se pudo preparar la vista previa');
      setVista(null);
    } finally {
      setPidiendo(false);
    }
  };

  const enviar = async () => {
    setEnviando(true);
    try {
      const envio = await enviarEnvioMasivo({ ...datos(), confirmacion: vista.confirmacion });
      mostrarToast(`Aviso enviado a ${envio.totalDestinatarios} cuentas.`, 'exito');
      setForm(VACIO);
      setVista(null);
      setPagina(1);
      cargarHistorial();
    } catch (err) {
      const texto = JSON.stringify(err?.response?.data || '');
      // El código venció o no corresponde: hay que volver a pedir la vista previa
      if (statusDe(err) === 400 && /confirmaci/i.test(texto)) {
        setVista(null);
        setErrorGeneral('La confirmación venció o el contenido cambió. Vuelve a pedir la vista previa.');
      } else {
        mostrarErrores(err, 'No se pudo enviar el aviso');
      }
    } finally {
      setEnviando(false);
      setConfirmar(false);
    }
  };

  const campoError = (campo) => errores[campo] && <span id={`masivo-${campo}-error`} className="campo-error">{errores[campo]}</span>;

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Avisos masivos</h1>
          <p className="pagina-subtitulo">
            Envía un aviso a muchas cuentas a la vez. Llega a su bandeja y, según cómo lo eligió cada persona, al teléfono o al correo.
            Máximo 1000 cuentas por envío y 3 envíos al día.
          </p>
        </div>
      </div>

      <form className="tarjeta formulario campana-form" onSubmit={pedirVista} noValidate aria-label="Nuevo aviso masivo">
        <div className="campo">
          <label className="campo-label" htmlFor="masivo-titulo">Título *</label>
          <input id="masivo-titulo" className={`input${errores.titulo ? ' input-error' : ''}`} maxLength={150} value={form.titulo}
            onChange={cambiar('titulo')} aria-invalid={Boolean(errores.titulo)} aria-describedby={errores.titulo ? 'masivo-titulo-error' : undefined}
            placeholder="Ej. La biblioteca cierra el lunes" />
          {campoError('titulo')}
        </div>
        <div className="campo">
          <label className="campo-label" htmlFor="masivo-mensaje">Mensaje *</label>
          <textarea id="masivo-mensaje" className={`input textarea${errores.mensaje ? ' input-error' : ''}`} rows={3} maxLength={1000}
            value={form.mensaje} onChange={cambiar('mensaje')} aria-invalid={Boolean(errores.mensaje)}
            aria-describedby={errores.mensaje ? 'masivo-mensaje-error' : undefined} />
          <span className="nota-tabla">{form.mensaje.length}/1000</span>
          {campoError('mensaje')}
        </div>
        <div className="campana-fila">
          <div className="campo">
            <label className="campo-label" htmlFor="masivo-enlace">Enlace (opcional)</label>
            <input id="masivo-enlace" className={`input${errores.enlace ? ' input-error' : ''}`} value={form.enlace} spellCheck={false}
              onChange={cambiar('enlace')} aria-invalid={Boolean(errores.enlace)} aria-describedby={errores.enlace ? 'masivo-enlace-error' : undefined}
              placeholder="/explorar" />
            {campoError('enlace')}
          </div>
          <div className="campo">
            <label className="campo-label" htmlFor="masivo-grupo">Para *</label>
            <select id="masivo-grupo" className="input" value={form.destinatarios} onChange={cambiar('destinatarios')}>
              {GRUPOS.map((g) => <option key={g.valor} value={g.valor}>{g.texto}</option>)}
            </select>
            {campoError('destinatarios')}
          </div>
        </div>
        {errorGeneral && <p className="campo-error" role="alert">{errorGeneral}</p>}

        {vista && (
          <div className="masivo-vista" role="status">
            <p><strong>Lo recibirán {vista.destinatarios} cuentas</strong> ({nombreGrupo(form.destinatarios).toLowerCase()}).</p>
            {vista.muestra?.length > 0 && (
              <p className="nota-tabla">Por ejemplo: {vista.muestra.map((m) => m.nombre).join(', ')}.</p>
            )}
            {vista.expiraEn && <p className="nota-tabla">La confirmación vale hasta las {fechaHora(vista.expiraEn)}.</p>}
          </div>
        )}

        <div className="acciones-fila">
          <button type="submit" className="btn btn-secundario" disabled={pidiendo || enviando}>{pidiendo ? 'Calculando...' : 'Vista previa'}</button>
          {vista && (
            <button type="button" className="btn btn-primario" onClick={() => setConfirmar(true)} disabled={enviando || vista.destinatarios === 0}>
              Enviar a {vista.destinatarios} cuentas
            </button>
          )}
        </div>
      </form>

      <section className="respaldos-historial" aria-labelledby="titulo-envios">
        <h2 id="titulo-envios" className="seccion-titulo">Historial de avisos masivos</h2>
        {errorHistorial && (
          <div className="banner-error" role="alert">
            <span><Icono nombre="alerta" tamano={18} /> {errorHistorial}</span>
            <button type="button" className="btn-fantasma btn-reintentar" onClick={cargarHistorial}>Reintentar</button>
          </div>
        )}
        {historial.results.length === 0 ? (!errorHistorial && <p className="panel-vacio">Todavía no se envió ningún aviso masivo.</p>) : (
          <>
            <div className="tarjeta tabla-contenedor">
              <table className="tabla">
                <thead><tr><th>Aviso</th><th>Para</th><th>Leídas</th><th>Enviado</th><th>Detalle</th></tr></thead>
                <tbody>
                  {historial.results.map((e) => (
                    <tr key={e.id}>
                      <td data-label="Aviso">{e.titulo}<span className="celda-detalle">{e.mensaje}</span></td>
                      <td data-label="Para">{nombreGrupo(e.destinatarios)}<span className="celda-detalle">{e.totalDestinatarios} cuentas</span></td>
                      <td data-label="Leídas">{e.leidas} de {e.totalDestinatarios}</td>
                      <td data-label="Enviado">{fechaHora(e.enviadoEn)}<span className="celda-detalle">{e.autor?.nombre}</span></td>
                      <td data-label="Detalle">
                        <button type="button" className="btn btn-fantasma btn-mini" onClick={() => setDetalle(e)} aria-label={`Ver destinatarios de ${e.titulo}`}>Destinatarios</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacion pagina={pagina} lista={historial} onCambiar={setPagina} />
          </>
        )}
        {detalle && <Destinatarios envio={detalle} onCerrar={() => setDetalle(null)} />}
      </section>

      <ConfirmarDialogo
        abierto={confirmar}
        titulo={`¿Enviar el aviso a ${vista?.destinatarios ?? 0} cuentas?`}
        mensaje={`«${form.titulo.trim()}» llegará a ${nombreGrupo(form.destinatarios).toLowerCase()}. Un envío no se puede cambiar ni borrar.`}
        textoConfirmar="Sí, enviar"
        procesando={enviando}
        onConfirmar={enviar}
        onCancelar={() => setConfirmar(false)}
      />
    </div>
  );
}
