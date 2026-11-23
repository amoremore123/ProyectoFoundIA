import { useCallback, useEffect, useRef, useState } from 'react';
import Icono from './Icono';
import { getPreferenciasNotificaciones, guardarPreferenciasNotificaciones, mensajeError } from '../services/api';

const OPCIONES = [
  { campo: 'coincidencias', etiqueta: 'Posibles coincidencias', ayuda: 'Cuando aparece un objeto encontrado parecido a uno que perdiste.' },
  { campo: 'contactos', etiqueta: 'Solicitudes de contacto', ayuda: 'Cuando el dueño de un objeto perdido quiere contactarte por algo que encontraste.' },
  { campo: 'reclamos', etiqueta: 'Reclamos', ayuda: 'Cuando alguien dice que un objeto que publicaste es suyo.' },
  // H53: recordatorio cuando un objeto que perdiste sigue sin resolverse después de varios días
  { campo: 'recordatorios', etiqueta: 'Recordatorios', ayuda: 'Cuando un objeto que perdiste sigue sin resolverse después de varios días.' },
];

// H68: por dónde llega cada tipo de aviso, además de la bandeja (que siempre lo recibe). Por defecto, PUSH.
export const TIPOS_CANAL = [
  { clave: 'coincidencias', etiqueta: 'Posibles coincidencias' },
  { clave: 'contactos', etiqueta: 'Solicitudes de contacto' },
  { clave: 'reclamos', etiqueta: 'Reclamos' },
  { clave: 'recordatorios', etiqueta: 'Recordatorios' },
  { clave: 'mensajes', etiqueta: 'Mensajes del chat' },
  { clave: 'sistema', etiqueta: 'Avisos de la biblioteca' },
];
export const CANALES = [
  { valor: 'PUSH', texto: 'Notificación en el teléfono' },
  { valor: 'CORREO', texto: 'Solo correo' },
  { valor: 'AMBOS', texto: 'Teléfono y correo' },
  { valor: 'NINGUNO', texto: 'Solo en esta bandeja' },
];
const VALIDOS = CANALES.map((c) => c.valor);
const valoresDe = (data) => ({
  ...Object.fromEntries(OPCIONES.map(({ campo }) => [campo, data?.[campo] !== false])),
  canales: Object.fromEntries(TIPOS_CANAL.map(({ clave }) => [clave, VALIDOS.includes(data?.canales?.[clave]) ? data.canales[clave] : 'PUSH'])),
});

const iguales = (a, b) => Boolean(a && b) && OPCIONES.every(({ campo }) => a[campo] === b[campo])
  && TIPOS_CANAL.every(({ clave }) => a.canales[clave] === b.canales[clave]);

// H24 - Preferencias de avisos de la cuenta propia (coincidencias, contactos y reclamos).
// Los cambios solo afectan a los avisos nuevos; el historial se conserva.
export default function PreferenciasNotificaciones() {
  const [guardadas, setGuardadas] = useState(null);
  const [form, setForm] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorGuardar, setErrorGuardar] = useState('');
  const [aviso, setAviso] = useState('');
  const [guardando, setGuardando] = useState(false);
  const peticion = useRef(null);

  const cargar = useCallback(async () => {
    peticion.current?.abort();
    const control = new AbortController();
    peticion.current = control;
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getPreferenciasNotificaciones({ signal: control.signal });
      if (control.signal.aborted) return;
      const valores = valoresDe(data);
      setGuardadas(valores);
      setForm(valores);
    } catch (e) {
      if (control.signal.aborted) return;
      setErrorCarga(mensajeError(e, 'No se pudieron cargar tus preferencias'));
    } finally {
      if (!control.signal.aborted) setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
    return () => peticion.current?.abort();
  }, [cargar]);

  const cambiar = (campo) => (e) => {
    setAviso('');
    setErrorGuardar('');
    setForm((f) => ({ ...f, [campo]: e.target.checked }));
  };

  const cambiarCanal = (clave) => (e) => {
    setAviso('');
    setErrorGuardar('');
    const valor = e.target.value;
    setForm((f) => ({ ...f, canales: { ...f.canales, [clave]: valor } }));
  };

  const guardar = async (e) => {
    e.preventDefault();
    setGuardando(true);
    setErrorGuardar('');
    setAviso('');
    try {
      const data = await guardarPreferenciasNotificaciones(form);
      const valores = valoresDe(data);
      setGuardadas(valores);
      setForm(valores);
      setAviso('Preferencias guardadas. Se aplican a los avisos nuevos.');
    } catch (err) {
      setErrorGuardar(mensajeError(err, 'No se pudieron guardar tus preferencias'));
    } finally {
      setGuardando(false);
    }
  };

  const hayCambios = form && !iguales(form, guardadas);

  return (
    <details className="tarjeta preferencias">
      <summary className="preferencias-resumen">
        <Icono nombre="campana" tamano={16} /> Preferencias de avisos
      </summary>

      {cargando && <p className="nota-suave" role="status">Cargando tus preferencias...</p>}

      {!cargando && errorCarga && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {errorCarga}
          <button type="button" className="btn-enlace" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {!cargando && form && (
        <form className="preferencias-form" onSubmit={guardar} noValidate>
          {OPCIONES.map(({ campo, etiqueta, ayuda }) => (
            <label key={campo} className="preferencia">
              <input type="checkbox" checked={form[campo]} onChange={cambiar(campo)} disabled={guardando} />
              <span>
                <span className="preferencia-etiqueta">{etiqueta}</span>
                <span className="preferencia-ayuda">{ayuda}</span>
              </span>
            </label>
          ))}
          <p className="nota-suave">Desactivar un tipo no borra los avisos que ya recibiste.</p>
          <fieldset className="preferencias-canales">
            <legend className="preferencia-etiqueta">¿Por dónde te avisamos?</legend>
            <p className="preferencia-ayuda">
              Siempre los verás en esta bandeja. Elige si además llegan al teléfono, al correo o a ambos.
              Del chat llega como máximo un correo cada 30 minutos.
            </p>
            {TIPOS_CANAL.map(({ clave, etiqueta }) => (
              <label key={clave} className="preferencia-canal">
                <span className="preferencia-etiqueta">{etiqueta}</span>
                <select className="input" value={form.canales[clave]} onChange={cambiarCanal(clave)} disabled={guardando}>
                  {CANALES.map((c) => <option key={c.valor} value={c.valor}>{c.texto}</option>)}
                </select>
              </label>
            ))}
          </fieldset>
          {errorGuardar && <p role="alert" className="campo-error">{errorGuardar}</p>}
          {aviso && <p role="status" className="aviso-accion aviso-ok">{aviso}</p>}
          <div className="preferencias-botones">
            <button type="button" className="btn btn-secundario" disabled={!hayCambios || guardando}
              onClick={() => { setForm(guardadas); setErrorGuardar(''); }}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primario" disabled={!hayCambios || guardando}>
              {guardando ? 'Guardando...' : 'Guardar preferencias'}
            </button>
          </div>
        </form>
      )}
    </details>
  );
}
