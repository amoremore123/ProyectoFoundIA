import { useCallback, useEffect, useState } from 'react';
import {
  abrirArchivo, getConstanciaCustodia, getCustodias, getEtiquetaCustodia, guardarArchivo, mensajeError,
  registrarCustodia, verificarQrCustodia,
} from '../services/api';
import Icono from '../components/Icono';
import { useToast } from '../context/ToastContext';

const ESTADOS = { EN_CUSTODIA: 'En custodia', RETIRADO: 'Retirado' };
const SIN_FILTRO = { q: '', estado: '', desde: '', hasta: '' };
const MAX_OBSERVACIONES = 1000;

export function fechaHora(valor) {
  if (!valor) return '—';
  const d = new Date(valor);
  if (Number.isNaN(d.getTime())) return String(valor);
  return d.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
}

// H41 - Recepción de objetos encontrados en la biblioteca, con etiqueta QR y constancia.
export default function Custodia() {
  const { mostrarToast } = useToast();
  const [lista, setLista] = useState({ count: 0, results: [], next: null, previous: null });
  const [pagina, setPagina] = useState(1);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [filtros, setFiltros] = useState(SIN_FILTRO);
  const [aplicados, setAplicados] = useState(SIN_FILTRO);
  const [errorFiltro, setErrorFiltro] = useState('');

  const [form, setForm] = useState({ objetoId: '', observaciones: '' });
  const [errorForm, setErrorForm] = useState('');
  const [registrando, setRegistrando] = useState(false);
  const [registrada, setRegistrada] = useState(null);

  const [archivo, setArchivo] = useState(''); // "etiqueta-5" mientras se descarga
  const [errorArchivo, setErrorArchivo] = useState('');

  const [token, setToken] = useState('');
  const [verificando, setVerificando] = useState(false);
  const [verificacion, setVerificacion] = useState(null); // { ok, datos | mensaje }

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setLista(await getCustodias(aplicados, pagina));
    } catch (e) {
      setError(mensajeError(e, 'No se pudo cargar la custodia'));
      setLista({ count: 0, results: [], next: null, previous: null });
    } finally {
      setCargando(false);
    }
  }, [aplicados, pagina]);

  useEffect(() => { cargar(); }, [cargar]);

  const aplicar = (e) => {
    e.preventDefault();
    if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) {
      setErrorFiltro('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    setErrorFiltro('');
    setPagina(1);
    setAplicados({ ...filtros, q: filtros.q.trim() });
  };

  const limpiar = () => {
    setFiltros(SIN_FILTRO);
    setErrorFiltro('');
    setPagina(1);
    setAplicados(SIN_FILTRO);
  };

  const registrar = async (e) => {
    e.preventDefault();
    const id = Number(form.objetoId);
    if (!Number.isInteger(id) || id <= 0) {
      setErrorForm('Escribe el número de la publicación (por ejemplo, 12).');
      return;
    }
    if (form.observaciones.trim().length > MAX_OBSERVACIONES) {
      setErrorForm(`Las observaciones admiten hasta ${MAX_OBSERVACIONES} caracteres.`);
      return;
    }
    setRegistrando(true);
    setErrorForm('');
    try {
      const custodia = await registrarCustodia({ objetoId: id, observaciones: form.observaciones.trim() });
      setRegistrada(custodia);
      setForm({ objetoId: '', observaciones: '' });
      mostrarToast(`Objeto recibido con el código ${custodia.codigo}.`, 'exito');
      setPagina(1);
      cargar();
    } catch (err) {
      setErrorForm(mensajeError(err, 'No se pudo registrar la recepción'));
    } finally {
      setRegistrando(false);
    }
  };

  const descargar = async (custodia, tipo) => {
    setArchivo(`${tipo}-${custodia.id}`);
    setErrorArchivo('');
    try {
      if (tipo === 'etiqueta') {
        abrirArchivo(await getEtiquetaCustodia(custodia.id));
      } else {
        guardarArchivo(await getConstanciaCustodia(custodia.id), `constancia-${custodia.codigo}.pdf`);
      }
    } catch (err) {
      setErrorArchivo(mensajeError(err, `No se pudo generar la ${tipo}`));
    } finally {
      setArchivo('');
    }
  };

  const verificar = async (e) => {
    e.preventDefault();
    const valor = token.trim();
    if (!valor) { setVerificacion({ ok: false, mensaje: 'Pega o escanea el contenido del QR de la etiqueta.' }); return; }
    setVerificando(true);
    try {
      setVerificacion({ ok: true, datos: await verificarQrCustodia(valor) });
    } catch (err) {
      setVerificacion({ ok: false, mensaje: mensajeError(err, 'No se pudo verificar el QR') });
    } finally {
      setVerificando(false);
    }
  };

  const hayFiltro = Object.values(aplicados).some(Boolean) || Object.values(filtros).some(Boolean);

  const botonesArchivo = (c) => (
    <span className="acciones-fila">
      <button type="button" className="btn btn-fantasma btn-mini" onClick={() => descargar(c, 'etiqueta')}
        disabled={Boolean(archivo)} aria-label={`Abrir la etiqueta QR de ${c.codigo}`}>
        <Icono nombre="qr" tamano={14} /> {archivo === `etiqueta-${c.id}` ? 'Generando...' : 'Etiqueta'}
      </button>
      <button type="button" className="btn btn-fantasma btn-mini" onClick={() => descargar(c, 'constancia')}
        disabled={Boolean(archivo)} aria-label={`Descargar la constancia de ${c.codigo}`}>
        <Icono nombre="archivo" tamano={14} /> {archivo === `constancia-${c.id}` ? 'Generando...' : 'Constancia'}
      </button>
    </span>
  );

  return (
    <div className="pagina">
      <div className="seccion-header">
        <div>
          <h1 className="pagina-titulo">Custodia</h1>
          <p className="pagina-subtitulo">Objetos encontrados que se entregaron en la biblioteca.</p>
        </div>
        <span className="contador">{lista.count} registros</span>
      </div>

      <div className="custodia-fila">
        <form className="tarjeta formulario" onSubmit={registrar} noValidate aria-labelledby="titulo-recepcion">
          <h2 id="titulo-recepcion"><Icono nombre="caja" tamano={18} /> Registrar recepción</h2>
          <label className="campo">
            <span className="campo-label">Número de publicación *</span>
            <input className={`input${errorForm ? ' input-error' : ''}`} inputMode="numeric" value={form.objetoId}
              disabled={registrando} placeholder="Ej. 12"
              onChange={(e) => { const objetoId = e.target.value.replace(/\D/g, ''); setForm((f) => ({ ...f, objetoId })); setErrorForm(''); }} />
            <span className="nota-tabla">Aparece como «#12» en la publicación. Solo se reciben objetos de tipo Encontrado.</span>
          </label>
          <label className="campo">
            <span className="campo-label">Observaciones</span>
            <textarea className="input textarea" rows={2} maxLength={MAX_OBSERVACIONES} value={form.observaciones}
              disabled={registrando} placeholder="Ej. Entregado en el mostrador, con el cierre roto."
              onChange={(e) => { const observaciones = e.target.value; setForm((f) => ({ ...f, observaciones })); }} />
          </label>
          {errorForm && <p className="campo-error" role="alert">{errorForm}</p>}
          <button type="submit" className="btn btn-primario" disabled={registrando}>
            {registrando ? 'Registrando...' : 'Registrar recepción'}
          </button>

          {registrada && (
            <div className="custodia-resultado" role="status">
              <p><Icono nombre="check" tamano={16} /> <strong>{registrada.codigo}</strong> · {registrada.objeto?.nombre}</p>
              <p className="nota-tabla">Imprime la etiqueta para el objeto y entrega la constancia a quien lo dejó.</p>
              {botonesArchivo(registrada)}
            </div>
          )}
        </form>

        <form className="tarjeta formulario" onSubmit={verificar} noValidate aria-labelledby="titulo-verificar">
          <h2 id="titulo-verificar"><Icono nombre="qr" tamano={18} /> Verificar etiqueta</h2>
          <label className="campo">
            <span className="campo-label">Contenido del QR</span>
            <input className="input" value={token} disabled={verificando} autoComplete="off"
              placeholder="Escanea el QR con el lector o pega el texto"
              onChange={(e) => { setToken(e.target.value); setVerificacion(null); }} />
          </label>
          <button type="submit" className="btn btn-secundario" disabled={verificando}>
            {verificando ? 'Verificando...' : 'Verificar'}
          </button>
          {verificacion && (verificacion.ok ? (
            <div className="custodia-resultado" role="status">
              <p><Icono nombre="check" tamano={16} /> Etiqueta válida: <strong>{verificacion.datos.codigo}</strong></p>
              <p className="nota-tabla">{verificacion.datos.objeto?.nombre} · {ESTADOS[verificacion.datos.estado] || verificacion.datos.estado} · recibido {fechaHora(verificacion.datos.fechaRecepcion)}</p>
            </div>
          ) : (
            <p className="campo-error" role="alert">{verificacion.mensaje}</p>
          ))}
        </form>
      </div>

      <form className="tarjeta filtro-fechas filtro-custodia" onSubmit={aplicar} noValidate aria-label="Filtrar custodia">
        <label className="campo">
          <span className="campo-label">Buscar</span>
          <input className="input" value={filtros.q} placeholder="Código o nombre del objeto"
            onChange={(e) => setFiltros((f) => ({ ...f, q: e.target.value }))} />
        </label>
        <label className="campo">
          <span className="campo-label">Estado</span>
          <select className="input" value={filtros.estado} onChange={(e) => setFiltros((f) => ({ ...f, estado: e.target.value }))}>
            <option value="">Todos</option>
            <option value="EN_CUSTODIA">En custodia</option>
            <option value="RETIRADO">Retirado</option>
          </select>
        </label>
        <label className="campo">
          <span className="campo-label">Recibido desde</span>
          <input type="date" className={`input ${errorFiltro ? 'input-error' : ''}`} value={filtros.desde}
            onChange={(e) => setFiltros((f) => ({ ...f, desde: e.target.value }))} />
        </label>
        <label className="campo">
          <span className="campo-label">Hasta</span>
          <input type="date" className={`input ${errorFiltro ? 'input-error' : ''}`} value={filtros.hasta}
            onChange={(e) => setFiltros((f) => ({ ...f, hasta: e.target.value }))} />
        </label>
        <div className="filtro-acciones">
          <button type="submit" className="btn btn-primario" disabled={cargando}>Aplicar</button>
          {hayFiltro && <button type="button" className="btn btn-fantasma" onClick={limpiar} disabled={cargando}>Limpiar</button>}
        </div>
        {errorFiltro && <p className="campo-error filtro-error" role="alert">{errorFiltro}</p>}
      </form>

      {error && (
        <div className="banner-error" role="alert">
          <span><Icono nombre="alerta" tamano={18} /> {error}</span>
          <button type="button" className="btn-fantasma btn-reintentar" onClick={cargar}>Reintentar</button>
        </div>
      )}
      {errorArchivo && (
        <div className="banner-error" role="alert"><Icono nombre="alerta" tamano={18} /> {errorArchivo}</div>
      )}

      {cargando ? (
        <div className="cargando" role="status"><span className="spinner" aria-hidden="true" /><span>Cargando...</span></div>
      ) : error ? null : lista.results.length === 0 ? (
        <div className="estado-vacio">
          <span className="estado-vacio-icono" aria-hidden="true"><Icono nombre="caja" tamano={28} /></span>
          <p>{Object.values(aplicados).some(Boolean) ? 'Ningún objeto coincide con los filtros.' : 'Aún no se recibió ningún objeto en custodia.'}</p>
        </div>
      ) : (
        <>
          <div className="tarjeta tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr><th>Código</th><th>Objeto</th><th>Recibido por</th><th>Fecha</th><th>Estado</th><th>Documentos</th></tr>
              </thead>
              <tbody>
                {lista.results.map((c) => (
                  <tr key={c.id}>
                    <td data-label="Código"><strong>{c.codigo}</strong></td>
                    <td data-label="Objeto">
                      {c.objeto?.nombre} <span className="celda-detalle">#{c.objeto?.id} · {c.objeto?.categoria || 'Sin categoría'}</span>
                    </td>
                    <td data-label="Recibido por">{c.recibidoPor?.nombre || '—'}</td>
                    <td data-label="Fecha">{fechaHora(c.fechaRecepcion)}</td>
                    <td data-label="Estado">
                      <span className={`chip-estado ${c.estado === 'RETIRADO' ? 'recuperado' : 'activo'}`}>{ESTADOS[c.estado] || c.estado}</span>
                    </td>
                    <td data-label="Documentos">{botonesArchivo(c)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} lista={lista} onCambiar={setPagina} />
        </>
      )}
    </div>
  );
}

export function Paginacion({ pagina, lista, onCambiar }) {
  if (!lista.next && !lista.previous) return null;
  const total = Math.max(1, Math.ceil(lista.count / 10));
  return (
    <nav className="paginacion" aria-label="Paginación">
      <button type="button" className="btn btn-fantasma btn-mini" disabled={!lista.previous} onClick={() => onCambiar(pagina - 1)}>
        <Icono nombre="flechaIzq" tamano={14} /> Anterior
      </button>
      <span className="nota-tabla">Página {pagina} de {total}</span>
      <button type="button" className="btn btn-fantasma btn-mini" disabled={!lista.next} onClick={() => onCambiar(pagina + 1)}>
        Siguiente <Icono nombre="flechaDer" tamano={14} />
      </button>
    </nav>
  );
}
