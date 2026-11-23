import { useCallback, useEffect, useState } from 'react';
import Icono from './Icono';
import { erroresDeValidacion, getPromociones, getSugerenciaPromocion, mensajeError, registrarPromocion, statusDe } from '../services/api';

// H66: preparar el texto de una campaña para una red (con su enlace de origen), copiarlo para publicarlo a
// mano, y registrar después lo que se publicó. FoundIA no publica en las redes por su cuenta.
export const REDES = [
  { valor: 'FACEBOOK', texto: 'Facebook', maximo: 2200 },
  { valor: 'INSTAGRAM', texto: 'Instagram', maximo: 2200 },
  { valor: 'TIKTOK', texto: 'TikTok', maximo: 2200 },
  { valor: 'X', texto: 'X (Twitter)', maximo: 280 },
  { valor: 'LINKEDIN', texto: 'LinkedIn', maximo: 2200 },
  { valor: 'WHATSAPP', texto: 'WhatsApp', maximo: 2200 },
  { valor: 'OTRA', texto: 'Otra red', maximo: 2200 },
];
const maximoDe = (red) => REDES.find((r) => r.valor === red)?.maximo || 2200;
const nombreRed = (red) => REDES.find((r) => r.valor === red)?.texto || red;

// El input datetime-local no trae zona: se envía con la de Lima (UTC−5)
export function conZonaLima(local) {
  return local ? `${local.length === 16 ? `${local}:00` : local}-05:00` : '';
}
function ahoraLocal() {
  const d = new Date(Date.now() - 5 * 3600000);
  return d.toISOString().slice(0, 16);
}

export default function PromocionCampana({ campana, onCerrar }) {
  const [red, setRed] = useState('FACEBOOK');
  const [texto, setTexto] = useState('');
  const [preparando, setPreparando] = useState(false);
  const [errorPreparar, setErrorPreparar] = useState('');
  const [copiado, setCopiado] = useState(false);
  const [publicadaEn, setPublicadaEn] = useState(ahoraLocal);
  const [url, setUrl] = useState('');
  const [errores, setErrores] = useState({});
  const [errorRegistro, setErrorRegistro] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [aviso, setAviso] = useState('');
  const [lista, setLista] = useState(null);

  const cargarLista = useCallback(async () => {
    try { setLista((await getPromociones({ campana: campana.id }, 1)).results); } catch { setLista([]); }
  }, [campana.id]);
  useEffect(() => { cargarLista(); }, [cargarLista]);

  const maximo = maximoDe(red);
  const preparar = async () => {
    setPreparando(true);
    setErrorPreparar('');
    setCopiado(false);
    try {
      const s = await getSugerenciaPromocion(campana.id, red);
      setTexto(s.texto || '');
    } catch (e) {
      setErrorPreparar(mensajeError(e, 'No se pudo preparar el texto'));
    } finally {
      setPreparando(false);
    }
  };

  const copiar = async () => {
    try { await navigator.clipboard.writeText(texto); setCopiado(true); } catch { setCopiado(false); }
  };

  const registrar = async (e) => {
    e.preventDefault();
    const encontrados = {};
    const limpio = texto.trim();
    if (limpio.length < 3 || limpio.length > maximo) encontrados.texto = `El texto debe tener entre 3 y ${maximo} caracteres para ${nombreRed(red)}.`;
    if (!publicadaEn) encontrados.publicadaEn = 'Indica cuándo se publicó.';
    if (url.trim() && !url.trim().startsWith('https://')) encontrados.urlPublicacion = 'La dirección debe empezar con https://';
    setErrores(encontrados);
    if (Object.keys(encontrados).length) return;
    setGuardando(true);
    setErrorRegistro('');
    setAviso('');
    try {
      await registrarPromocion({
        campanaId: campana.id, red, texto: limpio, publicadaEn: conZonaLima(publicadaEn),
        ...(url.trim() ? { urlPublicacion: url.trim() } : {}),
      });
      setAviso(`Publicación en ${nombreRed(red)} registrada.`);
      setUrl('');
      cargarLista();
    } catch (err) {
      const campos = erroresDeValidacion(err);
      if (statusDe(err) === 400 && campos) {
        const { non_field_errors: general, ...resto } = campos;
        setErrores(resto);
        setErrorRegistro(general || '');
      } else {
        setErrorRegistro(mensajeError(err, 'No se pudo registrar la publicación'));
      }
    } finally {
      setGuardando(false);
    }
  };

  return (
    <section className="tarjeta formulario campana-form" aria-label={`Promocionar ${campana.nombre}`}>
      <div className="acciones-fila">
        <h2>Promocionar «{campana.nombre}» en redes</h2>
        <button type="button" className="btn btn-fantasma btn-mini" onClick={onCerrar}>Cerrar</button>
      </div>
      <p className="nota-tabla">
        1) Elige la red y prepara el texto con el enlace de la campaña. 2) Cópialo y publícalo en la red. 3) Registra aquí lo que publicaste.
      </p>
      <div className="campana-fila">
        <label className="campo">
          <span className="campo-label">Red</span>
          <select className="input" value={red} onChange={(e) => { setRed(e.target.value); setCopiado(false); }} disabled={preparando || guardando}>
            {REDES.map((r) => <option key={r.valor} value={r.valor}>{r.texto}</option>)}
          </select>
        </label>
        <div className="campo promocion-preparar">
          <button type="button" className="btn btn-secundario" onClick={preparar} disabled={preparando}>
            <Icono nombre="chispa" tamano={16} /> {preparando ? 'Preparando...' : 'Preparar texto'}
          </button>
        </div>
      </div>
      {errorPreparar && <p className="campo-error" role="alert">{errorPreparar}</p>}

      <form onSubmit={registrar} noValidate className="promocion-form">
        <div className="campo">
          <label className="campo-label" htmlFor="promo-texto">Texto de la publicación *</label>
          <textarea id="promo-texto" className={`input textarea${errores.texto ? ' input-error' : ''}`} rows={4} value={texto}
            onChange={(e) => { setTexto(e.target.value); setErrores((x) => ({ ...x, texto: undefined })); setCopiado(false); }}
            aria-invalid={Boolean(errores.texto)} aria-describedby={`promo-contador${errores.texto ? ' promo-texto-error' : ''}`}
            placeholder="Pulsa «Preparar texto» o escribe el tuyo" />
          <div className="acciones-fila">
            <span id="promo-contador" className={`nota-tabla${texto.length > maximo ? ' promocion-excedido' : ''}`}>{texto.length}/{maximo} caracteres para {nombreRed(red)}</span>
            <button type="button" className="btn btn-fantasma btn-mini" onClick={copiar} disabled={!texto}>{copiado ? 'Copiado' : 'Copiar'}</button>
          </div>
          {errores.texto && <span id="promo-texto-error" className="campo-error">{errores.texto}</span>}
        </div>
        <div className="campana-fila">
          <div className="campo">
            <label className="campo-label" htmlFor="promo-fecha">Publicada el *</label>
            <input id="promo-fecha" type="datetime-local" className={`input${errores.publicadaEn ? ' input-error' : ''}`} value={publicadaEn}
              max={ahoraLocal()} onChange={(e) => setPublicadaEn(e.target.value)} aria-invalid={Boolean(errores.publicadaEn)} />
            {errores.publicadaEn && <span className="campo-error">{errores.publicadaEn}</span>}
          </div>
          <div className="campo">
            <label className="campo-label" htmlFor="promo-url">Dirección de la publicación (opcional)</label>
            <input id="promo-url" className={`input${errores.urlPublicacion ? ' input-error' : ''}`} value={url} spellCheck={false}
              onChange={(e) => { setUrl(e.target.value); setErrores((x) => ({ ...x, urlPublicacion: undefined })); }}
              aria-invalid={Boolean(errores.urlPublicacion)} placeholder="https://www.instagram.com/p/…" />
            {errores.urlPublicacion && <span className="campo-error">{errores.urlPublicacion}</span>}
          </div>
        </div>
        {errorRegistro && <p className="campo-error" role="alert">{errorRegistro}</p>}
        {aviso && <p className="nota-tabla" role="status">{aviso}</p>}
        <div className="acciones-fila">
          <span className="nota-tabla">Registra solo lo que ya publicaste.</span>
          <button type="submit" className="btn btn-primario" disabled={guardando}>{guardando ? 'Registrando...' : 'Registrar publicación'}</button>
        </div>
      </form>

      <h3 className="seccion-titulo">Publicaciones registradas</h3>
      {lista === null ? <p className="panel-vacio" role="status">Cargando...</p> : lista.length === 0 ? (
        <p className="panel-vacio">Todavía no se registró ninguna publicación de esta campaña.</p>
      ) : (
        <ul className="promocion-lista">
          {lista.map((p) => (
            <li key={p.id}>
              <strong>{nombreRed(p.red)}</strong> · {new Date(p.publicadaEn).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })}
              <span className="celda-detalle">{p.texto}</span>
              {p.urlPublicacion && <a className="celda-detalle" href={p.urlPublicacion} target="_blank" rel="noopener noreferrer">Ver publicación</a>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
